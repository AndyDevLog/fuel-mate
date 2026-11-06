package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.LocationOrigin
import kotlin.math.min

/**
 * 에뮬레이터는 달려도 내비 위치가 시흥동에 고정이다(docs/pleos/open-questions.md). 그러면 달리는 동안 앞쪽 주유소를 다시 찾을 수 없어,
 * 내비 위치가 멈춰 있는데 차가 [STUCK_AFTER_M]만큼 달렸으면 차량 속도로 목적지 쪽 직선을 따라 위치를 움직인다(시연용, 도로를 따르지 않는다).
 *
 * 내비 위치가 한 번이라도 움직이면 실제 위치가 오는 환경(실차)으로 보고 다시는 흉내 내지 않는다.
 * 그래도 실차에서 처음부터 내비 위치가 멈춰 있으면(터널에서 출발 등) 잠시 직선 위치가 쓰일 수 있고, 화면에 "위치 데모"로 표시된다.
 */
class StuckNaviFallback {
    private var lastNavi: GeoPoint? = null
    private var lastMs: Long? = null
    private var stuckM = 0.0
    private var naviMoves = false
    private var simulated: GeoPoint? = null
    private var simulatedHeading = 0f

    /** 내비 위치가 올 때마다 부른다. 내비 위치를 그대로 쓰거나, 직선으로 움직인 위치를 돌려준다 */
    fun update(navi: GeoPoint, headingDeg: Float, speedMps: Float?, destination: GeoPoint?, nowMs: Long): GeoFix {
        val prevNavi = lastNavi
        if (prevNavi != null && GeoMath.distanceM(prevNavi, navi) > MOVED_M) naviMoves = true
        lastNavi = navi
        val dtMs = lastMs?.let { (nowMs - it).coerceIn(0, MAX_STEP_MS) } ?: 0L
        lastMs = nowMs

        if (naviMoves) {
            simulated = null
            return GeoFix(navi, headingDeg, nowMs, LocationOrigin.NAVI_HELPER)
        }

        val stepM = (speedMps ?: 0f) * dtMs / 1000.0
        val current = simulated
        if (current == null) {
            stuckM += stepM
            if (stuckM < STUCK_AFTER_M) return GeoFix(navi, headingDeg, nowMs, LocationOrigin.NAVI_HELPER)
        }
        // 처음 넘어갈 때는 멈춰 있는 동안 달린 거리를 한 번에 따라잡는다
        val moved = advance(current ?: navi, destination, if (current == null) stuckM else stepM)
        simulated = moved
        return GeoFix(moved, simulatedHeading, nowMs, LocationOrigin.NAVI_SIMULATED)
    }

    private fun advance(from: GeoPoint, destination: GeoPoint?, stepM: Double): GeoPoint {
        if (destination == null || stepM <= 0) return from
        val remain = GeoMath.distanceM(from, destination)
        if (remain < 1.0) return from
        val bearing = GeoMath.bearingDeg(from, destination)
        simulatedHeading = bearing.toFloat()
        return if (stepM >= remain) destination else GeoMath.destination(from, bearing, stepM)
    }

    companion object {
        /** 내비 위치가 멈춘 채 이만큼 달리면 위치가 안 움직이는 환경으로 본다 */
        const val STUCK_AFTER_M = 200.0
        /** 이만큼 넘게 바뀌면 내비 위치가 움직인 것이다(GPS 흔들림 몫을 뺀 값) */
        const val MOVED_M = 5.0
        /** 앱이 잠깐 멈췄다 돌아와도 한 번에 멀리 가지 않게 */
        const val MAX_STEP_MS = 10_000L
    }
}
