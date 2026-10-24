package io.roadsensor.core.detect

import io.roadsensor.core.model.RainLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class RainClassifierTest {

    @Test
    fun `와이퍼가 꺼져 있으면 스위치 값과 상관없이 비 없음`() {
        assertEquals(RainLevel.NONE, RainClassifier.classify(false, "CONTINUOUS_LEVEL_3"))
        assertEquals(RainLevel.NONE, RainClassifier.classify(null, null))
    }

    @Test
    fun `에뮬레이터 매핑 - Low는 약, High는 중, Auto는 미상`() {
        assertEquals(RainLevel.LIGHT, RainClassifier.classify(true, "CONTINUOUS_LEVEL_1"))
        assertEquals(RainLevel.MODERATE, RainClassifier.classify(true, "CONTINUOUS_LEVEL_3"))
        assertEquals(RainLevel.UNKNOWN, RainClassifier.classify(true, "AUTO"))
    }

    @Test
    fun `이슬비와 간헐은 약, 연속 4~5는 강`() {
        assertEquals(RainLevel.LIGHT, RainClassifier.classify(true, "MIST"))
        assertEquals(RainLevel.LIGHT, RainClassifier.classify(true, "INTERMITTENT_LEVEL_5"))
        assertEquals(RainLevel.HEAVY, RainClassifier.classify(true, "CONTINUOUS_LEVEL_4"))
        assertEquals(RainLevel.HEAVY, RainClassifier.classify(true, "CONTINUOUS_LEVEL_5"))
    }

    @Test
    fun `켜져 있는데 스위치를 아직 못 읽었으면 미상`() {
        assertEquals(RainLevel.UNKNOWN, RainClassifier.classify(true, null))
        assertEquals(RainLevel.UNKNOWN, RainClassifier.classify(true, "SERVICE"))
    }
}
