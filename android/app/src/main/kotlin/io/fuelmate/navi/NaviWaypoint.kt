package io.fuelmate.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
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
import io.fuelmate.Log
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.WaypointState
import io.fuelmate.core.store.Action

/**
 * 추천 주유소를 내비에 넣고, 내비가 다시 계산한 목적지 거리로 실제 우회 거리를 구한다(docs/pivot-fuel.md 6장).
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

    private class Pending(val station: Station, val beforeM: Int?)

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            routing = routeStateInfo.isRouting
            if (!routing) remainM = null
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            remainM = drivingInfo.destination.distance
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            routing = true
            if (remainM == null) remainM = routeStartInfo.distance
        }

        override fun onRouteCancelled() {
            // 경유지를 넣은 직후에도 한 번 오고 다시 경로가 잡힌다(스파이크 확인). 상태만 다시 묻는다
            navi.getRouteStateInfo()
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            val p = pending ?: return
            if (waypointChangedInfo.cmdType != WaypointCmdType.ADD) return
            pending = null
            val after = waypointChangedInfo.routeStartInfo.distance
            val detour = p.beforeM?.let { (after - it).toDouble() }
            Log.i("경유지 추가됨: ${p.station.name}, 목적지 거리 ${p.beforeM ?: "?"} → ${after}m, 우회 ${detour ?: "?"}m")
            dispatch(Action.WaypointSet(WaypointState(p.station.id, p.station.name, asDestination = false, detourM = detour)))
        }

        override fun onError(errorCode: NaviErrorCode) = Log.w("내비 오류 $errorCode")
    }

    private val poll = object : Runnable {
        override fun run() {
            navi.getRouteStateInfo()
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
            pending = Pending(station, remainM)
            navi.addWaypoint(RequestWaypointInfo(p.lat, p.lon, station.name, WaypointIndex.FIRST, "", "", "", ""))
            Log.i("경유지 추가 요청: ${station.name}")
            dispatch(Action.WaypointSet(WaypointState(station.id, station.name, asDestination = false)))
        } else {
            pending = null
            navi.requestRoute(RouteInfo(p.lat, p.lon, null, null, station.name, "", "", "", RouteOption.RECOMMENDED, "", false))
            Log.i("안내 중이 아니라 목적지로 안내 요청: ${station.name}")
            dispatch(Action.WaypointSet(WaypointState(station.id, station.name, asDestination = true)))
        }
    }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
