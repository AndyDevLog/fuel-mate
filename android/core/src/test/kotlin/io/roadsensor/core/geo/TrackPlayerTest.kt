package io.roadsensor.core.geo

import io.roadsensor.core.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackPlayerTest {

    // 북쪽으로 약 1.11km, 이어서 동쪽으로 약 0.88km (위도 37.5 기준)
    private val a = GeoPoint(37.50, 127.00)
    private val b = GeoPoint(37.51, 127.00)
    private val c = GeoPoint(37.51, 127.01)

    @Test
    fun `점이 하나뿐이면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { TrackPlayer(listOf(a)) }
    }

    @Test
    fun `속도와 시간만큼 궤적을 따라 이동한다`() {
        val p = TrackPlayer(listOf(a, b, c))
        val pos = p.advance(dtMs = 10_000, speedMps = 50f)  // 500m 북쪽
        assertEquals(500.0, p.travelledM, 1e-6)
        assertTrue(abs(GeoMath.distanceM(a, pos.point) - 500) < 1)
        assertTrue(abs(pos.headingDeg - 0f) < 1f, "북쪽 ${pos.headingDeg}")
    }

    @Test
    fun `구간이 바뀌면 진행 방향도 바뀐다`() {
        val p = TrackPlayer(listOf(a, b, c))
        val pos = p.positionAt(GeoMath.distanceM(a, b) + 100)
        assertTrue(abs(pos.headingDeg - 90f) < 1f, "동쪽 ${pos.headingDeg}")
    }

    @Test
    fun `반복하지 않으면 끝에서 멈춘다`() {
        val p = TrackPlayer(listOf(a, b), loop = false)
        val pos = p.advance(dtMs = 600_000, speedMps = 30f)
        assertTrue(pos.finished)
        assertEquals(p.lengthM, p.travelledM, 1e-6)
        assertTrue(GeoMath.distanceM(pos.point, b) < 1)
    }

    @Test
    fun `반복하면 처음으로 돌아간다`() {
        val p = TrackPlayer(listOf(a, b), loop = true)
        val pos = p.advance(dtMs = 1_000, speedMps = (p.lengthM + 100).toFloat())
        assertFalse(pos.finished)
        assertEquals(100.0, p.travelledM, 1e-3)
    }

    @Test
    fun `정지 상태(속도 0)에서는 움직이지 않는다`() {
        val p = TrackPlayer(listOf(a, b))
        p.advance(dtMs = 5_000, speedMps = 0f)
        assertEquals(0.0, p.travelledM)
    }

    @Test
    fun `같은 점이 반복돼도 방향이 망가지지 않는다`() {
        val p = TrackPlayer(listOf(a, a, b))
        assertTrue(abs(p.positionAt(0.0).headingDeg - 0f) < 1f)
    }
}
