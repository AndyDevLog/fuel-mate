package io.fuelmate.core.detect

import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.Gear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DriveDetectorTest {

    /** 0.2초 간격(에뮬레이터 속도 표본 간격)으로 [speedsKmh]를 넣고 닫힌 사건을 모은다 */
    private fun run(
        speedsKmh: List<Float>,
        brake: (Int) -> Boolean = { false },
        gear: Gear = Gear.D,
        stepMs: Long = 200,
    ): Pair<List<DriveEvent>, DriveTracker> {
        var t = DriveTracker()
        val events = mutableListOf<DriveEvent>()
        speedsKmh.forEachIndexed { i, kmh ->
            val s = DriveDetector.onSpeed(t, i * stepMs, kmh / 3.6f, brake(i), gear)
            t = s.tracker
            events += s.events
        }
        return events to t
    }

    /** [from]에서 [to]까지 0.2초마다 [perStep]km/h씩 바꾸고, 끝 속도로 2초 유지 */
    private fun ramp(from: Float, to: Float, perStep: Float): List<Float> {
        val n = ((to - from) / perStep).toInt()
        return (0..n).map { from + it * perStep } + List(10) { to }
    }

    @Test
    fun `초당 18km 가속은 급가속 하나, 시작·끝 속도를 남긴다`() {
        // 0.2초마다 3.6km/h = 초당 18km/h(5m/s²): 에뮬레이터 ACCEL 정도
        val (events, _) = run(ramp(0f, 72f, 3.6f))
        val e = assertIs<DriveEvent.HardAccel>(events.single())
        assertTrue(e.startSpeedMps < 1f, "시작 ${e.startSpeedMps}")
        assertTrue(e.endSpeedMps * 3.6f in 60f..72f, "끝 ${e.endSpeedMps * 3.6f}")
        assertEquals(5f, e.peakMps2, 0.1f)
    }

    @Test
    fun `완만한 가속(초당 5km)은 세지 않는다`() {
        val (events, _) = run(ramp(0f, 60f, 1f))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `브레이크와 함께 초당 11km 넘게 감속하면 급제동`() {
        val (events, _) = run(ramp(60f, 0f, -3.6f), brake = { it >= 1 })
        val e = assertIs<DriveEvent.HardBrake>(events.single())
        assertTrue(e.startSpeedMps * 3.6f >= 56f)
        assertTrue(e.endSpeedMps * 3.6f <= 5f)
    }

    @Test
    fun `브레이크 없이 초당 9km 감속은 급감속`() {
        // 0.2초마다 1.8km/h = 초당 9km/h: 급감속(7.5) 이상, 급제동(11) 아래
        val (events, _) = run(ramp(80f, 30f, -1.8f))
        assertIs<DriveEvent.HardDecel>(events.single())
    }

    @Test
    fun `브레이크를 밟아도 급제동 기준 아래면 급감속`() {
        val (events, _) = run(ramp(80f, 30f, -1.8f), brake = { true })
        assertIs<DriveEvent.HardDecel>(events.single())
    }

    @Test
    fun `저속(20km 미만)의 강한 감속은 세지 않는다`() {
        val (events, _) = run(ramp(15f, 0f, -3f), brake = { true })
        assertTrue(events.isEmpty())
    }

    @Test
    fun `0점2초 튐은 1초 창에서 걸러진다`() {
        val (events, _) = run(listOf(60f, 60f, 60f, 60f, 60f, 60f, 54f, 60f, 60f, 60f, 60f, 60f, 60f))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `가속하다 바로 급제동하면 사건 두 개`() {
        val speeds = ramp(0f, 72f, 3.6f).dropLast(10) + ramp(72f, 0f, -3.6f)
        val (events, _) = run(speeds, brake = { it > 20 })
        assertEquals(2, events.size, "$events")
        assertIs<DriveEvent.HardAccel>(events[0])
        assertIs<DriveEvent.HardBrake>(events[1])
    }

    @Test
    fun `D단으로 2분 넘게 서 있다가 출발하면 긴 정차`() {
        // 1초 간격으로 150초 정지 후 출발
        val speeds = List(150) { 0f } + listOf(5f, 10f)
        val (events, _) = run(speeds, stepMs = 1_000)
        val e = assertIs<DriveEvent.LongIdle>(events.single())
        assertEquals(150_000L, e.durationMs)
    }

    @Test
    fun `P단으로 서 있으면 정차를 세지 않고, 서 있다가 P로 바꾸면 정차가 끝난다`() {
        val (parked, pt) = run(List(200) { 0f }, gear = Gear.P, stepMs = 1_000)
        assertTrue(parked.isEmpty())
        assertEquals(null, pt.idleSinceMs)

        val (_, t) = run(List(130) { 0f }, stepMs = 1_000)
        val step = DriveDetector.onGear(t, 130_000, Gear.P, 0f)
        assertEquals(130_000L, step.idleEndedMs)
        assertIs<DriveEvent.LongIdle>(step.events.single())
    }

    @Test
    fun `표본이 3초 넘게 끊기면 그 사이를 가속으로 보지 않는다`() {
        var t = DriveTracker()
        t = DriveDetector.onSpeed(t, 0, 0f, false, Gear.D).tracker
        val s = DriveDetector.onSpeed(t, 10_000, 30f, false, Gear.D)
        assertTrue(s.events.isEmpty())
        assertEquals(1, s.tracker.window.size)
    }
}
