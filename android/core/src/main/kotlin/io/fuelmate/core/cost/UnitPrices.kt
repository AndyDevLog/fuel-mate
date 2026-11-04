package io.fuelmate.core.cost

import io.fuelmate.core.model.EnergyKind

/**
 * 공공 충전요금(기후에너지환경부, 2026-08-01 시행 5단계). 공공 충전기와 협약 민간 충전기의 기후부 회원카드 결제에 적용되고,
 * 민간 사업자 자체 요금은 다르다. 값은 보도(2026-07-01 확정 발표) 기준이고 공식 고시 원문은 확인 필요(docs/progress.md).
 * 공공 API가 없어서 사용자가 고른다.
 */
enum class ChargeTier(val label: String, val wonPerKWh: Double) {
    SLOW("완속(30kW 미만)", 295.0),
    FAST_30("급속 30~50kW", 307.2),
    FAST_50("급속 50~100kW", 325.6),
    FAST_100("급속 100~200kW", 348.4),
    ULTRA("초급속 200kW 이상", 393.1),
}

/**
 * 에너지 단가(원).
 * - 연료: 오피넷 전국 주유소 평균가격(휘발유). [fuelTradeDate]가 null이면 아직 못 받은 것이고 가정값 [ASSUMED_FUEL_WON]을 쓴다
 * - 전기: 사용자가 고른 공공 충전요금 단계([chargeTier]). 기본은 급속 100~200kW
 *
 * 경유·LPG 차는 아직 구분하지 않는다(휘발유 가격과 휘발유 발열량으로 계산한다).
 */
data class UnitPrices(
    val fuelWonPerL: Double = ASSUMED_FUEL_WON,
    /** 오피넷 기준일(yyyyMMdd). null = 가정값 */
    val fuelTradeDate: String? = null,
    val chargeTier: ChargeTier = ChargeTier.FAST_100,
) {
    val electricWonPerKWh: Double get() = chargeTier.wonPerKWh
    val fuelAssumed: Boolean get() = fuelTradeDate == null

    fun of(kind: EnergyKind) = when (kind) {
        EnergyKind.FUEL -> fuelWonPerL
        EnergyKind.ELECTRIC -> electricWonPerKWh
    }

    /** 단가 근거 한 줄. 예: "오피넷 전국 평균 휘발유(2026-11-04)", "공공 충전요금 급속 100~200kW" */
    fun basis(kind: EnergyKind): String = when (kind) {
        EnergyKind.FUEL -> fuelTradeDate?.let { "오피넷 전국 평균 휘발유(${it.take(4)}-${it.drop(4).take(2)}-${it.drop(6)})" }
            ?: "가정 단가(오피넷 값을 아직 못 받음)"
        EnergyKind.ELECTRIC -> "공공 충전요금 ${chargeTier.label}"
    }

    companion object {
        /** docs/pivot-fuel.md 13-3 어림 계산과 같은 값 */
        const val ASSUMED_FUEL_WON = 1_700.0
        val ASSUMED = UnitPrices()
    }
}

/** 오피넷 전국 평균가격 응답 한 번. 원/L */
data class AvgFuelPrices(
    val tradeDate: String,
    val gasoline: Double?,
    val premiumGasoline: Double?,
    val diesel: Double?,
    val autoLpg: Double?,
)
