package io.roadsensor.core.geo

import io.roadsensor.core.model.GeoPoint

/**
 * geohash 인코딩. 격자 ID로 쓴다(7자리 ≈ 150m, 5자리 ≈ 4.9km 구독 타일, docs/architecture.md 6-1).
 * 앞부분만 자르면 상위 격자가 되는 성질을 그대로 쓴다.
 */
object Geohash {
    const val CELL_PRECISION = 7
    const val TILE_PRECISION = 5

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lon: Double, precision: Int = CELL_PRECISION): String {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        val out = StringBuilder(precision)
        var bit = 0; var ch = 0; var evenBit = true
        while (out.length < precision) {
            if (evenBit) {
                val mid = (lonLo + lonHi) / 2
                if (lon >= mid) { ch = ch or (1 shl (4 - bit)); lonLo = mid } else lonHi = mid
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latLo = mid } else latHi = mid
            }
            evenBit = !evenBit
            if (bit < 4) bit++ else { out.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return out.toString()
    }

    fun encode(p: GeoPoint, precision: Int = CELL_PRECISION) = encode(p.lat, p.lon, precision)

    /** 이 정밀도 격자 하나의 크기(위도 폭, 경도 폭). */
    fun span(precision: Int): Pair<Double, Double> {
        val bits = precision * 5
        val lonBits = (bits + 1) / 2
        val latBits = bits / 2
        return 180.0 / (1 shl latBits) to 360.0 / (1 shl lonBits)
    }

    /** [p]가 들어 있는 타일과 둘레 8칸, 총 9개(구독 단위, docs/architecture.md 3-5). */
    fun tilesAround(p: GeoPoint, precision: Int = TILE_PRECISION): Set<String> {
        val center = center(encode(p, precision))
        val (dLat, dLon) = span(precision)
        return buildSet {
            for (i in -1..1) for (j in -1..1) {
                val lat = (center.lat + i * dLat).coerceIn(-89.999999, 89.999999)
                val lon = ((center.lon + j * dLon + 540) % 360) - 180
                add(encode(lat, lon, precision))
            }
        }
    }

    /** 격자 중심점. */
    fun center(hash: String): GeoPoint {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        var evenBit = true
        for (c in hash) {
            val idx = BASE32.indexOf(c)
            require(idx >= 0) { "geohash가 아닌 문자: $c" }
            for (b in 4 downTo 0) {
                val on = idx shr b and 1 == 1
                if (evenBit) {
                    val mid = (lonLo + lonHi) / 2
                    if (on) lonLo = mid else lonHi = mid
                } else {
                    val mid = (latLo + latHi) / 2
                    if (on) latLo = mid else latHi = mid
                }
                evenBit = !evenBit
            }
        }
        return GeoPoint((latLo + latHi) / 2, (lonLo + lonHi) / 2)
    }
}
