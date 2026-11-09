package io.fuelmate.core.cost

import io.fuelmate.core.energy.DemoEnergy
import io.fuelmate.core.energy.VehicleParams
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.Trip
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitCostTest {

    private val kmh = 1 / 3.6f
    private val day = 86_400_000L

    private fun brake(from: Float, to: Float = 0f, peak: Float = 5f) =
        DriveEvent.HardBrake(0, from * kmh, to * kmh, peak)

    private fun trip(kind: EnergyKind, vararg events: DriveEvent, km: Double = 10.0, wall: Long = 0, excess: Double = 0.0) =
        Trip(startMs = 0, startWallMs = wall, distanceM = km * 1000, energyKind = kind, events = events.toList(), highSpeedExcess = excess)

    @Test
    fun `내연기관 60km에서 급제동 한 번은 약 0점027L, 약 46원`() {
        // 문서 어림 계산(1,600kg 가정): 약 0.028L, 약 47원. 여기서는 1,750kg에서 저항 몫을 뺀다
        val l = HabitCostEstimator.brakingWaste(VehicleParams.DEMO_FUEL, 60 * kmh, 0f, 5f)
        assertTrue(l in 0.026..0.032, "$l L")
        val cost = assertNotNull(HabitCostEstimator.estimate(trip(EnergyKind.FUEL, brake(60f))))
        assertTrue(cost.won in 44.0..54.0, "${cost.won}원")
        assertEquals(1, cost.item(HabitCategory.BRAKING).count)
    }

    @Test
    fun `전기차는 회생으로 되찾는 만큼 급제동 비용이 훨씬 작다`() {
        val ev = HabitCostEstimator.brakingWaste(VehicleParams.DEMO_EV, 60 * kmh, 0f, 5f)
        // 운동 에너지 약 0.081kWh × (1 − 0.6) ÷ 0.88 ≈ 0.036kWh
        assertTrue(ev in 0.03..0.04, "$ev kWh")
        val won = HabitCostEstimator.estimate(trip(EnergyKind.ELECTRIC, brake(60f)))!!.won
        assertTrue(won in 10.0..14.0, "${won}원")
    }

    @Test
    fun `급감속도 같은 식으로 세고, 급가속은 횟수만 센다`() {
        val decel = DriveEvent.HardDecel(0, 80 * kmh, 40 * kmh, 2.5f)
        val accel = DriveEvent.HardAccel(0, 0f, 100 * kmh, 4f)
        val cost = HabitCostEstimator.estimate(trip(EnergyKind.FUEL, decel, accel))!!
        assertEquals(1, cost.item(HabitCategory.BRAKING).count)
        assertTrue(cost.item(HabitCategory.BRAKING).won > 0)
        assertEquals(1, cost.hardAccelCount)
        assertEquals(cost.item(HabitCategory.BRAKING).won, cost.won)
    }

    @Test
    fun `긴 정차는 습관 비용에 넣지 않는다 - 신호·정체라 고칠 수 있는 습관이 아니다`() {
        val idle = DriveEvent.LongIdle(0, 180_000)
        val cost = assertNotNull(HabitCostEstimator.estimate(trip(EnergyKind.FUEL, idle)))
        assertEquals(0.0, cost.won)
        assertEquals(listOf(HabitCategory.BRAKING, HabitCategory.HIGH_SPEED), cost.items.map { it.category })
    }

    @Test
    fun `시속 120km로 10km 달리면 100km 기준보다 공기저항 약 0점18L 더`() {
        val v = 120 * kmh.toDouble()
        val ref = Trip.HIGH_SPEED_REF_MPS.toDouble()
        val excess = (v * v - ref * ref) * 10_000
        val l = HabitCostEstimator.estimate(trip(EnergyKind.FUEL, excess = excess))!!.item(HabitCategory.HIGH_SPEED).amount
        assertTrue(l in 0.16..0.20, "$l L")
    }

    @Test
    fun `운행은 시속 100km를 넘는 구간만 쌓는다`() {
        fun drive(kmhSpeed: Float): Trip {
            val samples = (0..50).map { Action.SpeedSample(it * 200L, kmhSpeed * kmh) }
            val s = (listOf(Action.GearChanged(Gear.D, 0, 0)) + samples).fold(AppState(), ::reduce)
            return s.trip!!
        }
        assertEquals(0.0, drive(90f).highSpeedExcess)
        val fast = drive(120f)
        // 10초 × 33.3m/s ≈ 333m × (33.3² − 27.8²)
        assertEquals((33.333 * 33.333 - 27.778 * 27.778) * 333.3, fast.highSpeedExcess, 2_000.0)
    }

    @Test
    fun `데모 소비 - 에뮬레이터처럼 급가속·급제동해도 운행 소비가 급제동 비용보다 크다`() {
        // 에뮬레이터 주행(2026-11-04): 0 → 100km/h 약 5초(5.6m/s²), 100 → 0 약 2초(13.6m/s²). 데모는 1초마다 계산
        val speeds = (0..5).map { it * 20f } + listOf(100f, 50f, 0f, 0f)
        for (p in listOf(VehicleParams.DEMO_EV, VehicleParams.DEMO_FUEL)) {
            var demo = DemoEnergy.start(p, 50.0)
            val start = demo.amount
            speeds.forEachIndexed { i, v -> demo = demo.tick(i * 1_000L, v * kmh, poweredOn = true) }
            val used = start - demo.amount
            val waste = HabitCostEstimator.brakingWaste(p, 100 * kmh, 0f, 13.6f)
            assertTrue(used > waste, "${p.kind}: 소비 $used < 급제동 $waste")
        }
    }

    @Test
    fun `에너지 종류를 모르는 운행은 계산하지 않는다`() {
        assertNull(HabitCostEstimator.estimate(Trip(startMs = 0, startWallMs = 0)))
    }

    @Test
    fun `평소 대비 - 2주 넘게 쌓이기 전에는 비교하지 않는다`() {
        val past = (1..10).map { trip(EnergyKind.FUEL, brake(60f), km = 10.0, wall = it * day) }
        val now = trip(EnergyKind.FUEL, brake(60f), brake(60f), km = 10.0, wall = 12 * day)
        assertNull(HabitBaseline.compare(now, listOf(now) + past))
    }

    @Test
    fun `평소 대비 - 같은 거리에 급제동이 두 배면 한 번 몫만큼 더 쓴 것`() {
        // 지난 20일 동안 10km마다 급제동 한 번(합계 200km)
        val past = (1..20).map { trip(EnergyKind.FUEL, brake(60f), km = 10.0, wall = it * day) }
        val now = trip(EnergyKind.FUEL, brake(60f), brake(60f), km = 10.0, wall = 21 * day)
        val c = assertNotNull(HabitBaseline.compare(now, listOf(now) + past))
        val one = HabitCostEstimator.estimate(trip(EnergyKind.FUEL, brake(60f)))!!.won
        assertEquals(one, c.diffWon, 1e-6)
        assertEquals(20, c.baselineTrips)
    }

    @Test
    fun `평소 대비 - 다른 에너지 종류, 4주 넘은 기록, 1km 미만 운행은 빼거나 비교하지 않는다`() {
        val old = (1..20).map { trip(EnergyKind.FUEL, brake(60f), km = 10.0, wall = it * day) }
        val ev = (31..50).map { trip(EnergyKind.ELECTRIC, brake(60f), km = 10.0, wall = it * day) }
        val now = trip(EnergyKind.FUEL, brake(60f), km = 10.0, wall = 51 * day)
        assertNull(HabitBaseline.compare(now, old + ev))
        val short = trip(EnergyKind.FUEL, brake(60f), km = 0.5, wall = 21 * day)
        assertNull(HabitBaseline.compare(short, old))
    }
}
