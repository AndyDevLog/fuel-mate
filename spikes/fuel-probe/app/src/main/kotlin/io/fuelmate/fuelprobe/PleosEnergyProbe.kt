package io.fuelmate.fuelprobe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.FuelEconomyListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Pleos Vehicle SDK 2.3.2: Fuel·EvBattery·Odometer·HVAC·Gear. 시그니처는 javap로 확인한 것만 쓴다.
 * 모든 값을 한 번 읽고(readOnce), 구독 콜백은 값이 올 때마다 찍는다.
 */
class PleosEnergyProbe(context: Context, private val log: (String) -> Unit) {

    private val vehicle = Vehicle(context)
    private val main = Handler(Looper.getMainLooper())
    private val unregisters = mutableListOf<() -> Unit>()
    private val driver = SeatArea(SeatValue.ROW_1_LEFT)

    fun start() {
        vehicle.initialize()
        // initialize()에 연결 완료 콜백이 없어서 잠깐 기다린다(vehicle-probe와 같음)
        main.postDelayed({
            readOnce()
            subscribe()
        }, 1500)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        unregisters.forEach { runCatching(it) }
        vehicle.release()
    }

    private fun fail(name: String): (Exception) -> Unit = { log("P $name 실패: ${it.javaClass.simpleName} ${it.message}") }
    private fun <T> ok(name: String): (T) -> Unit = { log("P $name = $it") }

    fun readOnce() {
        val fuel = vehicle.getFuel()
        fuel.getFuelPercent(ok("fuel.percent"), fail("fuel.percent"))
        fuel.getInstantaneousFuelEconomy(ok("fuel.econ.instant"), fail("fuel.econ.instant"))
        fuel.getCurrentFuelEconomy(ok("fuel.econ.current"), fail("fuel.econ.current"))
        fuel.getCumulativeFuelEconomy(ok("fuel.econ.cumulative"), fail("fuel.econ.cumulative"))
        fuel.getSinceChargeFuelEconomy(ok("fuel.econ.sinceRefill"), fail("fuel.econ.sinceRefill"))

        val ev = vehicle.getEvBattery()
        ev.getEvBatteryLevel(ok("ev.level"), fail("ev.level"))
        ev.getEvBatteryStateOfCharge(ok("ev.soc"), fail("ev.soc"))
        ev.getEvBatteryCapacity(ok("ev.capacity"), fail("ev.capacity"))
        ev.getInstantaneousEvEfficiency(ok("ev.eff.instant"), fail("ev.eff.instant"))
        ev.getCurrentEvEfficiency(ok("ev.eff.current"), fail("ev.eff.current"))
        ev.getCumulativeEvEfficiency(ok("ev.eff.cumulative"), fail("ev.eff.cumulative"))
        ev.getSinceChargeEvEfficiency(ok("ev.eff.sinceCharge"), fail("ev.eff.sinceCharge"))
        ev.isEvChargePortOpen(ok("ev.portOpen"), fail("ev.portOpen"))
        ev.getEvChargeSwitchState(ok("ev.chargeSwitch"), fail("ev.chargeSwitch"))

        val odo = vehicle.getOdometer()
        odo.getCurrentDistanceDriven(ok("odo.distance"), fail("odo.distance"))
        odo.getRangeRemainingDistance(ok("odo.range"), fail("odo.range"))
        odo.getTripCurrentDrivingDistance(ok("odo.trip.current"), fail("odo.trip.current"))
        odo.getTripSinceRefillDrivingDistance(ok("odo.trip.sinceRefill"), fail("odo.trip.sinceRefill"))
        odo.getTripSinceRefillDrivingTime(ok("odo.trip.sinceRefillTime"), fail("odo.trip.sinceRefillTime"))

        val hvac = vehicle.getHVAC()
        hvac.getHvacPowerState(driver, ok("hvac.power"), fail("hvac.power"))
        hvac.getHvacAcState(driver, ok("hvac.ac"), fail("hvac.ac"))
        hvac.getHvacAutoState(driver, ok("hvac.auto"), fail("hvac.auto"))
        hvac.getHvacFanSpeed(driver, ok("hvac.fan"), fail("hvac.fan"))
        hvac.getHvacTemperature(driver, TemperatureUnit.CELSIUS, ok("hvac.setTemp"), fail("hvac.setTemp"))
        hvac.getHvacTemperatureCurrent(driver, ok("hvac.cabinTemp"), fail("hvac.cabinTemp"))

        vehicle.getGear().getCurrentGearSelectionState(ok("gear"), fail("gear"))

        // 운전석(0x1)으로 안 되는 공조 항목은 지원 영역을 물어 그 영역으로 다시 읽는다
        hvac.checkHvacPowerCapabilities({ areas ->
            log("P hvac.power 영역 ${areas.map { it.values.toList() }}")
            areas.forEach { hvac.getHvacPowerState(it, ok("hvac.power${it.values.toList()}"), fail("hvac.power")) }
        }, fail("hvac.power 영역"))
        hvac.checkHvacAcCapabilities({ areas ->
            log("P hvac.ac 영역 ${areas.map { it.values.toList() }}")
            areas.forEach { hvac.getHvacAcState(it, ok("hvac.ac${it.values.toList()}"), fail("hvac.ac")) }
        }, fail("hvac.ac 영역"))
    }

