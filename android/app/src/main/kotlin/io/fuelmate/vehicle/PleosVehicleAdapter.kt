package io.fuelmate.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.fuelmate.Log
import io.fuelmate.core.store.Action

/**
 * Pleos Vehicle SDK → 스토어 액션. 판정은 하지 않고 값만 옮긴다.
 * 시그니처는 SDK 2.3.2를 javap로 확인한 것만 쓴다(docs/pleos/vehicle-sdk.md).
 */
class PleosVehicleAdapter(context: Context, private val dispatch: (Action) -> Unit) {

    private val vehicle = Vehicle(context)
    private val main = Handler(Looper.getMainLooper())
    private val unregisters = mutableListOf<() -> Unit>()

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
        subscribeBrake()
        // 구독은 값이 바뀔 때만 온다. 앱을 켠 직후 "모름"으로 남지 않게 지금 값을 한 번 읽는다
        vehicle.getBrake().getCurrentBrakePedalStatus({ dispatch(Action.BrakeChanged(it)) },
            { e -> Log.w("브레이크 조회 실패 ${VehicleErrors.describe(e)}") })
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

    private companion object {
        const val CONNECT_DELAY_MS = 1_500L
    }
}
