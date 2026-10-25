package io.roadsensor.core.store

import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.Gear
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.Telltale

/** 스토어에 들어오는 모든 변화. 어댑터는 SDK 값을 이 타입으로 바꿔서 넣기만 한다. */
sealed interface Action {
    // 차량 (Pleos Vehicle SDK)
    data class WiperChanged(val on: Boolean?) : Action
    data class WiperSwitchChanged(val switch: String?) : Action
    data class LowBeamChanged(val on: Boolean?) : Action
    data class TailLampChanged(val on: Boolean?) : Action
    data class BrakeChanged(val pressed: Boolean?) : Action
    /** null = 경고등 API를 쓸 수 없음. */
    data class TelltalesChanged(val telltales: List<Telltale>?) : Action

    // 차량 (AAOS Car API)
    data class SpeedSample(val timeMs: Long, val speedMps: Float) : Action
    data class GearChanged(val gear: Gear) : Action
    data class DriveModeChanged(val restricted: Boolean, val activeRestrictions: Int) : Action

    // 위치
    /**
     * 위치와 그 순간의 앞길(첫 점 = 지금 위치, null = 모름)을 한 번에 바꾼다.
     * 따로 보내면 1초에 상태가 두 번 바뀌고, 사이에 "새 위치 + 예전 앞길" 상태가 생긴다.
     */
    data class LocationFixed(val fix: GeoFix, val routeAhead: List<GeoPoint>? = null) : Action
    data class DestinationChanged(val destination: GeoPoint?) : Action

    // 서버
    data class CellsUpdated(val updated: List<CellState>, val expired: List<String>) : Action
    data class UploadStatusChanged(val ok: Boolean, val pending: Int) : Action
    data class FeedStatusChanged(val connected: Boolean) : Action
}
