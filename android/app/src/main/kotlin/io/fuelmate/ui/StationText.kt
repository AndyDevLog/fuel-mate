package io.fuelmate.ui

import io.fuelmate.core.model.AppState
import io.fuelmate.core.station.StationRecommender
import io.fuelmate.core.station.StationSearchRule
import kotlin.math.roundToInt

/** 주유소 추천 문구. 이득은 추정이라 "약"을 붙인다(HabitText.won) */
object StationText {

    fun summary(s: AppState): String {
        val r = StationRecommender.recommend(s) ?: return when {
            s.stationSearch == null && (s.energy?.percent ?: 100f) > StationSearchRule.SEARCH_BELOW_PERCENT ->
                "연료가 ${StationSearchRule.SEARCH_BELOW_PERCENT.toInt()}% 넘게 남아 찾지 않음"
            s.stationSearch == null -> "찾는 중"
            else -> "가는 길에 주유소 없음"
        }
        val b = r.best
        val km = "%.1f".format(b.detourM / 1000)
        return if (r.worthDetour) "${b.station.name} ${b.station.priceWonPerL.roundToInt()}원 · 우회 약 ${km}km · 가까운 곳보다 ${HabitText.won(b.gainWon)} 이득"
        else "${b.station.name} ${b.station.priceWonPerL.roundToInt()}원 · 가까운 곳에서 넣어도 차이가 작음"
    }

    fun details(s: AppState): List<String> {
        val r = StationRecommender.recommend(s) ?: return emptyList()
        return buildList {
            add("가득 ${"%.0f".format(r.fillL)}L 기준, 비교 기준: ${r.baseline.station.name} ${r.baseline.station.priceWonPerL.roundToInt()}원")
            r.options.take(3).forEachIndexed { i, o ->
                add("${i + 1}. ${o.station.name} ${o.station.priceWonPerL.roundToInt()}원 · 우회 약 ${"%.1f".format(o.detourM / 1000)}km · ${if (o.gainWon >= 0) "" else "-"}${HabitText.won(kotlin.math.abs(o.gainWon))}")
            }
            s.waypoint?.let { w ->
                add(
                    when {
                        w.asDestination -> "내비: ${w.stationName}로 안내 중(경로가 없어 우회 거리는 모름)"
                        w.detourM != null -> "내비: ${w.stationName} 경유, 실제 우회 ${"%.1f".format(w.detourM!! / 1000)}km"
                        else -> "내비: ${w.stationName} 경유지 추가 중"
                    },
                )
            }
            add("이득 = (기준 가격 − 가격) × 넣을 양 − 더 도는 거리 × 0.09L/km × 기준 가격 (추정)")
        }
    }
}
