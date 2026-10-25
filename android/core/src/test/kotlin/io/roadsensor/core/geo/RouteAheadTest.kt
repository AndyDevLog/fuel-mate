package io.roadsensor.core.geo

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.LocationOrigin
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.present.DriveLine
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouteAheadTest {

    private val start = GeoPoint(37.40, 127.10)
    // 동쪽으로 약 1km 간 뒤 북쪽으로 약 1.5km 가는 ㄱ자 길
    private val corner = GeoPoint(37.40, 127.10 + 1_000 / 88_430.0)
    private val end = GeoPoint(37.40 + 1_500 / 111_320.0, corner.lon)
    private val route = listOf(start, corner, end)

    private fun fix(heading: Float = 90f) = GeoFix(start, heading, 0, LocationOrigin.DEMO_TRACK)

    private fun rain(p: GeoPoint, level: RainLevel = RainLevel.MODERATE) =
        CellState(Geohash.encode(p), cars = 5, rainRatio = 0.8f, rainLevel = level, darkRatio = 0f, hardBrakes = 0, updatedAtMin = 0)

    /** 모퉁이에서 북쪽으로 [fromM]~[toM] 구간의 격자 */
    private fun rainNorth(fromM: Int, toM: Int, level: RainLevel = RainLevel.MODERATE) =
        (fromM..toM step 30).map { rain(GeoPoint(corner.lat + it / 111_320.0, corner.lon), level) }.distinctBy { it.cell }

    @Test
    fun `앞길 거리는 길을 따라 잰다`() {
        val cells = rainNorth(500, 800).associateBy { it.cell }
        val p = assertNotNull(RouteAhead.profile(fix(), route, cells))
        val s = p.segments.single()
        assertEquals(AlertKind.RAIN, s.kind)
        // 길 따라 약 1.5km(직선으로는 약 1.1km)
        assertTrue(s.fromM in 1_400..1_560, "시작 ${s.fromM}")
        // 300m 구간이지만 양 끝 격자(높이 약 150m)가 걸친 만큼 길어진다
        assertTrue(s.toM - s.fromM in 250..480, "길이 ${s.toM - s.fromM}")
        assertTrue(!p.estimated)
        assertTrue(p.lengthM in 2_400..2_560, "앞길 길이 ${p.lengthM}")
    }

    @Test
    fun `앞길이 있으면 알림도 길 위 구간과 같은 거리다`() {
        var s = AppState()
        s = reduce(s, Action.LocationFixed(fix()))
        s = reduce(s, Action.RouteAheadChanged(route))
        s = reduce(s, Action.CellsUpdated(rainNorth(500, 800), emptyList()))
        val alert = s.alerts.single()
        assertEquals(s.ahead!!.segments.single().fromM, alert.distanceM)
        assertTrue(DriveLine.of(s).startsWith("약 1.5km 앞 비"), DriveLine.of(s))
    }

    @Test
    fun `내 차 격자부터 시작하는 구간은 이미 구간 안이다`() {
        val cells = (0..600 step 30).map { rain(GeoPoint(start.lat, start.lon + it / 88_430.0), RainLevel.HEAVY) }
        var s = AppState()
        s = reduce(s, Action.LocationFixed(fix()))
        s = reduce(s, Action.RouteAheadChanged(route))
        s = reduce(s, Action.CellsUpdated(cells, emptyList()))
        val alert = s.alerts.single()
        assertTrue(alert.here)
        assertTrue(DriveLine.of(s).startsWith("지금 비 (강)"), DriveLine.of(s))
    }

    @Test
    fun `짧은 틈은 한 구간으로 메우고 강도는 가장 센 값을 따른다`() {
        val cells = (rainNorth(100, 300) + rainNorth(360, 500, RainLevel.HEAVY)).associateBy { it.cell }
        val seg = RouteAhead.profile(fix(), route, cells)!!.segments.single()
        assertEquals(RainLevel.HEAVY, seg.rainLevel)
    }

    @Test
    fun `앞길을 모르면 진행 방향 직선으로 대신하고 알림은 부채꼴 방식이다`() {
        val p = RouteAhead.profile(fix(heading = 90f), null, emptyMap())!!
        assertTrue(p.estimated)
        assertEquals(2, p.path.size)
        assertTrue(p.lengthM >= 4_950, "길이 ${p.lengthM}")
        // 동쪽 직선 위 비 격자는 직선 프로필에도, 부채꼴 알림에도 잡힌다
        val east = (1_000..1_300 step 30).map { rain(GeoPoint(start.lat, start.lon + it / 88_430.0)) }.distinctBy { it.cell }
        var s = reduce(AppState(), Action.LocationFixed(fix(heading = 90f)))
        s = reduce(s, Action.CellsUpdated(east, emptyList()))
        assertEquals(1, s.ahead!!.segments.size)
        assertEquals(1, s.alerts.size)
    }

    @Test
    fun `궤적 앞길은 지금 위치에서 시작해 끝에서 멈춘다`() {
        val player = TrackPlayer(route, loop = true)
        player.advance(10_000, 100f) // 1km 진행 → 모퉁이 근처
        val ahead = player.pathAhead(5_000.0)
        assertEquals(player.current().point, ahead.first())
        assertEquals(end, ahead.last())
        val len = ahead.zipWithNext { a, b -> GeoMath.distanceM(a, b) }.sum()
        assertTrue(len in 1_450.0..1_560.0, "남은 길 $len")
    }
}
