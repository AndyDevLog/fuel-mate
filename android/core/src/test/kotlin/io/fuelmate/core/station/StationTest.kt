package io.fuelmate.core.station

import io.fuelmate.core.geo.GeoMath
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.LocationOrigin
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationTest {

    /** 오피넷 문서(apiId=3)의 예시 응답 5곳(상표 태그는 예시대로 POLL_DIV_CO) */
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <RESULT>
          <OIL><UNI_ID>A0009907</UNI_ID><POLL_DIV_CO>GSC</POLL_DIV_CO><OS_NM>에너지플러스허브 삼방주유소</OS_NM><PRICE>1725</PRICE><DISTANCE>885.4</DISTANCE><GIS_X_COOR>313828.81720</GIS_X_COOR><GIS_Y_COOR>545078.98990</GIS_Y_COOR></OIL>
          <OIL><UNI_ID>A0010269</UNI_ID><POLL_DIV_CO>SOL</POLL_DIV_CO><OS_NM>극동유화㈜ 개나리주유소</OS_NM><PRICE>1745</PRICE><DISTANCE>912.7</DISTANCE><GIS_X_COOR>315589.00000</GIS_X_COOR><GIS_Y_COOR>544735.00000</GIS_Y_COOR></OIL>
          <OIL><UNI_ID>A0010207</UNI_ID><POLL_DIV_CO>SKE</POLL_DIV_CO><OS_NM>SK서광주유소</OS_NM><PRICE>1745</PRICE><DISTANCE>846.6</DISTANCE><GIS_X_COOR>314871.80000</GIS_X_COOR><GIS_Y_COOR>544012.00000</GIS_Y_COOR></OIL>
          <OIL><UNI_ID>A0006039</UNI_ID><POLL_DIV_CO>SKE</POLL_DIV_CO><OS_NM>(유)동하석유 힐탑셀프주유소</OS_NM><PRICE>1887</PRICE><DISTANCE>996.0</DISTANCE><GIS_X_COOR>314681.80000</GIS_X_COOR><GIS_Y_COOR>545833.60000</GIS_Y_COOR></OIL>
          <OIL><UNI_ID>A0009974</UNI_ID><POLL_DIV_CO>GSC</POLL_DIV_CO><OS_NM>지에스칼텍스㈜에너지플러스허브GS타워</OS_NM><PRICE>1887</PRICE><DISTANCE>329.8</DISTANCE><GIS_X_COOR>314996.15900</GIS_X_COOR><GIS_Y_COOR>544938.50758</GIS_Y_COOR></OIL>
        </RESULT>
    """.trimIndent()

    /** 문서 예시의 검색 중심(KATEC 314681.8, 544837)을 PROJ로 푼 위치 */
    private val center = GeoPoint(37.501758, 127.032849)

    private fun st(id: String, price: Double, lat: Double, lon: Double) = Station(id, id, null, price, GeoPoint(lat, lon))

    @Test
    fun `반경 검색 응답을 읽고 KATEC을 위경도로 바꾼다 - 거리가 응답의 DISTANCE와 맞는다`() {
        val stations = assertNotNull(OpinetAround.parse(sample))
        assertEquals(5, stations.size)
        val gs = stations.first { it.id == "A0009974" }
        assertEquals("GSC", gs.brand)
        assertEquals(1887.0, gs.priceWonPerL)
        // 응답 DISTANCE 329.8m(KATEC 평면 거리)와 위경도로 잰 거리가 몇 m 안에서 맞아야 한다
        assertEquals(329.8, GeoMath.distanceM(center, gs.point), 3.0)
        assertEquals(885.4, GeoMath.distanceM(center, stations.first { it.id == "A0009907" }.point), 3.0)
    }

    @Test
    fun `요청 주소에는 검색 중심 KATEC 좌표와 반경(최대 5km)만 들어간다`() {
        val url = OpinetAround.requestUrl("KEY", center, radiusM = 9_000)
        assertTrue(url.startsWith("https://www.opinet.co.kr/api/aroundAll.do?out=xml&x=314681."), url)
        assertTrue("y=54483" in url && "radius=5000" in url && "prodcd=B027" in url && "sort=1" in url, url)
    }

    @Test
    fun `깨진 응답은 null, 가격·좌표 없는 항목은 건너뛴다`() {
        assertNull(OpinetAround.parse("오류"))
        val partial = OpinetAround.parse("<RESULT><OIL><UNI_ID>X</UNI_ID><PRICE>1700</PRICE></OIL></RESULT>")
        assertEquals(emptyList(), partial)
    }

    @Test
    fun `가는 길에 있는 싼 곳을 추천하고, 가까운 곳 대비 이득을 계산한다`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)   // 북쪽으로 5km
        val near = st("near", 1900.0, 37.401, 127.100)        // 바로 앞, 우회 거의 0
        val cheapOnWay = st("cheap", 1800.0, 37.42, 127.101)  // 앞 2.2km, 길 옆 약 90m
        val cheapBehind = st("behind", 1700.0, 37.37, 127.10) // 뒤로 3.3km(우회 약 8.6km → 제외)
        val r = assertNotNull(StationRecommender.recommend(listOf(near, cheapOnWay, cheapBehind), here, ahead, fillL = 45.0))
        assertEquals("near", r.baseline.station.id)
        assertEquals("cheap", r.best.station.id)
        assertTrue(r.worthDetour)
        // 100원 × 45L = 4,500원에서 작은 우회 연료비를 뺀 값
        assertTrue(r.best.gainWon in 4_300.0..4_500.0, "${r.best.gainWon}")
        assertFalse(r.options.any { it.station.id == "behind" })
    }

    @Test
    fun `싸도 많이 돌아가면 손해라 가까운 곳을 추천한다`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)
        val near = st("near", 1900.0, 37.401, 127.100)
        // 20원 싸지만 옆으로 약 2km 떨어짐 → 우회 약 2.4km × 0.09L × 1,900원 ≈ 410원 > 20원 × 10L
        val farSide = st("side", 1880.0, 37.42, 127.123)
        val r = assertNotNull(StationRecommender.recommend(listOf(near, farSide), here, ahead, fillL = 10.0))
        assertEquals("near", r.best.station.id)
        assertFalse(r.worthDetour)
        assertTrue(r.options.first { it.station.id == "side" }.gainWon < 0)
    }

    @Test
    fun `경유지로 고른 곳 - 추천이면 덜 아끼는 돈 0, 다른 곳이면 추천과의 이득 차, 후보에 없으면 null`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)
        val near = st("near", 1900.0, 37.401, 127.100)
        val cheap = st("cheap", 1800.0, 37.42, 127.101)
        val mid = st("mid", 1850.0, 37.43, 127.1005)
        val r = assertNotNull(StationRecommender.recommend(listOf(near, cheap, mid), here, ahead, fillL = 45.0))
        assertEquals("cheap", r.best.station.id)

        val best = assertNotNull(r.chosen("cheap"))
        assertTrue(best.isBest)
        assertEquals(0.0, best.lessThanBestWon)

        val other = assertNotNull(r.chosen("mid"))
        assertFalse(other.isBest)
        assertEquals(r.best.gainWon - other.option.gainWon, other.lessThanBestWon, 0.001)
        // 50원 × 45L = 2,250원 차이 근처(우회 차이 몫만큼 조금 다름)
        assertTrue(other.lessThanBestWon in 2_000.0..2_500.0, "${other.lessThanBestWon}")

        // 가까운 곳(비교 기준)을 고르면 이득 0, 추천보다 추천의 이득만큼 덜 아낀다
        val base = assertNotNull(r.chosen("near"))
        assertEquals(0.0, base.option.gainWon, 0.001)
        assertEquals(r.best.gainWon, base.lessThanBestWon, 0.001)

        assertNull(r.chosen("unknown"))
    }

    @Test
    fun `내비 실제 우회가 오면 고른 곳의 이득을 그 값으로 다시 계산한다`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)
        val near = st("near", 1900.0, 37.401, 127.100)
        val cheap = st("cheap", 1800.0, 37.42, 127.101)
        val r = assertNotNull(StationRecommender.recommend(listOf(near, cheap), here, ahead, fillL = 45.0))

        val estimated = assertNotNull(r.chosen("cheap"))
        assertFalse(estimated.actualDetour)
        assertEquals(r.best.gainWon, estimated.option.gainWon)

        // 100원 × 45L = 4,500원 − (6,900m − 기준 우회) × 0.09L/km × 1,900원
        val actual = assertNotNull(r.chosen("cheap", 6_900.0))
        assertTrue(actual.actualDetour)
        assertEquals(6_900.0, actual.option.detourM)
        val expected = 4_500.0 - (6_900.0 - r.baseline.detourM) / 1000 * 0.09 * 1900.0
        assertEquals(expected, actual.option.gainWon, 0.001)
        assertTrue(actual.option.gainWon < estimated.option.gainWon)

        // 많이 돌면 손해가 될 수 있다
        assertTrue(assertNotNull(r.chosen("cheap", 40_000.0)).option.gainWon < 0)
        // 실제 우회가 추정보다 작으면 이득이 커진다
        assertTrue(assertNotNull(r.chosen("near", 0.0)).option.gainWon >= 0.0)
    }

    @Test
    fun `후보가 5곳보다 많아도 화면 목록은 5곳, 경유지 비교는 모든 후보에서 찾는다`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)
        val stations = (0 until 7).map { i -> st("s$i", 1800.0 + i * 10, 37.401 + i * 0.002, 127.100) }
        val r = assertNotNull(StationRecommender.recommend(stations, here, ahead, fillL = 45.0))
        assertEquals(7, r.candidates.size)
        assertEquals(5, r.options.size)
        assertNotNull(r.chosen("s6"))
        assertFalse(r.options.any { it.station.id == "s6" })
    }

    @Test
    fun `앞쪽 지점 - 목적지가 가까우면 목적지, 멀면 그 방향 5km`() {
        val here = GeoPoint(37.40, 127.10)
        val nearDest = GeoPoint(37.41, 127.10)
        assertEquals(nearDest, StationRecommender.aheadPoint(here, nearDest))
        val far = GeoPoint(37.60, 127.10)
        assertEquals(5_000.0, GeoMath.distanceM(here, StationRecommender.aheadPoint(here, far)), 1.0)
    }

    @Test
    fun `검색 규칙 - 목적지가 있고 연료 50% 이하일 때, 처음이거나 3km 움직였거나 15분 지나면 찾는다`() {
        fun fix(lat: Double) = Action.LocationFixed(GeoFix(GeoPoint(lat, 127.10), 0f, 0, LocationOrigin.DEMO_TRACK))
        fun fuel(l: Double) = Action.EnergyReading(0, EnergyKind.FUEL, EnergyOrigin.DEMO, l, 60.0)
        val dest = GeoPoint(37.60, 127.10)
        val low = listOf(fix(37.40), fuel(15.0), Action.DestinationChanged(dest)).fold(AppState(), ::reduce)
        assertTrue(StationSearchRule.shouldSearch(low, 0))
        val full = reduce(low, fuel(50.0))
        assertFalse(StationSearchRule.shouldSearch(listOf(fuel(50.0), fix(37.40), Action.DestinationChanged(dest)).fold(AppState(), ::reduce), 0))
        assertFalse(full.energy!!.percent!! <= StationSearchRule.SEARCH_BELOW_PERCENT)

        val searched = reduce(low, Action.StationsFound(StationSearch(emptyList(), GeoPoint(37.40, 127.10), 0, dest)))
        assertFalse(StationSearchRule.shouldSearch(searched, 60_000))
        assertTrue(StationSearchRule.shouldSearch(searched, 16 * 60_000))
        assertTrue(StationSearchRule.shouldSearch(reduce(searched, fix(37.43)), 60_000))
    }

    @Test
    fun `검색 규칙 - 목적지가 없으면 찾지 않고, 목적지가 바뀌면 바로 다시 찾는다`() {
        val here = GeoPoint(37.40, 127.10)
        val dest = GeoPoint(37.60, 127.10)
        val base = listOf(
            Action.LocationFixed(GeoFix(here, 0f, 0, LocationOrigin.NAVI_HELPER)),
            Action.EnergyReading(0, EnergyKind.FUEL, EnergyOrigin.DEMO, 15.0, 60.0),
        ).fold(AppState(), ::reduce)
        assertFalse(StationSearchRule.shouldSearch(base, 0))

        val searched = listOf(
            Action.DestinationChanged(dest),
            Action.StationsFound(StationSearch(emptyList(), here, 0, dest)),
        ).fold(base, ::reduce)
        assertFalse(StationSearchRule.shouldSearch(searched, 60_000))
        // 내비 좌표가 조금 흔들린 정도(약 100m)로는 다시 찾지 않는다
        assertFalse(StationSearchRule.shouldSearch(reduce(searched, Action.DestinationChanged(GeoPoint(37.601, 127.10))), 60_000))
        assertTrue(StationSearchRule.shouldSearch(reduce(searched, Action.DestinationChanged(GeoPoint(37.50, 127.20))), 60_000))
        // 목적지가 사라지면(안내 취소) 찾지 않는다
        assertFalse(StationSearchRule.shouldSearch(reduce(searched, Action.DestinationChanged(null)), 16 * 60_000))
    }

    @Test
    fun `목적지가 없으면 주유소 검색 결과가 있어도 추천하지 않는다`() {
        val here = GeoPoint(37.40, 127.10)
        val actions = listOf(
            Action.LocationFixed(GeoFix(here, 0f, 0, LocationOrigin.NAVI_HELPER)),
            Action.EnergyReading(0, EnergyKind.FUEL, EnergyOrigin.DEMO, 15.0, 60.0),
            Action.StationsFound(StationSearch(listOf(st("near", 1900.0, 37.401, 127.100)), here, 0)),
        )
        val state = actions.fold(AppState(), ::reduce)
        assertNull(StationRecommender.recommend(state))
        assertNotNull(StationRecommender.recommend(reduce(state, Action.DestinationChanged(GeoPoint(37.60, 127.10)))))
    }

    @Test
    fun `상태에서 추천 - 탱크를 가득 채우는 양으로 계산한다`() {
        val here = GeoPoint(37.40, 127.10)
        val actions = listOf(
            Action.LocationFixed(GeoFix(here, 0f, 0, LocationOrigin.DEMO_TRACK)),
            Action.EnergyReading(0, EnergyKind.FUEL, EnergyOrigin.DEMO, 15.0, 60.0),
            Action.DestinationChanged(GeoPoint(37.60, 127.10)),
            Action.StationsFound(StationSearch(listOf(st("near", 1900.0, 37.401, 127.100), st("cheap", 1800.0, 37.42, 127.101)), here, 0)),
        )
        val r = assertNotNull(StationRecommender.recommend(actions.fold(AppState(), ::reduce)))
        assertEquals(45.0, r.fillL)
        assertEquals("cheap", r.best.station.id)
    }

    @Test
    fun `실제 우회 - 내비 값이 말이 되면 쓰고, 음수·20km 초과·넣기 전 거리 없음이면 모른다`() {
        assertEquals(1_500.0, WaypointState.detourOrNull(3_100, 4_600))
        // 에뮬레이터에서 본 값: 넣기 전 10000m → 넣은 뒤 4800m
        assertNull(WaypointState.detourOrNull(10_000, 4_800))
        assertNull(WaypointState.detourOrNull(null, 4_800))
        assertNull(WaypointState.detourOrNull(1_000, 30_000))
    }
}
