package io.roadsensor.core.present

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.model.RoadAlert

/**
 * 주행 모드 한 줄 문구. 주행 중에는 이 한 줄만 보여준다(docs/architecture.md 7장).
 * 거리는 직선거리라서 "약"을 붙인다. LIMIT_STRING_LENGTH 규제가 켜지면 짧은 문구를 쓴다.
 */
object DriveLine {

    fun of(state: AppState): String {
        val alert = state.alerts.firstOrNull() ?: return if (state.drive.limitStringLength) "이상 없음" else "앞쪽 도로 이상 없음"
        return if (state.drive.limitStringLength) short(alert) else full(alert)
    }

    private fun full(a: RoadAlert) = "약 ${distance(a.distanceM)} 앞 ${label(a)}"

    private fun short(a: RoadAlert) = "${distance(a.distanceM)} ${shortLabel(a)}"

    // 1km 미만은 100m 단위, 10km 미만은 0.1km 단위, 그 이상은 1km 단위
    internal fun distance(m: Int): String = when {
        m < 1_000 -> "${(m / 100).coerceAtLeast(1) * 100}m"
        m < 10_000 -> {
            val tenths = (m + 50) / 100
            if (tenths % 10 == 0) "${tenths / 10}km" else "${tenths / 10}.${tenths % 10}km"
        }
        else -> "${(m + 500) / 1_000}km"
    }

    internal fun label(a: RoadAlert) = when (a.kind) {
        AlertKind.RAIN -> "비" + rainSuffix(a.rainLevel)
        AlertKind.DARK -> "어두운 구간"
        AlertKind.HARD_BRAKE -> "급정거 잦음"
    }

    private fun shortLabel(a: RoadAlert) = when (a.kind) {
        AlertKind.RAIN -> "비"
        AlertKind.DARK -> "어두움"
        AlertKind.HARD_BRAKE -> "급정거"
    }

    private fun rainSuffix(level: RainLevel) = when (level) {
        RainLevel.LIGHT -> " (약)"
        RainLevel.MODERATE -> " (중)"
        RainLevel.HEAVY -> " (강)"
        else -> ""
    }
}
