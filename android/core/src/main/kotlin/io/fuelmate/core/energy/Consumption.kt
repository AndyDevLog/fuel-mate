package io.fuelmate.core.energy

import io.fuelmate.core.model.EnergyKind

/**
 * 차 제원 가정값. 데모 소비 계산에 쓰고, 다음 단계(운전 습관 비용 추정)에서도 같은 값을 쓴다.
 * 차 무게는 서드파티 권한으로 읽을 수 없어서(`VEHICLE_CURB_WEIGHT`는 PRIVILEGED) 가정값이다(docs/pivot-fuel.md 13-2).
 *
 * @property cdA 공기저항계수 × 정면 면적(m²)
 * @property drivetrainEfficiency 에너지원(연료·배터리) → 바퀴 효율
 * @property regenEfficiency 감속 에너지 가운데 배터리로 돌아오는 몫. 연료차는 0
 * @property idleKw 차가 켜져 있으면 늘 쓰는 에너지원 전력. 전기: 보조 장치, 연료: 공회전 연료(L/h × 발열량)
 * @property capacity 연료 탱크(L) 또는 배터리(kWh)
 */
data class VehicleParams(
    val kind: EnergyKind,
    val massKg: Double,
    val cdA: Double,
    val rollingCoef: Double,
    val drivetrainEfficiency: Double,
    val regenEfficiency: Double,
    val idleKw: Double,
    val maxWheelKw: Double,
    val capacity: Double,
) {
    companion object {
        /**
         * 바퀴 출력 상한. 실차 출력(150~240kW)으로 자르지 않는다: 에뮬레이터는 실차보다 빨리 가속해서(최대 약 5.6m/s²,
         * 고속에서 300kW 넘음) 실차 값으로 자르면 쓴 에너지가 운동 에너지보다 작게 나오고, 습관 비용(운동 에너지로 계산)이
         * 운행 소비보다 커지는 모순이 생긴다(2026-11-04 확인). 속도 기록에 맞는 에너지가 나오게 넉넉히 둔다
         */
        private const val DEMO_MAX_WHEEL_KW = 500.0

        /** 에뮬레이터 차(배터리 77.4kWh, 길이 4,950mm, 폭 2,008mm)에 맞춘 전기 SUV. 탑승자 포함 무게 */
        val DEMO_EV = VehicleParams(
            kind = EnergyKind.ELECTRIC, massKg = 2_100.0, cdA = 0.75, rollingCoef = 0.010,
            drivetrainEfficiency = 0.88, regenEfficiency = 0.60, idleKw = 0.6, maxWheelKw = DEMO_MAX_WHEEL_KW, capacity = 77.4,
        )

        /**
         * 2.5L 휘발유 대형 세단. 엔진·구동 효율 25%, 공회전 시간당 약 0.8L(미국 에너지부 범위 0.95~1.9L/h의 아래쪽,
         * docs/pivot-fuel.md 13-3) → 0.8 × [Consumption.GASOLINE_KWH_PER_L] ≈ 7.1kW
         */
        val DEMO_FUEL = VehicleParams(
            kind = EnergyKind.FUEL, massKg = 1_750.0, cdA = 0.70, rollingCoef = 0.010,
            drivetrainEfficiency = 0.25, regenEfficiency = 0.0, idleKw = 0.8 * Consumption.GASOLINE_KWH_PER_L,
            maxWheelKw = DEMO_MAX_WHEEL_KW, capacity = 60.0,
        )

        /** 실제 차 제원을 읽을 수 없어서 종류별 가정값을 쓴다(데모 소비와 습관 비용 추정이 같은 값) */
        fun assumedFor(kind: EnergyKind) = when (kind) {
            EnergyKind.ELECTRIC -> DEMO_EV
            EnergyKind.FUEL -> DEMO_FUEL
        }
    }
}

/**
 * 속도 변화로 쓴 에너지를 계산하는 단순 물리 모델: 바퀴 출력 = 가속 + 구름저항 + 공기저항.
 * 에뮬레이터는 달려도 잔량이 줄지 않아서(docs/pivot-fuel.md 9장) 데모 공급원이 이 값으로 잔량을 줄인다.
 * 우리가 정한 가정값으로 만든 소비라 **시연용**이다. 실제 소비 추정이 맞는지 확인하는 데는 쓸 수 없다.
 */
object Consumption {
    const val AIR_DENSITY = 1.2
    const val G = 9.81

    /** 휘발유 저위발열량 약 32MJ/L ÷ 3.6 */
    const val GASOLINE_KWH_PER_L = 8.9

    /**
     * [dtS]초 동안 속도가 [v0] → [v1](m/s)로 바뀔 때 에너지원에서 쓴 양. 연료 L, 전기 kWh.
     * 음수는 회생제동으로 되찾은 양이다. [poweredOn]이 아니고 서 있으면(P단) 0이다.
     */
    fun step(p: VehicleParams, dtS: Double, v0: Double, v1: Double, poweredOn: Boolean): Double {
        if (dtS <= 0) return 0.0
        val v = (v0 + v1) / 2
        val a = (v1 - v0) / dtS
        val wheelW = p.massKg * a * v +
            p.massKg * G * p.rollingCoef * v +
            0.5 * AIR_DENSITY * p.cdA * v * v * v
        val wheelKw = (wheelW / 1000).coerceAtMost(p.maxWheelKw)
        val driveKw = if (wheelKw >= 0) wheelKw / p.drivetrainEfficiency else wheelKw * p.regenEfficiency
        val idle = if (poweredOn || v > 0) p.idleKw else 0.0
        return toSourceAmount(p, (driveKw + idle) * dtS / 3600)
    }

    /** 에너지원에서 나간 kWh → 연료 L(휘발유 발열량) 또는 그대로 kWh */
    fun toSourceAmount(p: VehicleParams, kWh: Double): Double =
        if (p.kind == EnergyKind.FUEL) kWh / GASOLINE_KWH_PER_L else kWh
}
