package io.roadsensor.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * 스파이크: 앱 화면이 뒤로 가도 신호 수집이 계속되는지 확인하는 포그라운드 서비스.
 * 액티비티만 있을 때는 onStop 후 약 8초 만에 주기 작업이 멈췄다(docs/pleos/open-questions.md).
 * 로그는 "SVC " 접두사로 구분한다: adb logcat -s VehicleProbe | grep SVC
 */
class ProbeService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var vehicle: Vehicle
    private lateinit var carProbe: CarProbe
    private lateinit var naviProbe: NaviProbe
    private val frontWiper = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var heartbeat = 0
    private var lastSwitch: String? = null

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = log("WIPER 변경 → $state")
        override fun onFailed(e: Exception) = log("WIPER 구독 실패: ${e.message}")
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("BRAKE 변경 → $status")
        override fun onFailed(e: Exception) = log("BRAKE 구독 실패: ${e.message}")
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        log("서비스 시작 (foregroundServiceType=dataSync)")

        vehicle = Vehicle(this)
        vehicle.initialize()
        main.postDelayed({
            vehicle.getWiper().registerWiperState(frontWiper, wiperListener)
            vehicle.getBrake().registerBrakePedalStatus(brakeListener)
            pollWiperSwitch()
        }, 1500)

        carProbe = CarProbe(this, ::log).also { it.start() }
        naviProbe = NaviProbe(this, ::log).also { it.start() }
        beat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle.getWiper().unregisterWiperState(frontWiper, wiperListener) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        vehicle.release()
        carProbe.stop()
        naviProbe.stop()
        log("서비스 종료")
        super.onDestroy()
    }

    // 살아 있는지 한눈에 보려고 5초마다 찍는다
    private fun beat() {
        log("heartbeat ${++heartbeat}")
        main.postDelayed({ beat() }, 5_000L)
    }

    private fun pollWiperSwitch() {
        vehicle.getWiper().getCurrentWiperSwitch(frontWiper, { value ->
            val text = value.toString()
            main.post { if (text != lastSwitch) { lastSwitch = text; log("WIPER 스위치 변경 → $text") } }
        }, { log("WIPER 스위치 get 실패: ${it.message}") })
        main.postDelayed({ pollWiperSwitch() }, 1_000L)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Vehicle Probe", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Vehicle Probe")
            .setContentText("차량 신호 수집 중")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun log(message: String) {
        Log.i(TAG, "SVC $message")
    }

    companion object {
        private const val TAG = "VehicleProbe"
        private const val CHANNEL_ID = "probe"
        private const val NOTIFICATION_ID = 1
    }
}
