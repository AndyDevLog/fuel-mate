package io.fuelmate.location

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.fuelmate.Log
import io.fuelmate.core.geo.StuckNaviFallback
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.LocationOrigin

/**
 * NaviHelper 위치와 목적지. get*Info()는 반환값이 없고 결과가 리스너로 온다.
 * 자동 푸시가 확인되지 않아서 주기적으로 요청한다(docs/pleos/navihelper-sdk.md).
 * 내비 위치가 달려도 멈춰 있으면(에뮬레이터) [StuckNaviFallback]이 목적지 쪽 직선으로 움직인다.
 */
class NaviHelperSource(context: Context, private val speedMps: () -> Float?) : LocationSource {

    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var sink: LocationSink? = null
    private var ticks = 0
    private val fallback = StuckNaviFallback()
    private var destination: GeoPoint? = null
    private var lastOrigin: LocationOrigin? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val info = currentLocationInfo
            val fix = fallback.update(
                navi = GeoPoint(info.latitude, info.longitude),
                headingDeg = info.heading.toFloat(),
                speedMps = speedMps(),
                destination = destination,
                nowMs = System.currentTimeMillis(),
            )
            // 좌표는 로그에 남기지 않는다
            if (fix.origin != lastOrigin) Log.i("위치 출처: ${fix.origin}")
            lastOrigin = fix.origin
            sink?.onFix(fix)
        }

        // 목적지가 없으면 좌표가 0.0으로 온다
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo
            setDestination(if (d.latitude == 0.0 && d.longitude == 0.0) null else GeoPoint(d.latitude, d.longitude))
        }

        // 목적지가 바뀌는 때에는 10초 주기를 기다리지 않고 바로 묻는다
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = navi.getDestinationInfo()

        // 경유지를 넣은 직후에도 한 번 온다(스파이크 확인). 지우지 않고 다시 묻는다
        override fun onRouteCancelled() = navi.getDestinationInfo()

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = setDestination(null)
    }

    private fun setDestination(point: GeoPoint?) {
        if ((point == null) != (destination == null)) Log.i(if (point == null) "내비 목적지 없음" else "내비 목적지 있음")
        destination = point
        sink?.onDestination(point)
    }

    private val poller = object : Runnable {
        override fun run() {
            navi.getCurrentLocationInfo()
            if (ticks++ % DESTINATION_EVERY == 0) navi.getDestinationInfo()
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun start(sink: LocationSink) {
        this.sink = sink
        navi.initialize({
            Log.i("NaviHelper 연결됨")
            navi.addListener(listener)
            main.post(poller)
        }, { Log.w("NaviHelper 연결 실패", it) })
    }

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { navi.removeListener(listener) }
        navi.release()
        sink = null
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val DESTINATION_EVERY = 5   // 10초마다
    }
}
