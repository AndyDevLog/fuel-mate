package io.fuelmate.core.store

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripTest {

    private fun AppState.apply(actions: List<Action>) = actions.fold(this, ::reduce)
    private fun gear(g: Gear, t: Long) = Action.GearChanged(g, timeMs = t, wallMs = 1_700_000_000_000 + t)
    private fun ev(t: Long, kWh: Double) = Action.EnergyReading(t, EnergyKind.ELECTRIC, EnergyOrigin.DEMO, kWh, 77.4)

    /** [fromMs]부터 0.2초마다 속도 표본 */
    private fun speeds(fromMs: Long, kmh: List<Float>) =
        kmh.mapIndexed { i, v -> Action.SpeedSample(fromMs + i * 200L, v / 3.6f) }

    @Test
    fun `P에서 벗어나면 운행이 시작되고 P로 돌아오면 끝나 기록에 쌓인다`() {
        // D → 0에서 72km/h까지 급가속 → 72km/h로 약 20초 → 급제동 → P
        val accel = (0..20).map { it * 3.6f }
        val cruise = List(100) { 72f }
        val brake = (20 downTo 0).map { it * 3.6f }
        val actions = buildList {
            add(gear(Gear.D, 0))
            add(ev(0, 30.0))
            addAll(speeds(0, accel + cruise))
            add(Action.BrakeChanged(true))
            addAll(speeds(24_200, brake))
            add(Action.BrakeChanged(false))
            add(ev(28_500, 29.8))
            add(gear(Gear.P, 30_000))
        }
        val s = AppState().apply(actions)
        assertNull(s.trip)
        val trip = s.trips.single()
        assertEquals(0L, trip.startMs)
        assertEquals(30_000L, trip.durationMs)
        assertEquals(1_700_000_000_000, trip.startWallMs)
        // 가속 4초 평균 36km/h(40m) + 정속 20초(400m) + 감속 4초(40m) ≈ 480m
        assertTrue(trip.distanceM in 450.0..510.0, "거리 ${trip.distanceM}")
        assertEquals(72f / 3.6f, trip.maxSpeedMps, 0.01f)
        assertEquals(1, trip.count<DriveEvent.HardAccel>())
        assertEquals(1, trip.count<DriveEvent.HardBrake>())
        assertEquals(0.2, trip.energyUsed, 1e-9)
        assertEquals(EnergyKind.ELECTRIC, trip.energyKind)
    }

    @Test
    fun `기어만 넣었다 뺀 운행(100m 미만)은 남기지 않는다`() {
        val s = AppState().apply(listOf(gear(Gear.D, 0)) + speeds(0, List(20) { 10f }) + gear(Gear.P, 5_000))
        assertNull(s.trip)
        assertTrue(s.trips.isEmpty())
    }

    @Test
    fun `서 있던 시간은 정차 시간으로, 2분 넘으면 긴 정차로 센다`() {
        val actions = buildList {
            add(gear(Gear.D, 0))
            // 150초 정지(1초 간격) → 출발해 36km/h로 20초
            addAll((0 until 150).map { Action.SpeedSample(it * 1_000L, 0f) })
            addAll(speeds(150_000, List(100) { 36f }))
            add(gear(Gear.P, 171_000))
        }
        val trip = AppState().apply(actions).trips.single()
        assertEquals(1, trip.count<DriveEvent.LongIdle>())
        assertEquals(150_000L, trip.idleMs)
    }

    @Test
    fun `에너지 공급원이 바뀌면 그 차이는 운행 소비에 넣지 않는다`() {
        val actions = listOf(
            gear(Gear.D, 0), ev(0, 30.0), ev(1_000, 29.9),
            Action.EnergyReading(2_000, EnergyKind.FUEL, EnergyOrigin.DEMO, 15.0, 60.0),
            Action.EnergyReading(3_000, EnergyKind.FUEL, EnergyOrigin.DEMO, 14.9, 60.0),
        )
        val trip = assertNotNull(AppState().apply(actions).trip)
        assertEquals(0.2, trip.energyUsed, 1e-9)
    }

    @Test
    fun `되돌린 기록 앞에 새 운행이 쌓이고 최대 개수를 넘지 않는다`() {
        val old = List(AppState.MAX_TRIPS) { Trip(startMs = it.toLong(), startWallMs = 0, distanceM = 1_000.0) }
        val actions = listOf(Action.TripsRestored(old), gear(Gear.D, 0)) + speeds(0, List(60) { 36f }) + gear(Gear.P, 13_000)
        val s = AppState().apply(actions)
        assertEquals(AppState.MAX_TRIPS, s.trips.size)
        assertEquals(13_000L, s.trips.first().endMs)
    }
}
