package io.roadsensor.vehicle

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
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.roadsensor.Log
import io.roadsensor.core.model.Telltale
import io.roadsensor.core.store.Action

/**
 * Pleos Vehicle SDK → 스토어 액션. 판정은 하지 않고 값만 옮긴다.
 * 시그니처는 SDK 2.3.2를 javap로 확인한 것만 쓴다(spikes/vehicle-probe).
 */
class PleosVehicleAdapter(context: Context, private val dispatch: (Action) -> Unit) {

    private val vehicle = Vehicle(context)
    private val main = Handler(Looper.getMainLooper())
    private val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val unregisters = mutableListOf<() -> Unit>()

    @Volatile private var wiperOn = false
    private var lastSwitch: String? = null

    fun start() {
        vehicle.initialize()
        // initialize()에 연결 완료 콜백이 없다. 스파이크에서 1.5초 뒤 구독이 정상 동작했다.
        main.postDelayed({ subscribe() }, CONNECT_DELAY_MS)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        vehicle.release()
    }

    private fun subscribe() {
        subscribeWiper()
        subscribeLights()
        subscribeBrake()
        subscribeTelltales()
        readCurrent()
    }

    /**
     * 구독은 값이 바뀔 때만 온다. 그래서 앱을 켠 직후에는 브레이크·라이트가 "모름"으로 남는다 → 지금 값을 한 번 읽는다.
     * 조회 응답보다 구독 변경이 먼저 올 수도 있지만, 둘 다 같은 순간의 차량 값이라 화면에 남는 차이는 없다고 본다.
     * 시그니처는 SDK 2.3.2 javap로 확인했다(docs/pleos/vehicle-sdk.md).
     */
    private fun readCurrent() {
        vehicle.getWiper().getCurrentWiperState(front, { state ->
            val on = state?.let { it == WiperState.ON }
            wiperOn = on == true
            dispatch(Action.WiperChanged(on))
            main.post { pollSwitchNow() }
        }, { e -> Log.w("와이퍼 조회 실패 ${VehicleErrors.describe(e)}") })
        val light = vehicle.getLight()
        light.getCurrentLowBeamLightState({ dispatch(Action.LowBeamChanged(it?.let { s -> s == LightState.ON })) },
            { e -> Log.w("하향등 조회 실패 ${VehicleErrors.describe(e)}") })
        light.getCurrentTailLampLightState({ dispatch(Action.TailLampChanged(it?.let { s -> s == LightState.ON })) },
            { e -> Log.w("미등 조회 실패 ${VehicleErrors.describe(e)}") })
        vehicle.getBrake().getCurrentBrakePedalStatus({ dispatch(Action.BrakeChanged(it)) },
            { e -> Log.w("브레이크 조회 실패 ${VehicleErrors.describe(e)}") })
    }

    // 와이퍼: 켜짐/꺼짐은 구독, 강도(WiperSwitch)는 구독 API가 없어서 켜져 있는 동안 조회한다
    private fun subscribeWiper() {
        val wiper = vehicle.getWiper()
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                val on = state?.let { it == WiperState.ON }
                wiperOn = on == true
                dispatch(Action.WiperChanged(on))
                main.post { pollSwitchNow() }
            }
            override fun onFailed(e: Exception) = Log.w("와이퍼 구독 실패 ${VehicleErrors.describe(e)}")
        }
        wiper.registerWiperState(front, listener)
        unregisters += { wiper.unregisterWiperState(front, listener) }
        main.postDelayed(switchPoller, SWITCH_POLL_MS)
    }

    private val switchPoller = object : Runnable {
        override fun run() {
            if (wiperOn) pollSwitchNow()
            main.postDelayed(this, SWITCH_POLL_MS)
        }
    }

    private fun pollSwitchNow() {
        vehicle.getWiper().getCurrentWiperSwitch(front, { value ->
            val name = value?.name
            main.post {
                if (name != lastSwitch) {
                    lastSwitch = name
                    dispatch(Action.WiperSwitchChanged(name))
                }
            }
        }, { e -> Log.w("와이퍼 스위치 조회 실패 ${VehicleErrors.describe(e)}") })
    }

    // 라이트: 같은 리스너 타입을 등마다 따로 만든다(어느 등의 변화인지 구분하려고)
    private fun subscribeLights() {
        val light = vehicle.getLight()
        val lowBeam = lightListener("하향등") { dispatch(Action.LowBeamChanged(it)) }
        val tailLamp = lightListener("미등") { dispatch(Action.TailLampChanged(it)) }
        light.registerLowBeamLightState(lowBeam)
        light.registerTailLampLightState(tailLamp)
        unregisters += { light.unregisterLowBeamLightState(lowBeam) }
        unregisters += { light.unregisterTailLampLightState(tailLamp) }
    }

    private fun lightListener(name: String, onChange: (Boolean?) -> Unit) = object : LightStateListener {
        // 주간주행등(DAYTIME_RUNNING)은 어두움 판정에 쓰지 않는다
        override fun onLightStateUpdated(state: LightState?) = onChange(state?.let { it == LightState.ON })
        override fun onFailed(e: Exception) = Log.w("$name 구독 실패 ${VehicleErrors.describe(e)}")
    }

    private fun subscribeBrake() {
        val brake = vehicle.getBrake()
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) = dispatch(Action.BrakeChanged(status))
            override fun onFailed(e: Exception) = Log.w("브레이크 구독 실패 ${VehicleErrors.describe(e)}")
        }
        brake.registerBrakePedalStatus(listener)
        unregisters += { brake.unregisterBrakePedalStatus(listener) }
    }

    // 경고등: PRIVATE_CAR_SAFETY가 필요하다. 실패하면 null을 넣어 화면에서 숨긴다
    private fun subscribeTelltales() {
        val safety = vehicle.getSafety()
        val listener = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(telltales: Telltales?) = dispatch(Action.TelltalesChanged(telltales?.toModel() ?: emptyList()))
            override fun onFailed(e: Exception) {
                Log.w("경고등 구독 실패 ${VehicleErrors.describe(e)}")
                dispatch(Action.TelltalesChanged(null))
            }
        }
        runCatching { safety.registerTelltales(listener) }
            .onSuccess { unregisters += { safety.unregisterTelltales(listener) } }
            .onFailure {
                Log.w("경고등 등록 실패", it)
                dispatch(Action.TelltalesChanged(null))
            }
    }

    private fun Telltales.toModel() = telltales.map { Telltale(it.type.toString(), it.state.toString()) }

    private companion object {
        const val CONNECT_DELAY_MS = 1_500L
        const val SWITCH_POLL_MS = 2_000L
    }
}
