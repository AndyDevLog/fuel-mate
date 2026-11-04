package io.fuelmate.core.model

enum class Gear { P, R, N, D, UNKNOWN }

/** 차량에서 받은 원본 신호. null은 "아직 못 받음"이다. */
data class VehicleSnapshot(
    val brakePressed: Boolean? = null,
    val speedMps: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
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
    /** 많은 내용을 보여 주지 말라는 규제. 켜지면 주행 화면은 한 줄만 둔다. */
    val limitContent: Boolean get() = activeRestrictions and LIMIT_CONTENT != 0

    companion object {
        const val LIMIT_STRING_LENGTH = 4
        const val NO_SETUP = 64
        const val LIMIT_CONTENT = 32
    }
}
