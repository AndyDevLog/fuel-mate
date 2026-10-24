package io.roadsensor.core.detect

/**
 * 급정거 판정. 브레이크 신호는 페달 on/off뿐이라 속도 감속률을 같이 본다(docs/architecture.md 3-3).
 * 순수 함수 + 불변 상태라서 AppState 안에 두고 리듀서에서 돌린다.
 */
data class BrakeTracker(
    val lastTimeMs: Long? = null,
    val lastSpeedMps: Float? = null,
    /** 이번 페달 입력이 시작될 때의 속도. 페달을 안 밟고 있으면 null. */
    val pressStartSpeedMps: Float? = null,
    /** 감속도가 기준을 넘기 시작한 시각. 기준 아래면 null. */
    val overThresholdSinceMs: Long? = null,
    /** 한 번 밟는 동안 한 번만 센다. */
    val firedThisPress: Boolean = false,
)

object HardBrakeRule {
    // 초기값. 시뮬레이터·에뮬레이터로 조정한다.
    const val MIN_START_SPEED_MPS = 20f / 3.6f
    const val MIN_DECEL_MPS2 = 3.5f
    const val SUSTAIN_MS = 500L

    data class Result(val tracker: BrakeTracker, val fired: Boolean)

    /** 속도 샘플이 들어올 때마다 부른다. [brakePressed]는 그 시점의 페달 상태다. */
    fun onSpeed(t: BrakeTracker, timeMs: Long, speedMps: Float, brakePressed: Boolean): Result {
        if (!brakePressed) {
            return Result(BrakeTracker(lastTimeMs = timeMs, lastSpeedMps = speedMps), fired = false)
        }

        val startSpeed = t.pressStartSpeedMps ?: t.lastSpeedMps ?: speedMps
        val dtMs = if (t.lastTimeMs != null) timeMs - t.lastTimeMs else 0L
        val decel = if (t.lastSpeedMps != null && dtMs > 0) (t.lastSpeedMps - speedMps) / (dtMs / 1000f) else 0f

        val overSince = when {
            decel >= MIN_DECEL_MPS2 -> t.overThresholdSinceMs ?: (t.lastTimeMs ?: timeMs)
            else -> null
        }
        val fire = !t.firedThisPress &&
            startSpeed >= MIN_START_SPEED_MPS &&
            overSince != null &&
            timeMs - overSince >= SUSTAIN_MS

        return Result(
            BrakeTracker(
                lastTimeMs = timeMs,
                lastSpeedMps = speedMps,
                pressStartSpeedMps = startSpeed,
                overThresholdSinceMs = overSince,
                firedThisPress = t.firedThisPress || fire,
            ),
            fired = fire,
        )
    }

    /** 페달을 떼면 다음 입력을 새로 센다. 마지막 속도 샘플은 남긴다. */
    fun onBrakeReleased(t: BrakeTracker): BrakeTracker =
        BrakeTracker(lastTimeMs = t.lastTimeMs, lastSpeedMps = t.lastSpeedMps)
}
