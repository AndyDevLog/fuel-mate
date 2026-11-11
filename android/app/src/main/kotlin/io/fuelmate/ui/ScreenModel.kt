package io.fuelmate.ui

import io.fuelmate.core.cost.HabitBaseline
import io.fuelmate.core.cost.HabitCategory
import io.fuelmate.core.cost.HabitCostEstimator
import io.fuelmate.core.cost.HabitTimeline
import io.fuelmate.core.energy.Efficiency
import io.fuelmate.core.energy.EfficiencyStatus
import io.fuelmate.core.energy.MyEfficiency
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.model.LocationOrigin
import io.fuelmate.core.model.PlaceKind
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.model.SavedPlaces
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

    /**
     * 주행 중: 큰 글자 한 줄 + 보조 한 줄 + 경유지 추가 하나(docs/pleos/driving-restriction.md).
     * [cards]가 있으면 정차 화면과 같은 두 열(왼쪽 큰 카드, 오른쪽 연료·운행)로 그리되, 그림은 모두 멈춰 있고 목록·펼치기는 없다.
     */
    data class Driving(
        val main: String,
        /** LIMIT_CONTENT 규제가 켜지면 null(한 줄만) */
        val sub: String?,
        val addWaypoint: Station?,
        /** 큰 글자가 이득 금액이면 초록으로 쓴다 */
        val mainGain: Boolean = false,
        val buttonLabel: String = "여기 들르기",
        /** LIMIT_CONTENT 규제가 켜지면 null(큰 글자 한 줄과 버튼만) */
        val cards: DriveCards? = null,
    ) : ScreenModel

    /** 주행 화면의 카드들. 정차 화면에서 글자를 줄인 것 */
    data class DriveCards(
        /** 왼쪽 큰 카드 제목("가는 길 추천 주유소") */
        val title: String,
        /** 왼쪽 큰 카드의 그림. null이면 빈 칸 */
        val scene: DriveScene?,
        /** 오른쪽 위 연료 카드. 연비·단가 줄은 그리지 않는다 */
        val fuel: FuelCard,
        /** 오른쪽 아래 운행 카드. 금액 한 줄과 설명 한 줄만 */
        val trip: DriveTrip?,
    )

    sealed interface DriveScene {
        /** 경로 그림(정차 화면과 같은 마지막 장면). [chip]은 이득 핀 위 글자 */
        data class Route(val chip: String) : DriveScene
        /** 이득이 없을 때 주유소 핀 하나 */
        data class Pin(val kind: DrivePin) : DriveScene
    }

    enum class DrivePin { PLAIN, ACTIVE }

    data class DriveTrip(val title: String, val costLine: String, val caption: String)

    /** 정차 중: 추천 카드, 이번 운행 요약, 계산 방법 */
    data class Parked(
        val fuel: FuelCard,
        /** 목적지가 없을 때의 첫 화면. 있으면 [rec] 대신 보인다 */
        val start: StartCard?,
        val rec: RecCard,
        val trip: TripCard?,
    ) : ScreenModel

    /** 오른쪽 위 카드: 남은 연료와 오늘 단가 */
    data class FuelCard(
        val title: String,
        val percentText: String,
        /** 연료 통 그림 값(0~100). 모르면 null */
        val percent: Int?,
        val electric: Boolean,
        val detail: String?,
        /** 연료차만: "내 차 연비 12.4km/L · 312km 기록" 또는 배우는 중 진행. 전기차는 null */
        val efficiencyLine: String? = null,
        val priceLine: String,
    )

    /** 첫 화면: 앱 소개 + 목적지 고르기. 정차 중에만 보인다 */
    data class StartCard(val places: List<PlaceRow>)

    data class PlaceRow(val place: SavedPlace, val name: String, val kind: String)

    data class RecCard(
        val title: String,
        /** 추천이 없을 때 이유만 보인다 */
        val message: String?,
        /** 가장 크게 보이는 값: "약 1,680원 이득" */
        val gainLine: String? = null,
        /** 이름 · 가격 · 우회 */
        val placeLine: String? = null,
        val gainPositive: Boolean = false,
        /** 이득이 있을 때 금액이 올라가는 연출의 끝값. 이득이 없으면 null(그림도 숨김). 끝난 뒤에는 [gainLine]을 그대로 보인다 */
        val gainWon: Double? = null,
        /** 이 주유소가 바뀌면 연출을 다시 재생한다 */
        val gainStationId: String? = null,
        val basisLine: String? = null,
        /** 경유지로 고른 곳이 추천과 다를 때: 추천과 비교한 한 줄 */
        val compareLine: String? = null,
        /** 큰 버튼("한강에너지 들르기")이 넣을 곳. 이미 경유지가 있으면 null(버튼 숨김) */
        val station: Station? = null,
        val others: List<OtherOption> = emptyList(),
        /** 큰 버튼 글자. 들르는 곳이 하나뿐이라는 게 보이게 주유소 이름을 넣는다 */
        val buttonLabel: String = "여기 들르기",
        /**
         * "대신 들를 수 있는 곳" 표의 버튼 글자. 추천 대신 그곳 한 곳만 들른다는 뜻으로 "여기로",
         * 경유지가 들어간 뒤에는 "바꾸기"(앞의 경유지를 빼고 넣는다)
         */
        val othersAction: String = "여기로",
        val waypointLine: String? = null,
    )

    /** 다른 곳 표의 한 줄. 열을 맞춰 비교하기 쉽게 값을 나눠 둔다 */
    data class OtherOption(val station: Station, val name: String, val price: String, val detour: String, val gain: String)

    /**
     * 지난 운행(또는 지금 운행) 카드. 큰 금액 + 운행 타임라인 + 항목 수.
     * @property costLine 큰 글자 "약 1,130원". 타임라인 연출 동안은 [timeline]의 금액이 올라가다 이 값에서 멈춘다
     * @property tripKey 운행마다 다른 값(시작 시각). 바뀌면 타임라인을 다시 재생한다
     * @property live 아직 달리는 중인 운행이면 true(타임라인을 움직이지 않는다)
     */
    data class TripCard(
        val title: String,
        val costLine: String,
        val caption: String,
        val timeline: HabitTimeline,
        val tripKey: Long,
        val live: Boolean,
        val brakingCount: Int,
        val accelCount: Int,
        val detail: String,
        val explanation: List<String>,
    )

    companion object {
        fun of(s: AppState): ScreenModel = if (s.drive.restricted) driving(s) else parked(s)

        private fun driving(s: AppState): Driving {
            val limit = s.drive.limitContent
            val short = s.drive.limitStringLength || limit
            // 그림·제목은 정차 카드와 같은 판단(recCard)에서 가져와, P↔D를 오가도 같은 주유소·금액으로 보이게 한다
            val rec = if (limit) null else recCard(s)
            fun cards(scene: DriveScene?) = rec?.let { DriveCards(it.title, scene, fuelCard(s), driveTrip(s)) }
            val gainScene = rec?.let { r -> r.gainLine?.takeIf { r.gainWon != null }?.let { DriveScene.Route(it) } }

            val w = s.waypoint
            if (w != null) {
                val main = "${shortName(w.stationName, short)} ${if (w.asDestination) "안내 중" else "경유 중"}"
                val sub = w.detourM?.let { "실제 우회 ${km(it)}" } ?: if (w.added) "경유지 추가됨" else "내비에 넣는 중"
                return Driving(main, sub.takeUnless { limit }, null, cards = cards(gainScene ?: DriveScene.Pin(DrivePin.ACTIVE)))
            }
            val r = StationRecommender.recommend(s)
            if (r == null) {
                // 운전 중에는 내비를 만지라고 하지 않는다. 목적지가 생기면 앱이 알아서 찾는다는 것만 말한다
                val reason = if (s.destination == null && s.energy?.kind != EnergyKind.ELECTRIC) "목적지가 정해지면 가는 길 주유소를 찾아요" else noRecReason(s)
                if (limit) {
                    // 한 줄만 보일 때는 연료가 가장 먼저 읽을 값이다
                    val percent = s.energy?.percent?.roundToInt()?.coerceIn(0, 100)
                    return Driving(percent?.let { "연료 $it%" } ?: "연료 모름", null, null)
                }
                // 연료는 오른쪽 카드에 있으니 큰 글자는 이유를 쓴다. 그림은 첫 화면과 같은 설명 그림("이득"만, 금액 없음)
                val scene = if (s.energy?.kind == EnergyKind.ELECTRIC) null else DriveScene.Route("이득")
                return Driving(reason, null, null, cards = cards(scene))
            }
            // 한눈에 읽을 값은 이득 금액이라 그것을 큰 글자로, 어디인지는 보조 줄로 쓴다(정차 카드와 같은 순서)
            val b = r.best
            val place = "${shortName(b.station.name, short)} ${price(b.station.priceWonPerL)}"
            return if (r.worthDetour) Driving(
                main = "${HabitText.won(b.gainWon)} 이득",
                sub = "$place · 우회 ${km(b.detourM)}".takeUnless { limit },
                addWaypoint = b.station,
                mainGain = true,
                buttonLabel = visitLabel(b.station.name),
                cards = cards(gainScene),
            ) else Driving(
                main = place,
                sub = "가까운 곳이라 차이가 작아요".takeUnless { limit },
                addWaypoint = b.station,
                buttonLabel = visitLabel(b.station.name),
                cards = cards(DriveScene.Pin(DrivePin.PLAIN)),
            )
        }

        /** 주행 중 운행 카드: 금액과 설명 한 줄. 타임라인·항목 수·계산 방법은 정차 중에만 */
        private fun driveTrip(s: AppState) = tripCard(s)?.let { DriveTrip(it.title, it.costLine, it.caption) }

        /** 다른 곳 표에 보일 수. 넓은 화면의 세로 공간을 채우면서 비교할 후보를 늘린다 */
        private const val OTHERS_SHOWN = 4

        private fun parked(s: AppState): Parked {
            val start = startCard(s)
            return Parked(fuelCard(s), start, if (start == null) recCard(s) else RecCard(title = "", message = null), tripCard(s))
        }

        private fun fuelCard(s: AppState): FuelCard {
            val e = s.energy
            val kind = e?.kind ?: EnergyKind.FUEL
            val percent = e?.percent?.roundToInt()?.coerceIn(0, 100)
            val detail = listOfNotNull(
                e?.amount?.let { "${"%.1f".format(it)} / ${"%.0f".format(e.capacity ?: 0.0)} ${e.kind.unit}" + if (e.origin == EnergyOrigin.DEMO) " (데모)" else "" },
                "위치 데모(직선 이동)".takeIf { s.location?.origin == LocationOrigin.NAVI_SIMULATED },
            ).joinToString(" · ").ifEmpty { null }
            return FuelCard(
                title = kind.label,
                percentText = percent?.let { "$it%" } ?: "모름",
                percent = percent,
                electric = kind == EnergyKind.ELECTRIC,
                detail = detail,
                efficiencyLine = if (kind == EnergyKind.FUEL) efficiencyLine(MyEfficiency.of(s.trips)) else null,
                priceLine = "단가 ${"%,.0f".format(s.prices.of(kind))}원/${kind.unit} · ${s.prices.basis(kind)}",
            )
        }

        /** 목적지·경유지가 없고 연료차일 때(전기차는 충전소 추천이 준비 중이라 그 안내를 보인다) */
        private fun startCard(s: AppState): StartCard? {
            if (s.destination != null || s.waypoint != null || s.energy?.kind == EnergyKind.ELECTRIC) return null
            val rows = SavedPlaces.forStartScreen(s.bookmarks, s.recents).map {
                val kind = when (it.kind) {
                    PlaceKind.HOME -> "집"
                    PlaceKind.OFFICE -> "회사"
                    PlaceKind.BOOKMARK -> "즐겨찾기"
                    PlaceKind.RECENT -> "최근"
                }
                PlaceRow(it, it.name, kind)
            }
            return StartCard(rows)
        }

        private fun recCard(s: AppState): RecCard {
            val w = s.waypoint
            val r = StationRecommender.recommend(s)
            // 이름과 실제 우회는 카드 위쪽에 이미 있어 내비 상태만 말한다
            val waypointLine = w?.let {
                when {
                    it.asDestination -> "내비가 이 주유소로 안내 중이에요"
                    it.detourM != null -> "내비 경유지로 들어갔어요"
                    it.added -> "내비 경유지로 들어갔어요 · 실제 우회 거리는 확인하지 못함"
                    else -> "내비에 경유지를 넣는 중이에요"
                }
            }
            if (w != null) return chosenCard(w, r, waypointLine)
            r ?: return RecCard(title = "가는 길 추천 주유소", message = noRecReason(s))
            return RecCard(
                title = "가는 길 추천 주유소",
                message = null,
                gainLine = if (r.worthDetour) "${HabitText.won(r.best.gainWon)} 이득" else "가까운 곳에서 넣어도 차이가 작아요",
                placeLine = "${shortName(r.best.station.name, false)} · ${price(r.best.station.priceWonPerL)}/L · 우회 약 ${km(r.best.detourM)}",
                gainPositive = r.worthDetour,
                gainWon = r.best.gainWon.takeIf { r.worthDetour },
                gainStationId = r.best.station.id.takeIf { r.worthDetour },
                basisLine = basis(r),
                station = r.best.station,
                buttonLabel = visitLabel(r.best.station.name),
                others = others(r, exclude = r.best.station.id),
            )
        }

        /**
         * 경유지를 넣은 뒤: 카드는 고른 곳 기준. 추천과 다르면 추천보다 얼마나 덜 아끼는지 한 줄 붙이고, 추천은 "다른 곳"으로 내린다.
         * 경유지를 또 넣으면 내비에 경유지가 늘어나므로 큰 버튼은 숨기고, 다른 곳은 [바꾸기]로 바꾼다.
         */
        private fun chosenCard(w: WaypointState, r: Recommendation?, waypointLine: String?): RecCard {
            val title = if (w.asDestination) "안내 중인 주유소" else "경유 중인 주유소"
            val c = r?.chosen(w.stationId, w.detourM)
            if (r == null || c == null) {
                // 다시 찾은 뒤 후보에서 빠졌거나 아직 검색 전: 이름과 내비 상태만
                return RecCard(
                    title = title,
                    message = null,
                    placeLine = shortName(w.stationName, false),
                    waypointLine = waypointLine,
                    others = r?.let { others(it, exclude = w.stationId) } ?: emptyList(),
                )
            }
            val o = c.option
            val detour = if (c.actualDetour) "실제 우회 ${km(o.detourM)}" else "우회 약 ${km(o.detourM)}"
            val gainLine = when {
                o.gainWon >= 10 -> "${HabitText.won(o.gainWon)} 이득"
                o.gainWon <= -10 -> "${HabitText.won(-o.gainWon)} 더 들어요"
                else -> "가까운 곳과 비슷해요"
            }
            // 고른 곳이 실제 우회가 되면 추정 우회인 추천과 맞비교할 수 없어 보이지 않는다
            val compareLine = if (!c.isBest && !c.actualDetour && c.lessThanBestWon >= 10)
                "추천 ${shortName(r.best.station.name, false)}보다 ${HabitText.won(c.lessThanBestWon)} 덜 아껴요"
            else null
            return RecCard(
                title = title,
                message = null,
                gainLine = gainLine,
                placeLine = "${shortName(o.station.name, false)} · ${price(o.station.priceWonPerL)}/L · $detour",
                gainPositive = o.gainWon >= 10,
                gainWon = o.gainWon.takeIf { it >= 10 },
                gainStationId = o.station.id.takeIf { o.gainWon >= 10 },
                basisLine = basis(r),
                compareLine = compareLine,
                station = null,
                others = others(r, exclude = o.station.id),
                othersAction = if (w.added && !w.asDestination) "바꾸기" else "여기로",
                waypointLine = waypointLine,
            )
        }

        private fun others(r: Recommendation, exclude: String) =
            r.options.filter { it.station.id != exclude }.take(OTHERS_SHOWN).map {
                OtherOption(
                    station = it.station,
                    name = (if (it.station.id == r.best.station.id && r.worthDetour) "추천 " else "") + shortName(it.station.name, false),
                    price = price(it.station.priceWonPerL),
                    detour = km(it.detourM),
                    gain = if (it.gainWon >= 0) HabitText.won(it.gainWon) else "-${HabitText.won(abs(it.gainWon))}",
                )
            }

        private fun basis(r: Recommendation) =
            "가까운 ${shortName(r.baseline.station.name, false)} ${price(r.baseline.station.priceWonPerL)}보다 · 가득 ${"%.0f".format(r.fillL)}L · " +
                "${if (r.efficiency.mine) "내 차 연비" else "가정 연비"} ${kmPerL(r.efficiency.kmPerL)} 기준"

        /**
         * 돌아가는 길 기름값에 쓰는 연비. 내 운행 기록이 쌓이면 그 값을 쓴다는 것을 연료 카드에서 보여 준다.
         * 배우는 중에는 쌓인 거리를 보여서, 달릴수록 내 차 값으로 바뀐다는 것이 보이게 한다
         */
        private fun efficiencyLine(e: Efficiency) = when (e.status) {
            EfficiencyStatus.MINE -> "내 차 연비 ${kmPerL(e.kmPerL)} · 운행 ${"%,.0f".format(e.recordKm)}km 기록"
            EfficiencyStatus.LEARNING ->
                "내 차 연비 배우는 중 · ${"%.1f".format(e.recordKm)} / ${"%.0f".format(MyEfficiency.MIN_KM)}km"
            EfficiencyStatus.ODD -> "내 차 연비 기록이 맞지 않아 가정값을 써요"
        }

        private fun kmPerL(v: Double) = "${"%.1f".format(v)}km/L"

        private fun tripCard(s: AppState): TripCard? {
            val live = s.trip
            val trip = live ?: s.trips.firstOrNull() ?: return null
            val cost = HabitCostEstimator.estimate(trip, s.prices) ?: return null
            val comparison = HabitBaseline.compare(trip, s.trips, s.prices)
            val minutes = trip.durationMs / 60_000
            val money = if (cost.kind == EnergyKind.FUEL) "기름값" else "전기 요금"
            val caption = (if (live != null) "지금까지 " else "") + "습관 때문에 더 쓴 $money" +
                (comparison?.let { " · ${HabitText.compare(it)}" } ?: "")
            return TripCard(
                title = if (live != null) "지금 운행" else "지난 운행",
                costLine = HabitText.won(cost.won),
                caption = caption,
                timeline = HabitTimeline.of(trip, cost),
                tripKey = trip.startWallMs,
                live = live != null,
                brakingCount = cost.item(HabitCategory.BRAKING).count,
                accelCount = cost.hardAccelCount,
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
                s.destination == null -> "내비에 목적지를 넣으면 가는 길 주유소를 찾아요"
                s.stationSearch == null -> "주유소를 찾는 중"
                else -> "가는 길에 주유소가 없어요"
            }
        }

        /** 이름이 버튼에 들어갈 만큼 짧으면 "한강에너지 들르기", 길면 "여기 들르기" */
        private fun visitLabel(name: String): String {
            val n = shortName(name, false)
            return if (n.length <= VISIT_NAME_MAX) "$n 들르기" else "여기 들르기"
        }
        private const val VISIT_NAME_MAX = 12

        private fun price(won: Double) = "${"%,d".format(won.roundToInt())}원"
        private fun km(m: Double) = "${"%.1f".format(m / 1000)}km"

        /** 법인 표기를 빼서 이름을 짧게. 주행 중 글자 수 제한(LIMIT_STRING_LENGTH)이면 12자에서 자른다 */
        fun shortName(name: String, limit: Boolean): String {
            val clean = name.replace(Regex("""\(주\)|㈜|\(유\)|\(합\)|주식회사|유한회사"""), "").trim()
            return if (limit && clean.length > 12) clean.take(11) + "…" else clean
        }
    }
}
