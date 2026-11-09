package io.fuelmate.core.cost

import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.Trip

enum class TimelineMarkKind {
    /** 급제동·급감속. 원화로 계산한다 */
    BRAKING,
    /** 급가속. 원화 근거가 약해 횟수만 보인다([TimelineMark.won] = 0) */
    ACCEL,
    /** 긴 정차. [TimelineMark.at]부터 [TimelineMark.end]까지 구간이다 */
    IDLE,
}

/**
 * 운행 타임라인 위 표시 하나.
 * @property at 운행 안 위치(0 = 출발, 1 = 도착)
 * @property end 긴 정차는 끝 위치, 나머지는 [at]과 같다
 * @property won 이 사건의 습관 비용(원). 급가속은 0
 */
data class TimelineMark(val kind: TimelineMarkKind, val at: Float, val end: Float, val won: Double)

/**
 * "지난 운행" 카드의 타임라인. 운행 중 언제 습관 비용이 생겼는지 사건 시각으로 늘어놓는다.
 * 위치는 운행 기록에 없어서 저장하지 않는다(시간 순서만).
 *
 * @property spreadWon 언제 생겼는지 모르는 비용(시속 100km 넘는 고속. 운행이 합계만 쌓아 둔다). 운행 전체에 고르게 나눈다
 */
data class HabitTimeline(val marks: List<TimelineMark>, val spreadWon: Double) {

    /** [HabitCost.won]과 같다 */
    val totalWon: Double get() = spreadWon + marks.sumOf { it.won }

    /**
     * 처음부터 [progress](0~1)까지 쌓인 비용. 급제동은 지나는 순간 더하고, 긴 정차는 구간 동안, 고속은 운행 전체에 고르게 더한다.
     * 화면에서 차 아이콘이 지나가며 금액이 올라가는 연출에 쓴다. 1이면 [totalWon]
     */
    fun wonAt(progress: Float): Double {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return totalWon
        return spreadWon * p + marks.sumOf { m ->
            when {
                m.won == 0.0 || p < m.at -> 0.0
                m.end > m.at && p < m.end -> m.won * (p - m.at) / (m.end - m.at)
                else -> m.won
            }
        }
    }

    companion object {
        fun of(trip: Trip, cost: HabitCost): HabitTimeline {
            val price = cost.prices.of(cost.kind)
            val p = cost.params
            val duration = trip.durationMs.toFloat()
            fun at(timeMs: Long) = if (duration <= 0f) 0f else ((timeMs - trip.startMs) / duration).coerceIn(0f, 1f)

            val marks = trip.events.map { e ->
                val start = at(e.timeMs)
                when (e) {
                    is DriveEvent.HardBrake ->
                        TimelineMark(TimelineMarkKind.BRAKING, start, start, HabitCostEstimator.brakingWaste(p, e.startSpeedMps, e.endSpeedMps, e.peakMps2) * price)
                    is DriveEvent.HardDecel ->
                        TimelineMark(TimelineMarkKind.BRAKING, start, start, HabitCostEstimator.brakingWaste(p, e.startSpeedMps, e.endSpeedMps, e.peakMps2) * price)
                    is DriveEvent.HardAccel -> TimelineMark(TimelineMarkKind.ACCEL, start, start, 0.0)
                    is DriveEvent.LongIdle ->
                        TimelineMark(TimelineMarkKind.IDLE, start, at(e.timeMs + e.durationMs), HabitCostEstimator.idleWaste(p, e.durationMs) * price)
                }
            }.sortedBy { it.at }
            return HabitTimeline(marks, cost.item(HabitCategory.HIGH_SPEED).won)
        }
    }
}
