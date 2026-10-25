package io.roadsensor.core.store

import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.DriveMode
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.model.RoadAlert
import io.roadsensor.core.model.RoadEvent
import io.roadsensor.core.present.DriveLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReducerTest {

    private fun AppState.apply(vararg actions: Action) = actions.fold(this) { s, a -> reduce(s, a) }

    @Test
    fun `와이퍼 켜짐과 스위치 값으로 내 차 비 세기가 정해진다`() {
        val s = AppState().apply(Action.WiperChanged(true), Action.WiperSwitchChanged("CONTINUOUS_LEVEL_3"))
        assertEquals(RainLevel.MODERATE, s.rainLevel)
        assertEquals(RainLevel.NONE, s.apply(Action.WiperChanged(false)).rainLevel)
    }

    @Test
    fun `브레이크를 밟은 채 급감속하면 급정거 사건이 하나 쌓인다`() {
        var s = AppState().apply(Action.SpeedSample(0, 60f / 3.6f), Action.BrakeChanged(true))
        for (i in 1..10) s = reduce(s, Action.SpeedSample(i * 200L, (60f - i * 3.6f) / 3.6f))
        assertEquals(1, s.events.size)
        assertIs<RoadEvent.HardBrake>(s.events.first())
        assertEquals(1, s.contribution.hardBrakes)

        // 떼었다가 다시 천천히 멈추면 더 세지 않는다
        s = s.apply(Action.BrakeChanged(false), Action.BrakeChanged(true))
        for (i in 11..20) s = reduce(s, Action.SpeedSample(i * 200L, 20f / 3.6f - i * 0.01f))
        assertEquals(1, s.events.size)
    }

    @Test
    fun `격자가 들어오면 위치 기준으로 알림이 다시 계산된다`() {
        val here = GeoPoint(37.4127212, 127.094681)
        val pangyo = GeoPoint(37.39479166, 127.11256944)
        val cell = CellState(Geohash.encode(pangyo), 5, 0.8f, RainLevel.HEAVY, 0f, 0, 0)

        var s = AppState().apply(
            Action.LocationFixed(GeoFix(here, 140f, 0, LocationOrigin.DEMO_TRACK)),
            Action.CellsUpdated(listOf(cell), emptyList()),
        )
        assertEquals(1, s.alerts.size)

        s = s.apply(Action.CellsUpdated(emptyList(), listOf(cell.cell)))
        assertTrue(s.alerts.isEmpty())
        assertTrue(s.cells.isEmpty())
    }

    @Test
    fun `주행 모드 한 줄 - 거리와 세기, 짧은 문구 규제`() {
        val s = AppState(
            alerts = listOf(RoadAlert(AlertKind.RAIN, "wydm9qk", 2_480, RainLevel.HEAVY)),
            drive = DriveMode(restricted = true, activeRestrictions = 80),
        )
        assertEquals("약 2.5km 앞 비 (강)", DriveLine.of(s))
        val short = s.apply(Action.DriveModeChanged(true, 80 or DriveMode.LIMIT_STRING_LENGTH))
        assertEquals("2.5km 비", DriveLine.of(short))
        assertEquals("앞쪽 도로 이상 없음", DriveLine.of(AppState()))

        // 이미 구간 안이면 거리 대신 "지금"과 앞으로 이어지는 길이
        val inside = AppState(alerts = listOf(RoadAlert(AlertKind.RAIN, "wydm9qk", 60, RainLevel.HEAVY, lengthM = 4_900, here = true)))
        assertEquals("지금 비 (강) · 약 4.9km 계속", DriveLine.of(inside))
        assertEquals("지금 비", DriveLine.of(inside.copy(drive = DriveMode(true, DriveMode.LIMIT_STRING_LENGTH))))
        assertEquals("지금 비 (강)", DriveLine.of(AppState(alerts = listOf(RoadAlert(AlertKind.RAIN, "wydm9qk", 60, RainLevel.HEAVY, here = true)))))
    }

    @Test
    fun `주행 모드 거리 표기 단위`() {
        assertEquals("100m", DriveLine.distance(30))
        assertEquals("800m", DriveLine.distance(860))
        assertEquals("1km", DriveLine.distance(1_020))
        assertEquals("4.2km", DriveLine.distance(4_240))
        assertEquals("12km", DriveLine.distance(12_400))
    }

    @Test
    fun `구독이 끊기면 받은 격자와 알림을 비운다`() {
        val here = GeoPoint(37.4127212, 127.094681)
        val pangyo = GeoPoint(37.39479166, 127.11256944)
        val cell = CellState(Geohash.encode(pangyo), 5, 0.8f, RainLevel.HEAVY, 0f, 0, 0)
        val s = AppState().apply(
            Action.FeedStatusChanged(true),
            Action.LocationFixed(GeoFix(here, 140f, 0, LocationOrigin.DEMO_TRACK)),
            Action.CellsUpdated(listOf(cell), emptyList()),
            Action.UploadStatusChanged(ok = true, pending = 2),
        )
        assertEquals(1, s.alerts.size)
        val down = s.apply(Action.FeedStatusChanged(false))
        assertTrue(down.cells.isEmpty() && down.alerts.isEmpty())
        assertEquals(false, down.link.feedConnected)
        assertEquals(true, down.link.uploadOk)
    }

    @Test
    fun `위치와 앞길은 한 액션으로 같이 바뀌고 앞길 없는 위치가 오면 앞길을 지운다`() {
        val here = GeoPoint(37.4127212, 127.094681)
        val next = GeoPoint(37.4120000, 127.095500)
        val route = listOf(here, next)
        val s = AppState().apply(Action.LocationFixed(GeoFix(here, 140f, 0, LocationOrigin.DEMO_TRACK), route))
        assertEquals(here, s.location?.point)
        assertEquals(route, s.routeAhead)
        assertTrue(s.ahead?.estimated == false)

        // 데모 → 내비 전환: 내비 위치에는 앞길이 없어서 진행 방향 직선(추정)으로 돌아간다
        val navi = s.apply(Action.LocationFixed(GeoFix(next, 140f, 1, LocationOrigin.NAVI_HELPER)))
        assertNull(navi.routeAhead)
        assertTrue(navi.ahead?.estimated == true)
    }

    @Test
    fun `경고등 API를 못 쓰면 null로 남는다`() {
        assertNull(AppState().apply(Action.TelltalesChanged(null)).vehicle.telltales)
    }
}
