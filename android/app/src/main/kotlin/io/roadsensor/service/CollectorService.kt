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
import io.roadsensor.Log
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.Store
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

    private lateinit var vehicle: PleosVehicleAdapter
    private lateinit var carApi: CarApiAdapter
    private lateinit var location: LocationSource

    inner class LocalBinder : Binder() {
        val store: Store get() = this@CollectorService.store
        fun onPermissionsChanged() = carApi.onPermissionsChanged()
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Log.i("수집 서비스 시작")

        vehicle = PleosVehicleAdapter(this, store::dispatch).also { it.start() }
        carApi = CarApiAdapter(this, store::dispatch).also { it.start() }
        location = NaviHelperSource(this).also {
            it.start(object : LocationSink {
                override fun onFix(fix: GeoFix) = store.dispatch(Action.LocationFixed(fix))
                override fun onDestination(destination: GeoPoint?) = store.dispatch(Action.DestinationChanged(destination))
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

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
