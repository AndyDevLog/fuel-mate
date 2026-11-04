package io.fuelmate.energy

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.fuelmate.Log
import io.fuelmate.core.energy.DemoEnergy
import io.fuelmate.core.energy.VehicleParams
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.Gear
import io.fuelmate.core.store.Action

/**
 * 데모 에너지: 1초마다 지금 속도·기어를 읽어 잔량을 줄인다. 속도는 실제 차량 신호(Virtual Console ACCEL·BRAKE)다.
 * 시작 잔량은 정해 둔 값이라 앱을 다시 켜면 처음부터 시작한다.
 */
class DemoEnergySource(
    params: VehicleParams,
    startPercent: Double,
    private val state: () -> AppState,
    private val dispatch: (Action) -> Unit,
) : EnergySource {

    private val main = Handler(Looper.getMainLooper())
    private var demo = DemoEnergy.start(params, startPercent)

    private val tick = object : Runnable {
        override fun run() {
            val vehicle = state().vehicle
            // P단이거나 기어를 모르면 꺼진 것으로 본다(공회전·보조 전력을 세지 않는다)
            val poweredOn = vehicle.gear == Gear.D || vehicle.gear == Gear.R || vehicle.gear == Gear.N
            demo = demo.tick(SystemClock.elapsedRealtime(), vehicle.speedMps, poweredOn)
            dispatch(demo.reading())
            logWhileMoving(vehicle.speedMps ?: 0f)
            main.postDelayed(this, TICK_MS)
        }
    }

    private var ticks = 0
    private var lastLoggedMoving = false

    /** 확인용: 움직이는 동안(과 멈춘 직후 한 번) 2초마다 잔량·계량기를 남긴다. 위치는 남기지 않는다 */
    private fun logWhileMoving(speedMps: Float) {
        val moving = speedMps > 0.5f
        ticks++
        if ((moving && ticks % 2 == 0) || (!moving && lastLoggedMoving)) {
            val m = state().energyMeter
            Log.i("데모 에너지 ${(speedMps * 3.6f).toInt()}km/h 잔량 ${"%.3f".format(demo.amount)} " +
                "사용 ${"%.3f".format(m.used)} 충전 ${"%.3f".format(m.added)}")
        }
        lastLoggedMoving = moving
    }

    override fun start() {
        main.post(tick)
    }

    override fun stop() {
        main.removeCallbacks(tick)
    }

    companion object {
        const val TICK_MS = 1_000L

        /** 시연에서 "충전·주유할 때가 됐다"가 보이도록 낮게 잡은 시작 잔량 */
        fun ev(state: () -> AppState, dispatch: (Action) -> Unit) =
            DemoEnergySource(VehicleParams.DEMO_EV, 35.0, state, dispatch)

        fun fuel(state: () -> AppState, dispatch: (Action) -> Unit) =
            DemoEnergySource(VehicleParams.DEMO_FUEL, 25.0, state, dispatch)
    }
}
