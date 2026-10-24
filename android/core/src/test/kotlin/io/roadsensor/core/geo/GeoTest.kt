package io.roadsensor.core.geo

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin
import io.roadsensor.core.model.RainLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {

    // 에뮬레이터 기본 위치(수정구 시흥동)와 판교역 신분당선 (docs/pleos/open-questions.md)
    private val sihung = GeoPoint(37.4127212, 127.094681)
    private val pangyo = GeoPoint(37.39479166, 127.11256944)

    @Test
    fun `geohash 표준 예제`() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun `geohash 앞부분은 상위 격자다`() {
        val cell = Geohash.encode(sihung)
        assertEquals(7, cell.length)
        assertEquals(Geohash.encode(sihung, Geohash.TILE_PRECISION), cell.take(Geohash.TILE_PRECISION))
    }

    @Test
    fun `구독 타일은 내 타일과 둘레 8칸`() {
        val tiles = Geohash.tilesAround(sihung)
        assertEquals(9, tiles.size)
        assertTrue(Geohash.encode(sihung, Geohash.TILE_PRECISION) in tiles)
        assertTrue(tiles.all { it.length == Geohash.TILE_PRECISION })
        // 둘레 타일 중심은 내 타일 중심에서 한 칸(약 4~5km) 거리
        val mine = Geohash.center(Geohash.encode(sihung, 5))
        val far = tiles.map { GeoMath.distanceM(mine, Geohash.center(it)) }.max()
        assertTrue(far in 5_000.0..8_000.0, "대각선 $far")
    }

    @Test
    fun `5자리 타일 크기`() {
        val (dLat, dLon) = Geohash.span(5)
        assertEquals(0.0439453125, dLat, 1e-12)
        assertEquals(0.0439453125, dLon, 1e-12)
    }

    @Test
    fun `격자 중심은 원래 점에서 150m 격자 안쪽`() {
        val center = Geohash.center(Geohash.encode(sihung))
        assertTrue(GeoMath.distanceM(sihung, center) < 120)
    }

    @Test
    fun `시흥동에서 판교역까지 약 2점5km, 남동쪽`() {
        val d = GeoMath.distanceM(sihung, pangyo)
        assertTrue(abs(d - 2_530) < 100, "거리 $d")
        val b = GeoMath.bearingDeg(sihung, pangyo)
        assertTrue(b in 130.0..150.0, "방위 $b")
    }

    @Test
    fun `방위각 차이는 360도를 넘어가도 짧은 쪽`() {
        assertEquals(20.0, GeoMath.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, GeoMath.angleDiffDeg(0.0, 180.0), 1e-9)
    }

    private fun rainCell(p: GeoPoint, level: RainLevel = RainLevel.MODERATE) =
        CellState(Geohash.encode(p), cars = 5, rainRatio = 0.8f, rainLevel = level, darkRatio = 0f, hardBrakes = 0, updatedAtMin = 0)

    private fun fix(heading: Float) = GeoFix(sihung, heading, 0, LocationOrigin.DEMO_TRACK)

    @Test
    fun `진행 방향 앞 부채꼴 안의 비 구간만 알린다`() {
        val ahead = rainCell(pangyo)                         // 남동쪽 약 2.5km
        val behind = rainCell(GeoPoint(37.43, 127.077))       // 북서쪽
        val alerts = AheadFilter.alerts(fix(heading = 140f), null, listOf(ahead, behind))
        assertEquals(listOf(ahead.cell), alerts.map { it.cell })
        assertEquals(AlertKind.RAIN, alerts.single().kind)
        assertEquals(RainLevel.MODERATE, alerts.single().rainLevel)
    }

    @Test
    fun `반대쪽을 보고 있어도 목적지 방향 띠 안이면 알린다`() {
        val onTheWay = rainCell(GeoPoint(37.4037, 127.1036)) // 시흥동-판교역 중간쯤
        assertTrue(AheadFilter.alerts(fix(heading = 320f), null, listOf(onTheWay)).isEmpty())
        assertEquals(1, AheadFilter.alerts(fix(heading = 320f), pangyo, listOf(onTheWay)).size)
    }

    @Test
    fun `비율이 낮은 격자는 알리지 않는다`() {
        val drizzleFew = rainCell(pangyo).copy(rainRatio = 0.1f)
        assertTrue(AheadFilter.alerts(fix(heading = 140f), null, listOf(drizzleFew)).isEmpty())
    }

    /** 시흥동 → 판교역 직선의 [from, to] 구간을 30m 간격으로 훑은 격자들(시뮬레이터 cellsAlong과 같은 방식). */
    private fun cellsAlong(from: Double, to: Double, level: RainLevel = RainLevel.MODERATE): List<CellState> {
        val out = linkedMapOf<String, CellState>()
        var t = from
        while (t <= to) {
            val p = GeoPoint(sihung.lat + (pangyo.lat - sihung.lat) * t, sihung.lon + (pangyo.lon - sihung.lon) * t)
            val c = rainCell(p, level)
            out.getOrPut(c.cell) { c }
            t += 30.0 / 2_543
        }
        return out.values.toList()
    }

    @Test
    fun `이어진 비 격자는 알림 하나로 묶는다`() {
        val zone = cellsAlong(0.4, 0.9)
        assertTrue(zone.size > 5)
        val alerts = AheadFilter.alerts(fix(heading = 140f), null, zone)
        val a = alerts.single()
        assertEquals(zone.size, a.cells.size)
        assertEquals(a.cell, a.cells.first())
        // 시작은 약 1km 앞, 길이는 약 1.27km(구간 0.4~0.9 × 2.54km)
        assertTrue(a.distanceM in 900..1_150, "시작 ${a.distanceM}")
        assertTrue(a.lengthM in 1_100..1_400, "길이 ${a.lengthM}")
    }

    @Test
    fun `떨어진 비 구간은 따로 알리고, 구간 강도는 가장 센 격자를 따른다`() {
        val near = cellsAlong(0.2, 0.3, RainLevel.LIGHT) + cellsAlong(0.31, 0.31, RainLevel.HEAVY)
        val far = cellsAlong(0.7, 0.8)
        val alerts = AheadFilter.alerts(fix(heading = 140f), null, near + far)
        assertEquals(2, alerts.size)
        assertEquals(RainLevel.HEAVY, alerts[0].rainLevel)
        assertEquals(RainLevel.MODERATE, alerts[1].rainLevel)
        assertTrue(alerts[0].distanceM < alerts[1].distanceM)
    }

    @Test
    fun `내 차 격자가 든 구간은 이미 구간 안이다`() {
        val zone = cellsAlong(0.0, 0.5)
        val inside = AheadFilter.alerts(fix(heading = 140f), null, zone).single()
        assertTrue(inside.here)
        val ahead = AheadFilter.alerts(fix(heading = 140f), null, cellsAlong(0.4, 0.9)).single()
        assertTrue(!ahead.here)
    }
}
