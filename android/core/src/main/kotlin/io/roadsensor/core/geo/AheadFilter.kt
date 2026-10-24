package io.roadsensor.core.geo

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.model.RoadAlert

/**
 * 진행 방향 앞의 도로 이벤트 고르기.
 * 내비 경로 선형 API가 없어서(docs/pleos/navihelper-sdk.md) 진행 방향 부채꼴을 기본으로 하고,
 * 목적지가 있으면 목적지까지 직선 띠로 보강한다(docs/pleos/open-questions.md 2번 결정).
 */
object AheadFilter {
    // 초기값. 시뮬레이터로 조정한다.
    const val CONE_RADIUS_M = 5_000.0
    const val CONE_HALF_ANGLE_DEG = 30.0
    const val CORRIDOR_HALF_WIDTH_M = 1_000.0
    const val MIN_RAIN_RATIO = 0.3f
    const val MIN_DARK_RATIO = 0.5f
    const val MIN_HARD_BRAKES = 2
    const val MAX_ALERTS = 5

    fun alerts(fix: GeoFix?, destination: GeoPoint?, cells: Collection<CellState>): List<RoadAlert> {
        if (fix == null) return emptyList()
        val here = fix.point
        return cells.asSequence()
            .mapNotNull { cell ->
                val center = Geohash.center(cell.cell)
                val distance = GeoMath.distanceM(here, center)
                if (!isAhead(here, fix.headingDeg.toDouble(), center, distance, destination)) null
                else cell to distance
            }
            .flatMap { (cell, distance) -> kindsOf(cell).map { kind -> toAlert(kind, cell, distance) } }
            .sortedBy { it.distanceM }
            .take(MAX_ALERTS)
            .toList()
    }

    private fun isAhead(here: GeoPoint, heading: Double, target: GeoPoint, distance: Double, destination: GeoPoint?): Boolean {
        val inCone = distance <= CONE_RADIUS_M &&
            GeoMath.angleDiffDeg(GeoMath.bearingDeg(here, target), heading) <= CONE_HALF_ANGLE_DEG
        if (inCone) return true
        if (destination == null) return false
        val (offset, t) = GeoMath.distanceToSegment(target, here, destination)
        return offset <= CORRIDOR_HALF_WIDTH_M && t in 0.0..1.0
    }

    private fun kindsOf(c: CellState): List<AlertKind> = buildList {
        if (c.rainRatio >= MIN_RAIN_RATIO && c.rainLevel != RainLevel.NONE) add(AlertKind.RAIN)
        if (c.darkRatio >= MIN_DARK_RATIO) add(AlertKind.DARK)
        if (c.hardBrakes >= MIN_HARD_BRAKES) add(AlertKind.HARD_BRAKE)
    }

    private fun toAlert(kind: AlertKind, c: CellState, distance: Double) = RoadAlert(
        kind = kind,
        cell = c.cell,
        distanceM = distance.toInt(),
        rainLevel = if (kind == AlertKind.RAIN) c.rainLevel else RainLevel.NONE,
    )
}
