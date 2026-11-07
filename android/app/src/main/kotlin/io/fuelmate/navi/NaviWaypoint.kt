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
 *
 * 경유지를 넣는 동안 내비는 7~8초 동안 SAFE_GUIDING(안내 없음)이 되고 목적지도 비었다가 돌아온다(2026-11-08 에뮬레이터 두 번).
 * 그래서 넣는 중에는 "안내가 끝났다"로 보지 않는다. 다만 [PENDING_TIMEOUT_MS] 안에 추가 응답이 없으면 넣기를 포기한다.
 * 그 사이 안내를 끝냈거나 내비가 요청을 받지 않았을 때 화면이 "넣는 중"에 멈추지 않게 하려는 것이다.
 *
 * 경유지가 있는 채로 다른 주유소를 고르면 바꾼다: 앞의 경유지를 빼고(`removeWaypoint(FIRST)`, 우리가 FIRST로 넣었다),
 * 뺀 뒤의 목적지 거리를 받아 그 값을 "넣기 전"으로 새 경유지를 넣는다. 그냥 또 넣으면 내비에 경유지가 쌓이고
 * 실제 우회도 두 번째 주유소 몫만 잡힌다(2026-11-08 "경유지 2개 포함" 확인). 넣거나 바꾸는 중에 온 요청은 넘긴다.
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
    /** 화면에 띄운 경유 상태. null이면 경유지 없음 */
    private var current: WaypointState? = null

    /** @property previous 넣기 전 화면 상태. 응답이 없으면 이 상태로 되돌린다 */
    private class Pending(val station: Station, val beforeM: Int?, val sinceMs: Long, val previous: WaypointState?)

    /** 경유지 바꾸기: 앞의 것을 빼는 중이거나([removed] false), 뺀 뒤 목적지 거리를 기다리는 중 */
    private class Replace(val next: Station, val sinceMs: Long, val previous: WaypointState) {
        var removed = false
    }
    private var replace: Replace? = null

    private fun publish(state: WaypointState?) {
        current = state
        dispatch(Action.WaypointSet(state))
    }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            // isRouting은 안내 중에도 false로 온다(2026-11-05 에뮬레이터: 주유소로 안내 중인데 isRouting=false,
            // naviMode=ROUTE_GUIDING). 그래서 안내 중인지는 naviMode로 본다. SAFE_GUIDING은 경로 없는 안전운전 모드다
            val now = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
            if (now != routing) Log.i("내비 안내 상태: ${if (now) "안내 중" else "안내 없음"}(${routeStateInfo.naviMode})")
            routing = now
            val nowMs = SystemClock.elapsedRealtime()
            // 넣는 중에 안내를 끝냈거나 내비가 요청을 받지 않았다: 넣기 전 상태로 되돌린다(안내가 끝났으면 바로 아래에서 지운다)
            pending?.let { p ->
                if (nowMs - p.sinceMs > PENDING_TIMEOUT_MS) {
                    Log.w("경유지 추가 응답 없음: ${p.station.name}")
                    pending = null
                    publish(p.previous)
                }
            }
            replace?.let { r ->
                if (nowMs - r.sinceMs > PENDING_TIMEOUT_MS) {
                    replace = null
                    when {
                        // 빼기 응답이 없다: 앞의 경유지가 그대로 있다고 본다
                        !r.removed -> { Log.w("경유지 빼기 응답 없음"); publish(r.previous) }
                        // 뺐지만 거리를 못 받았다: 우회 거리는 모르는 채로 넣는다
                        now -> requestAdd(r.next, beforeM = null, previous = null)
                        else -> publish(null)
                    }
                }
            }
            // 안내가 그사이 취소되면 거리가 오지 않는다. 그때는 실제 우회를 모르는 채로 둔다
            if (awaitingAfter != null && nowMs - awaitingSinceMs > AWAIT_AFTER_MS) awaitingAfter = null
            // 경유지를 넣는 동안 잠깐 SAFE_GUIDING이 된다. 그 사이에는 넣기 전 거리를 지우지 않는다
            if (!now && pending == null && awaitingAfter == null && replace == null) {
                remainM = null
                // 도착·취소로 안내가 끝났으면 화면의 "경유 중/안내 중"을 지운다. 목적지도 없어지므로 첫 화면으로 돌아간다
                if (current != null) {
                    Log.i("안내가 끝나 경유 상태를 지움")
                    publish(null)
                }
            }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val d = drivingInfo.destination.distance
            if (d > 0 && onBaseDistance(d)) return
            if (d > 0 && pending == null) {
                remainM = d
                resolveDetour(d)
            }
        }

        // 넣기 전 목적지까지 남은 거리. onDrivingInfo는 경유지를 넣기 전에 오지 않았다(2026-11-05) → 안내 중이면 주기적으로 묻는다.
        // DestinationInfo에는 좌표가 들어 있어 남은 거리만 쓰고 로그에 남기지 않는다
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo.remainDistance
            if (d > 0 && onBaseDistance(d)) return
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
            if (current != null) publish(null)
        }

        override fun onRouteCancelled() {
            // 경유지를 넣은 직후에도 한 번 오고 다시 경로가 잡힌다(스파이크 확인). 상태만 다시 묻는다
            navi.getRouteStateInfo()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            val r = replace
            if (r != null && !r.removed && waypointChangedInfo.cmdType == WaypointCmdType.REMOVE) {
                r.removed = true
                Log.i("앞의 경유지를 뺌, 목적지 거리를 다시 묻는 중")
                navi.getDestinationInfo()
                return
            }
            val p = pending ?: return
            if (waypointChangedInfo.cmdType != WaypointCmdType.ADD) return
            pending = null
            Log.i("경유지 추가됨: ${p.station.name}")
            publish(WaypointState(p.station.id, p.station.name, asDestination = false, added = true))
            awaitingAfter = p
            awaitingSinceMs = SystemClock.elapsedRealtime()
            navi.getDestinationInfo()
        }

        override fun onError(errorCode: NaviErrorCode) = Log.w("내비 오류 $errorCode")
    }

    /** 바꾸기에서 앞의 경유지를 뺀 뒤 처음 온 목적지 거리. 이 값을 "넣기 전"으로 새 경유지를 넣는다 */
    private fun onBaseDistance(d: Int): Boolean {
        val r = replace ?: return false
        if (!r.removed) return false
        replace = null
        remainM = d
        requestAdd(r.next, beforeM = d, previous = null)
        return true
    }

    /** 경유지를 넣은 뒤 처음 온 목적지 거리로 실제 우회를 구한다. 말이 안 되는 값이면 모른다고 표시한다 */
    private fun resolveDetour(afterM: Int) {
        val p = awaitingAfter ?: return
        awaitingAfter = null
        val detour = WaypointState.detourOrNull(p.beforeM, afterM)
        Log.i("실제 우회: 목적지 거리 ${p.beforeM ?: "?"} → ${afterM}m, 우회 ${detour ?: "확인 못 함"}")
        publish(WaypointState(p.station.id, p.station.name, asDestination = false, detourM = detour, added = true))
    }

    private val poll = object : Runnable {
        override fun run() {
            navi.getRouteStateInfo()
            if (routing || replace != null) navi.getDestinationInfo()
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
        if (pending != null || replace != null) return Log.i("경유지를 넣는 중이라 이번 요청은 넘김: ${station.name}")
        val cur = current
        if (cur != null && cur.stationId == station.id) return Log.i("이미 경유 중인 주유소: ${station.name}")
        val p = station.point
        if (routing && cur != null && cur.added && !cur.asDestination) {
            awaitingAfter = null
            replace = Replace(station, SystemClock.elapsedRealtime(), cur)
            navi.removeWaypoint(WaypointIndex.FIRST)
            Log.i("경유지 바꾸기: ${cur.stationName} 빼고 ${station.name} 넣기")
            publish(WaypointState(station.id, station.name, asDestination = false))
        } else if (routing) {
            awaitingAfter = null
            requestAdd(station, remainM, previous = cur)
        } else {
            pending = null
            awaitingAfter = null
            navi.requestRoute(RouteInfo(p.lat, p.lon, null, null, station.name, "", "", "", RouteOption.RECOMMENDED, "", false))
            Log.i("안내 중이 아니라 목적지로 안내 요청: ${station.name}")
            publish(WaypointState(station.id, station.name, asDestination = true))
        }
    }

    private fun requestAdd(station: Station, beforeM: Int?, previous: WaypointState?) {
        val p = station.point
        pending = Pending(station, beforeM, SystemClock.elapsedRealtime(), previous)
        navi.addWaypoint(RequestWaypointInfo(p.lat, p.lon, station.name, WaypointIndex.FIRST, "", "", "", ""))
        Log.i("경유지 추가 요청: ${station.name}")
        publish(WaypointState(station.id, station.name, asDestination = false))
    }

    private companion object {
        /** 넣기 전 거리가 이만큼 늦을 수 있다(시속 100km면 약 140m 덜 센다) */
        const val POLL_MS = 5_000L
        const val AWAIT_AFTER_MS = 15_000L
        /** 넣기 응답을 기다리는 시간. 에뮬레이터에서 응답까지 7초 걸렸다(2026-11-08) */
        const val PENDING_TIMEOUT_MS = 15_000L
    }
}
