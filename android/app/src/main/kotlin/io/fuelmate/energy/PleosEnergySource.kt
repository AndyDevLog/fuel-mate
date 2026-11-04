package io.fuelmate.energy

import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import android.os.SystemClock
import io.fuelmate.Log
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.store.Action
import io.fuelmate.vehicle.PleosVehicleAdapter
import io.fuelmate.vehicle.VehicleErrors

/**
 * Pleos Vehicle SDK 2.3.2 Fuel·EvBattery → 스토어 액션. 시그니처는 javap로 확인했다(docs/pivot-fuel.md 1·9장).
 * - 전기: 잔량 `getEvBatteryLevel`(Float Wh), SoC(Int %), 용량(Float Wh). 에뮬레이터에서 동작한다
 * - 연료: `getFuelPercent`(Int %)만 있다. 리터는 퍼센트 × 탱크 용량. 에뮬레이터 차는 전기차라 "지원하지 않는 속성"으로 실패한다.
 *   실차 지원 여부는 확인 필요(docs/pivot-fuel.md 11장 1번)
 *
 * @param fuelCapacityL AAOS `INFO_FUEL_CAPACITY`(mL → L). 못 읽으면 [DEFAULT_FUEL_CAPACITY_L]
 */
class PleosEnergySource(
    private val vehicle: PleosVehicleAdapter,
    private val kind: EnergyKind,
    fuelCapacityL: Double?,
    private val dispatch: (Action) -> Unit,
) : EnergySource {

    private val fuelCapacityL = fuelCapacityL ?: DEFAULT_FUEL_CAPACITY_L
    private val unregisters = mutableListOf<() -> Unit>()
    private var stopped = false

    override fun start() {
        Log.i("에너지 공급원: Pleos ${kind.label}")
        vehicle.whenReady {
            if (stopped) return@whenReady
            when (kind) {
                EnergyKind.ELECTRIC -> startEv()
                EnergyKind.FUEL -> startFuel()
            }
        }
    }

    override fun stop() {
        stopped = true
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
    }

    private fun send(amount: Double? = null, capacity: Double? = null, percent: Float? = null) {
        if (!stopped) dispatch(Action.EnergyReading(SystemClock.elapsedRealtime(), kind, EnergyOrigin.VEHICLE, amount, capacity, percent))
    }

    private fun fail(name: String): (Exception) -> Unit = { Log.w("$name 실패 ${VehicleErrors.describe(it)}") }

    private fun startEv() {
        val ev = vehicle.vehicle.getEvBattery()
        // 용량을 먼저 넣어야 첫 잔량 변화부터 "값 조정(한 번에 5% 넘게)" 판정을 할 수 있다
        ev.getEvBatteryCapacity({ wh -> wh?.let { send(capacity = it / 1000.0) } }, fail("배터리 용량 조회"))
        ev.getEvBatteryLevel({ wh -> wh?.let { send(amount = it / 1000.0) } }, fail("배터리 잔량 조회"))
        ev.getEvBatteryStateOfCharge({ soc -> soc?.let { send(percent = it.toFloat()) } }, fail("배터리 SoC 조회"))

        val level = object : EvBatteryLevelListener {
            override fun onEvBatteryLevelUpdated(value: Float?) {
                if (value != null) send(amount = value / 1000.0)
            }
            override fun onFailed(e: Exception) = fail("배터리 잔량 구독")(e)
        }
        ev.registerEvBatteryLevel(level)
        unregisters += { ev.unregisterEvBatteryLevel(level) }

        val soc = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                if (value != null) send(percent = value.toFloat())
            }
            override fun onFailed(e: Exception) = fail("배터리 SoC 구독")(e)
        }
        ev.registerEvBatteryStateOfCharge(soc)
        unregisters += { ev.unregisterEvBatteryStateOfCharge(soc) }
    }

    private fun startFuel() {
        val fuel = vehicle.vehicle.getFuel()
        send(capacity = fuelCapacityL)
        val onPercent = { percent: Int? ->
            if (percent != null) send(amount = fuelCapacityL * percent / 100.0, percent = percent.toFloat())
        }
        fuel.getFuelPercent(onPercent, fail("연료량 조회"))

        val listener = object : FuelPercentListener {
            override fun onFuelPercentUpdated(value: Int?) = onPercent(value)
            override fun onFailed(e: Exception) = fail("연료량 구독")(e)
        }
        fuel.registerFuelPercent(listener)
        unregisters += { fuel.unregisterFuelPercent(listener) }
    }

    companion object {
        /** 탱크 용량을 못 읽을 때. 지금 Pleos 탑재 차종(그랜저·아반떼) 탱크는 47~64L 범위라 그 사이 값을 쓴다(확인 필요) */
        const val DEFAULT_FUEL_CAPACITY_L = 55.0
    }
}
