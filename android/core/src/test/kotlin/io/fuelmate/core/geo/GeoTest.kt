package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {

    // 에뮬레이터 기본 위치(수정구 시흥동)와 판교역 신분당선 (docs/pleos/open-questions.md)
    private val sihung = GeoPoint(37.4127212, 127.094681)
    private val pangyo = GeoPoint(37.39479166, 127.11256944)

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

    @Test
    fun `점에서 선분까지 거리와 선분 위 위치`() {
        val (d, t) = GeoMath.distanceToSegment(midpoint(sihung, pangyo), sihung, pangyo)
        assertTrue(d < 1.0, "거리 $d")
        assertTrue(abs(t - 0.5) < 0.01, "위치 $t")
    }

    private fun midpoint(a: GeoPoint, b: GeoPoint) = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
}
