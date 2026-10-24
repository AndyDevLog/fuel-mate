package io.roadsensor.location

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.roadsensor.Log
import io.roadsensor.core.geo.TrackPlayer
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin
import org.json.JSONObject

/**
 * 데모 궤적 재생. assets/tracks/의 궤적을 따라 1초마다 위치를 낸다.
 * 속도는 차량 속도(Virtual Console의 ACCEL·BRAKE)를 따른다. 속도를 못 읽으면 [FALLBACK_SPEED_MPS]로 간다.
 */
class DemoTrackSource(
    private val context: Context,
    private val speedMps: () -> Float?,
    private val asset: String = DEFAULT_TRACK,
) : LocationSource {

    private val main = Handler(Looper.getMainLooper())
    private var player: TrackPlayer? = null
    private var sink: LocationSink? = null
    private var lastTickMs = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val now = SystemClock.elapsedRealtime()
            val pos = p.advance(now - lastTickMs, speedMps() ?: FALLBACK_SPEED_MPS)
            lastTickMs = now
            sink?.onFix(GeoFix(pos.point, pos.headingDeg, System.currentTimeMillis(), LocationOrigin.DEMO_TRACK))
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun start(sink: LocationSink) {
        this.sink = sink
        val track = runCatching { load(asset) }
            .onFailure { Log.w("데모 궤적을 읽지 못함: $asset", it) }
            .getOrNull() ?: return
        player = track
        Log.i("데모 궤적 재생 시작: $asset (${track.lengthM.toInt()}m)")
        // 궤적 끝을 목적지로 둔다. 목적지 방향 띠 알림도 데모에서 보이게 하려고.
        sink.onDestination(track.destination)
        lastTickMs = SystemClock.elapsedRealtime()
        main.post(ticker)
    }

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        sink?.onDestination(null)
        sink = null
        player = null
    }

    private fun load(asset: String): TrackPlayer {
        val json = JSONObject(context.assets.open(asset).bufferedReader().use { it.readText() })
        val arr = json.getJSONArray("points")
        val points = (0 until arr.length()).map { i ->
            val p = arr.getJSONArray(i)
            GeoPoint(p.getDouble(0), p.getDouble(1))
        }
        return TrackPlayer(points, loop = json.optBoolean("loop", true))
    }

    companion object {
        const val DEFAULT_TRACK = "tracks/sample-sihung-pangyo.json"
        private const val TICK_MS = 1_000L
        private const val FALLBACK_SPEED_MPS = 40f / 3.6f
    }
}
