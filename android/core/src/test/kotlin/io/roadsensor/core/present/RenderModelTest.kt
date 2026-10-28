package io.roadsensor.core.present

import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint
import io.roadsensor.core.model.Gear
import io.roadsensor.core.model.LocationOrigin
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RenderModelTest {

    private fun AppState.apply(vararg actions: Action) = actions.fold(this) { s, a -> reduce(s, a) }

    private val here = GeoPoint(37.4127212, 127.094681)
    private val pangyo = GeoPoint(37.39479166, 127.11256944)
    private val rainCell = CellState(Geohash.encode(pangyo), 5, 0.8f, RainLevel.MODERATE, 0.1f, 7, 0)

    private fun parkedWithRain() = AppState().apply(
        Action.LocationFixed(GeoFix(here, 140f, 0, LocationOrigin.DEMO_TRACK)),
        Action.FeedStatusChanged(true),
        Action.CellsUpdated(listOf(rainCell), emptyList()),
    )

    @Test
    fun `격자 강도는 0~1로 맞추고 비가 없으면 rain은 0이다`() {
        val dry = rainCell.copy(cell = "wydm9qk", rainLevel = RainLevel.NONE, rainRatio = 0f, hardBrakes = 1)
        val m = RenderModels.of(parkedWithRain().apply(Action.CellsUpdated(listOf(dry), emptyList())))

        // 격자 ID 순으로 정렬된다
        assertEquals(listOf("wydm9qk", rainCell.cell).sorted(), m.cells.map { it.cell })
        val rain = m.cells.first { it.cell == rainCell.cell }
        assertEquals(0.8f, rain.rain)
        assertEquals(RainLevel.MODERATE, rain.rainLevel)
        assertEquals(1f, rain.brake) // 7회 ≥ BRAKE_FULL(5)
        val clear = m.cells.first { it.cell == "wydm9qk" }
        assertEquals(0f, clear.rain)
        assertEquals(0.2f, clear.brake)
    }

    @Test
    fun `알림 문구는 주행 모드 한 줄과 같은 규칙으로 만든다`() {
        val s = parkedWithRain()
        val m = RenderModels.of(s)
        assertEquals(listOf(AlertKind.RAIN, AlertKind.HARD_BRAKE), m.alerts.map { it.kind })
        val first = m.alerts.first()
        assertEquals("${first.headline} ${first.label}", DriveLine.of(s))
        assertEquals("약 ${first.distanceText} 앞", first.headline)
        assertEquals("비 (중)", first.label)
        assertEquals(null, first.lengthText) // 격자 한 칸
        assertEquals(140f, m.heading)
        assertEquals(here, m.center)
        assertEquals(true, m.online)
    }

    @Test
    fun `그림에 안 쓰는 값이 바뀌면 렌더 모델은 같다`() {
        val s = parkedWithRain()
        val before = RenderModels.of(s)
        // 속도는 0.2초마다 오지만 지도에는 쓰지 않는다
        assertEquals(before, RenderModels.of(s.apply(Action.SpeedSample(1_000, 3.2f), Action.UploadStatusChanged(true, 2))))
        // 와이퍼는 내 차 표시에 쓰므로 바뀐다
        assertNotEquals(before, RenderModels.of(s.apply(Action.WiperChanged(true))))
    }

    @Test
    fun `위치가 없으면 중심도 알림도 없다`() {
        val m = RenderModels.of(AppState())
        assertNull(m.center)
        assertEquals(0f, m.heading)
        assertEquals(emptyList(), m.alerts)
        assertEquals(false, m.online)
    }

    @Test
    fun `JSON은 shared 렌더러 형식과 같다`() {
        val m = RenderModel(
            screen = Screen.MAP,
            center = GeoPoint(37.5, 127.0),
            heading = 90f,
            destination = null,
            ahead = AheadView(5000, 30, 1000),
            route = RouteView(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.0)), 1112, false,
                listOf(SegmentView(300, 600, AlertKind.RAIN, RainLevel.HEAVY))),
            cells = listOf(CellView("wydm9qk", 0.5f, RainLevel.HEAVY, 0f, 0.4f)),
            alerts = listOf(AlertView(AlertKind.RAIN, "wydm9qk", 1200, "1.2km", "비 \"강\"", "약 1.2km 앞", false, listOf("wydm9qk", "wydm9qs"), "400m 구간", "앞서 간 차 4대가 와이퍼를 켰습니다")),
            myCar = MyCarView(true, "연속 1", RainLevel.LIGHT, null, true, false, Gear.P, listOf("ABS ON"), 2),
            online = true,
        )
        assertEquals(
            """{"v":1,"screen":"MAP","center":{"lat":37.5,"lon":127.0},"heading":90.0,"destination":null,""" +
                """"ahead":{"radiusM":5000,"halfAngleDeg":30,"corridorHalfWidthM":1000},""" +
                """"route":{"path":[[37.5,127.0],[37.51,127.0]],"lengthM":1112,"estimated":false,""" +
                """"segments":[{"fromM":300,"toM":600,"kind":"RAIN","rainLevel":3}]},""" +
                """"cells":[{"cell":"wydm9qk","rain":0.5,"rainLevel":3,"dark":0.0,"brake":0.4}],""" +
                """"alerts":[{"kind":"RAIN","cell":"wydm9qk","distanceM":1200,"distanceText":"1.2km","label":"비 \"강\"","headline":"약 1.2km 앞","here":false,""" +
                """"cells":["wydm9qk","wydm9qs"],"lengthText":"400m 구간","reportersText":"앞서 간 차 4대가 와이퍼를 켰습니다"}],""" +
                """"myCar":{"wiperOn":true,"wiperText":"연속 1","rainLevel":1,"lowBeamOn":null,"tailLampOn":true,""" +
                """"brakePressed":false,"gear":"P","telltales":["ABS ON"],"hardBrakes":2},"online":true,"drive":null}""",
            RenderModelJson.encode(m),
        )
    }

    @Test
    fun `내 차 화면은 와이퍼 스위치 문구를 보여주고 경고등 사용 불가와 없음을 구분한다`() {
        val s = AppState().apply(
            Action.WiperChanged(true), Action.WiperSwitchChanged("CONTINUOUS_LEVEL_3"),
            Action.GearChanged(Gear.D), Action.TelltalesChanged(null),
        )
        val car = RenderModels.of(s, Screen.MY_CAR).myCar
        assertEquals("연속 3", car.wiperText)
        assertEquals(RainLevel.MODERATE, car.rainLevel)
        assertEquals(Gear.D, car.gear)
        assertNull(car.telltales)
        assertEquals(emptyList(), RenderModels.of(s.apply(Action.TelltalesChanged(emptyList()))).myCar.telltales)
        assertEquals("간헐 2", RenderModels.wiperText("INTERMITTENT_LEVEL_2"))
        assertEquals("자동", RenderModels.wiperText("AUTO"))
        assertEquals("SERVICE", RenderModels.wiperText("SERVICE"))
    }
}
