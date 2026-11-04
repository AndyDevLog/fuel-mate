package io.fuelmate.fuelprobe

import android.car.Car
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * AAOS 표준 속성 비교. 속성 ID는 에뮬레이터 dumpsys car_service에서 확인한 값(docs/pivot-fuel.md).
 * 연속 속성은 가장 빠른 주기, 바뀔 때만 오는 속성은 ONCHANGE로 구독하고, 정적 속성은 한 번만 읽는다.
 */
class AaosEnergyProbe(context: Context, private val log: (String) -> Unit) {

    private val car = Car.createCar(context)
    private var props: CarPropertyManager? = null

    private val static = mapOf(
        0x11410105 to "INFO_FUEL_TYPE",
        0x11600104 to "INFO_FUEL_CAPACITY",
        0x11600106 to "INFO_EV_BATTERY_CAPACITY",
        0x11410107 to "INFO_EV_CONNECTOR_TYPE",
        0x11400108 to "INFO_FUEL_DOOR_LOCATION",
        // 운전 습관 비용의 물리 계산(운동 에너지)에 쓸 차량 무게(kg)·크기(mm)
        0x11400f46 to "VEHICLE_CURB_WEIGHT",
        0x1141010b to "INFO_EXTERIOR_DIMENSIONS",
    )
    private val dynamic = mapOf(
        0x11600307 to "FUEL_LEVEL",
        0x11200405 to "FUEL_LEVEL_LOW",
        0x11600308 to "RANGE_REMAINING",
        0x11600309 to "EV_BATTERY_LEVEL",
        0x1160030d to "EV_CURRENT_BATTERY_CAPACITY",
        0x1160030c to "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE",
        0x1120030b to "EV_CHARGE_PORT_CONNECTED",
        0x11400f41 to "EV_CHARGE_STATE",
        0x11400f43 to "EV_CHARGE_TIME_REMAINING",
        0x11400f44 to "EV_REGENERATIVE_BRAKING_STATE",
        0x1140040c to "EV_BRAKE_REGENERATION_LEVEL",
        0x11600204 to "PERF_ODOMETER",
        0x11600207 to "PERF_VEHICLE_SPEED",
        0x11600703 to "ENV_OUTSIDE_TEMPERATURE",
    )

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // 속도는 0.2초마다 와서 너무 많다. 바뀐 값만 찍으려고 직전 값과 비교한다
            val name = dynamic[value.propertyId] ?: return
            val text = describe(value.value)
            if (last.put(value.propertyId, text) != text) log("A ▶ $name $text")
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) = log("A 오류 ${dynamic[propertyId]} area=$areaId")
    }
    private val last = mutableMapOf<Int, String>()

    fun start() {
        val p = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        props = p
        static.forEach { (id, name) -> read(p, id, name) }
        dynamic.forEach { (id, name) ->
            read(p, id, name)
            val config = runCatching { p.getCarPropertyConfig(id) }.getOrNull()
            if (config == null) {
                log("A $name config 없음(권한 부족 또는 미지원)")
                return@forEach
            }
            val rate = if (config.changeMode == 2) CarPropertyManager.SENSOR_RATE_FASTEST else CarPropertyManager.SENSOR_RATE_ONCHANGE
            runCatching { p.registerCallback(callback, id, rate) }
                .onSuccess { log("A $name 구독=$it rate ${config.minSampleRate}~${config.maxSampleRate}Hz") }
                .onFailure { log("A $name 구독 실패: ${it.javaClass.simpleName} ${it.message}") }
        }
    }

    fun stop() {
        runCatching { props?.unregisterCallback(callback) }
        car.disconnect()
    }

    private fun read(p: CarPropertyManager, id: Int, name: String) {
        runCatching { p.getProperty<Any>(id, 0) }
            .onSuccess { log("A $name = ${describe(it.value)} status=${it.status}") }
            .onFailure { log("A $name 읽기 실패: ${it.javaClass.simpleName} ${it.message}") }
    }

    private fun describe(value: Any?): String = when (value) {
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }
}
