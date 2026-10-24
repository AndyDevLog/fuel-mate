package io.roadsensor.core.detect

import io.roadsensor.core.model.RainLevel

/**
 * 와이퍼 상태·스위치 → 비의 세기.
 * 스위치 이름은 Pleos `WiperSwitch` enum 이름이다: OFF, MIST, INTERMITTENT_LEVEL_1~5, CONTINUOUS_LEVEL_1~5, AUTO, SERVICE, OTHER.
 * 에뮬레이터 매핑: Low = CONTINUOUS_LEVEL_1, High = CONTINUOUS_LEVEL_3, Auto = AUTO (docs/pleos/open-questions.md).
 */
object RainClassifier {

    fun classify(wiperOn: Boolean?, switch: String?): RainLevel {
        if (wiperOn != true) return RainLevel.NONE
        return when {
            switch == null -> RainLevel.UNKNOWN
            switch == "MIST" || switch.startsWith("INTERMITTENT_LEVEL_") -> RainLevel.LIGHT
            switch.startsWith("CONTINUOUS_LEVEL_") -> when (switch.removePrefix("CONTINUOUS_LEVEL_").toIntOrNull()) {
                1 -> RainLevel.LIGHT
                2, 3 -> RainLevel.MODERATE
                4, 5 -> RainLevel.HEAVY
                else -> RainLevel.UNKNOWN
            }
            // AUTO(레인센서)는 세기를 알 수 없다. SERVICE·OTHER도 마찬가지다.
            else -> RainLevel.UNKNOWN
        }
    }
}
