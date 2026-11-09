package io.fuelmate.core.station

import io.fuelmate.core.energy.Efficiency
import io.fuelmate.core.energy.EfficiencyStatus
import io.fuelmate.core.energy.MyEfficiency
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
 * @property efficiency 우회 기름값에 쓴 연비. 내 운행 기록이 쌓이면 내 차 값, 아니면 가정값([MyEfficiency])
 */
data class Recommendation(
    val best: StationOption,
    val baseline: StationOption,
    val fillL: Double,
    val candidates: List<StationOption>,
    val aheadPoint: GeoPoint,
    val efficiency: Efficiency = ASSUMED,
) {
    val worthDetour: Boolean get() = best.station.id != baseline.station.id

    /** 화면에 보일 후보(이득 큰 순 5곳) */
    val options: List<StationOption> get() = candidates.take(5)

    /**
     * 경유지로 고른 주유소를 추천과 비교한다. 지금 후보에 없으면(다시 찾은 뒤 우회 한도 밖 등) null.
     * [actualDetourM]은 내비가 다시 계산한 실제 우회다. 있으면 이득을 그 값으로 다시 계산한다.
     */
    fun chosen(stationId: String, actualDetourM: Double? = null): ChosenOption? {
        val found = candidates.firstOrNull { it.station.id == stationId } ?: return null
        val isBest = found.station.id == best.station.id
        val lessThanBest = if (isBest) 0.0 else max(0.0, best.gainWon - found.gainWon)
        val o = if (actualDetourM == null) found else found.copy(
            detourM = actualDetourM,
            gainWon = StationRecommender.gainWon(found.station, baseline, actualDetourM, fillL, efficiency.lPerKm),
        )
        return ChosenOption(o, isBest, lessThanBest, actualDetour = actualDetourM != null)
    }

    private companion object {
        val ASSUMED = Efficiency(MyEfficiency.ASSUMED_KM_PER_L, EfficiencyStatus.LEARNING)
    }
}

/**
 * 사용자가 경유지로 고른 주유소.
 * @property lessThanBestWon 추천([Recommendation.best])을 골랐을 때보다 덜 아끼는 돈(둘 다 추정 우회로 비교). 추천을 골랐거나 고른 곳이 더 나으면 0
 * @property actualDetour [option]의 우회·이득이 내비가 다시 계산한 실제 우회 기준인가
 */
data class ChosenOption(val option: StationOption, val isBest: Boolean, val lessThanBestWon: Double, val actualDetour: Boolean = false)

/**
 * "실제 이득" 주유소 추천(docs/pivot-fuel.md 12장 A안). 순수 계산이라 단위 테스트로 확인한다.
 * 이득 = (기준 가격 − 후보 가격) × 넣을 양 − (후보 우회 − 기준 우회) ÷ 내 차 연비 × 기준 가격
 * 우회 거리는 직선거리 추정이다. 경유지를 넣으면 내비가 실제 우회 거리를 알려 준다(docs/pivot-fuel.md 6장).
 */
object StationRecommender {
    /** 목적지 쪽 앞쪽 지점까지의 거리. 반경 검색(최대 5km)을 이 지점에서 한 번 더 한다 */
    const val AHEAD_M = 5_000.0
    /** 직선거리 → 도로 거리 보정 */
    const val ROAD_FACTOR = 1.3
    /** 이보다 적게 아끼면 굳이 돌아가지 않는다(가까운 곳 추천) */
    const val MIN_GAIN_WON = 300.0
    /** 우회가 이보다 크면 후보에서 뺀다(되돌아가는 곳) */
    const val MAX_DETOUR_M = 6_000.0

