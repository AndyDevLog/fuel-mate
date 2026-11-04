package io.fuelmate.ui

import io.fuelmate.core.cost.HabitBaseline
import io.fuelmate.core.cost.HabitCostEstimator
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.station.Recommendation
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.StationRecommender
import io.fuelmate.core.station.StationSearchRule
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 화면이 그릴 것. 상태(AppState)에서 문구만 뽑은 값이라, 같으면 다시 그리지 않는다(CLAUDE.md: 상태가 바뀔 때만 다시 그린다).
 * 금액은 추정이라 "약"을 붙인다(HabitText.won).
 */
sealed interface ScreenModel {

    /** 주행 중: 큰 글자 한 줄 + 보조 한 줄 + 경유지 추가 하나(docs/pleos/driving-restriction.md) */
    data class Driving(
        val main: String,
        /** LIMIT_CONTENT 규제가 켜지면 null(한 줄만) */
        val sub: String?,
        val addWaypoint: Station?,
    ) : ScreenModel

    /** 정차 중: 추천 카드, 이번 운행 요약, 계산 방법 */
    data class Parked(
        val fuelLine: String,
        val rec: RecCard,
        val trip: TripCard?,
        val priceLine: String,
    ) : ScreenModel

    data class RecCard(
        val title: String,
        /** 추천이 없을 때 이유만 보인다 */
        val message: String?,
        val name: String? = null,
        val priceLine: String? = null,
        val gainLine: String? = null,
        val gainPositive: Boolean = false,
        val basisLine: String? = null,
        val station: Station? = null,
        val others: List<OtherOption> = emptyList(),
        val waypointLine: String? = null,
    )

    data class OtherOption(val station: Station, val line: String)

    data class TripCard(val title: String, val summary: String, val detail: String, val explanation: List<String>)

    companion object {
        fun of(s: AppState): ScreenModel = if (s.drive.restricted) driving(s) else parked(s)

        private fun driving(s: AppState): Driving {
            val limit = s.drive.limitContent
            val short = s.drive.limitStringLength || limit
            val w = s.waypoint
            if (w != null) {
                val main = "${shortName(w.stationName, short)} ${if (w.asDestination) "안내 중" else "경유 중"}"
                val sub = w.detourM?.let { "실제 우회 ${km(it)}" } ?: if (w.added) "경유지 추가됨" else null
                return Driving(main, sub.takeUnless { limit }, null)
            }
            val r = StationRecommender.recommend(s)
            if (r == null) {
                val fuel = s.energy?.percent?.let { "연료 ${it.roundToInt()}%" } ?: "연료 모름"
                return Driving(fuel, noRecReason(s).takeUnless { limit }, null)
            }
            val b = r.best
            val main = "${shortName(b.station.name, short)} ${price(b.station.priceWonPerL)}"
            val sub = if (r.worthDetour) "${HabitText.won(b.gainWon)} 이득 · 우회 ${km(b.detourM)}" else "가까운 곳 · 차이 작음"
            return Driving(main, sub.takeUnless { limit }, b.station)
        }

        private fun parked(s: AppState): Parked {
            val e = s.energy
            val fuelLine = if (e == null) "연료 모름" else buildString {
                append("${e.kind.label} ${e.percent?.roundToInt() ?: "?"}%")
                e.amount?.let { append(" · ${"%.1f".format(it)} / ${"%.0f".format(e.capacity ?: 0.0)} ${e.kind.unit}") }
                if (e.origin == EnergyOrigin.DEMO) append(" (데모)")
            }
            val kind = e?.kind ?: EnergyKind.FUEL
            val priceLine = "단가 ${"%,.0f".format(s.prices.of(kind))}원/${kind.unit} · ${s.prices.basis(kind)}"
            return Parked(fuelLine, recCard(s), tripCard(s), priceLine)
        }

        private fun recCard(s: AppState): RecCard {
            val r = StationRecommender.recommend(s)
                ?: return RecCard(title = "가는 길 추천 주유소", message = noRecReason(s))
            return RecCard(
                title = "가는 길 추천 주유소",
                message = null,
                name = shortName(r.best.station.name, false),
                priceLine = "${price(r.best.station.priceWonPerL)}/L · 우회 약 ${km(r.best.detourM)}",
                gainLine = if (r.worthDetour) "가까운 곳보다 ${HabitText.won(r.best.gainWon)} 이득" else "가까운 곳에서 넣어도 차이가 작아요",
                gainPositive = r.worthDetour,
                basisLine = basis(r),
                station = r.best.station,
                others = r.options.filter { it.station.id != r.best.station.id }.take(2).map {
                    OtherOption(
                        it.station,
                        "${shortName(it.station.name, false)} ${price(it.station.priceWonPerL)} · 우회 ${km(it.detourM)} · " +
                            (if (it.gainWon >= 0) HabitText.won(it.gainWon) else "-${HabitText.won(abs(it.gainWon))}"),
                    )
                },
                waypointLine = s.waypoint?.let { w ->
                    when {
                        w.asDestination -> "내비: ${shortName(w.stationName, false)}로 안내 중"
                        w.detourM != null -> "내비: ${shortName(w.stationName, false)} 경유 · 실제 우회 ${km(w.detourM!!)}"
                        w.added -> "내비: ${shortName(w.stationName, false)} 경유 · 실제 우회 거리는 확인하지 못함"
                        else -> "내비: ${shortName(w.stationName, false)} 경유지 추가 중"
                    }
                },
            )
        }

        private fun basis(r: Recommendation) =
            "가득 ${"%.0f".format(r.fillL)}L 기준 · 비교: ${shortName(r.baseline.station.name, false)} ${price(r.baseline.station.priceWonPerL)}"

        private fun tripCard(s: AppState): TripCard? {
            val live = s.trip
            val trip = live ?: s.trips.firstOrNull() ?: return null
            val cost = HabitCostEstimator.estimate(trip, s.prices) ?: return null
            val comparison = HabitBaseline.compare(trip, s.trips, s.prices)
            val minutes = trip.durationMs / 60_000
            return TripCard(
                title = if (live != null) "지금 운행" else "지난 운행",
                summary = HabitText.summary(cost, comparison),
                detail = "${"%.1f".format(trip.distanceM / 1000)}km · ${if (minutes > 0) "${minutes}분" else "${trip.durationMs / 1000}초"} · " +
                    "사용 ${"%.2f".format(trip.energyUsed)}${trip.energyKind?.unit ?: ""}",
                explanation = HabitText.explanation(cost, comparison),
            )
        }

        private fun noRecReason(s: AppState): String {
            val percent = s.energy?.percent
            return when {
                s.energy?.kind == EnergyKind.ELECTRIC -> "충전소 추천은 준비 중이에요"
                percent != null && percent > StationSearchRule.SEARCH_BELOW_PERCENT -> "연료가 넉넉해요"
                s.location == null -> "위치를 기다리는 중"
                s.stationSearch == null -> "주유소를 찾는 중"
                else -> "가는 길에 주유소가 없어요"
            }
        }

        private fun price(won: Double) = "${"%,d".format(won.roundToInt())}원"
        private fun km(m: Double) = "${"%.1f".format(m / 1000)}km"

        /** 법인 표기를 빼서 이름을 짧게. 주행 중 글자 수 제한(LIMIT_STRING_LENGTH)이면 12자에서 자른다 */
        fun shortName(name: String, limit: Boolean): String {
            val clean = name.replace(Regex("""\(주\)|㈜|\(유\)|\(합\)|주식회사|유한회사"""), "").trim()
            return if (limit && clean.length > 12) clean.take(11) + "…" else clean
        }
    }
}
