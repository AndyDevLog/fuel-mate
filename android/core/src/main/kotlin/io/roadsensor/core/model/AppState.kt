package io.roadsensor.core.model

import io.roadsensor.core.detect.BrakeTracker
import io.roadsensor.core.detect.RainClassifier

/**
 * 앱 전체 상태. 화면이 보여줄 것은 이 값 하나로 결정된다(docs/architecture.md 4장).
 * 리듀서(store/Reducer.kt)만 새 값을 만든다.
 */
data class AppState(
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val drive: DriveMode = DriveMode(),
    val location: GeoFix? = null,
    val destination: GeoPoint? = null,
    val cells: Map<String, CellState> = emptyMap(),
    val alerts: List<RoadAlert> = emptyList(),
    val link: LinkStatus = LinkStatus(),
    val contribution: Contribution = Contribution(),
    /** 최근 사건(새 것이 앞). 업로더가 가져간 뒤에도 화면 표시용으로 최대 [MAX_EVENTS]개 남긴다. */
    val events: List<RoadEvent> = emptyList(),
    /** 급정거 판정용 내부 상태. 화면은 쓰지 않는다. */
    val brakeTracker: BrakeTracker = BrakeTracker(),
) {
    /** 내 차 기준 비의 세기. */
    val rainLevel: RainLevel get() = RainClassifier.classify(vehicle.wiperOn, vehicle.wiperSwitch)

    companion object {
        const val MAX_EVENTS = 20
    }
}