    private fun subscribe() {
        val fuel = vehicle.getFuel()
        val fuelPercent = object : FuelPercentListener {
            override fun onFuelPercentUpdated(value: Int?) = log("P ▶ fuel.percent $value")
            override fun onFailed(e: Exception) = fail("▶ fuel.percent")(e)
        }
        fuel.registerFuelPercent(fuelPercent)
        unregisters += { fuel.unregisterFuelPercent(fuelPercent) }
        val instant = econ("fuel.econ.instant")
        fuel.registerInstantaneousFuelEconomy(instant)
        unregisters += { fuel.unregisterInstantaneousFuelEconomy(instant) }
        val current = econ("fuel.econ.current")
        fuel.registerCurrentFuelEconomy(current)
        unregisters += { fuel.unregisterCurrentFuelEconomy(current) }

        val ev = vehicle.getEvBattery()
        val level = object : EvBatteryLevelListener {
            override fun onEvBatteryLevelUpdated(value: Float?) = log("P ▶ ev.level $value")
            override fun onFailed(e: Exception) = fail("▶ ev.level")(e)
        }
        ev.registerEvBatteryLevel(level)
        unregisters += { ev.unregisterEvBatteryLevel(level) }
        val soc = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) = log("P ▶ ev.soc $value")
            override fun onFailed(e: Exception) = fail("▶ ev.soc")(e)
        }
        ev.registerEvBatteryStateOfCharge(soc)
        unregisters += { ev.unregisterEvBatteryStateOfCharge(soc) }
        val evInstant = evEff("ev.eff.instant")
        ev.registerInstantaneousEvEfficiency(evInstant)
        unregisters += { ev.unregisterInstantaneousEvEfficiency(evInstant) }
        val evCurrent = evEff("ev.eff.current")
        ev.registerCurrentEvEfficiency(evCurrent)
        unregisters += { ev.unregisterCurrentEvEfficiency(evCurrent) }
        val port = object : EvChargePortOpenListener {
            override fun onEvChargePortOpenUpdated(value: Boolean?) = log("P ▶ ev.portOpen $value")
            override fun onFailed(e: Exception) = fail("▶ ev.portOpen")(e)
        }
        ev.registerEvChargePortOpen(port)
        unregisters += { ev.unregisterEvChargePortOpen(port) }

        val odo = vehicle.getOdometer()
        val distance = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) = log("P ▶ odo.distance $value")
            override fun onFailed(e: Exception) = fail("▶ odo.distance")(e)
        }
        odo.registerDistanceDriven(distance)
        unregisters += { odo.unregisterDistanceDriven(distance) }
        val range = object : RangeRemainingListener {
            override fun onRangeRemainingChanged(value: Float?) = log("P ▶ odo.range $value")
            override fun onFailed(e: Exception) = fail("▶ odo.range")(e)
        }
        odo.registerRangeRemainingListener(range)
        unregisters += { odo.unregisterRangeRemainingListener(range) }
        val sinceRefill = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) = log("P ▶ odo.trip.sinceRefill $value")
            override fun onFailed(e: Exception) = fail("▶ odo.trip.sinceRefill")(e)
        }
        odo.registerTripSinceRefillDrivingDistance(sinceRefill)
        unregisters += { odo.unregisterTripSinceRefillDrivingDistance(sinceRefill) }

        val hvac = vehicle.getHVAC()
        // 전원·에어컨은 운전석(0x1) 영역이 없다. 지원 영역(1·2열 전체 한 영역)으로 구독한다
        val power = object : HvacPowerStateListener {
            override fun onHvacPowerStateUpdated(value: Boolean?) = log("P ▶ hvac.power $value")
            override fun onFailed(e: Exception) = fail("▶ hvac.power")(e)
        }
        hvac.checkHvacPowerCapabilities({ areas ->
            areas.forEach { area ->
                hvac.registerHvacPowerState(area, power)
                unregisters += { hvac.unregisterHvacPowerState(area, power) }
            }
        }, fail("▶ hvac.power 영역"))
        val ac = object : HvacAcStateListener {
            override fun onHvacAcStateUpdated(value: Boolean?) = log("P ▶ hvac.ac $value")
            override fun onFailed(e: Exception) = fail("▶ hvac.ac")(e)
        }
        hvac.checkHvacAcCapabilities({ areas ->
            areas.forEach { area ->
                hvac.registerHvacAcState(area, ac)
                unregisters += { hvac.unregisterHvacAcState(area, ac) }
            }
        }, fail("▶ hvac.ac 영역"))
        val fan = object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(value: Int?) = log("P ▶ hvac.fan $value")
            override fun onFailed(e: Exception) = fail("▶ hvac.fan")(e)
        }
        hvac.registerHvacFanSpeed(driver, fan)
        unregisters += { hvac.unregisterHvacFanSpeed(driver, fan) }
        val setTemp = object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) = log("P ▶ hvac.setTemp $value")
            override fun onFailed(e: Exception) = fail("▶ hvac.setTemp")(e)
        }
        hvac.registerHvacTemperature(driver, setTemp)
        unregisters += { hvac.unregisterHvacTemperature(driver, setTemp) }

        val gear = vehicle.getGear()
        val gearListener = object : GearSelectionListener {
            override fun onSelected(value: VehicleGear?) = log("P ▶ gear $value")
            override fun onFailed(e: Exception) = fail("▶ gear")(e)
        }
        gear.registerGearSelection(gearListener)
        unregisters += { gear.unregisterGearSelection(gearListener) }
        log("P 구독 완료")
    }

    private fun econ(name: String) = object : FuelEconomyListener {
        override fun onFuelEconomyUpdated(value: Float?) = log("P ▶ $name $value")
        override fun onFailed(e: Exception) = fail("▶ $name")(e)
    }

    private fun evEff(name: String) = object : EvBatteryEfficiencyListener {
        override fun onEvBatteryEfficiencyUpdated(value: Float?) = log("P ▶ $name $value")
        override fun onFailed(e: Exception) = fail("▶ $name")(e)
    }
}
