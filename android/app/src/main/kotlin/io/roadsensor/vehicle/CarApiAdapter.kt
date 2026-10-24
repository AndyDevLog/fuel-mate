package io.roadsensor.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import io.roadsensor.Log
import io.roadsensor.core.model.Gear
import io.roadsensor.core.store.Action

/**
 * AAOS 표준 Car API → 스토어 액션: 주행 규제(CarUxRestrictions), 속도, 기어.
 * Pleos 문서는 주행/정차를 CarUxRestrictions로만 판단하라고 한다(docs/pleos/driving-restriction.md).
 */
class CarApiAdapter(private val context: Context, private val dispatch: (Action) -> Unit) {

    private val car = Car.createCar(context)
    private var ux: CarUxRestrictionsManager? = null
    private var props: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                    dispatch(Action.SpeedSample(SystemClock.elapsedRealtime(), value.value as Float))
                VehiclePropertyIds.GEAR_SELECTION -> dispatch(Action.GearChanged(gearOf(value.value as Int)))
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) =
            Log.w("Car 속성 오류 ${VehiclePropertyIds.toString(propertyId)}")
    }

    fun start() {
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        ux = manager
        dispatchUx(manager.currentCarUxRestrictions)
        manager.registerListener { dispatchUx(it) }

        props = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        subscribeProperties()
    }

    /** 속도 권한을 새로 받았을 때 다시 구독한다. */
    fun onPermissionsChanged() {
        props?.let { runCatching { it.unregisterCallback(callback) } }
        subscribeProperties()
    }

    fun stop() {
        runCatching { ux?.unregisterListener() }
        runCatching { props?.unregisterCallback(callback) }
        car.disconnect()
    }

    private fun subscribeProperties() {
        val manager = props ?: return
        subscribe(manager, VehiclePropertyIds.GEAR_SELECTION, "기어")
        if (hasSpeedPermission(context)) subscribe(manager, VehiclePropertyIds.PERF_VEHICLE_SPEED, "속도")
        else Log.w("속도 권한(${Car.PERMISSION_SPEED}) 없음: 급정거 판정을 하지 않는다")
    }

    private fun subscribe(manager: CarPropertyManager, propertyId: Int, name: String) {
        runCatching { manager.registerCallback(callback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
            .onSuccess { ok -> if (!ok) Log.w("$name 구독 거부됨") }
            .onFailure { Log.w("$name 구독 실패", it) }
    }

    private fun dispatchUx(r: CarUxRestrictions) =
        dispatch(Action.DriveModeChanged(r.isRequiresDistractionOptimization, r.activeRestrictions))

    // VehicleGear: NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8, 그 이상은 수동 단수(1단~)
    private fun gearOf(value: Int) = when (value) {
        1 -> Gear.N
        2 -> Gear.R
        4 -> Gear.P
        8 -> Gear.D
        else -> if (value > 8) Gear.D else Gear.UNKNOWN
    }

    companion object {
        fun hasSpeedPermission(context: Context) =
            context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED
    }
}
