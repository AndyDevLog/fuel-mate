package io.fuelmate.core.store

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.DriveMode
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.LocationOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReducerTest {

    private fun AppState.apply(vararg actions: Action) = actions.fold(this, ::reduce)

    @Test
    fun `브레이크를 밟은 채 급감속하면 급정거 사건이 하나 쌓인다`() {
        // 60km/h에서 브레이크를 밟고 0.2초마다 1m/s씩 줄인다(초당 5m/s 감속)
        val start = 60f / 3.6f
        val speeds = (0..10).map { Action.SpeedSample(1_000L + it * 200, start - it * 1f) }
        val s = AppState().apply(Action.SpeedSample(800, start), Action.BrakeChanged(true), *speeds.toTypedArray())
        val event = assertIs<DriveEvent.HardBrake>(s.events.single())
        assertTrue(event.startSpeedMps >= start - 1f)
        assertEquals(speeds.last().speedMps, s.vehicle.speedMps)
    }

    @Test
    fun `기어와 주행 규제를 그대로 담는다`() {
        val s = AppState().apply(
            Action.GearChanged(Gear.D),
            Action.DriveModeChanged(restricted = true, activeRestrictions = DriveMode.LIMIT_CONTENT),
        )
        assertEquals(Gear.D, s.vehicle.gear)
        assertTrue(s.drive.restricted && s.drive.limitContent)
    }

    @Test
    fun `거리계는 이동한 만큼 더하고, 위치가 튀면 더하지 않고 epoch를 올린다`() {
        fun fix(lat: Double) = Action.LocationFixed(GeoFix(GeoPoint(lat, 127.0), 0f, 0, LocationOrigin.DEMO_TRACK))
        // 위도 0.0001도 ≈ 11m
        val moved = AppState().apply(fix(37.0), fix(37.0001), fix(37.0002))
        assertEquals(1, moved.odometer.epoch)
        assertTrue(moved.odometer.meters in 21.0..23.5, "거리 ${moved.odometer.meters}")

        val jumped = moved.apply(fix(37.1))
        assertEquals(2, jumped.odometer.epoch)
        assertEquals(moved.odometer.meters, jumped.odometer.meters)
    }
}
