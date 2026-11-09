package io.fuelmate.core.energy

import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.Trip

/**
 * 돌아가는 길의 기름값에 쓰는 연비.
 *
 * @property kmPerL 계산에 쓰는 값. [status]가 [EfficiencyStatus.MINE]이면 내 운행 기록, 아니면 가정값([MyEfficiency.ASSUMED_KM_PER_L])
 * @property recordKm 연비를 낼 때 본 운행 거리 합(km). 배우는 중이면 지금까지 쌓인 거리
 * @property recordL 그동안 쓴 연료 합(L)
 * @property recordKmPerL 기록으로 낸 값. 기록이 없거나 쓴 연료가 0이면 null
 */
data class Efficiency(
    val kmPerL: Double,
    val status: EfficiencyStatus,
    val recordKm: Double = 0.0,
    val recordL: Double = 0.0,
    val recordKmPerL: Double? = null,
) {
    val lPerKm: Double get() = 1.0 / kmPerL
    val mine: Boolean get() = status == EfficiencyStatus.MINE
}

enum class EfficiencyStatus {
    /** 내 운행 기록으로 낸 값을 쓴다 */
    MINE,
    /** 기록이 [MyEfficiency.MIN_KM]보다 적어 가정값을 쓴다 */
    LEARNING,
    /** 기록은 충분하지만 값이 차가 낼 수 없는 범위라 가정값을 쓴다(연료값 오류, 기록 중 주유 등) */
    ODD,
}

/**
 * "내 차 연비": 지난 [WINDOW_DAYS]일 연료차 운행의 거리 합 ÷ 쓴 연료 합.
 *
 * 실차 연료값은 1% 단위 정수라(60L 탱크면 0.6L) 운행 하나로는 0 또는 1%로 튄다. 운행을 합쳐 [MIN_KM]이 넘을 때만 쓴다
 * (약 11km/L 차가 30km 달리면 약 2.7L, 4~5칸). 짧은 운행에서 안 잡힌 소비는 다음 운행에 잡히므로 합치면 평균이 맞아 간다.
 * 연비 API(`Fuel.getCumulativeFuelEconomy` 등)는 실차 지원·단위를 모르고 에뮬레이터에서 실패해 쓰지 않는다(docs/pivot-fuel.md 2장).
 *
 * 기간은 가장 최근 운행부터 센다. 한동안 안 달려도 마지막 기록을 그대로 쓴다.
 * 운행 기록에 정차·공회전이 다 들어 있어서, 시내에서 잠깐 돌아가는 우회 비용에 맞는 값이다.
 */
object MyEfficiency {
    const val WINDOW_DAYS = 28
    const val MIN_KM = 30.0
    /** 기존 가정값(2.5L 가솔린 세단 복합 연비 수준, docs/pivot-fuel.md 12장) */
    const val ASSUMED_KM_PER_L = 11.0
    /** 이 범위를 벗어나면 기록이 잘못된 것으로 본다. 하이브리드 실연비 20km/L대까지 넉넉히 둔다 */
    const val MIN_PLAUSIBLE_KM_PER_L = 4.0
    const val MAX_PLAUSIBLE_KM_PER_L = 30.0

    private const val DAY_MS = 86_400_000L

    /** [trips]는 새 것이 앞(AppState.trips 순서)이어도, 아니어도 된다 */
    fun of(trips: List<Trip>): Efficiency {
        val fuel = trips.filter { it.energyKind == EnergyKind.FUEL }
        if (fuel.isEmpty()) return Efficiency(ASSUMED_KM_PER_L, EfficiencyStatus.LEARNING)
        val newest = fuel.maxOf { it.startWallMs }
        val recent = fuel.filter { newest - it.startWallMs <= WINDOW_DAYS * DAY_MS }
        val km = recent.sumOf { it.distanceM } / 1000
        val liters = recent.sumOf { it.energyUsed }
        val record = if (liters > 0) km / liters else null
        val status = when {
            km < MIN_KM -> EfficiencyStatus.LEARNING
            record == null || record !in MIN_PLAUSIBLE_KM_PER_L..MAX_PLAUSIBLE_KM_PER_L -> EfficiencyStatus.ODD
            else -> EfficiencyStatus.MINE
        }
        val use = if (status == EfficiencyStatus.MINE && record != null) record else ASSUMED_KM_PER_L
        return Efficiency(use, status, km, liters, record)
    }
}
