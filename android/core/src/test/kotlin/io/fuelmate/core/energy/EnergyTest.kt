package io.fuelmate.core.energy

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnergyTest {

    private fun AppState.apply(vararg actions: Action) = actions.fold(this, ::reduce)
    private fun ev(amount: Double? = null, capacity: Double? = null, percent: Float? = null, timeMs: Long = 0) =
        Action.EnergyReading(timeMs, EnergyKind.ELECTRIC, EnergyOrigin.VEHICLE, amount, capacity, percent)

    /** [kmh]로 [hours]시간 일정하게 달렸을 때 100km당 소비량 */
    private fun per100km(p: VehicleParams, kmh: Double): Double {
        val v = kmh / 3.6
        val hourly = Consumption.step(p, 3600.0, v, v, poweredOn = true)
        return hourly / kmh * 100
    }

    @Test
    fun `연료 종류 - 전기만 있으면 전기차, 다른 연료가 섞이면 연료차, 모르면 null`() {
        assertEquals(EnergyKind.ELECTRIC, EnergyRules.kindOf(listOf(10)))
        assertEquals(EnergyKind.FUEL, EnergyRules.kindOf(listOf(1)))
        assertEquals(EnergyKind.FUEL, EnergyRules.kindOf(listOf(1, 10)))
        assertNull(EnergyRules.kindOf(listOf(0)))
        assertNull(EnergyRules.kindOf(emptyList()))
    }

    @Test
    fun `데모 소비 모델 - 정속 주행 소비가 실제 차와 비슷한 범위에 든다`() {
        // 아이오닉5·EV6(77.4kWh) 고속도로 실측은 대략 100km당 17~21kWh, 2.5L 대형 세단은 6~8L
        val ev100 = per100km(VehicleParams.DEMO_EV, 100.0)
        val fuel100 = per100km(VehicleParams.DEMO_FUEL, 100.0)
        assertTrue(ev100 in 15.0..22.0, "전기 $ev100 kWh/100km")
        assertTrue(fuel100 in 5.5..9.0, "연료 $fuel100 L/100km")
        // 빨리 달릴수록 더 쓴다(공기저항)
        assertTrue(per100km(VehicleParams.DEMO_EV, 130.0) > ev100)
    }

    @Test
    fun `데모 소비 모델 - 공회전은 연료를 쓰고, P단에 서 있으면 아무것도 안 쓴다`() {
        val idleHour = Consumption.step(VehicleParams.DEMO_FUEL, 3600.0, 0.0, 0.0, poweredOn = true)
        assertEquals(0.8, idleHour, 0.01)
        assertEquals(0.0, Consumption.step(VehicleParams.DEMO_FUEL, 3600.0, 0.0, 0.0, poweredOn = false))
    }

    @Test
    fun `데모 소비 모델 - 전기차는 감속할 때 되찾고, 연료차는 못 되찾는다`() {
        val v = 60 / 3.6
        val evBrake = Consumption.step(VehicleParams.DEMO_EV, 4.0, v, 0.0, poweredOn = true)
        val fuelBrake = Consumption.step(VehicleParams.DEMO_FUEL, 4.0, v, 0.0, poweredOn = true)
        assertTrue(evBrake < 0, "전기 $evBrake")
        assertTrue(fuelBrake > 0, "연료 $fuelBrake (공회전만큼)")
    }

    @Test
    fun `데모 공급원 - 달리면 잔량이 줄고, 긴 공백은 세지 않는다`() {
        var demo = DemoEnergy.start(VehicleParams.DEMO_EV, percent = 40.0)
        val start = demo.amount
        assertEquals(30.96, start, 1e-9)
        // 시속 100km로 60초
        val v = 100f / 3.6f
        for (t in 0..60) demo = demo.tick(t * 1_000L, v, poweredOn = true)
        val used = start - demo.amount
        assertTrue(used in 0.25..0.37, "1분 $used kWh")

        val afterGap = demo.tick(600_000L, v, poweredOn = true)
        assertEquals(demo.amount, afterGap.amount)
    }

    @Test
    fun `계량기 - 줄어든 양을 쓴 양으로 센다`() {
        val s = AppState().apply(ev(capacity = 77.4), ev(amount = 40.0), ev(amount = 39.9), ev(amount = 39.7))
        assertEquals(0.3, s.energyMeter.used, 1e-9)
        assertEquals(0.0, s.energyMeter.added)
        assertEquals(39.7, s.energy?.amount)
        assertEquals(77.4, s.energy?.capacity)
    }

    @Test
    fun `계량기 - 한 번에 용량 5% 넘게 줄면 값 조정으로 보고 세지 않는다`() {
        // Virtual Console에서 Battery Energy를 77.4 → 50으로 바꾼 경우
        val s = AppState().apply(ev(capacity = 77.4), ev(amount = 77.4), ev(amount = 50.0))
        assertEquals(0.0, s.energyMeter.used)
        assertEquals(50.0, s.energy?.amount)
    }

    @Test
    fun `계량기 - 달리면서 늘면 회생, 서서 늘면 충전`() {
        val moving = AppState()
            .apply(Action.SpeedSample(0, 20f), ev(capacity = 77.4), ev(amount = 40.0), ev(amount = 39.5), ev(amount = 39.6))
        assertEquals(0.4, moving.energyMeter.used, 1e-9)
        assertEquals(0.0, moving.energyMeter.added)

        val charged = moving.apply(Action.SpeedSample(1_000, 0f), ev(amount = 45.0, timeMs = 20_000))
        assertEquals(5.4, charged.energyMeter.added, 1e-9)
    }

    @Test
    fun `계량기 - 멈춘 직후 늦게 온 회생은 충전으로 세지 않는다`() {
        // 감속하다 0이 된 뒤 1초 늦게 잔량이 늘어난 값이 온다(데모 1초 간격, 실차 1~4초 간격)
        val s = AppState().apply(
            Action.SpeedSample(0, 5f), ev(capacity = 77.4, amount = 40.0, timeMs = 0), ev(amount = 39.5, timeMs = 500),
            Action.SpeedSample(1_000, 0f), ev(amount = 39.52, timeMs = 2_000),
        )
        assertEquals(0.48, s.energyMeter.used, 1e-9)
        assertEquals(0.0, s.energyMeter.added)
    }

    @Test
    fun `공급원이 바뀌면 계량기를 새로 시작하고, 이번에 안 온 값은 유지한다`() {
        val s = AppState().apply(ev(capacity = 77.4, amount = 40.0), ev(percent = 52f), ev(amount = 39.0))
        assertEquals(52f, s.energy?.percent)
        assertEquals(1.0, s.energyMeter.used, 1e-9)

        val demo = s.apply(DemoEnergy.start(VehicleParams.DEMO_FUEL, 25.0).reading())
        assertEquals(EnergyKind.FUEL, demo.energy?.kind)
        assertEquals(EnergyOrigin.DEMO, demo.energy?.origin)
        assertEquals(0.0, demo.energyMeter.used)
        // 데모는 퍼센트를 따로 주지 않는다 → 잔량 ÷ 용량
        assertEquals(25f, demo.energy?.percent)
    }
}
