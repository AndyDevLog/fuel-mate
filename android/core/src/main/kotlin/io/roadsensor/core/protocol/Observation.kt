package io.roadsensor.core.protocol

/**
 * 서버 프로토콜 v1의 관측값 (docs/architecture.md 6-2, server/src/protocol.ts와 같다).
 * 원본 좌표는 없다. 격자 ID(geohash 7자리)와 분 단위 시각만 있다.
 */
sealed interface Observation {
    val cell: String
    /** 유닉스 분 */
    val t: Long

    /** level: 0=꺼짐, 1=약, 2=중, 3=강, 9=미상 */
    data class Rain(override val cell: String, override val t: Long, val level: Int) : Observation
    data class Dark(override val cell: String, override val t: Long, val on: Boolean) : Observation
    /** dir: 0~7, 북에서 시계 방향 45° 단위 */
    data class Brake(override val cell: String, override val t: Long, val dir: Int) : Observation
}

object Protocol {
    const val VERSION = 1
    const val SOURCE_CAR = "car"

    /** 진행 방향(도) → 8방위 0~7 */
    fun direction(headingDeg: Float): Int = (((headingDeg % 360 + 360) % 360 + 22.5f) / 45f).toInt() % 8
}
