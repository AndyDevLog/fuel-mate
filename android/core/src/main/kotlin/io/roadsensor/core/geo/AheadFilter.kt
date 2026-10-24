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
        // 내 차가 있는 격자는 방향과 상관없이 넣는다(격자 중심이 살짝 뒤에 있어도 "이미 구간 안"을 알 수 있게)
        val myCell = Geohash.encode(here)
        val hits = cells.asSequence()
            .mapNotNull { cell ->
                val center = Geohash.center(cell.cell)
                val distance = GeoMath.distanceM(here, center)
                if (cell.cell != myCell && !isAhead(here, fix.headingDeg.toDouble(), center, distance, destination)) null
                else Hit(cell, center, distance)
            }
            .toList()
        return AlertKind.entries
            .flatMap { kind -> zones(hits.filter { kind in kindsOf(it.cell) }).map { toAlert(kind, it, myCell) } }
            .sortedBy { it.distanceM }
            .take(MAX_ALERTS)
    }

    private class Hit(val cell: CellState, val center: GeoPoint, val distance: Double)

    /**
     * 이웃(8방향) 격자끼리 이어 붙인 덩어리들. 각 덩어리는 가까운 순으로 정렬한다.
     * 격자 중심을 정수 격자 번호로 바꿔 해시로 이웃을 찾는다(격자 수천 개에서도 O(n)).
     */
    private fun zones(hits: List<Hit>): List<List<Hit>> {
        val (dLat, dLon) = Geohash.span(Geohash.CELL_PRECISION)
        val index = hits.indices.groupBy { i ->
            Math.round(hits[i].center.lat / dLat) to Math.round(hits[i].center.lon / dLon)
        }
        val parent = IntArray(hits.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }
        for ((key, group) in index) for (di in -1L..1L) for (dj in -1L..1L) {
            val near = index[(key.first + di) to (key.second + dj)] ?: continue
            for (j in near) parent[root(j)] = root(group.first())
        }
        return hits.indices.groupBy(::root).values.map { group -> group.map { hits[it] }.sortedBy { it.distance } }
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

    private fun toAlert(kind: AlertKind, zone: List<Hit>, myCell: String) = RoadAlert(
        kind = kind,
        cell = zone.first().cell.cell,
        distanceM = zone.first().distance.toInt(),
        rainLevel = if (kind == AlertKind.RAIN) strongest(zone.map { it.cell.rainLevel }) else RainLevel.NONE,
        cells = zone.map { it.cell.cell },
        lengthM = (zone.last().distance - zone.first().distance).toInt(),
        here = zone.any { it.cell.cell == myCell },
    )

    /** 구간에서 가장 센 비. 세기를 모르는(AUTO) 격자만 있으면 UNKNOWN. */
    private fun strongest(levels: List<RainLevel>): RainLevel =
        levels.filter { it != RainLevel.UNKNOWN && it != RainLevel.NONE }.maxByOrNull { it.code }
            ?: if (RainLevel.UNKNOWN in levels) RainLevel.UNKNOWN else RainLevel.NONE
}
