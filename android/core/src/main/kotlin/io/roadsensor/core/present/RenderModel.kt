package io.roadsensor.core.present

import io.roadsensor.core.geo.AheadFilter
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.RainLevel

/**
 * 렌더러에 넘기는 값(docs/architecture.md 4장 "렌더 모델").
 * AppState에서 화면에 필요한 것만 추린다. 속도처럼 자주 바뀌지만 그림에 안 쓰는 값은 넣지 않아서,
 * distinctUntilChanged()만으로 "바뀔 때만 그린다"가 된다.
 */
data class RenderModel(
    val screen: Screen,
    /** 내 위치. 원본 좌표지만 화면(같은 기기 안의 WebView)에만 쓰고 서버로 보내지 않는다. */
    val center: GeoPoint?,
    val heading: Float,
    val destination: GeoPoint?,
    val ahead: AheadView,
    /** 격자 ID 순으로 정렬한다(같은 상태면 같은 값). */
    val cells: List<CellView>,
    val alerts: List<AlertView>,
    val myCar: MyCarView,
    /** 지도 구독이 연결되어 있는가. 끊기면 격자가 비고 화면에 "오프라인"을 표시한다. */
    val online: Boolean,
)

enum class Screen { MAP, MY_CAR }

/** 진행 방향 판정 범위. 렌더러가 같은 상수를 따로 두지 않게 넘긴다. */
data class AheadView(val radiusM: Int, val halfAngleDeg: Int, val corridorHalfWidthM: Int)

/** 격자 하나. 종류별 강도는 0~1이다. rain은 비가 있을 때(rainLevel != NONE)만 0보다 크다. */
data class CellView(
    val cell: String,
    val rain: Float,
    val rainLevel: RainLevel,
    val dark: Float,
    val brake: Float,
)

/** 알림 한 줄. 문구는 주행 모드 한 줄(DriveLine)과 같은 규칙으로 여기서 만든다. */
data class AlertView(
    val kind: AlertKind,
    val cell: String,
    val distanceM: Int,
    val distanceText: String,
    val label: String,
)

/** 내 차 상태(트윈 화면용). null = 아직 못 받음. */
data class MyCarView(
    val wiperOn: Boolean?,
    val rainLevel: RainLevel,
    val lowBeamOn: Boolean?,
    val brakePressed: Boolean?,
)

object RenderModels {
    /** 급정거 강도 1이 되는 15분 급정거 수. 알림 기준(AheadFilter.MIN_HARD_BRAKES)보다 크게 둔다. */
    const val BRAKE_FULL = 5

    fun of(state: AppState, screen: Screen = Screen.MAP): RenderModel = RenderModel(
        screen = screen,
        center = state.location?.point,
        heading = state.location?.headingDeg?.takeIf { it.isFinite() } ?: 0f,
        destination = state.destination,
        ahead = AHEAD,
        cells = state.cells.values.sortedBy { it.cell }.map(::cellView),
        alerts = state.alerts.map { AlertView(it.kind, it.cell, it.distanceM, DriveLine.distance(it.distanceM), DriveLine.label(it)) },
        myCar = MyCarView(state.vehicle.wiperOn, state.rainLevel, state.vehicle.lowBeamOn, state.vehicle.brakePressed),
        online = state.link.feedConnected,
    )

    private val AHEAD = AheadView(
        AheadFilter.CONE_RADIUS_M.toInt(),
        AheadFilter.CONE_HALF_ANGLE_DEG.toInt(),
        AheadFilter.CORRIDOR_HALF_WIDTH_M.toInt(),
    )

    private fun cellView(c: CellState) = CellView(
        cell = c.cell,
        rain = if (c.rainLevel == RainLevel.NONE) 0f else c.rainRatio.coerceIn(0f, 1f),
        rainLevel = c.rainLevel,
        dark = c.darkRatio.coerceIn(0f, 1f),
        brake = (c.hardBrakes.toFloat() / BRAKE_FULL).coerceIn(0f, 1f),
    )
}
