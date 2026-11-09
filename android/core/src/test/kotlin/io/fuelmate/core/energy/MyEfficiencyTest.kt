package io.fuelmate.core.energy

import io.fuelmate.core.geo.GeoMath
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.Trip
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.StationRecommender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MyEfficiencyTest {

    private val day = 86_400_000L

    private fun trip(dayIndex: Int, km: Double, liters: Double, kind: EnergyKind = EnergyKind.FUEL) =
        Trip(startMs = 0, startWallMs = dayIndex * day, distanceM = km * 1000, energyKind = kind, energyUsed = liters)

    @Test
    fun `기록이 없으면 가정값으로 배우는 중`() {
        val e = MyEfficiency.of(emptyList())
        assertEquals(EfficiencyStatus.LEARNING, e.status)
        assertEquals(MyEfficiency.ASSUMED_KM_PER_L, e.kmPerL)
        assertNull(e.recordKmPerL)
    }

    @Test
    fun `30km보다 적으면 기록 값이 있어도 가정값을 쓰고 쌓인 거리를 알려 준다`() {
        val e = MyEfficiency.of(listOf(trip(1, 12.0, 1.0), trip(2, 8.5, 0.6)))
        assertEquals(EfficiencyStatus.LEARNING, e.status)
        assertEquals(MyEfficiency.ASSUMED_KM_PER_L, e.kmPerL)
        assertEquals(20.5, e.recordKm, 1e-9)
    }

    @Test
    fun `30km가 넘으면 거리 합을 연료 합으로 나눈 값을 쓴다 - 운행별 평균이 아니다`() {
        // 연료 1% 단위라 짧은 운행은 0L, 다음 운행에 몰려 잡힌다. 합치면 맞는다
        val e = MyEfficiency.of(listOf(trip(1, 4.0, 0.0), trip(2, 20.0, 2.4), trip(3, 16.0, 1.2)))
        assertEquals(EfficiencyStatus.MINE, e.status)
        assertEquals(40.0 / 3.6, e.kmPerL, 1e-9)
        assertTrue(e.mine)
    }

    @Test
    fun `전기차 운행과 4주보다 오래된 운행은 빼고 센다`() {
        val e = MyEfficiency.of(
            listOf(
                trip(40, 30.0, 2.0),
                trip(35, 100.0, 20.0, EnergyKind.ELECTRIC),
                trip(5, 300.0, 100.0),  // 가장 최근(40일)보다 35일 앞: 뺀다
            ),
        )
        assertEquals(EfficiencyStatus.MINE, e.status)
        assertEquals(15.0, e.kmPerL, 1e-9)
    }

    @Test
    fun `차가 낼 수 없는 값이면 가정값으로 돌아간다`() {
        val tooGood = MyEfficiency.of(listOf(trip(1, 40.0, 0.6)))   // 66km/L
        assertEquals(EfficiencyStatus.ODD, tooGood.status)
        assertEquals(MyEfficiency.ASSUMED_KM_PER_L, tooGood.kmPerL)
        val noFuel = MyEfficiency.of(listOf(trip(1, 40.0, 0.0)))
        assertEquals(EfficiencyStatus.ODD, noFuel.status)
    }

    @Test
    fun `내 차 연비가 나쁠수록 돌아가는 비용이 커져 이득이 줄어든다`() {
        val here = GeoPoint(37.40, 127.10)
        val ahead = GeoMath.destination(here, 0.0, 5_000.0)
        val near = Station("near", "near", null, 1900.0, GeoPoint(37.401, 127.100))
        // 30원 싸지만 옆으로 약 2km: 우회 약 1.9km
        val side = Station("side", "side", null, 1870.0, GeoPoint(37.42, 127.123))
        val stations = listOf(near, side)

        val thrifty = MyEfficiency.of(listOf(trip(1, 200.0, 10.0)))   // 20km/L
        val thirsty = MyEfficiency.of(listOf(trip(1, 200.0, 40.0)))   // 5km/L
        val a = assertNotNull(StationRecommender.recommend(stations, here, ahead, fillL = 30.0, efficiency = thrifty))
        val b = assertNotNull(StationRecommender.recommend(stations, here, ahead, fillL = 30.0, efficiency = thirsty))
        val gainA = a.options.first { it.station.id == "side" }.gainWon
        val gainB = b.options.first { it.station.id == "side" }.gainWon
        assertTrue(gainA > gainB, "$gainA > $gainB")
        // 30원 × 30L = 900원. 연비 좋은 차는 우회 비용 약 180원이라 이득, 나쁜 차는 약 710원이라 남는 게 300원 미만
        assertEquals("side", a.best.station.id)
        assertFalse(b.worthDetour)
        assertEquals(thirsty, b.efficiency)
    }
}
