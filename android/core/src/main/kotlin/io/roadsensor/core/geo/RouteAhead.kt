package io.roadsensor.core.geo

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.RainLevel
import kotlin.math.cos
import kotlin.math.sin

/**
 * 앞길 프로필: 내가 갈 길 앞 [LENGTH_M]을 [STEP_M]마다 훑어 격자 상태를 읽고, 같은 종류가 이어진 곳을 구간으로 묶는다.
 * 지도(앞길 색칠)와 패널(앞 5km 막대), 알림 문구가 모두 이 구간을 쓴다. 그래서 세 곳의 거리가 늘 같다.
 *
 * 길: 위치 출처가 앞길을 주면(데모 궤적) 그 길을 쓰고, 없으면(내비 위치, 경로 선형 API 없음)
 * 진행 방향 직선으로 대신한다([AheadProfile.estimated]). 앞길은 화면용으로만 쓰고 저장하거나 보내지 않는다.
 */
object RouteAhead {
    const val LENGTH_M = 5_000.0
    const val STEP_M = 30.0
    /** 같은 종류 구간 사이의 틈이 이보다 짧으면 한 구간으로 본다(격자 경계에서 한두 칸 빠지는 것 메우기). */
    const val MERGE_GAP_M = 100.0

    fun profile(fix: GeoFix?, route: List<GeoPoint>?, cells: Map<String, CellState>): AheadProfile? {
        if (fix == null) return null
        val estimated = route == null || route.size < 2
        val path = if (estimated) straight(fix) else route!!
        val samples = sample(path)
        val segments = AlertKind.entries.flatMap { kind -> segmentsOf(kind, samples, cells) }.sortedBy { it.fromM }
        return AheadProfile(path, samples.lastOrNull()?.first?.toInt() ?: 0, estimated, segments)
    }

    /** 진행 방향으로 [LENGTH_M] 직선. 수 km라서 평면 근사로 충분하다. */
    private fun straight(fix: GeoFix): List<GeoPoint> {
        val rad = Math.toRadians(fix.headingDeg.toDouble())
        val dLat = cos(rad) * LENGTH_M / 111_320.0
        val dLon = sin(rad) * LENGTH_M / (111_320.0 * cos(Math.toRadians(fix.point.lat)))
        return listOf(fix.point, GeoPoint(fix.point.lat + dLat, fix.point.lon + dLon))
    }

    /** 길을 따라 STEP_M마다 (출발점부터 거리, 격자 ID). 길이는 LENGTH_M까지. */
    private fun sample(path: List<GeoPoint>): List<Pair<Double, String>> {
        val out = mutableListOf<Pair<Double, String>>()
        var walked = 0.0
        var next = 0.0
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val seg = GeoMath.distanceM(a, b)
            while (next <= walked + seg && next <= LENGTH_M) {
                val t = if (seg == 0.0) 0.0 else (next - walked) / seg
                out += next to Geohash.encode(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                next += STEP_M
            }
            walked += seg
            if (next > LENGTH_M) break
        }
        return out
    }

    private fun segmentsOf(kind: AlertKind, samples: List<Pair<Double, String>>, cells: Map<String, CellState>): List<AheadSegment> {
        val out = mutableListOf<AheadSegment>()
        var from = -1.0; var to = -1.0
        val hitCells = linkedSetOf<String>()
        val levels = mutableListOf<RainLevel>()
        fun flush() {
            if (from >= 0) out += AheadSegment(from.toInt(), to.toInt(), kind, AheadFilter.strongest(levels), hitCells.toList())
            from = -1.0; hitCells.clear(); levels.clear()
        }
        for ((d, cell) in samples) {
            val state = cells[cell] ?: continue
            if (kind !in AheadFilter.kindsOf(state)) continue
            if (from >= 0 && d - to > MERGE_GAP_M) flush()
            if (from < 0) from = d
            to = d + STEP_M
            hitCells += cell
            if (kind == AlertKind.RAIN) levels += state.rainLevel
        }
        flush()
        return out
    }
}

/** 앞길 [lengthM] 중 같은 종류가 이어진 구간. 거리는 길을 따라 잰 값이다. */
data class AheadSegment(
    val fromM: Int,
    val toM: Int,
    val kind: AlertKind,
    val rainLevel: RainLevel,
    val cells: List<String>,
)

data class AheadProfile(
    val path: List<GeoPoint>,
    val lengthM: Int,
    /** true = 실제 길이 아니라 진행 방향 직선으로 대신했다 */
    val estimated: Boolean,
    val segments: List<AheadSegment>,
)
