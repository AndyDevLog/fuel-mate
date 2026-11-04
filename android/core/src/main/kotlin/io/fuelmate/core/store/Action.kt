package io.fuelmate.core.store

import io.fuelmate.core.cost.AvgFuelPrices
import io.fuelmate.core.cost.ChargeTier
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.Trip

/** 스토어에 들어오는 모든 변화. 어댑터는 SDK 값을 이 타입으로 바꿔서 넣기만 한다. */
sealed interface Action {
    // 차량 (Pleos Vehicle SDK)
    data class BrakeChanged(val pressed: Boolean?) : Action

    // 차량 (AAOS Car API)
    data class SpeedSample(val timeMs: Long, val speedMps: Float) : Action
    /** [timeMs]는 속도 표본과 같은 시계, [wallMs]는 벽시계(운행 날짜용). 운행 시작·끝이 기어로 정해진다 */
    data class GearChanged(val gear: Gear, val timeMs: Long, val wallMs: Long) : Action
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

    // 단가 (오피넷 평균가는 서비스가 하루 한 번 받는다, 충전요금 단계는 사용자 설정)
    data class FuelPriceUpdated(val prices: AvgFuelPrices) : Action
    data class ChargeTierChanged(val tier: ChargeTier) : Action

    // 운행 기록 (앱이 차 안 저장소에서 읽어 되돌린다)
    data class TripsRestored(val trips: List<Trip>) : Action

    // 위치
    data class LocationFixed(val fix: GeoFix) : Action
    data class DestinationChanged(val destination: GeoPoint?) : Action
}
