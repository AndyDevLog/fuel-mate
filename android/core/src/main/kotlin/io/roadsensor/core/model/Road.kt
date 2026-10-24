package io.roadsensor.core.model

data class GeoPoint(val lat: Double, val lon: Double)

enum class LocationOrigin { NAVI_HELPER, DEMO_TRACK }

/** 위치 하나. 원본 좌표는 화면용이고 서버로 보내지 않는다(격자 ID만 보낸다). */
data class GeoFix(
    val point: GeoPoint,
    val headingDeg: Float,
    val timeMs: Long,
    val origin: LocationOrigin,
)

/** 서버가 배포하는 격자 하나의 집계 상태(docs/architecture.md 6-2 "cells"). */
data class CellState(
    val cell: String,
    val cars: Int,
    val rainRatio: Float,
    val rainLevel: RainLevel,
    val darkRatio: Float,
    val hardBrakes: Int,
    val updatedAtMin: Long,
)

enum class AlertKind { RAIN, DARK, HARD_BRAKE }

/**
 * 진행 방향 앞(또는 목적지 방향 띠 안)의 도로 이벤트. 거리는 직선거리다.
 * 같은 종류의 이웃 격자는 한 구간으로 묶는다: [cell]·[distanceM]은 가장 가까운 격자,
 * [cells]는 구간의 모든 격자(가까운 순), [lengthM]은 구간 안 가장 먼 격자까지의 거리 차이.
 * 비는 구간에서 가장 센 강도를 쓴다. [here]: 내 차가 있는 격자가 이 구간에 들어 있다(이미 구간 안).
 */
data class RoadAlert(
    val kind: AlertKind,
    val cell: String,
    val distanceM: Int,
    val rainLevel: RainLevel = RainLevel.NONE,
    val cells: List<String> = listOf(cell),
    val lengthM: Int = 0,
    val here: Boolean = false,
)

/** 내 차에서 감지한 사건. 업로드와 "내 기여" 표시에 쓴다. */
sealed interface RoadEvent {
    val timeMs: Long

    data class HardBrake(
        override val timeMs: Long,
        val point: GeoPoint?,
        val headingDeg: Float?,
        val startSpeedMps: Float,
    ) : RoadEvent
}

/**
 * 서버 연결 상태. 업로드(HTTP)와 구독(WebSocket)은 따로 끊길 수 있어서 나눠 둔다.
 * uploadOk: null = 아직 한 번도 안 보냄.
 */
data class LinkStatus(
    val uploadOk: Boolean? = null,
    val pendingUploads: Int = 0,
    val feedConnected: Boolean = false,
)

data class Contribution(val hardBrakes: Int = 0, val rainyMinutes: Int = 0)
