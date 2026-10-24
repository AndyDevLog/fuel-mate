package io.roadsensor.core.upload

import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin
import io.roadsensor.core.model.RoadEvent
import io.roadsensor.core.model.VehicleSnapshot
import io.roadsensor.core.protocol.Observation
import io.roadsensor.core.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObservationBuilderTest {

    private val sihung = GeoPoint(37.4127212, 127.094681)
    private val pangyo = GeoPoint(37.39479166, 127.11256944)
    private val t0 = 1_800_000_000_000L

    private fun state(at: GeoPoint?, wiper: Boolean = false, switch: String? = null, lowBeam: Boolean = false, heading: Float = 140f) = AppState(
        vehicle = VehicleSnapshot(wiperOn = wiper, wiperSwitch = switch, lowBeamOn = lowBeam),
        location = at?.let { GeoFix(it, heading, 0, LocationOrigin.DEMO_TRACK) },
    )

    @Test
    fun `위치를 모르면 보내지 않는다`() {
        assertTrue(ObservationBuilder().onState(state(null, wiper = true), t0).isEmpty())
    }

    @Test
    fun `처음 격자에 들어오면 꺼진 상태도 보낸다`() {
        val obs = ObservationBuilder().onState(state(sihung), t0)
        val cell = Geohash.encode(sihung)
        assertEquals(listOf(Observation.Rain(cell, t0 / 60_000, 0), Observation.Dark(cell, t0 / 60_000, false)), obs)
    }

    @Test
    fun `같은 격자에서 바뀐 게 없으면 보내지 않고, 와이퍼가 켜지면 비만 보낸다`() {
        val b = ObservationBuilder()
        b.onState(state(sihung), t0)
        assertTrue(b.onState(state(sihung), t0 + 1_000).isEmpty())
        val obs = b.onState(state(sihung, wiper = true, switch = "CONTINUOUS_LEVEL_3"), t0 + 2_000)
        assertEquals(listOf<Observation>(Observation.Rain(Geohash.encode(sihung), t0 / 60_000, 2)), obs)
    }

    @Test
    fun `격자가 바뀌면 새 격자로 다시 보낸다`() {
        val b = ObservationBuilder()
        b.onState(state(sihung), t0)
        val obs = b.onState(state(pangyo), t0 + 1_000)
        assertEquals(setOf(Geohash.encode(pangyo)), obs.map { it.cell }.toSet())
        assertEquals(2, obs.size)
    }

    @Test
    fun `같은 격자에 5분 넘게 있으면 다시 보낸다`() {
        val b = ObservationBuilder()
        b.onState(state(sihung), t0)
        assertTrue(b.onState(state(sihung), t0 + ObservationBuilder.REFRESH_MS - 1).isEmpty())
        assertEquals(2, b.onState(state(sihung), t0 + ObservationBuilder.REFRESH_MS).size)
    }

    @Test
    fun `하향등은 30초 이상 켜져 있어야 어두움으로 보낸다 (터널 거르기)`() {
        val b = ObservationBuilder()
        b.onState(state(sihung), t0)
        assertTrue(b.onState(state(sihung, lowBeam = true), t0 + 1_000).isEmpty())
        val obs = b.onState(state(sihung, lowBeam = true), t0 + 31_000)
        assertEquals(listOf<Observation>(Observation.Dark(Geohash.encode(sihung), (t0 + 31_000) / 60_000, true)), obs)
    }

    @Test
    fun `급정거는 한 번만, 일어난 위치의 격자와 방향으로 보낸다`() {
        val b = ObservationBuilder()
        b.onState(state(sihung), t0)
        val brake = RoadEvent.HardBrake(timeMs = 5, point = pangyo, headingDeg = 90f, startSpeedMps = 16f)
        val s = state(sihung).copy(events = listOf(brake))
        assertEquals(listOf<Observation>(Observation.Brake(Geohash.encode(pangyo), t0 / 60_000, 2)), b.onState(s, t0 + 1_000))
        assertTrue(b.onState(s, t0 + 2_000).isEmpty())
    }

    @Test
    fun `진행 방향 8방위`() {
        assertEquals(0, Protocol.direction(0f))
        assertEquals(0, Protocol.direction(350f))
        assertEquals(1, Protocol.direction(45f))
        assertEquals(3, Protocol.direction(140f))
        assertEquals(7, Protocol.direction(-45f))
    }

    @Test
    fun `임시 ID는 30분마다 바뀐다`() {
        var n = 0
        val r = TempIdRotator({ "id-${n++}" })
        assertEquals("id-0", r.current(0))
        assertEquals("id-0", r.current(TempIdRotator.ROTATE_MS - 1))
        assertEquals("id-1", r.current(TempIdRotator.ROTATE_MS))
    }
}
