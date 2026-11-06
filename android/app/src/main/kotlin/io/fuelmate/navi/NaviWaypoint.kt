package io.fuelmate.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.fuelmate.Log
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.WaypointState
import io.fuelmate.core.store.Action

/**
 * 추천 주유소를 내비에 넣고, 내비가 다시 계산한 목적지 거리로 실제 우회 거리를 구한다(docs/pivot-fuel.md 6장).
 * `WaypointChangedInfo.routeStartInfo.distance`는 경유지까지 거리다(2,300m 경로에 넣자 경유지 4,800m, 목적지 9,200m, 2026-11-06 확인).
 * 그래서 넣은 뒤의 목적지 거리는 `onDrivingInfo`·`onDestinationInfo`에서 받는다.
 * - 안내 중이면 첫 번째 경유지로 넣는다(`addWaypoint`, WaypointIndex.FIRST)
 * - 안내 중이 아니면 주유소를 목적지로 안내한다(`requestRoute`). 이때는 비교할 경로가 없어 우회 거리가 없다
 * 시그니처는 NaviHelper 3.0.5·navi-data 3.0.4 javap와 spikes/fuel-probe에서 확인한 것만 쓴다.
 * POI ID 없이 좌표·이름만 넣는다(문서 미확인, docs/pivot-fuel.md 11장 6번). 로그에 좌표를 남기지 않는다.
 */
class NaviWaypoint(context: Context, private val dispatch: (Action) -> Unit) {

    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var connected = false
    private var routing = false
    /** 가장 최근 목적지까지 남은 거리(m). 경유지를 넣기 전 값과 비교한다 */
    private var remainM: Int? = null
    private var pending: Pending? = null
    /** 경유지는 들어갔고, 내비가 다시 계산한 목적지 거리를 기다리는 중 */
    private var awaitingAfter: Pending? = null
    private var awaitingSinceMs = 0L
    /** 화면에 "경유 중/안내 중"을 띄웠나 */
    private var shown = false

    private class Pending(val station: Station, val beforeM: Int?)

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            // isRouting은 안내 중에도 false로 온다(2026-11-05 에뮬레이터: 주유소로 안내 중인데 isRouting=false,
            // naviMode=ROUTE_GUIDING). 그래서 안내 중인지는 naviMode로 본다. SAFE_GUIDING은 경로 없는 안전운전 모드다
            val now = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            if (now != routing) Log.i("내비 안내 상태: ${if (now) "안내 중" else "안내 없음"}(${routeStateInfo.naviMode})")
            routing = now
            // 안내가 그사이 취소되면 거리가 오지 않는다. 그때는 실제 우회를 모르는 채로 둔다
            if (awaitingAfter != null && SystemClock.elapsedRealtime() - awaitingSinceMs > AWAIT_AFTER_MS) awaitingAfter = null
            // 경유지를 넣는 동안 잠깐 SAFE_GUIDING이 된다. 그 사이에는 넣기 전 거리를 지우지 않는다
            if (!now && pending == null && awaitingAfter == null) {
                remainM = null
                // 도착·취소로 안내가 끝났으면 화면의 "경유 중/안내 중"을 지운다
                if (shown && pending == null) {
                    shown = false
                    dispatch(Action.WaypointSet(null))
                }
            }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val d = drivingInfo.destination.distance
            if (d > 0 && pending == null) {
                remainM = d
                resolveDetour(d)
            }
        }

        // 넣기 전 목적지까지 남은 거리. onDrivingInfo는 경유지를 넣기 전에 오지 않았다(2026-11-05) → 안내 중이면 주기적으로 묻는다.
        // DestinationInfo에는 좌표가 들어 있어 남은 거리만 쓰고 로그에 남기지 않는다
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo.remainDistance
            if (d > 0 && pending == null) resolveDetour(d)
            if (routing && d > 0 && pending == null) remainM = d
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i("내비 경로 시작: ${routeStartInfo.distance}m, ${routeStartInfo.duration}초")
            routing = true
            if (remainM == null) remainM = routeStartInfo.distance
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i("내비 목적지 도착")
            if (shown) {
                shown = false
                dispatch(Action.WaypointSet(null))
            }
        }

        override fun onRouteCancelled() {
            // 경유지를 넣은 직후에도 한 번 오고 다시 경로가 잡힌다(스파이크 확인). 상태만 다시 묻는다
            navi.getRouteStateInfo()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            val p = pending ?: return
            if (waypointChangedInfo.cmdType != WaypointCmdType.ADD) return
            pending = null
            Log.i("경유지 추가됨: ${p.station.name}")
            dispatch(Action.WaypointSet(WaypointState(p.station.id, p.station.name, asDestination = false, added = true)))
            awaitingAfter = p
            awaitingSinceMs = SystemClock.elapsedRealtime()
            navi.getDestinationInfo()
        }

        override fun onError(errorCode: NaviErrorCode) = Log.w("내비 오류 $errorCode")
    }

    /** 경유지를 넣은 뒤 처음 온 목적지 거리로 실제 우회를 구한다. 말이 안 되는 값이면 모른다고 표시한다 */
    private fun resolveDetour(afterM: Int) {
        val p = awaitingAfter ?: return
        awaitingAfter = null
        val detour = WaypointState.detourOrNull(p.beforeM, afterM)
        Log.i("실제 우회: 목적지 거리 ${p.beforeM ?: "?"} → ${afterM}m, 우회 ${detour ?: "확인 못 함"}")
        dispatch(Action.WaypointSet(WaypointState(p.station.id, p.station.name, asDestination = false, detourM = detour, added = true)))
    }

    private val poll = object : Runnable {
        override fun run() {
            navi.getRouteStateInfo()
            if (routing) navi.getDestinationInfo()
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        navi.initialize({
            connected = true
            navi.addListener(listener)
            main.post(poll)
        }, { Log.w("NaviHelper(경유지) 연결 실패", it) })
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { navi.removeListener(listener) }
        navi.release()
    }

    fun add(station: Station) {
        if (!connected) return Log.w("내비에 아직 연결되지 않음")
        val p = station.point
        if (routing) {
            awaitingAfter = null
            pending = Pending(station, remainM)
            navi.addWaypoint(RequestWaypointInfo(p.lat, p.lon, station.name, WaypointIndex.FIRST, "", "", "", ""))
            Log.i("경유지 추가 요청: ${station.name}")
            dispatch(Action.WaypointSet(WaypointState(station.id, station.name, asDestination = false)))
            shown = true
        } else {
            pending = null
            awaitingAfter = null
            navi.requestRoute(RouteInfo(p.lat, p.lon, null, null, station.name, "", "", "", RouteOption.RECOMMENDED, "", false))
            Log.i("안내 중이 아니라 목적지로 안내 요청: ${station.name}")
            dispatch(Action.WaypointSet(WaypointState(station.id, station.name, asDestination = true)))
            shown = true
        }
    }

    private companion object {
        /** 넣기 전 거리가 이만큼 늦을 수 있다(시속 100km면 약 140m 덜 센다) */
        const val POLL_MS = 5_000L
        const val AWAIT_AFTER_MS = 30_000L
    }
}
