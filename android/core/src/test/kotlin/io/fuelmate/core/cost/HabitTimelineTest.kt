package io.fuelmate.core.cost

import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HabitTimelineTest {

    private val kmh = 1 / 3.6f
    private val start = 10_000L

    /** 100초 운행: 20초에 급가속, 30초에 급제동, 50~70초 긴 정차, 90초에 급감속, 고속 구간도 있음 */
    private val trip = Trip(
        startMs = start,
        startWallMs = 0,
        endMs = start + 100_000,
        distanceM = 1_000.0,
        energyKind = EnergyKind.FUEL,
        highSpeedExcess = 5.0e6,
        events = listOf(
            DriveEvent.HardBrake(start + 30_000, 60 * kmh, 0f, 5f),
            DriveEvent.HardAccel(start + 20_000, 0f, 40 * kmh, 4f),
            DriveEvent.LongIdle(start + 50_000, 20_000),
            DriveEvent.HardDecel(start + 90_000, 50 * kmh, 20 * kmh, 3f),
        ),
    )
    private val cost = assertNotNull(HabitCostEstimator.estimate(trip))
    private val t = HabitTimeline.of(trip, cost)

    @Test
    fun `사건을 운행 안 위치 순서로 늘어놓고 합계는 습관 비용과 같다`() {
        assertEquals(listOf(TimelineMarkKind.ACCEL, TimelineMarkKind.BRAKING, TimelineMarkKind.IDLE, TimelineMarkKind.BRAKING), t.marks.map { it.kind })
        assertEquals(listOf(0.2f, 0.3f, 0.5f, 0.9f), t.marks.map { it.at })
        assertEquals(0.7f, t.marks[2].end, 1e-6f)
        assertEquals(0.0, t.marks[0].won)
        assertEquals(cost.won, t.totalWon, 1e-9)
        assertEquals(cost.item(HabitCategory.HIGH_SPEED).won, t.spreadWon, 1e-9)
    }

    @Test
    fun `금액은 지나는 순간 더하고, 긴 정차는 구간 동안, 고속은 고르게 더한다`() {
        val brake = t.marks[1].won
        val idle = t.marks[2].won
        assertEquals(0.0, t.wonAt(0f), 1e-9)
        assertEquals(t.spreadWon * 0.29, t.wonAt(0.29f), 1e-4)
        assertEquals(t.spreadWon * 0.3 + brake, t.wonAt(0.3f), 1e-4)
        assertEquals(t.spreadWon * 0.6 + brake + idle / 2, t.wonAt(0.6f), 1e-4)
        assertEquals(t.totalWon, t.wonAt(1f), 1e-9)
        // 처음부터 끝까지 줄지 않는다
        val values = (0..100).map { t.wonAt(it / 100f) }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `길이가 0인 운행은 모두 출발 위치에 둔다`() {
        val zero = trip.copy(endMs = start, events = listOf(DriveEvent.HardBrake(start, 30 * kmh, 0f, 5f)))
        val z = HabitTimeline.of(zero, assertNotNull(HabitCostEstimator.estimate(zero)))
        assertEquals(0f, z.marks.single().at)
    }
}
