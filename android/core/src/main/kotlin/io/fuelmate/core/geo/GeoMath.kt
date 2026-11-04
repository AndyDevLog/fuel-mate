package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** a에서 b를 바라보는 방위각(북 0°, 시계 방향). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** 두 방위각의 차이(0~180°). */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    /**
     * 점 p에서 선분 a→b까지의 거리(m)와 선분 위 위치(0~1, 범위 밖이면 0 미만이나 1 초과).
     * 수 km 범위라서 평면 근사로 충분하다.
     */
    fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, Double> {
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(a.lat))
        val bx = (b.lon - a.lon) * mPerDegLon; val by = (b.lat - a.lat) * mPerDegLat
        val px = (p.lon - a.lon) * mPerDegLon; val py = (p.lat - a.lat) * mPerDegLat
        val len2 = bx * bx + by * by
        if (len2 == 0.0) return sqrt(px * px + py * py) to 0.0
        val t = (px * bx + py * by) / len2
        val cx = bx * t.coerceIn(0.0, 1.0); val cy = by * t.coerceIn(0.0, 1.0)
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy)) to t
    }
}
