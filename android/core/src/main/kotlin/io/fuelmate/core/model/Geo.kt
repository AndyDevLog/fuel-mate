package io.fuelmate.core.model

data class GeoPoint(val lat: Double, val lon: Double)

enum class LocationOrigin {
    NAVI_HELPER,
    DEMO_TRACK,
    /** 내비 위치가 멈춰 있어(에뮬레이터) 앱이 차량 속도로 목적지 쪽 직선을 따라 움직인 위치. 시연용 */
    NAVI_SIMULATED,
}

/** 위치 하나. 원본 좌표는 차 안에서만 쓰고 로그·서버로 보내지 않는다. */
data class GeoFix(
    val point: GeoPoint,
    val headingDeg: Float,
    val timeMs: Long,
    val origin: LocationOrigin,
)
