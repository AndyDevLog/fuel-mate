package io.fuelmate.ui

import io.fuelmate.core.cost.HabitBaseline
import io.fuelmate.core.cost.HabitCategory
import io.fuelmate.core.cost.HabitComparison
import io.fuelmate.core.cost.HabitCost
import io.fuelmate.core.model.EnergyKind
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 운전 습관 비용 문구. 추정값이라 금액마다 "약"을 붙이고 10원 단위로 반올림한다(과장하지 않기, CLAUDE.md 차량 정책).
 * 화면(정차·주행)과 계산 방법 펼침이 이 문구를 같이 쓴다.
 */
object HabitText {

    fun won(value: Double): String {
        val rounded = (value / 10).roundToLong() * 10
        return when {
            value <= 0.0 -> "0원"
            rounded < 10 -> "10원 미만"
            else -> "약 ${"%,d".format(rounded)}원"
        }
    }

    /** 이번 운행 요약 한 줄. 예: "습관 비용 약 50원 · 급제동 1회" */
    fun summary(cost: HabitCost, comparison: HabitComparison?): String = buildString {
        append("습관 비용 ${won(cost.won)}")
        val brakes = cost.item(HabitCategory.BRAKING).count
        val parts = listOfNotNull(
            "급제동 ${brakes}회".takeIf { brakes > 0 },
            "급가속 ${cost.hardAccelCount}회".takeIf { cost.hardAccelCount > 0 },
        )
        if (parts.isNotEmpty()) append(" · ").append(parts.joinToString(" · "))
        comparison?.let { append(" · ").append(compare(it)) }
    }

    fun compare(c: HabitComparison): String = when {
        abs(c.diffWon) < 10 -> "평소와 비슷"
        c.diffWon > 0 -> "평소보다 ${won(c.diffWon)} 더"
        else -> "평소보다 ${won(-c.diffWon)} 덜"
    }

    /** 계산 방법 펼침: 항목별 금액과 쓴 가정값 */
    fun explanation(cost: HabitCost, comparison: HabitComparison?): List<String> {
        val unit = cost.kind.unit
        val p = cost.params
        val price = cost.prices.of(cost.kind)
        return buildList {
            cost.items.forEach { item ->
                val count = if (item.category == HabitCategory.HIGH_SPEED) "" else " ${item.count}회"
                add("${item.category.label}$count: ${won(item.won)} (${"%.3f".format(item.amount)} $unit)")
            }
            add("급가속 ${cost.hardAccelCount}회: 원화로 계산하지 않음(근거가 약함)")
            add(
                when (comparison) {
                    null -> "평소 대비: 기록이 ${HabitBaseline.MIN_SPAN_DAYS}일·${HabitBaseline.MIN_BASELINE_KM.toInt()}km 넘게 쌓이면 보여 줌"
                    else -> "평소 대비: 지난 ${HabitBaseline.WINDOW_DAYS}일 ${comparison.baselineTrips}회 운행 100km당 " +
                        "${won(comparison.baselineWonPer100Km)}, 이번 ${won(comparison.tripWonPer100Km)}"
                },
            )
            add("계산 방법")
            add("· 급제동·급감속: 브레이크로 버린 운동 에너지 ½·m·(v₁²−v₂²) − 저항 몫" +
                if (cost.kind == EnergyKind.ELECTRIC) ", 회생 ${(p.regenEfficiency * 100).toInt()}%는 뺌" else "")
            add("· 고속: 시속 100km를 넘는 동안 더 든 공기저항")
            add("· 신호·정체로 서 있던 시간은 넣지 않음")
            add("· 가정값: 무게 ${"%,d".format(p.massKg.toInt())}kg, 구동 효율 ${(p.drivetrainEfficiency * 100).toInt()}%")
            add("· 단가: ${"%,.1f".format(price)}원/$unit, ${cost.prices.basis(cost.kind)}")
            add("· 실제 소비를 잰 값이 아니라 추정값이다")
        }
    }
}
