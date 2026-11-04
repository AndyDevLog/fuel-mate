package io.fuelmate.core.store

import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
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

    /**
     * 에너지 값(Pleos Fuel·EvBattery 또는 데모). 양은 연료 L, 전기 kWh. null 필드는 "이번에 안 옴"이라
     * 같은 공급원의 이전 값을 유지한다(전기차는 잔량·SoC·용량이 따로 온다).
     * [timeMs]는 [SpeedSample]과 같은 시계(elapsedRealtime)다. 회생과 충전을 가를 때 쓴다.
     */
    data class EnergyReading(
        val timeMs: Long,
        val kind: EnergyKind,
        val origin: EnergyOrigin,
        val amount: Double? = null,
        val capacity: Double? = null,
        val percent: Float? = null,
    ) : Action

    // 위치
    data class LocationFixed(val fix: GeoFix) : Action
    data class DestinationChanged(val destination: GeoPoint?) : Action
}
