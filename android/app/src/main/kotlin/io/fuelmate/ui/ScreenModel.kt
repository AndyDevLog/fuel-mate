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
import io.fuelmate.core.station.WaypointState
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
        /** 경유지로 고른 곳이 추천과 다를 때: 추천과 비교한 한 줄 */
        val compareLine: String? = null,
        /** [경유지 추가] 큰 버튼이 넣을 곳. 이미 경유지가 있으면 null(버튼 숨김) */
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
            val w = s.waypoint
            val r = StationRecommender.recommend(s)
            val waypointLine = w?.let {
                when {
                    it.asDestination -> "내비: ${shortName(it.stationName, false)}로 안내 중"
                    it.detourM != null -> "내비: ${shortName(it.stationName, false)} 경유 · 실제 우회 ${km(it.detourM!!)}"
                    it.added -> "내비: ${shortName(it.stationName, false)} 경유 · 실제 우회 거리는 확인하지 못함"
                    else -> "내비: ${shortName(it.stationName, false)} 경유지 추가 중"
                }
            }
            if (w != null) return chosenCard(w, r, waypointLine)
            r ?: return RecCard(title = "가는 길 추천 주유소", message = noRecReason(s))
            return RecCard(
                title = "가는 길 추천 주유소",
                message = null,
                name = shortName(r.best.station.name, false),
                priceLine = "${price(r.best.station.priceWonPerL)}/L · 우회 약 ${km(r.best.detourM)}",
                gainLine = if (r.worthDetour) "가까운 곳보다 ${HabitText.won(r.best.gainWon)} 이득" else "가까운 곳에서 넣어도 차이가 작아요",
                gainPositive = r.worthDetour,
                basisLine = basis(r),
                station = r.best.station,
                others = others(r, exclude = r.best.station.id),
            )
        }

        /**
         * 경유지를 넣은 뒤: 카드는 고른 곳 기준. 추천과 다르면 추천보다 얼마나 덜 아끼는지 한 줄 붙이고, 추천은 "다른 곳"으로 내린다.
         * 경유지를 또 넣으면 내비에 경유지가 늘어나므로 큰 [경유지 추가] 버튼은 숨긴다.
         */
        private fun chosenCard(w: WaypointState, r: Recommendation?, waypointLine: String?): RecCard {
            val title = if (w.asDestination) "안내 중인 주유소" else "경유 중인 주유소"
            val c = r?.chosen(w.stationId)
            if (r == null || c == null) {
                // 다시 찾은 뒤 후보에서 빠졌거나 아직 검색 전: 이름과 내비 상태만
                return RecCard(
                    title = title,
                    message = null,
                    name = shortName(w.stationName, false),
                    waypointLine = waypointLine,
                    others = r?.let { others(it, exclude = w.stationId) } ?: emptyList(),
                )
            }
            val o = c.option
            // 이득 계산과 같은 추정 우회를 쓴다. 내비가 준 실제 우회는 값이 이상할 때가 있어(넣기 전·후 거리가 같게 옴) 내비 줄에만 보인다
            val detour = "우회 약 ${km(o.detourM)}"
            val gainLine = when {
                o.gainWon >= 10 -> "가까운 곳보다 ${HabitText.won(o.gainWon)} 이득"
                o.gainWon <= -10 -> "가까운 곳보다 ${HabitText.won(-o.gainWon)} 더 들어요"
                else -> "가까운 곳과 비슷해요"
            }
            val compareLine = if (!c.isBest && c.lessThanBestWon >= 10)
                "추천 ${shortName(r.best.station.name, false)}보다 ${HabitText.won(c.lessThanBestWon)} 덜 아껴요"
            else null
            return RecCard(
                title = title,
                message = null,
                name = shortName(o.station.name, false),
                priceLine = "${price(o.station.priceWonPerL)}/L · $detour",
                gainLine = gainLine,
                gainPositive = o.gainWon >= 10,
                basisLine = basis(r),
                compareLine = compareLine,
                station = null,
                others = others(r, exclude = o.station.id),
                waypointLine = waypointLine,
            )
        }

        private fun others(r: Recommendation, exclude: String) =
            r.options.filter { it.station.id != exclude }.take(2).map {
                OtherOption(
                    it.station,
                    (if (it.station.id == r.best.station.id && r.worthDetour) "추천 " else "") +
                        "${shortName(it.station.name, false)} ${price(it.station.priceWonPerL)} · 우회 ${km(it.detourM)} · " +
                        (if (it.gainWon >= 0) HabitText.won(it.gainWon) else "-${HabitText.won(abs(it.gainWon))}"),
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
