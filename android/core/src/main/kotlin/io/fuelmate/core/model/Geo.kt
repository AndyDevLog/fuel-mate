package io.fuelmate.core.model

data class GeoPoint(val lat: Double, val lon: Double)

enum class LocationOrigin { NAVI_HELPER, DEMO_TRACK }

/** 위치 하나. 원본 좌표는 차 안에서만 쓰고 로그·서버로 보내지 않는다. */
data class GeoFix(
    val point: GeoPoint,
    val headingDeg: Float,
    val timeMs: Long,
    val origin: LocationOrigin,
)
