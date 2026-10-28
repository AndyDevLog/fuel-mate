package io.roadsensor.core.present

import io.roadsensor.core.geo.AheadProfile
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.RainLevel

/**
 * 주행 화면의 앞길 띠: 지도 대신 "내 차 뒤 [BEHIND_M]부터 앞 [AHEAD_M]까지"를 한 줄로 보여 준다.
 * 운전 중에 필요한 건 "몇 m 앞에 무엇이 얼마나 이어지는가"뿐이고, 신호가 150m 격자 단위라 지도보다 이 정밀도에 맞는다.
 * 뒤 여유는 오차 범위다: 격자 단위·1초 갱신 때문에 이미 들어선 구간이 바로 사라지지 않게 한다.
 * 그리기는 :app DriveStripView가 하고, 여기서는 항목과 문구만 만든다(단위 테스트).
 */
object DriveStrip {
    const val BEHIND_M = 150
    const val AHEAD_M = 3_000
    /** 이보다 짧은 구간(급정거처럼 한두 격자)은 시작 거리만 말한다. */
    const val SHORT_M = 300

    /** [fromM]·[toM]은 내 차 기준 거리(-[BEHIND_M]..[AHEAD_M]). */
    data class Item(val fromM: Int, val toM: Int, val kind: AlertKind, val rainLevel: RainLevel, val label: String)

    /** 앞길을 모르거나 범위 안에 구간이 없으면 빈 목록(띠에 "앞 3km 이상 없음"). */
    fun of(profile: AheadProfile?): List<Item> =
        profile?.segments.orEmpty()
            .filter { it.fromM < AHEAD_M && it.toM > 0 }
            .sortedBy { it.fromM }
            .map { s ->
                // 앞길은 내 위치(0)부터 훑으므로, 지금 들어선 구간은 0에서 시작한다 → 뒤 여유까지 칠한다
                val here = s.fromM <= 0
                Item(
                    fromM = if (here) -BEHIND_M else s.fromM,
                    toM = s.toM.coerceAtMost(AHEAD_M),
                    kind = s.kind,
                    rainLevel = s.rainLevel,
                    label = label(s.kind, s.rainLevel, s.fromM, s.toM, here),
                )
            }

    private fun label(kind: AlertKind, level: RainLevel, fromM: Int, toM: Int, here: Boolean): String {
        val name = when (kind) {
            AlertKind.RAIN -> "비" + when (level) {
                RainLevel.LIGHT -> "(약)"
                RainLevel.MODERATE -> "(중)"
                RainLevel.HEAVY -> "(강)"
                else -> ""
            }
            AlertKind.DARK -> "어두움"
            AlertKind.HARD_BRAKE -> "급정거"
        }
        return when {
            here -> if (toM >= AHEAD_M) "지금 $name" else "지금 $name ~${DriveLine.distance(toM)}"
            toM - fromM < SHORT_M -> "$name ${DriveLine.distance(fromM)}"
            else -> "$name ${DriveLine.distance(fromM)}~"
        }
    }
}
