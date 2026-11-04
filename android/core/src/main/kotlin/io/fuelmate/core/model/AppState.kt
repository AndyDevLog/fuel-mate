package io.fuelmate.core.model

import io.fuelmate.core.detect.BrakeTracker

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
    /** 최근 운전 사건(새 것이 앞). 최대 [MAX_EVENTS]개. */
    val events: List<DriveEvent> = emptyList(),
    /** 급정거 판정용 내부 상태. 화면은 쓰지 않는다. */
    val brakeTracker: BrakeTracker = BrakeTracker(),
) {
    companion object {
        const val MAX_EVENTS = 50
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

/** 내 차에서 감지한 운전 사건. 운행 요약과 운전 습관 비용 계산에 쓴다. */
sealed interface DriveEvent {
    val timeMs: Long

    data class HardBrake(
        override val timeMs: Long,
        val startSpeedMps: Float,
    ) : DriveEvent
}
