package io.fuelmate.core.cost

import io.fuelmate.core.energy.Consumption
import io.fuelmate.core.energy.VehicleParams
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.Trip
import kotlin.math.max

enum class HabitCategory(val label: String) {
    BRAKING("급제동·급감속"),
    HIGH_SPEED("시속 100km 넘는 고속"),
}

/** 항목 하나. [amount]는 연료 L 또는 전기 kWh, [count]는 사건 수(고속은 0) */
data class HabitItem(val category: HabitCategory, val count: Int, val amount: Double, val won: Double)

/**
 * 이번 운행의 운전 습관 비용 **추정**. 실제 소비를 잰 값이 아니라 속도 기록과 가정값(차 무게·효율·회생률)으로 계산한다.
 * 급가속은 원화 근거가 약해서(docs/pivot-fuel.md 13-3) 횟수만 둔다.
 * 긴 정차(P가 아닌 채 2분 넘게 섬)는 넣지 않는다: 대부분 신호·정체라 운전자가 고칠 수 있는 습관으로 보기 어렵고,
 * ISG·하이브리드는 서 있는 동안 엔진이 꺼져 공회전 연료 가정도 맞지 않는다(2026-11-09 사용자 결정). 사건 판정·저장은 그대로 둔다.
 */
data class HabitCost(
    val kind: EnergyKind,
    val items: List<HabitItem>,
    val hardAccelCount: Int,
    val prices: UnitPrices,
    val params: VehicleParams,
) {
    val amount: Double get() = items.sumOf { it.amount }
    val won: Double get() = items.sumOf { it.won }
    fun item(c: HabitCategory) = items.first { it.category == c }
}

/**
 * 계산 방법(docs/pivot-fuel.md 13-3). 화면의 "계산 방법"도 이 내용을 그대로 설명한다.
 * - 급제동·급감속: 잃은 운동 에너지 ½·m·(v₁² − v₂²)에서 그동안 구름·공기저항이 쓴 몫을 뺀 것이 브레이크로 버린 에너지다.
 *   전기차는 그중 회생으로 되찾은 몫을 뺀다. 바퀴 에너지 ÷ 구동 효율 = 연료·배터리에서 나간 에너지
 * - 고속: 시속 100km를 넘는 동안 공기저항 ½·ρ·CdA·(v² − v₀²)·거리. 운행이 Σ(v² − v₀²)·Δs를 쌓아 둔다
 */
object HabitCostEstimator {

    fun estimate(trip: Trip, prices: UnitPrices = UnitPrices.ASSUMED): HabitCost? {
        val kind = trip.energyKind ?: return null
        val p = VehicleParams.assumedFor(kind)
        val price = prices.of(kind)

        val braking = trip.events.filter { it is DriveEvent.HardBrake || it is DriveEvent.HardDecel }
        val brakingAmount = braking.sumOf {
            when (it) {
                is DriveEvent.HardBrake -> brakingWaste(p, it.startSpeedMps, it.endSpeedMps, it.peakMps2)
                is DriveEvent.HardDecel -> brakingWaste(p, it.startSpeedMps, it.endSpeedMps, it.peakMps2)
                else -> 0.0
            }
        }
        val highAmount = highSpeedWaste(p, trip.highSpeedExcess)

        fun item(c: HabitCategory, count: Int, amount: Double) = HabitItem(c, count, amount, amount * price)
        return HabitCost(
            kind = kind,
            items = listOf(
                item(HabitCategory.BRAKING, braking.size, brakingAmount),
                item(HabitCategory.HIGH_SPEED, 0, highAmount),
            ),
            hardAccelCount = trip.count<DriveEvent.HardAccel>(),
            prices = prices,
            params = p,
        )
    }

    /**
     * 감속 한 번에 버린 에너지(연료 L, 전기 kWh). 감속 시간은 최대 감속도로 잡는다:
     * 실제보다 짧게 잡혀 저항 몫이 조금 작아지지만, 60→0 급제동이면 저항 몫이 운동 에너지의 몇 %라 차이가 작다.
     */
    fun brakingWaste(p: VehicleParams, v1: Float, v2: Float, peakMps2: Float): Double {
        val a = v1.toDouble()
        val b = v2.toDouble()
        val kineticJ = 0.5 * p.massKg * (a * a - b * b)
        if (kineticJ <= 0 || peakMps2 <= 0) return 0.0
        val seconds = (a - b) / peakMps2
        val vAvg = (a + b) / 2
        val resistJ = (p.massKg * Consumption.G * p.rollingCoef + 0.5 * Consumption.AIR_DENSITY * p.cdA * vAvg * vAvg) *
            vAvg * seconds
        val brakeJ = max(0.0, kineticJ - resistJ) * (1 - p.regenEfficiency)
        return Consumption.toSourceAmount(p, brakeJ / p.drivetrainEfficiency / J_PER_KWH)
    }

    /** [excess] = Σ(v² − v₀²)·Δs (m³/s²) */
    fun highSpeedWaste(p: VehicleParams, excess: Double): Double {
        if (excess <= 0) return 0.0
        val wheelJ = 0.5 * Consumption.AIR_DENSITY * p.cdA * excess
        return Consumption.toSourceAmount(p, wheelJ / p.drivetrainEfficiency / J_PER_KWH)
    }

    private const val J_PER_KWH = 3_600_000.0
}