    /** 내비에 넣은 목적지 기준으로 추천한다. 목적지가 없으면 "가는 길"을 모르므로 추천하지 않는다 */
    fun recommend(state: AppState): Recommendation? {
        val destination = state.destination ?: return null
        val search = state.stationSearch ?: return null
        val here = state.location ?: return null
        val energy = state.energy?.takeIf { it.kind == EnergyKind.FUEL } ?: return null
        val capacity = energy.capacity ?: return null
        val amount = energy.amount ?: return null
        val ahead = aheadPoint(here.point, destination)
        val efficiency = MyEfficiency.of(state.trips)
        return recommend(search.stations, here.point, ahead, fillL = max(0.0, capacity - amount), efficiency)
    }

    /** [efficiency]를 안 주면 가정값(약 11km/L)으로 계산한다 */
    fun recommend(
        stations: List<Station>,
        here: GeoPoint,
        ahead: GeoPoint,
        fillL: Double,
        efficiency: Efficiency = MyEfficiency.of(emptyList()),
    ): Recommendation? {
        if (stations.isEmpty() || fillL <= 0) return null
        val direct = GeoMath.distanceM(here, ahead)
        val withDetour = stations.map { s ->
            val via = GeoMath.distanceM(here, s.point) + GeoMath.distanceM(s.point, ahead)
            s to max(0.0, via - direct) * ROAD_FACTOR
        }.filter { it.second <= MAX_DETOUR_M }
        if (withDetour.isEmpty()) return null

        val (baseStation, baseDetour) = withDetour.minBy { it.second }
        val base = StationOption(baseStation, baseDetour, 0.0)
        val options = withDetour.map { (s, detour) ->
            StationOption(s, detour, gainWon(s, base, detour, fillL, efficiency.lPerKm))
        }.sortedByDescending { it.gainWon }
        val baseline = options.first { it.station.id == baseStation.id }
        val top = options.first()
        val best = if (top.gainWon >= MIN_GAIN_WON) top else baseline
        return Recommendation(best, baseline, fillL, options, ahead, efficiency)
    }

    /** 이득 = (기준 가격 − 후보 가격) × 넣을 양 − (후보 우회 − 기준 우회) × 우회 km당 연료(L) × 기준 가격 */
    fun gainWon(station: Station, baseline: StationOption, detourM: Double, fillL: Double, lPerKm: Double): Double {
        val basePrice = baseline.station.priceWonPerL
        val saved = (basePrice - station.priceWonPerL) * fillL
        val extraDetourCost = (detourM - baseline.detourM) / 1000 * lPerKm * basePrice
        return saved - extraDetourCost
    }

    /**
     * 목적지가 [AHEAD_M]보다 가까우면 목적지, 멀면 목적지 쪽 직선으로 [AHEAD_M].
     * NaviHelper에는 경로 선형을 주는 API가 없어 실제 도로를 따라갈 수 없다(docs/pleos/navihelper-sdk.md).
     */
    fun aheadPoint(here: GeoPoint, destination: GeoPoint): GeoPoint {
        val d = GeoMath.distanceM(here, destination)
        return if (d <= AHEAD_M) destination else GeoMath.destination(here, GeoMath.bearingDeg(here, destination), AHEAD_M)
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
    /** 목적지가 이만큼 넘게 바뀌면 바로 다시 찾는다 */
    const val DESTINATION_CHANGED_M = 500.0
    const val STALE_MS = 15 * 60 * 1000L

    fun shouldSearch(state: AppState, nowMs: Long): Boolean {
        val here = state.location ?: return false
        val destination = state.destination ?: return false
        val energy = state.energy?.takeIf { it.kind == EnergyKind.FUEL } ?: return false
        val percent = energy.percent ?: return false
        if (percent > SEARCH_BELOW_PERCENT) return false
        val last = state.stationSearch ?: return true
        val searchedFor = last.destination
        if (searchedFor == null || GeoMath.distanceM(searchedFor, destination) >= DESTINATION_CHANGED_M) return true
        return GeoMath.distanceM(last.fromPoint, here.point) >= MOVED_M || nowMs - last.timeMs >= STALE_MS
    }
}
