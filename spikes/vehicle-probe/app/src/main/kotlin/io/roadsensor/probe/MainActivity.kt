package io.roadsensor.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 스파이크: Vehicle SDK로 와이퍼·라이트·브레이크·경고등 값을 읽어 logcat과 화면에 찍는다.
 * 확인 방법: adb logcat -s VehicleProbe
 */
class MainActivity : Activity() {

    private lateinit var vehicle: Vehicle
    private lateinit var carProbe: CarProbe
    private lateinit var naviProbe: NaviProbe
    private lateinit var output: TextView
    private val main = Handler(Looper.getMainLooper())
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    // 해제할 때 쓰려고 등록한 것을 모아 둔다
    private val unregisters = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = 22f
            typeface = Typeface.MONOSPACE
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(output) })

        log("DO 선언=${BuildConfig.DISTRACTION_OPTIMIZED}")
        vehicle = Vehicle(this)
        vehicle.initialize()
        log("initialize() 호출")
        // initialize()에 연결 완료 콜백이 없어서 잠깐 기다렸다가 조회한다
        main.postDelayed({ probe() }, 1500)

        carProbe = CarProbe(this, ::log).also { it.start() }
        naviProbe = NaviProbe(this, ::log).also { it.start() }

        // 화면이 뒤로 가도 수집이 계속되는지 서비스 쪽 로그("SVC")로 비교한다
        startForegroundService(Intent(this, ProbeService::class.java))
    }

    // 주행 중 차단(DO 미선언) 여부를 보려고 생명주기를 찍는다
    override fun onResume() { super.onResume(); log("LIFECYCLE onResume") }
    override fun onPause() { log("LIFECYCLE onPause"); super.onPause() }
    override fun onStop() { log("LIFECYCLE onStop"); super.onStop() }
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        log("LIFECYCLE topResumed=$isTopResumedActivity")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        unregisters.forEach { runCatching(it) }
        vehicle.release()
        carProbe.stop()
        naviProbe.stop()
        super.onDestroy()
    }

    // Pleos 필수 정책: 최상단 화면에서 Back은 종료 대신 숨김
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun probe() {
        probeWiper()
        probeLights()
        probeBrake()
        probeSafety()
    }

    private fun probeWiper() {
        val wiper = vehicle.getWiper()
        wiper.checkWiperStateCapabilities({ config, areaMap ->
            log("WIPER capability: $config")
            areaMap.forEach { (area, states) -> log("WIPER area=${area.name()} states=$states") }
            subscribeWiper(areaMap.keys.ifEmpty { setOf(WindowArea(WindowValue.FRONT_WINDSHIELD)) })
        }, { e ->
            log("WIPER capability 실패: ${describe(e)}")
            subscribeWiper(setOf(WindowArea(WindowValue.FRONT_WINDSHIELD)))
        })
    }

    private fun subscribeWiper(areas: Collection<WindowArea>) {
        val wiper = vehicle.getWiper()
        areas.forEach { area ->
            val name = area.name()
            wiper.getCurrentWiperState(area, { log("WIPER[$name] 현재=$it") }, { log("WIPER[$name] get 실패: ${describe(it)}") })
            wiper.getCurrentWiperSwitch(area, { log("WIPER[$name] 스위치=$it") }, { log("WIPER[$name] 스위치 get 실패: ${describe(it)}") })
            val listener = object : WiperStateListener {
                override fun onWiperStateUpdated(state: WiperState?) = log("WIPER[$name] 변경 → $state")
                override fun onFailed(e: Exception) = log("WIPER[$name] 구독 실패: ${describe(e)}")
            }
            wiper.registerWiperState(area, listener)
            unregisters += { wiper.unregisterWiperState(area, listener) }
        }
        pollWiperSwitch(areas)
    }

    // WiperSwitch(강도)는 구독 API가 없어서 주기적으로 조회하고, 바뀔 때만 찍는다
    private val lastSwitch = mutableMapOf<String, String>()

    private fun pollWiperSwitch(areas: Collection<WindowArea>) {
        val wiper = vehicle.getWiper()
        areas.forEach { area ->
            val name = area.name()
            wiper.getCurrentWiperSwitch(area, { value ->
                val text = value.toString()
                main.post {
                    if (lastSwitch.put(name, text) != text) log("WIPER[$name] 스위치 변경 → $text")
                }
            }, { log("WIPER[$name] 스위치 get 실패: ${describe(it)}") })
        }
        main.postDelayed({ pollWiperSwitch(areas) }, SWITCH_POLL_MS)
    }

    private fun probeLights() {
        val light = vehicle.getLight()
        val targets = listOf(
            Triple("LOW_BEAM", light::getCurrentLowBeamLightState, light::registerLowBeamLightState to light::unregisterLowBeamLightState),
            Triple("HIGH_BEAM", light::getCurrentHighBeamLightState, light::registerHighBeamLightState to light::unregisterHighBeamLightState),
            Triple("TAIL_LAMP", light::getCurrentTailLampLightState, light::registerTailLampLightState to light::unregisterTailLampLightState),
            Triple("FRONT_FOG", light::getCurrentFrontFogLightState, light::registerFrontFogLightState to light::unregisterFrontFogLightState),
        )
        targets.forEach { (name, get, reg) ->
            get({ state: LightState? -> log("LIGHT[$name] 현재=$state") }, { e: Exception -> log("LIGHT[$name] get 실패: ${describe(e)}") })
            // LightStateListener는 모든 등이 같은 타입을 쓰므로 등마다 따로 만든다
            val listener = object : LightStateListener {
                override fun onLightStateUpdated(state: LightState?) = log("LIGHT[$name] 변경 → $state")
                override fun onFailed(e: Exception) = log("LIGHT[$name] 구독 실패: ${describe(e)}")
            }
            reg.first(listener)
            unregisters += { reg.second(listener) }
        }
    }

    private fun probeBrake() {
        val brake = vehicle.getBrake()
        brake.getCurrentBrakePedalStatus({ log("BRAKE 현재=$it") }, { log("BRAKE get 실패: ${describe(it)}") })
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) = log("BRAKE 변경 → $status")
            override fun onFailed(e: Exception) = log("BRAKE 구독 실패: ${describe(e)}")
        }
        brake.registerBrakePedalStatus(listener)
        unregisters += { brake.unregisterBrakePedalStatus(listener) }
    }

    // 경고등은 문서상 PRIVATE_CAR_SAFETY(signature|privileged) — 3rd party 앱에서 막히는지 확인
    private fun probeSafety() {
        val safety = vehicle.getSafety()
        safety.checkTelltalesCapabilities({ log("TELLTALE capability: $it") }, { log("TELLTALE capability 실패: ${describe(it)}") })
        safety.getTelltalesInfo({ log("TELLTALE 현재=${summary(it)}") }, { log("TELLTALE get 실패: ${describe(it)}") })
        val listener = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(telltales: Telltales?) = log("TELLTALE 변경 → ${summary(telltales)}")
            override fun onFailed(e: Exception) = log("TELLTALE 구독 실패: ${describe(e)}")
        }
        safety.registerTelltales(listener)
        unregisters += { safety.unregisterTelltales(listener) }
        // 경고등 대안 후보: 워셔액 부족 경고
        safety.hasLowWasherFluidWarning({ log("WASHER 부족 경고=$it") }, { log("WASHER get 실패: ${describe(it)}") })
    }

    private fun summary(t: Telltales?): String =
        t?.telltales?.joinToString(prefix = "${t.telltales.size}개 [", postfix = "]") { "${it.type}:${it.state}" } ?: "null"

    private fun WindowArea.name(): String = values.joinToString("+")

    /**
     * SDK 2.3.2의 VehicleException은 난독화되어 있고, ErrorCode는 컴파일 경로에 노출되지 않는다(vehicle-data 모듈).
     * 그래서 ErrorCode를 돌려주는 메서드를 이름으로 찾아 읽는다.
     */
    private fun describe(e: Exception): String {
        val code = e.javaClass.methods
            .firstOrNull { it.parameterCount == 0 && it.returnType.name == "ai.pleos.playground.vehicle.constant.ErrorCode" }
            ?.let { runCatching { it.invoke(e) }.getOrNull() }
        return "${e.javaClass.name} code=$code msg=${e.message}"
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        val line = "${time.format(Date())}  $message\n"
        main.post { output.append(line) }
    }

    companion object {
        private const val TAG = "VehicleProbe"
        // 단계 매핑 확인용으로 짧게 잡았다. 본 앱에서는 와이퍼가 켜져 있을 때만 10초 정도로.
        private const val SWITCH_POLL_MS = 1000L
    }
}
