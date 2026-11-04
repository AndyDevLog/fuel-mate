package io.fuelmate.fuelprobe

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context

/**
 * NaviHelper 3.0.5: 경로 요청 → 경유지 추가가 되는지, 도착 시 잔량·충전 사업자 정보가 오는지.
 * 시험 좌표는 에뮬레이터 고정 위치(성남 수정구 시흥동) 근처의 공개 장소다.
 */
class NaviFuelProbe(context: Context, private val log: (String) -> Unit) {

    private val navi = NaviHelper(context.applicationContext)
    private var connected = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("N 경로 시작 $routeStartInfo")
        override fun onRoutingCompleted() = log("N 경로 탐색 완료")
        override fun onRouteCancelled() = log("N 경로 취소")
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            log("N 경유지 변경 $waypointChangedInfo")
            navi.getWaypointInfo()
            navi.getRouteStateInfo()
        }
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) = log("N 경유지 목록 $waypointInfo")
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
            log("N 경로상태 routing=${routeStateInfo.isRouting} 도착잔량=${routeStateInfo.remainDestinationChargingInt} 경유지잔량=${routeStateInfo.remainWaypointChargingInt}")
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("N 주행정보 $drivingInfo")
        override fun onChargerOperatorInfo(chargerOperatorInfo: List<ChargerOperator>) = log("N 충전 사업자 ${chargerOperatorInfo.size}개 ${chargerOperatorInfo.take(5)}")
        override fun onError(errorCode: NaviErrorCode) = log("N 오류 $errorCode")
    }

    fun start() {
        navi.initialize({
            connected = true
            log("N 연결됨")
            navi.addListener(listener)
            navi.getRouteStateInfo()
            navi.getChargerOperatorInfo()
        }, { log("N 연결 실패: ${it.javaClass.simpleName} ${it.message}") })
    }

    fun stop() {
        runCatching { navi.removeListener(listener) }
        navi.release()
    }

    fun requestTestRoute() {
        if (!connected) return log("N 아직 연결 안 됨")
        // 판교역(공개 장소)
        navi.requestRoute(RouteInfo(37.3947, 127.1112, null, null, "판교역", "", "", "", RouteOption.RECOMMENDED, "", false))
        log("N requestRoute 보냄")
    }

    fun addTestWaypoint() {
        if (!connected) return log("N 아직 연결 안 됨")
        // 경로 중간쯤의 임의 지점. 주유소 POI ID는 없어서 좌표·이름만 넣는다
        navi.addWaypoint(RequestWaypointInfo(37.4060, 127.1180, "테스트 주유소", WaypointIndex.FIRST, "", "", "", ""))
        log("N addWaypoint 보냄")
    }

    fun requestChargerOperators() {
        if (!connected) return log("N 아직 연결 안 됨")
        navi.getChargerOperatorInfo()
        navi.getRouteStateInfo()
        log("N getChargerOperatorInfo·getRouteStateInfo 보냄")
    }
}
