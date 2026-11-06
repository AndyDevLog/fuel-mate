package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.LocationOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StuckNaviFallbackTest {
    private val start = GeoPoint(37.40, 127.10)
    private val north = GeoPoint(37.50, 127.10)   // 북쪽으로 약 11km

    @Test
    fun `멈춰 서 있으면 내비 위치를 그대로 쓴다`() {
        val f = StuckNaviFallback()
        repeat(100) { i ->
            val fix = f.update(start, 0f, 0f, north, i * 2_000L)
            assertEquals(LocationOrigin.NAVI_HELPER, fix.origin)
            assertEquals(start, fix.point)
        }
    }

    @Test
    fun `내비 위치가 멈춘 채 200m 넘게 달리면 목적지 쪽으로 달린 만큼 움직인다`() {
        val f = StuckNaviFallback()
        // 초속 20m, 2초마다: 200m까지는 내비 위치
        var t = 0L
        repeat(5) {
            val fix = f.update(start, 0f, 20f, north, t)
            assertEquals(LocationOrigin.NAVI_HELPER, fix.origin)
            t += 2_000
        }
        // 다섯 번째 간격에서 200m(40m × 5)를 넘긴다 → 달린 거리를 따라잡는다
        val first = f.update(start, 0f, 20f, north, t)
        assertEquals(LocationOrigin.NAVI_SIMULATED, first.origin)
        assertEquals(200.0, GeoMath.distanceM(start, first.point), 1.0)
        assertEquals(0f, first.headingDeg, 0.5f)   // 북쪽

        t += 2_000
        val second = f.update(start, 0f, 20f, north, t)
        assertEquals(240.0, GeoMath.distanceM(start, second.point), 1.0)
    }

    @Test
    fun `내비 위치가 한 번이라도 움직이면 실차로 보고 다시는 흉내 내지 않는다`() {
        val f = StuckNaviFallback()
        f.update(start, 0f, 20f, north, 0)
        val moved = GeoPoint(37.401, 127.10)   // 약 110m
        assertEquals(moved, f.update(moved, 10f, 20f, north, 2_000).point)
        // 그 뒤 터널에서 위치가 멈춰도 그대로 둔다
        var t = 4_000L
        repeat(50) {
            val fix = f.update(moved, 10f, 20f, north, t)
            assertEquals(LocationOrigin.NAVI_HELPER, fix.origin)
            assertEquals(moved, fix.point)
            t += 2_000
        }
    }

    @Test
    fun `직선으로 움직이다 내비 위치가 움직이면 내비 위치로 돌아간다`() {
        val f = StuckNaviFallback()
        var t = 0L
        repeat(10) { f.update(start, 0f, 20f, north, t); t += 2_000 }
        assertEquals(LocationOrigin.NAVI_SIMULATED, f.update(start, 0f, 20f, north, t).origin)
        val real = GeoPoint(37.45, 127.12)
        val fix = f.update(real, 45f, 20f, north, t + 2_000)
        assertEquals(LocationOrigin.NAVI_HELPER, fix.origin)
        assertEquals(real, fix.point)
    }

    @Test
    fun `목적지에 닿으면 멈추고, 목적지가 없으면 그 자리에 있다`() {
        val near = GeoPoint(37.403, 127.10)   // 약 330m
        val f = StuckNaviFallback()
        var t = 0L
        var last = start
        repeat(30) { last = f.update(start, 0f, 20f, near, t).point; t += 2_000 }
        assertEquals(near, last)

        val g = StuckNaviFallback()
        t = 0
        repeat(20) { g.update(start, 0f, 20f, north, t); t += 2_000 }
        val before = g.update(start, 0f, 20f, north, t).point
        t += 2_000
        assertEquals(before, g.update(start, 0f, 20f, null, t).point)
    }

    @Test
    fun `목적지가 바뀌면 지금 자리에서 새 목적지 쪽으로 간다`() {
        val east = GeoPoint(37.40, 127.20)
        val f = StuckNaviFallback()
        var t = 0L
        repeat(20) { f.update(start, 0f, 20f, north, t); t += 2_000 }
        val here = f.update(start, 0f, 20f, north, t).point
        t += 2_000
        val fix = f.update(start, 0f, 20f, east, t)
        assertEquals(40.0, GeoMath.distanceM(here, fix.point), 1.0)
        assertTrue(GeoMath.distanceM(fix.point, east) < GeoMath.distanceM(here, east))
    }

    @Test
    fun `앱이 오래 멈췄다 돌아와도 한 번에 10초치만 움직인다`() {
        val f = StuckNaviFallback()
        var t = 0L
        repeat(20) { f.update(start, 0f, 20f, north, t); t += 2_000 }
        val before = f.update(start, 0f, 20f, north, t).point
        val after = f.update(start, 0f, 20f, north, t + 10 * 60_000L).point
        assertEquals(200.0, GeoMath.distanceM(before, after), 1.0)
    }
}
