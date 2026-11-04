package io.fuelmate.core.station

import io.fuelmate.core.geo.GeoMath
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.GeoPoint
import kotlin.math.max

/**
 * @property detourM 추정 우회 거리(m): 지금 위치 → 주유소 → 앞쪽 지점 − 지금 위치 → 앞쪽 지점, × 도로 보정
 * @property gainWon 비교 기준(우회가 가장 적은 주유소)에서 넣을 때보다 아끼는 돈. 음수면 손해
 */
data class StationOption(val station: Station, val detourM: Double, val gainWon: Double)

/**
 * @property best 추천. 이득이 [StationRecommender.MIN_GAIN_WON]보다 작으면 비교 기준과 같다(가까운 곳에서 넣어도 된다)
 * @property baseline 비교 기준: 우회가 가장 적은 주유소("그냥 가까운 데서 넣었다면")
 * @property fillL 넣을 양: 탱크를 가득 채운다고 본다
 * @property candidates 우회 한도 안의 모든 후보(이득 큰 순). 화면 목록은 [options]
 */
data class Recommendation(
    val best: StationOption,
    val baseline: StationOption,
    val fillL: Double,
    val candidates: List<StationOption>,
    val aheadPoint: GeoPoint,
) {
    val worthDetour: Boolean get() = best.station.id != baseline.station.id

    /** 화면에 보일 후보(이득 큰 순 5곳) */
    val options: List<StationOption> get() = candidates.take(5)

    /** 경유지로 고른 주유소를 추천과 비교한다. 지금 후보에 없으면(다시 찾은 뒤 우회 한도 밖 등) null */
    fun chosen(stationId: String): ChosenOption? {
        val o = candidates.firstOrNull { it.station.id == stationId } ?: return null
        val isBest = o.station.id == best.station.id
        return ChosenOption(o, isBest, if (isBest) 0.0 else max(0.0, best.gainWon - o.gainWon))
    }
}

/**
 * 사용자가 경유지로 고른 주유소.
 * @property lessThanBestWon 추천([Recommendation.best])을 골랐을 때보다 덜 아끼는 돈. 추천을 골랐거나 고른 곳이 더 나으면 0
 */
data class ChosenOption(val option: StationOption, val isBest: Boolean, val lessThanBestWon: Double)

/**
 * "실제 이득" 주유소 추천(docs/pivot-fuel.md 12장 A안). 순수 계산이라 단위 테스트로 확인한다.
 * 이득 = (기준 가격 − 후보 가격) × 넣을 양 − (후보 우회 − 기준 우회) × 우회 연비 × 기준 가격
 * 우회 거리는 직선거리 추정이다. 경유지를 넣으면 내비가 실제 우회 거리를 알려 준다(docs/pivot-fuel.md 6장).
 */
object StationRecommender {
    /** 진행 방향 앞쪽 지점까지의 거리. 반경 검색(최대 5km)을 이 지점에서 한 번 더 한다 */
    const val AHEAD_M = 5_000.0
    /** 직선거리 → 도로 거리 보정 */
    const val ROAD_FACTOR = 1.3
    /** 우회 주행 연비 가정: 약 11km/L(2.5L 가솔린 세단 복합 연비 수준, 가정값) */
    const val DETOUR_L_PER_KM = 0.09
    /** 이보다 적게 아끼면 굳이 돌아가지 않는다(가까운 곳 추천) */
    const val MIN_GAIN_WON = 300.0
    /** 우회가 이보다 크면 후보에서 뺀다(되돌아가는 곳) */
    const val MAX_DETOUR_M = 6_000.0

    fun recommend(state: AppState): Recommendation? {
        val search = state.stationSearch ?: return null
        val here = state.location ?: return null
        val energy = state.energy?.takeIf { it.kind == EnergyKind.FUEL } ?: return null
        val capacity = energy.capacity ?: return null
        val amount = energy.amount ?: return null
        val ahead = aheadPoint(here.point, here.headingDeg.toDouble(), state.destination)
        return recommend(search.stations, here.point, ahead, fillL = max(0.0, capacity - amount))
    }

    fun recommend(stations: List<Station>, here: GeoPoint, ahead: GeoPoint, fillL: Double): Recommendation? {
        if (stations.isEmpty() || fillL <= 0) return null
        val direct = GeoMath.distanceM(here, ahead)
        val withDetour = stations.map { s ->
            val via = GeoMath.distanceM(here, s.point) + GeoMath.distanceM(s.point, ahead)
            s to max(0.0, via - direct) * ROAD_FACTOR
        }.filter { it.second <= MAX_DETOUR_M }
        if (withDetour.isEmpty()) return null

        val (baseStation, baseDetour) = withDetour.minBy { it.second }
        val basePrice = baseStation.priceWonPerL
        val options = withDetour.map { (s, detour) ->
            val saved = (basePrice - s.priceWonPerL) * fillL
            val extraDetourCost = (detour - baseDetour) / 1000 * DETOUR_L_PER_KM * basePrice
            StationOption(s, detour, saved - extraDetourCost)
        }.sortedByDescending { it.gainWon }
        val baseline = options.first { it.station.id == baseStation.id }
        val top = options.first()
        val best = if (top.gainWon >= MIN_GAIN_WON) top else baseline
        return Recommendation(best, baseline, fillL, options, ahead)
    }

    /** 목적지가 [AHEAD_M]보다 가까우면 목적지, 멀면 목적지 쪽으로 [AHEAD_M], 목적지가 없으면 진행 방향으로 [AHEAD_M] */
    fun aheadPoint(here: GeoPoint, headingDeg: Double, destination: GeoPoint?): GeoPoint {
        if (destination != null) {
            val d = GeoMath.distanceM(here, destination)
            return if (d <= AHEAD_M) destination else GeoMath.destination(here, GeoMath.bearingDeg(here, destination), AHEAD_M)
        }
        return GeoMath.destination(here, headingDeg, AHEAD_M)
    }
}

/**
 * 언제 다시 검색하나. 오피넷 키 하나의 하루 호출 한도(약 1,500건, docs/pivot-fuel.md 5장)를 모든 차가 나눠 쓰므로 아껴 부른다.
 * 한 번 검색 = 2건(지금 위치 + 앞쪽 지점).
 */
object StationSearchRule {
    /** 연료가 이보다 많으면 찾지 않는다 */
    const val SEARCH_BELOW_PERCENT = 50f
    const val MOVED_M = 3_000.0
    const val STALE_MS = 15 * 60 * 1000L

    fun shouldSearch(state: AppState, nowMs: Long): Boolean {
        val here = state.location ?: return false
        val energy = state.energy?.takeIf { it.kind == EnergyKind.FUEL } ?: return false
        val percent = energy.percent ?: return false
        if (percent > SEARCH_BELOW_PERCENT) return false
        val last = state.stationSearch ?: return true
        return GeoMath.distanceM(last.fromPoint, here.point) >= MOVED_M || nowMs - last.timeMs >= STALE_MS
    }
}
