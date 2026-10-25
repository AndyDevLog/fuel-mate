package io.roadsensor.core.present

import io.roadsensor.core.geo.AheadFilter
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.Gear
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.model.RoadAlert

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
    /** 앞길(지도 색칠·패널 막대). 위치가 없으면 null. */
    val route: RouteView?,
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

/** 앞길과 그 위 구간. 거리는 길을 따라 잰 값(m)이다. estimated = 실제 길을 몰라 진행 방향 직선으로 대신함. */
data class RouteView(
    val path: List<GeoPoint>,
    val lengthM: Int,
    val estimated: Boolean,
    val segments: List<SegmentView>,
)

data class SegmentView(val fromM: Int, val toM: Int, val kind: AlertKind, val rainLevel: RainLevel)

/** 격자 하나. 종류별 강도는 0~1이다. rain은 비가 있을 때(rainLevel != NONE)만 0보다 크다. */
data class CellView(
    val cell: String,
    val rain: Float,
    val rainLevel: RainLevel,
    val dark: Float,
    val brake: Float,
)

/**
 * 알림 한 줄(이웃 격자를 묶은 구간 하나). 문구는 주행 모드 한 줄(DriveLine)과 같은 규칙으로 여기서 만든다.
 * headline: "약 1km 앞" 또는 이미 구간 안이면 "지금 여기".
 * lengthText: "400m 구간" 또는 구간 안이면 "약 4.9km 계속". 짧은 구간은 null.
 */
data class AlertView(
    val kind: AlertKind,
    val cell: String,
    val distanceM: Int,
    val distanceText: String,
    val label: String,
    val headline: String,
    val here: Boolean,
    val cells: List<String>,
    val lengthText: String?,
)

/**
 * 내 차 상태(내 차 화면용). null = 아직 못 받음.
 * telltales: null = 경고등 API를 쓸 수 없음(권한), 빈 목록 = 켜진 경고등 없음.
 * hardBrakes: 이번 실행에서 내 차가 감지해 보낸 급정거 수(내 기여 표시, docs/architecture.md 6-1).
 */
data class MyCarView(
    val wiperOn: Boolean?,
    val wiperText: String?,
    val rainLevel: RainLevel,
    val lowBeamOn: Boolean?,
    val tailLampOn: Boolean?,
    val brakePressed: Boolean?,
    val gear: Gear,
    val telltales: List<String>?,
    val hardBrakes: Int,
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
        route = state.ahead?.let { p ->
            RouteView(p.path, p.lengthM, p.estimated, p.segments.map { SegmentView(it.fromM, it.toM, it.kind, it.rainLevel) })
        },
        cells = state.cells.values.sortedBy { it.cell }.map(::cellView),
        alerts = state.alerts.map(::alertView),
        myCar = myCarView(state),
        online = state.link.feedConnected,
    )

    private val AHEAD = AheadView(
        AheadFilter.CONE_RADIUS_M.toInt(),
        AheadFilter.CONE_HALF_ANGLE_DEG.toInt(),
        AheadFilter.CORRIDOR_HALF_WIDTH_M.toInt(),
    )

    private fun alertView(a: RoadAlert) = AlertView(
        kind = a.kind,
        cell = a.cell,
        distanceM = a.distanceM,
        distanceText = DriveLine.distance(a.distanceM),
        label = DriveLine.label(a),
        headline = DriveLine.headline(a),
        here = a.here,
        cells = a.cells,
        lengthText = DriveLine.zoneAhead(a),
    )

    private fun myCarView(s: AppState) = MyCarView(
        wiperOn = s.vehicle.wiperOn,
        wiperText = wiperText(s.vehicle.wiperSwitch),
        rainLevel = s.rainLevel,
        lowBeamOn = s.vehicle.lowBeamOn,
        tailLampOn = s.vehicle.tailLampOn,
        brakePressed = s.vehicle.brakePressed,
        gear = s.vehicle.gear,
        telltales = s.vehicle.telltales?.map { "${it.type} ${it.state}" },
        hardBrakes = s.contribution.hardBrakes,
    )

    /** WiperSwitch 이름(RainClassifier 참고) → 화면 문구. 모르는 값은 그대로 보여준다. */
    internal fun wiperText(switch: String?): String? = when {
        switch == null -> null
        switch == "OFF" -> "꺼짐"
        switch == "MIST" -> "안개"
        switch == "AUTO" -> "자동"
        switch.startsWith("INTERMITTENT_LEVEL_") -> "간헐 ${switch.removePrefix("INTERMITTENT_LEVEL_")}"
        switch.startsWith("CONTINUOUS_LEVEL_") -> "연속 ${switch.removePrefix("CONTINUOUS_LEVEL_")}"
        else -> switch
    }

    private fun cellView(c: CellState) = CellView(
        cell = c.cell,
        rain = if (c.rainLevel == RainLevel.NONE) 0f else c.rainRatio.coerceIn(0f, 1f),
        rainLevel = c.rainLevel,
        dark = c.darkRatio.coerceIn(0f, 1f),
        brake = (c.hardBrakes.toFloat() / BRAKE_FULL).coerceIn(0f, 1f),
    )
}
