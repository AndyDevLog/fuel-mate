package io.roadsensor.core.geo

import io.roadsensor.core.model.GeoPoint

/**
 * 데모 궤적(점 목록) 위를 일정 속도로 따라가며 현재 위치와 진행 방향을 계산한다.
 * 에뮬레이터는 NaviHelper 위치가 고정이라 데모에서 이걸로 위치를 만든다(docs/architecture.md 3-2).
 */
class TrackPlayer(private val points: List<GeoPoint>, private val loop: Boolean = true) {

    data class Position(val point: GeoPoint, val headingDeg: Float, val finished: Boolean)

    /** 각 점까지의 누적 거리(m). */
    private val cumulative: DoubleArray

    init {
        require(points.size >= 2) { "궤적에는 점이 2개 이상 있어야 한다" }
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + GeoMath.distanceM(points[i - 1], points[i])
        }
    }

    val lengthM: Double get() = cumulative.last()
    val destination: GeoPoint get() = points.last()

    var travelledM: Double = 0.0
        private set

    /** [dtMs] 동안 [speedMps]로 이동한 뒤의 위치. 끝에 닿으면 loop면 처음으로, 아니면 끝에 멈춘다. */
    fun advance(dtMs: Long, speedMps: Float): Position {
        travelledM += speedMps.coerceAtLeast(0f) * dtMs / 1000.0
        if (travelledM >= lengthM) travelledM = if (loop) travelledM % lengthM else lengthM
        return current()
    }

    fun current(): Position = positionAt(travelledM)

    fun positionAt(distanceM: Double): Position {
        val d = distanceM.coerceIn(0.0, lengthM)
        // d가 들어 있는 구간 [i, i+1]
        var i = cumulative.indexOfLast { it <= d }.coerceAtMost(points.size - 2)
        // 길이 0인 구간(같은 점 반복)은 건너뛴다
        while (i < points.size - 2 && cumulative[i + 1] == cumulative[i]) i++
        val a = points[i]
        val b = points[i + 1]
        val segment = cumulative[i + 1] - cumulative[i]
        val t = if (segment == 0.0) 0.0 else (d - cumulative[i]) / segment
        val point = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        return Position(point, GeoMath.bearingDeg(a, b).toFloat(), finished = !loop && d >= lengthM)
    }
}
