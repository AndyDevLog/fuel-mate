package io.fuelmate.core.model

/**
 * 운행 하나. 기어가 P에서 벗어나면 시작하고 P로 돌아오면 끝난다(시동 상태는 서드파티에 없다, docs/pivot-fuel.md 3장).
 * 위치는 담지 않는다. 시각은 [startMs]·[endMs]가 속도 표본과 같은 시계(elapsedRealtime)이고, 날짜 비교용으로 [startWallMs]를 둔다.
 *
 * @property distanceM 속도를 시간으로 적분한 거리. 위치 출처(데모 궤적·내비)와 상관없이 같은 기준이다
 * @property idleMs 기어가 P가 아닌 채 서 있던 시간(신호 대기 포함)
 * @property energyUsed 이번 운행에서 쓴 에너지(연료 L, 전기 kWh). 회생으로 되찾은 양은 뺀 값
 * @property highSpeedExcess 시속 100km([HIGH_SPEED_REF_MPS])를 넘는 동안 Σ(v² − v₀²)·Δs(m³/s²).
 *   × ½·ρ·CdA가 기준 속도보다 더 든 공기저항 에너지다. 차 제원과 무관한 값이라 운행에 쌓아 둔다
 */
data class Trip(
    val startMs: Long,
    val startWallMs: Long,
    val endMs: Long = startMs,
    val distanceM: Double = 0.0,
    val movingMs: Long = 0,
    val idleMs: Long = 0,
    val maxSpeedMps: Float = 0f,
    val energyKind: EnergyKind? = null,
    val energyUsed: Double = 0.0,
    val energyAdded: Double = 0.0,
    val highSpeedExcess: Double = 0.0,
    val events: List<DriveEvent> = emptyList(),
) {
    val durationMs: Long get() = endMs - startMs

    inline fun <reified T : DriveEvent> count(): Int = events.count { it is T }

    companion object {
        const val HIGH_SPEED_REF_MPS = 100f / 3.6f
    }
}

/**
 * 운전 사건. 운행 요약과 운전 습관 비용 계산에 쓴다.
 * 판정 기준은 docs/pivot-fuel.md 13-3(국내 운행기록 분석 논문의 위험운전 정의)을 따른다. 시작·끝 속도를 남겨서
 * 다음 단계에서 운동 에너지 손실을 계산할 수 있게 한다.
 */
sealed interface DriveEvent {
    /** 사건이 시작된 시각(elapsedRealtime) */
    val timeMs: Long

    /** 초당 11km/h 이상 가속 */
    data class HardAccel(
        override val timeMs: Long,
        val startSpeedMps: Float,
        val endSpeedMps: Float,
        val peakMps2: Float,
    ) : DriveEvent

    /** 초당 7.5km/h 이상 감속(브레이크 없이, 또는 브레이크를 밟았지만 급제동 기준 아래) */
    data class HardDecel(
        override val timeMs: Long,
        val startSpeedMps: Float,
        val endSpeedMps: Float,
        val peakMps2: Float,
    ) : DriveEvent

    /** 브레이크를 밟은 채 초당 11km/h 이상 감속 */
    data class HardBrake(
        override val timeMs: Long,
        val startSpeedMps: Float,
        val endSpeedMps: Float,
        val peakMps2: Float,
    ) : DriveEvent

    /** 기어가 P가 아닌 채 2분 이상 서 있음(내연기관은 공회전). ISG·하이브리드는 엔진이 꺼져 있을 수 있다 */
    data class LongIdle(
        override val timeMs: Long,
        val durationMs: Long,
    ) : DriveEvent
}
