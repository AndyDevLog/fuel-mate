package io.fuelmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import io.fuelmate.Log
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.Store
import io.fuelmate.location.DemoTrackSource
import io.fuelmate.location.LocationMode
import io.fuelmate.location.LocationSink
import io.fuelmate.location.LocationSource
import io.fuelmate.location.NaviHelperSource
import io.fuelmate.ui.MainActivity
import io.fuelmate.vehicle.CarApiAdapter
import io.fuelmate.vehicle.PleosVehicleAdapter

/**
 * 차량 신호 수집 서비스(포그라운드). 화면이 뒤로 가도 계속 동작한다.
 * Activity만 있으면 onStop 후 약 8초 만에 동결된다(docs/pleos/open-questions.md).
 * 상태 스토어는 여기 하나만 있고, 화면은 바인딩해서 구독만 한다.
 */
class CollectorService : Service() {

    val store = Store()

    private lateinit var vehicle: PleosVehicleAdapter
    private lateinit var carApi: CarApiAdapter
    private lateinit var location: LocationSource
    private lateinit var locationMode: LocationMode

    inner class LocalBinder : Binder() {
        val store: Store get() = this@CollectorService.store
        val locationMode: LocationMode get() = this@CollectorService.locationMode
        fun onPermissionsChanged() {
            carApi.onPermissionsChanged()
            // 알림 권한을 받기 전에 띄운 상시 알림은 숨겨진 채 남는다. 바로 보이게 다시 띄운다
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
        fun setLocationMode(mode: LocationMode) = this@CollectorService.switchLocation(mode)
    }

    // 원본 좌표는 로그에 남기지 않는다(데모·디버그 빌드도 기본으로 남기지 않는다)
    private val locationSink = object : LocationSink {
        override fun onFix(fix: GeoFix) = store.dispatch(Action.LocationFixed(fix))
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        location.stop()
        carApi.stop()
        vehicle.stop()
        Log.i("수집 서비스 종료")
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "운행 기록", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fuel Mate")
            .setContentText("운행 기록 중")
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
