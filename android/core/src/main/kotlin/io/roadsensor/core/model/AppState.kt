package io.roadsensor.core.model

import io.roadsensor.core.detect.BrakeTracker
import io.roadsensor.core.detect.RainClassifier
import io.roadsensor.core.geo.AheadProfile

/**
 * 앱 전체 상태. 화면이 보여줄 것은 이 값 하나로 결정된다(docs/architecture.md 4장).
 * 리듀서(store/Reducer.kt)만 새 값을 만든다.
 */
data class AppState(
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val drive: DriveMode = DriveMode(),
    val location: GeoFix? = null,
    val destination: GeoPoint? = null,
    /** 위치 출처가 주는 앞길(데모 궤적). null = 모름(내비 위치). 화면용이고 저장·전송하지 않는다. */
    val routeAhead: List<GeoPoint>? = null,
    /** 앞길 프로필. 리듀서가 위치·앞길·격자가 바뀔 때 다시 계산한다. */
    val ahead: AheadProfile? = null,
    /** 주행 거리계. 주행 화면의 앞길 띠가 띠를 고정하고 내 차만 움직이는 데 쓴다. 화면용이고 저장·전송하지 않는다. */
    val odometer: Odometer = Odometer(),
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

/**
 * [meters] = 위치를 받을 때마다 이전 위치와의 거리를 더한 값.
 * 한 번에 [JUMP_M] 넘게 튀면(데모 경로가 처음으로 돌아감, 위치 출처 바뀜) 이동이 아니라 위치가 바뀐 것이라
 * 더하지 않고 [epoch]를 올린다. 렌더러는 epoch가 바뀌면 띠를 새로 맞춘다.
 * [speedMps] = 그 위치를 받을 때의 차량 속도(모르면 null). 렌더러가 구간마다 가감속 곡선(에르미트)을 만드는 데 쓴다.
 * 속도는 0.2초마다 바뀌지만 위치를 받을 때만 찍어서, 렌더 모델이 1초에 한 번만 바뀌게 한다.
 */
data class Odometer(val meters: Double = 0.0, val epoch: Int = 0, val speedMps: Float? = null) {
    companion object {
        /** 위치는 1초마다 온다. 300m/s는 차가 낼 수 없는 속도다 */
        const val JUMP_M = 300.0
    }
}
