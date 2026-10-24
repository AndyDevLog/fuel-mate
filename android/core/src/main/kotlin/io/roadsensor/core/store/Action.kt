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
    data class LocationFixed(val fix: GeoFix) : Action
    data class DestinationChanged(val destination: GeoPoint?) : Action

    // 서버
    data class CellsUpdated(val updated: List<CellState>, val expired: List<String>) : Action
    data class UploadStatusChanged(val ok: Boolean, val pending: Int) : Action
    data class FeedStatusChanged(val connected: Boolean) : Action
}
