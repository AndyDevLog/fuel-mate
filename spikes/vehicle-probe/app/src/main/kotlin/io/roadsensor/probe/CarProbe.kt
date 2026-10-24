package io.roadsensor.probe

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * AAOS 표준 API 확인: 주행 규제(CarUxRestrictions), 속도(PERF_VEHICLE_SPEED), 기어(GEAR_SELECTION).
 * Pleos 문서는 주행/정차 판단에 CarUxRestrictions만 따르라고 한다(docs/pleos/driving-restriction.md).
 */
class CarProbe(context: Context, private val log: (String) -> Unit) {

    private val car = Car.createCar(context)
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null
    private var lastSpeedKmh = Int.MIN_VALUE

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    // m/s → km/h, 1km/h 이상 바뀔 때만 찍는다
                    val kmh = ((value.value as Float) * 3.6f).toInt()
                    if (kmh != lastSpeedKmh) {
                        lastSpeedKmh = kmh
                        log("SPEED $kmh km/h")
                    }
                }
                VehiclePropertyIds.GEAR_SELECTION -> log("GEAR ${gearName(value.value as Int)}")
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) =
            log("CARPROP 오류 ${VehiclePropertyIds.toString(propertyId)} area=$areaId")
    }

    fun start() {
        val ux = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        uxManager = ux
        log("UX 현재 ${describe(ux.currentCarUxRestrictions)}")
        ux.registerListener { log("UX 변경 → ${describe(it)}") }

        val props = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        propertyManager = props
        subscribe(props, VehiclePropertyIds.PERF_VEHICLE_SPEED, "SPEED")
        subscribe(props, VehiclePropertyIds.GEAR_SELECTION, "GEAR")
    }

    fun stop() {
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(propertyCallback) }
        car.disconnect()
    }

    private fun subscribe(props: CarPropertyManager, propertyId: Int, name: String) {
        // 구독이 실패하면 원인을 보려고 config와 현재 값을 먼저 찍는다
        val config = runCatching { props.getCarPropertyConfig(propertyId) }
        log("$name config=${config.getOrNull()?.let { "access=${it.access} areas=${it.areaIds.toList()}" } ?: "없음 ${config.exceptionOrNull()?.message ?: ""}"}")
        runCatching { props.getProperty<Any>(propertyId, 0) }
            .onSuccess { log("$name 현재값=${it.value} status=${it.status}") }
            .onFailure { log("$name 현재값 실패: ${it.javaClass.name} ${it.message}") }
        runCatching { props.registerCallback(propertyCallback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
            .onSuccess { log("$name 구독=${it}") }
            .onFailure { log("$name 구독 실패: ${it.javaClass.name} ${it.message}") }
    }

    private fun describe(r: CarUxRestrictions): String {
        val bits = r.activeRestrictions
        val names = RESTRICTION_NAMES.filter { (flag, _) -> bits and flag != 0 }.map { it.second }
        return "DO필요=${r.isRequiresDistractionOptimization} active=$bits ${names.ifEmpty { listOf("없음") }}"
    }

    private fun gearName(gear: Int) = when (gear) {
        1 -> "N"; 2 -> "R"; 4 -> "P"; 8 -> "D"
        else -> "기타($gear)"
    }

    companion object {
        private val RESTRICTION_NAMES = listOf(
            CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD to "NO_DIALPAD",
            CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING to "NO_FILTERING",
            CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH to "LIMIT_STRING_LENGTH",
            CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD to "NO_KEYBOARD",
            CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO to "NO_VIDEO",
            CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT to "LIMIT_CONTENT",
            CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP to "NO_SETUP",
            CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE to "NO_TEXT_MESSAGE",
            CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION to "NO_VOICE_TRANSCRIPTION",
        )
    }
}
