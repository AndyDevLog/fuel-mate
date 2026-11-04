package io.fuelmate.core.energy

import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.EnergyMeter
import kotlin.math.max

/** 에너지 값 해석 규칙. 순수 함수라 단위 테스트로 확인한다. */
object EnergyRules {

    /** AAOS FuelType: UNKNOWN=0, ELECTRIC=10. 나머지(휘발유·경유·LPG·수소 등)는 연료로 본다 */
    private const val FUEL_TYPE_UNKNOWN = 0
    private const val FUEL_TYPE_ELECTRIC = 10

    /**
     * 한 번 읽은 사이에 용량의 이만큼 넘게 줄면 소비가 아니라 값이 바뀐 것으로 본다(Virtual Console 조작 등).
     * 실차는 1~4초마다 값을 주므로 그 사이에 5%(77kWh 차면 약 3.9kWh)를 쓸 수 없다.
     */
    const val JUMP_FRACTION = 0.05

    /** 이 속도보다 빠르면 움직이는 중으로 본다 */
    const val MOVING_MPS = 0.5f

    /**
     * 마지막으로 움직인 뒤 이 시간 안에 늘어난 양은 충전이 아니라 회생제동이다.
     * 잔량은 속도보다 늦게 온다(데모는 1초 간격 계산, 실차는 1~4초 간격). "지금 속도"로 가르면
     * 멈추는 순간 되찾은 양이 충전으로 세진다(2026-11-04 에뮬레이터 주행에서 확인). 충전은 서고 10초 안에 시작될 수 없다.
     */
    const val REGEN_WINDOW_MS = 10_000L

    /** AAOS `INFO_FUEL_TYPE` 값 → 에너지 종류. 전기만 있으면 전기차, 다른 연료가 하나라도 있으면 연료차 */
    fun kindOf(fuelTypes: List<Int>): EnergyKind? {
        val known = fuelTypes.filter { it != FUEL_TYPE_UNKNOWN }
        return when {
            known.isEmpty() -> null
            known.all { it == FUEL_TYPE_ELECTRIC } -> EnergyKind.ELECTRIC
            else -> EnergyKind.FUEL
        }
    }

    /**
     * 잔량이 [from] → [to]로 바뀌었을 때 계량기를 고친다.
     * - 줄었다: 쓴 양. 단, 용량의 [JUMP_FRACTION]을 넘게 한 번에 줄면 값 조정으로 보고 세지 않는다
     * - [moving](최근 [REGEN_WINDOW_MS] 안에 움직임)일 때 늘었다: 회생제동으로 되찾은 양. 쓴 양에서 뺀다
     * - 그 밖에 늘었다: 주유·충전
     */
    fun advance(meter: EnergyMeter, from: Double?, to: Double?, capacity: Double?, moving: Boolean): EnergyMeter {
        if (from == null || to == null) return meter
        val d = to - from
        if (d == 0.0) return meter
        val jump = capacity?.takeIf { it > 0 }?.let { it * JUMP_FRACTION }
        return when {
            d < 0 && (jump == null || -d <= jump) -> meter.copy(used = meter.used - d)
            d < 0 -> meter
            moving -> meter.copy(used = max(0.0, meter.used - d))
            else -> meter.copy(added = meter.added + d)
        }
    }
}
