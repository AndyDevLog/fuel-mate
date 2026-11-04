package io.fuelmate.core.model

import io.fuelmate.core.cost.UnitPrices
import io.fuelmate.core.detect.DriveTracker

/**
 * 앱 전체 상태. 화면이 보여줄 것은 이 값 하나로 결정된다.
 * 리듀서(store/Reducer.kt)만 새 값을 만든다.
 */
data class AppState(
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val drive: DriveMode = DriveMode(),
    val location: GeoFix? = null,
    val destination: GeoPoint? = null,
    /** 주행 거리계. 위치 사이 거리를 더한다. 차량 거리계 API는 에뮬레이터에서 늘지 않는다(docs/pivot-fuel.md 2장). */
    val odometer: Odometer = Odometer(),
    /** 남은 에너지. 공급원(차량·데모)이 첫 값을 주기 전에는 null */
    val energy: EnergyState? = null,
    /** 이번 실행에서 쓴·넣은 에너지 */
    val energyMeter: EnergyMeter = EnergyMeter(),
    /** 마지막으로 움직이던 속도 표본의 시각. 잔량이 늘었을 때 회생인지 충전인지 가른다 */
    val lastMovingMs: Long? = null,
    /** 지금 운행(기어가 P가 아닌 동안). P면 null */
    val trip: Trip? = null,
    /** 끝난 운행(새 것이 앞). 최대 [MAX_TRIPS]개. 앱이 차 안에 저장하고 다시 켤 때 되돌린다 */
    val trips: List<Trip> = emptyList(),
    /** 에너지 단가. 오피넷 평균가를 받기 전에는 가정값 */
    val prices: UnitPrices = UnitPrices.ASSUMED,
    /** 운전 사건 판정용 내부 상태. 화면은 쓰지 않는다 */
    val driveTracker: DriveTracker = DriveTracker(),
) {
    companion object {
        /** 하루 4번이면 7주 가까이. "내 평소 대비"(지난 4주)에 충분하다 */
        const val MAX_TRIPS = 200
    }
}

/**
 * [meters] = 위치를 받을 때마다 이전 위치와의 거리를 더한 값.
 * 한 번에 [JUMP_M] 넘게 튀면(데모 경로가 처음으로 돌아감, 위치 출처 바뀜) 이동이 아니라 위치가 바뀐 것이라
 * 더하지 않고 [epoch]를 올린다.
 */
data class Odometer(val meters: Double = 0.0, val epoch: Int = 0) {
    companion object {
        /** 위치는 1초마다 온다. 300m/s는 차가 낼 수 없는 속도다 */
        const val JUMP_M = 300.0
    }
}
