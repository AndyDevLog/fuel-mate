package io.roadsensor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import io.roadsensor.BuildConfig
import io.roadsensor.Log
import io.roadsensor.net.MapFeed
import io.roadsensor.net.Uploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.Store
import io.roadsensor.location.DemoTrackSource
import io.roadsensor.location.LocationMode
import io.roadsensor.location.LocationSink
import io.roadsensor.location.LocationSource
import io.roadsensor.location.NaviHelperSource
import io.roadsensor.ui.MainActivity
import io.roadsensor.vehicle.CarApiAdapter
import io.roadsensor.vehicle.PleosVehicleAdapter

/**
 * 차량 신호 수집 서비스(포그라운드). 화면이 뒤로 가도 계속 동작한다.
 * Activity만 있으면 onStop 후 약 8초 만에 동결된다(docs/pleos/open-questions.md).
 * 상태 스토어는 여기 하나만 있고, 화면은 바인딩해서 구독만 한다(docs/architecture.md 1·3장).
 */
class CollectorService : Service() {

    val store = Store()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var vehicle: PleosVehicleAdapter
    private lateinit var carApi: CarApiAdapter
    private lateinit var location: LocationSource
    private lateinit var locationMode: LocationMode
    private lateinit var mapFeed: MapFeed

    inner class LocalBinder : Binder() {
        val store: Store get() = this@CollectorService.store
        val locationMode: LocationMode get() = this@CollectorService.locationMode
        fun onPermissionsChanged() = carApi.onPermissionsChanged()
        fun setLocationMode(mode: LocationMode) = this@CollectorService.switchLocation(mode)
    }

    private val locationSink = object : LocationSink {
        private var lastCell: String? = null

        override fun onFix(fix: GeoFix, routeAhead: List<GeoPoint>?) {
            store.dispatch(Action.LocationFixed(fix, routeAhead))
            // 원본 좌표는 로그에 남기지 않는다. 격자가 바뀔 때 격자 ID만 남긴다.
            val cell = Geohash.encode(fix.point)
            if (cell != lastCell) {
                lastCell = cell
                Log.i("격자 이동 → $cell (${fix.origin})")
            }
        }

        override fun onDestination(destination: GeoPoint?) = store.dispatch(Action.DestinationChanged(destination))
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Log.i("수집 서비스 시작")

        vehicle = PleosVehicleAdapter(this, store::dispatch).also { it.start() }
        carApi = CarApiAdapter(this, store::dispatch).also { it.start() }
        locationMode = LocationMode.load(this)
        location = createLocation(locationMode).also { it.start(locationSink) }

        Uploader(store, BuildConfig.SERVER_URL, scope, File(filesDir, "upload-queue.txt")).start()
        mapFeed = MapFeed(store, BuildConfig.SERVER_URL, scope).also { it.start() }
    }

    private fun createLocation(mode: LocationMode): LocationSource = when (mode) {
        LocationMode.NAVI -> NaviHelperSource(this)
        LocationMode.DEMO -> DemoTrackSource(this, speedMps = { store.state.value.vehicle.speedMps })
    }

    private fun switchLocation(mode: LocationMode) {
        if (mode == locationMode) return
        Log.i("위치 출처 변경: $locationMode → $mode")
        location.stop()
        locationMode = mode
        LocationMode.save(this, mode)
        location = createLocation(mode).also { it.start(locationSink) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        mapFeed.stop()
        scope.cancel()
        location.stop()
        carApi.stop()
        vehicle.stop()
        Log.i("수집 서비스 종료")
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "도로 정보 공유", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Road Sensor")
            .setContentText("도로 정보 공유 중")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "collector"
        const val NOTIFICATION_ID = 1
    }
}
