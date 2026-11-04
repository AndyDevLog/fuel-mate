package io.fuelmate.core.detect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HardBrakeRuleTest {

    /** 0.2초 간격(에뮬레이터 속도 샘플 간격)으로 [speedsKmh]를 넣고, 발동 횟수를 센다. */
    private fun run(speedsKmh: List<Float>, brakeFrom: Int = 1, brakeUntil: Int = Int.MAX_VALUE): Int {
        var t = BrakeTracker()
        var fired = 0
        speedsKmh.forEachIndexed { i, kmh ->
            val pressed = i in brakeFrom..brakeUntil
            val r = HardBrakeRule.onSpeed(t, i * 200L, kmh / 3.6f, pressed)
            t = r.tracker
            if (r.fired) fired++
        }
        return fired
    }

    @Test
    fun `60kmh에서 초당 약 5mps 감속을 1초 유지하면 급정거`() {
        // 0.2초마다 약 3.6km/h(1m/s)씩 감소 = 5m/s²
        val speeds = (0..10).map { 60f - it * 3.6f }
        assertEquals(1, run(speeds))
    }

    @Test
    fun `완만한 감속은 급정거가 아니다`() {
        // 0.2초마다 1km/h 감소 ≈ 1.4m/s²
        val speeds = (0..20).map { 60f - it * 1f }
        assertEquals(0, run(speeds))
    }

    @Test
    fun `저속(20kmh 미만)에서 시작한 강한 감속은 세지 않는다`() {
        val speeds = (0..5).map { 15f - it * 3f }
        assertEquals(0, run(speeds))
    }

    @Test
    fun `브레이크를 안 밟으면 감속이 커도 세지 않는다`() {
        val speeds = (0..10).map { 60f - it * 3.6f }
        assertEquals(0, run(speeds, brakeFrom = Int.MAX_VALUE))
    }

    @Test
    fun `한 번 밟는 동안에는 한 번만 센다`() {
        val speeds = (0..15).map { (80f - it * 4f).coerceAtLeast(0f) }
        assertEquals(1, run(speeds))
    }

    @Test
    fun `순간적인 튐(0점2초)은 유지 시간 기준에 걸러진다`() {
        val speeds = listOf(60f, 60f, 54f, 54f, 54f, 54f)
        assertEquals(0, run(speeds))
    }

    @Test
    fun `페달을 떼면 다음 입력을 새로 센다`() {
        val t = HardBrakeRule.onBrakeReleased(BrakeTracker(lastTimeMs = 1, lastSpeedMps = 10f, firedThisPress = true))
        assertFalse(t.firedThisPress)
        assertEquals(10f, t.lastSpeedMps)
    }
}
