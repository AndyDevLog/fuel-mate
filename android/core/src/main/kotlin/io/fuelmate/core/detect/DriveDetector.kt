package io.fuelmate.core.detect

import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.Gear

data class SpeedPoint(val timeMs: Long, val speedMps: Float)

/** 급가속·급감속이 이어지는 구간. 기준을 넘는 동안 이어지고, 기준 아래로 내려가면 사건 하나로 닫힌다 */
data class Episode(
    val accel: Boolean,
    val startMs: Long,
    val startSpeedMps: Float,
    val peakMps2: Float,
    val braked: Boolean,
)

/** 판정기 내부 상태. 순수 값이라 AppState 안에 두고 리듀서에서 돌린다. 화면은 쓰지 않는다 */
data class DriveTracker(
    /** 최근 약 1초의 속도 표본. 맨 앞이 가속도 계산의 기준점이다 */
    val window: List<SpeedPoint> = emptyList(),
    val episode: Episode? = null,
    /** 기어가 P가 아닌 채 서기 시작한 시각. 움직이거나 P면 null */
    val idleSinceMs: Long? = null,
)

/**
 * 급가속·급감속·급제동·긴 정차 판정. 가속 페달·RPM이 없어서 속도 변화로만 본다(docs/pivot-fuel.md 3장).
 * 가속도는 1초 창(지금 속도 − 1초 전 속도)으로 계산한다. 기준이 "초당 몇 km/h"라서 그대로 맞고, 0.2초 표본의 튐이 줄어든다.
 */
object DriveDetector {
    const val WINDOW_MS = 1_000L
    /** 창이 이보다 짧으면(시작 직후) 가속도를 계산하지 않는다 */
    const val MIN_WINDOW_MS = 500L
    /** 표본 사이가 이보다 길면 끊긴 것으로 보고 새로 시작한다 */
    const val MAX_GAP_MS = 3_000L

    const val HARD_ACCEL_MPS2 = 11f / 3.6f
    const val HARD_DECEL_MPS2 = 7.5f / 3.6f
    const val HARD_BRAKE_MPS2 = 11f / 3.6f
    /** 시작·끝 속도가 모두 이보다 느리면 세지 않는다(주차장·정체 속 출렁임) */
    const val MIN_EVENT_SPEED_MPS = 20f / 3.6f
    const val MOVING_MPS = 0.5f
    /** 이만큼 넘게 서 있으면 긴 정차. 신호 한 번(1~2분)은 넘기고, 조정할 수 있게 상수로 둔다 */
    const val LONG_IDLE_MS = 120_000L

    /**
     * @property events 이번 표본으로 닫힌 사건
     * @property idleEndedMs 이번에 끝난 정차 시간(정차가 끝나지 않았으면 null)
     */
    data class Step(val tracker: DriveTracker, val events: List<DriveEvent>, val idleEndedMs: Long?)

    fun onSpeed(t: DriveTracker, timeMs: Long, speedMps: Float, brakePressed: Boolean, gear: Gear): Step {
        val events = mutableListOf<DriveEvent>()
        val last = t.window.lastOrNull()
        val gap = last != null && timeMs - last.timeMs > MAX_GAP_MS
        var episode = t.episode
        if (gap && episode != null) {
            close(episode, last!!.speedMps)?.let(events::add)
            episode = null
        }

        val window = ((if (gap) emptyList() else t.window) + SpeedPoint(timeMs, speedMps))
            .dropWhileSecondIsOld(timeMs)
        val ref = window.first()
        val dt = timeMs - ref.timeMs
        val a = if (dt >= MIN_WINDOW_MS) (speedMps - ref.speedMps) / (dt / 1000f) else null

        episode = when {
            a != null && a >= HARD_ACCEL_MPS2 ->
                if (episode?.accel == true) episode.copy(peakMps2 = maxOf(episode.peakMps2, a))
                else {
                    episode?.let { close(it, speedMps) }?.let(events::add)
                    Episode(accel = true, ref.timeMs, ref.speedMps, a, brakePressed)
                }
            a != null && -a >= HARD_DECEL_MPS2 ->
                if (episode?.accel == false) episode.copy(peakMps2 = maxOf(episode.peakMps2, -a), braked = episode.braked || brakePressed)
                else {
                    episode?.let { close(it, speedMps) }?.let(events::add)
                    Episode(accel = false, ref.timeMs, ref.speedMps, -a, brakePressed)
                }
            else -> {
                episode?.let { close(it, speedMps) }?.let(events::add)
                null
            }
        }

        val idle = idleStep(t.idleSinceMs, timeMs, stoppedPowered = speedMps <= MOVING_MPS && powered(gear))
        idle.event?.let(events::add)
        return Step(DriveTracker(window, episode, idle.sinceMs), events, idle.endedMs)
    }

    /**
     * 기어가 바뀌면 정차 시작·끝이 바뀔 수 있다(서 있다가 P로 → 정차 끝, 서 있는 채 D로 → 정차 시작).
     * P로 바뀌면 운행이 끝나므로 진행 중인 급가속·급감속 구간도 닫는다(멈춘 뒤 속도 표본이 더 오지 않을 수 있다).
     */
    fun onGear(t: DriveTracker, timeMs: Long, gear: Gear, speedMps: Float?): Step {
        val speed = speedMps ?: 0f
        val idle = idleStep(t.idleSinceMs, timeMs, stoppedPowered = speed <= MOVING_MPS && powered(gear))
        val closing = t.episode?.takeIf { gear == Gear.P }
        val events = listOfNotNull(closing?.let { close(it, speed) }, idle.event)
        val episode = if (closing != null) null else t.episode
        return Step(t.copy(episode = episode, idleSinceMs = idle.sinceMs), events, idle.endedMs)
    }

    private fun powered(gear: Gear) = gear == Gear.D || gear == Gear.N || gear == Gear.R

    private class Idle(val sinceMs: Long?, val endedMs: Long?, val event: DriveEvent?)

    private fun idleStep(since: Long?, timeMs: Long, stoppedPowered: Boolean): Idle = when {
        stoppedPowered -> Idle(since ?: timeMs, null, null)
        since == null -> Idle(null, null, null)
        else -> {
            val duration = timeMs - since
            Idle(null, duration, if (duration >= LONG_IDLE_MS) DriveEvent.LongIdle(since, duration) else null)
        }
    }

    private fun close(e: Episode, endSpeedMps: Float): DriveEvent? {
        if (maxOf(e.startSpeedMps, endSpeedMps) < MIN_EVENT_SPEED_MPS) return null
        return when {
            e.accel -> DriveEvent.HardAccel(e.startMs, e.startSpeedMps, endSpeedMps, e.peakMps2)
            e.braked && e.peakMps2 >= HARD_BRAKE_MPS2 -> DriveEvent.HardBrake(e.startMs, e.startSpeedMps, endSpeedMps, e.peakMps2)
            else -> DriveEvent.HardDecel(e.startMs, e.startSpeedMps, endSpeedMps, e.peakMps2)
        }
    }

    /** 두 번째 표본도 1초 넘게 지났으면 맨 앞을 버린다 → 맨 앞 = 1초 전에 가장 가까운 표본 */
    private fun List<SpeedPoint>.dropWhileSecondIsOld(now: Long): List<SpeedPoint> {
        var from = 0
        while (size - from >= 2 && now - this[from + 1].timeMs >= WINDOW_MS) from++
        return if (from == 0) this else subList(from, size).toList()
    }
}
