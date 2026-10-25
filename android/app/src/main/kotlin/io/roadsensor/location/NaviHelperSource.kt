package io.roadsensor.location

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.roadsensor.Log
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin

/**
 * NaviHelper 위치. get*Info()는 반환값이 없고 결과가 리스너로 온다.
 * 자동 푸시가 확인되지 않아서 주기적으로 요청한다(docs/pleos/navihelper-sdk.md).
 */
class NaviHelperSource(context: Context) : LocationSource {

    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var sink: LocationSink? = null
    private var ticks = 0

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            val info = currentLocationInfo
            sink?.onFix(
                GeoFix(
                    point = GeoPoint(info.latitude, info.longitude),
                    headingDeg = info.heading.toFloat(),
                    timeMs = System.currentTimeMillis(),
                    origin = LocationOrigin.NAVI_HELPER,
                ),
                // NaviHelper에는 경로 선형 API가 없다(docs/pleos/navihelper-sdk.md) → 앞길은 진행 방향 직선으로 대신한다
                routeAhead = null,
            )
        }

        // 목적지가 없으면 좌표가 0.0으로 온다
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo
            sink?.onDestination(if (d.latitude == 0.0 && d.longitude == 0.0) null else GeoPoint(d.latitude, d.longitude))
        }
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
