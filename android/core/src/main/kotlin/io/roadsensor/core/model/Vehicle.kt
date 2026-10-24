package io.roadsensor.core.model

/** 와이퍼 강도로 추정한 비의 세기. code는 서버 프로토콜 값(docs/architecture.md 6-2). */
enum class RainLevel(val code: Int) {
    NONE(0), LIGHT(1), MODERATE(2), HEAVY(3), UNKNOWN(9);

    companion object {
        fun fromCode(code: Int): RainLevel = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class Gear { P, R, N, D, UNKNOWN }

/** 경고등 하나. 종류·상태는 SDK enum 이름을 문자열로 받는다(:core는 SDK에 의존하지 않는다). */
data class Telltale(val type: String, val state: String)

/**
 * 차량에서 받은 원본 신호. null은 "아직 못 받음"이다.
 * 와이퍼 강도는 구독이 안 되고 조회만 되므로(docs/pleos/open-questions.md) 스위치 값을 따로 둔다.
 */
data class VehicleSnapshot(
    val wiperOn: Boolean? = null,
    val wiperSwitch: String? = null,
    val lowBeamOn: Boolean? = null,
    val tailLampOn: Boolean? = null,
    val brakePressed: Boolean? = null,
    val speedMps: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
    /** null = 경고등 API를 쓸 수 없음(권한 등). 빈 목록 = 켜진 경고등 없음. */
    val telltales: List<Telltale>? = null,
)

/**
 * 주행 규제 상태. 주행/정차 판단은 CarUxRestrictions만 따른다(docs/pleos/driving-restriction.md).
 * 비트 값은 AAOS CarUxRestrictions 상수와 같다.
 */
data class DriveMode(
    val restricted: Boolean = false,
    val activeRestrictions: Int = 0,
) {
    val limitStringLength: Boolean get() = activeRestrictions and LIMIT_STRING_LENGTH != 0
    val noSetup: Boolean get() = activeRestrictions and NO_SETUP != 0

    companion object {
        const val LIMIT_STRING_LENGTH = 4
        const val NO_SETUP = 64
    }
}
