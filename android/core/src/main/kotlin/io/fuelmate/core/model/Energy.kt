package io.fuelmate.core.model

/**
 * 차가 쓰는 에너지. 계산은 "에너지 비용(원)"으로 일반화하고, 연료·전기 차이는 단위와 공급원(어댑터)에만 둔다.
 * 하이브리드(플러그인 아님)는 연료차로 본다. 플러그인 하이브리드는 확인 필요(지금은 연료차로 본다).
 */
enum class EnergyKind(val unit: String, val label: String) {
    FUEL("L", "연료"),
    ELECTRIC("kWh", "전기"),
}

/** 값이 어디서 왔나. DEMO는 속도로 소비를 계산한 시연용 값이다(docs/pivot-fuel.md 9장, 13-4-1). */
enum class EnergyOrigin { VEHICLE, DEMO }

/**
 * 지금 남은 에너지. 양은 연료 L, 전기 kWh. null은 "아직 못 받음"이다.
 * - 실차 연료는 Pleos가 1% 단위 정수만 준다. 그래서 [amount]는 퍼센트 × 용량이라 거칠다(docs/pivot-fuel.md 1장)
 * - 에뮬레이터 전기차는 SoC와 잔량(Wh)이 따로 움직인다(9장). [reportedPercent]는 차가 준 SoC를 그대로 둔다
 */
data class EnergyState(
    val kind: EnergyKind,
    val origin: EnergyOrigin,
    val amount: Double? = null,
    val capacity: Double? = null,
    val reportedPercent: Float? = null,
) {
    /** 차가 준 퍼센트가 있으면 그것, 없으면 잔량 ÷ 용량 */
    val percent: Float?
        get() = reportedPercent
            ?: if (amount != null && capacity != null && capacity > 0) (amount / capacity * 100).toFloat() else null
}

/**
 * 이번 실행에서 쓴 양([used])과 넣은 양([added], 주유·충전). 단위는 [EnergyState.amount]와 같다.
 * 공급원(차종·출처)이 바뀌면 새로 센다. 운행 단위 기록은 이 값을 운행 시작·끝에 떼어서 쓴다.
 */
data class EnergyMeter(val used: Double = 0.0, val added: Double = 0.0)
