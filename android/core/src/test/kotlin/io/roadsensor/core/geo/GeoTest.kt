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
}
