package io.fuelmate.core.store

import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint

/** 스토어에 들어오는 모든 변화. 어댑터는 SDK 값을 이 타입으로 바꿔서 넣기만 한다. */
sealed interface Action {
    // 차량 (Pleos Vehicle SDK)
    data class BrakeChanged(val pressed: Boolean?) : Action

    // 차량 (AAOS Car API)
    data class SpeedSample(val timeMs: Long, val speedMps: Float) : Action
    data class GearChanged(val gear: Gear) : Action
    data class DriveModeChanged(val restricted: Boolean, val activeRestrictions: Int) : Action

    // 위치
    data class LocationFixed(val fix: GeoFix) : Action
    data class DestinationChanged(val destination: GeoPoint?) : Action
}
