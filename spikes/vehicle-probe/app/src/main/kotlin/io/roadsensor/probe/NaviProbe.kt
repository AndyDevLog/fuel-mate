package io.roadsensor.probe

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * NaviHelper 위치 확인: get*Info()는 반환값이 없고 결과가 리스너로 온다(docs/pleos/navihelper-sdk.md).
 * 위치가 자동으로 푸시되는지 보려고, 처음 한 번만 요청하고 이후는 주기 요청과 구분해서 찍는다.
 */
class NaviProbe(context: Context, private val log: (String) -> Unit) {

    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var lastLocation: String? = null
    private var requests = 0

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val text = currentLocationInfo.toString()
            if (text != lastLocation) {
                lastLocation = text
                log("NAVI 위치 (요청 ${requests}회째) $text")
            }
        }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log("NAVI 경로상태 $routeStateInfo")
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = log("NAVI 목적지 $destinationInfo")
        override fun onNavigationStatus(navigationStatus: NavigationStatus) = log("NAVI 상태 $navigationStatus")
        override fun onError(errorCode: NaviErrorCode) = log("NAVI 오류 $errorCode")
    }

    fun start() {
        navi.initialize({
            log("NAVI 연결됨")
            navi.addListener(listener)
            navi.getRouteStateInfo()
            navi.getDestinationInfo()
            requestLocation()
        }, { log("NAVI 연결 실패: ${it.javaClass.name} ${it.message}") })
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { navi.removeListener(listener) }
        navi.release()
    }

    // 자동 푸시 여부를 보려고 첫 요청 뒤 10초 쉬었다가, 그다음부터 3초마다 요청한다
    private fun requestLocation() {
        requests++
        navi.getCurrentLocationInfo()
        main.postDelayed({ requestLocation() }, if (requests == 1) 10_000L else 3_000L)
    }
}
