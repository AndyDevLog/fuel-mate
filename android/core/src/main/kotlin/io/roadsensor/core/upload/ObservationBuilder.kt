package io.roadsensor.core.upload

import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.RoadEvent
import io.roadsensor.core.protocol.Observation
import io.roadsensor.core.protocol.Protocol

/**
 * 상태 변화 → 서버로 보낼 관측값 (docs/architecture.md 3-3, 3-4).
 * - 격자가 바뀌면: 새 격자의 비·어두움 상태를 보낸다. 꺼져 있어도 보낸다(서버가 "비 없음"과 "데이터 없음"을 구분하려면 필요하다).
 * - 같은 격자에서 비·어두움이 바뀌면: 바뀐 것만 보낸다.
 * - 같은 격자에 오래 있으면: [REFRESH_MS]마다 다시 보낸다(서버 창 15분이 지나 사라지지 않게).
 * - 급정거가 새로 생기면: 그 위치의 격자와 진행 방향을 보낸다.
 * 위치를 모르면 아무것도 보내지 않는다.
 */
class ObservationBuilder {

    private var lastCell: String? = null
    private var lastRain: Int? = null
    private var lastDark: Boolean? = null
    private var lastSentMs = 0L
    private var lastBrakeTimeMs = Long.MIN_VALUE
    private var lowBeamOnSinceMs: Long? = null

    fun onState(s: AppState, nowMs: Long): List<Observation> {
        val fix = s.location ?: return emptyList()
        val cell = Geohash.encode(fix.point)
        val t = nowMs / 60_000
        val rain = s.rainLevel.code
        val dark = isDark(s.vehicle.lowBeamOn, nowMs)
        val out = mutableListOf<Observation>()

        val moved = cell != lastCell
        val stale = nowMs - lastSentMs >= REFRESH_MS
        if (moved || stale || rain != lastRain) out += Observation.Rain(cell, t, rain)
        if (moved || stale || dark != lastDark) out += Observation.Dark(cell, t, dark)

        // 새로 생긴 급정거(새 것이 앞에 있다)
        val newBrakes = s.events.filterIsInstance<RoadEvent.HardBrake>().filter { it.timeMs > lastBrakeTimeMs }
        for (e in newBrakes.asReversed()) {
            val at = e.point?.let { Geohash.encode(it) } ?: cell
            out += Observation.Brake(at, t, Protocol.direction(e.headingDeg ?: fix.headingDeg))
        }
        newBrakes.maxOfOrNull { it.timeMs }?.let { lastBrakeTimeMs = it }

        if (out.any { it !is Observation.Brake }) lastSentMs = nowMs
        lastCell = cell
        lastRain = rain
        lastDark = dark
        return out
    }

    /** 터널 같은 짧은 점등을 거르려고 하향등이 [DARK_HOLD_MS] 이상 켜져 있어야 어두움으로 본다. */
    private fun isDark(lowBeamOn: Boolean?, nowMs: Long): Boolean {
        if (lowBeamOn != true) {
            lowBeamOnSinceMs = null
            return false
        }
        val since = lowBeamOnSinceMs ?: nowMs.also { lowBeamOnSinceMs = it }
        return nowMs - since >= DARK_HOLD_MS
    }

    companion object {
        const val REFRESH_MS = 5 * 60 * 1000L
        const val DARK_HOLD_MS = 30_000L
    }
}
