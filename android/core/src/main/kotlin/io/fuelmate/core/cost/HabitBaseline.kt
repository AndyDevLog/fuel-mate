package io.fuelmate.core.cost

import io.fuelmate.core.model.Trip

/**
 * "내 평소 대비". 같은 차·같은 가정끼리 비교라 무게·효율 가정의 오차가 상당 부분 상쇄된다(docs/pivot-fuel.md 13-3).
 *
 * @property baselineWonPer100Km 지난 [HabitBaseline.WINDOW_DAYS]일 운행의 100km당 습관 비용(거리 가중 평균)
 * @property diffWon 이번 운행 거리로 환산한 평소와의 차이. 양수 = 평소보다 더 씀
 */
data class HabitComparison(
    val baselineWonPer100Km: Double,
    val tripWonPer100Km: Double,
    val diffWon: Double,
    val baselineTrips: Int,
)

object HabitBaseline {
    const val WINDOW_DAYS = 28
    /** 기록이 이만큼 쌓이기 전에는 비교하지 않는다(문서: "2주쯤 쌓일 때까지는 숫자를 안 보여 준다") */
    const val MIN_SPAN_DAYS = 14
    const val MIN_BASELINE_KM = 50.0
    /** 짧은 운행은 100km당 값이 크게 튄다 */
    const val MIN_TRIP_KM = 1.0

    private const val DAY_MS = 86_400_000L

    /** [history]에 [trip]이 들어 있어도 된다(시작 시각이 같거나 늦은 운행은 뺀다) */
    fun compare(trip: Trip, history: List<Trip>, prices: UnitPrices = UnitPrices.ASSUMED): HabitComparison? {
        val km = trip.distanceM / 1000
        if (km < MIN_TRIP_KM) return null
        val now = HabitCostEstimator.estimate(trip, prices) ?: return null
        val past = history.filter {
            it.energyKind == trip.energyKind &&
                it.startWallMs < trip.startWallMs &&
                trip.startWallMs - it.startWallMs <= WINDOW_DAYS * DAY_MS
        }
        if (past.isEmpty()) return null
        val span = trip.startWallMs - past.minOf { it.startWallMs }
        val pastKm = past.sumOf { it.distanceM } / 1000
        if (span < MIN_SPAN_DAYS * DAY_MS || pastKm < MIN_BASELINE_KM) return null

        val pastWon = past.sumOf { HabitCostEstimator.estimate(it, prices)?.won ?: 0.0 }
        val base = pastWon / pastKm * 100
        val mine = now.won / km * 100
        return HabitComparison(base, mine, (mine - base) * km / 100, past.size)
    }
}
