package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** KATEC 평면 좌표(m). 오피넷 반경 검색이 이 좌표만 받는다(docs/pivot-fuel.md 5장) */
data class KatecPoint(val x: Double, val y: Double)

/**
 * WGS84(위경도) ↔ KATEC 변환. 라이브러리 없이 직접 계산한다.
 * KATEC 정의(PROJ 문자열로 쓰면):
 * `+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=bessel
 *  +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43`
 * 이 정의로 오피넷 문서 예시 좌표(314681.8, 544837)를 풀면 서울 역삼동이 나와서 오피넷과 맞는 것을 확인했다.
 * 계산 순서: WGS84 위경도 → 지구중심 좌표 → 7변수 변환(Position Vector) → Bessel 위경도 → 횡메르카토르(Snyder 식).
 * PROJ 9.3으로 만든 기준값과 0.1m 안에서 맞는지 테스트한다(KatecTest).
 */
object Katec {
    // WGS84 타원체
    private const val WGS_A = 6_378_137.0
    private const val WGS_F = 1 / 298.257223563

    // Bessel 1841 타원체
    private const val BES_A = 6_377_397.155
    private const val BES_F = 1 / 299.1528128

    // 횡메르카토르 원점
    private const val LAT0 = 38.0
    private const val LON0 = 128.0
    private const val K0 = 0.9999
    private const val X0 = 400_000.0
    private const val Y0 = 600_000.0

    // towgs84(Bessel → WGS84): 이동(m), 회전(초), 축척(ppm)
    private const val TX = -115.80
    private const val TY = 474.99
    private const val TZ = 674.11
    private val RX = arcsec(1.16)
    private val RY = arcsec(-2.31)
    private val RZ = arcsec(-1.63)
    private const val S = 6.43e-6

    fun fromWgs84(p: GeoPoint): KatecPoint {
        val (x, y, z) = toEcef(p.lat, p.lon, WGS_A, WGS_F)
        // Position Vector 변환의 역(회전각이 매우 작아 전치 행렬로 충분하다)
        val dx = (x - TX) / (1 + S)
        val dy = (y - TY) / (1 + S)
        val dz = (z - TZ) / (1 + S)
        val bx = dx + RZ * dy - RY * dz
        val by = -RZ * dx + dy + RX * dz
        val bz = RY * dx - RX * dy + dz
        val (lat, lon) = fromEcef(bx, by, bz, BES_A, BES_F)
        return tmForward(lat, lon)
    }

    fun toWgs84(k: KatecPoint): GeoPoint {
        val (lat, lon) = tmInverse(k.x, k.y)
        val (x, y, z) = toEcef(lat, lon, BES_A, BES_F)
        val wx = TX + (1 + S) * (x - RZ * y + RY * z)
        val wy = TY + (1 + S) * (RZ * x + y - RX * z)
        val wz = TZ + (1 + S) * (-RY * x + RX * y + z)
        val (wlat, wlon) = fromEcef(wx, wy, wz, WGS_A, WGS_F)
        return GeoPoint(wlat, wlon)
    }

    private fun arcsec(v: Double) = Math.toRadians(v / 3600)

    private fun toEcef(latDeg: Double, lonDeg: Double, a: Double, f: Double): Triple<Double, Double, Double> {
        val e2 = f * (2 - f)
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val n = a / sqrt(1 - e2 * sin(lat).pow(2))
        return Triple(n * cos(lat) * cos(lon), n * cos(lat) * sin(lon), n * (1 - e2) * sin(lat))
    }

    /** 높이는 버린다(지표면 근처라 위경도에 주는 영향이 mm 아래) */
    private fun fromEcef(x: Double, y: Double, z: Double, a: Double, f: Double): Pair<Double, Double> {
        val e2 = f * (2 - f)
        val p = sqrt(x * x + y * y)
        var lat = atan2(z, p * (1 - e2))
        repeat(6) {
            val n = a / sqrt(1 - e2 * sin(lat).pow(2))
            val h = p / cos(lat) - n
            lat = atan2(z, p * (1 - e2 * n / (n + h)))
        }
        return Math.toDegrees(lat) to Math.toDegrees(atan2(y, x))
    }

    private val e2 = BES_F * (2 - BES_F)
    private val ep2 = e2 / (1 - e2)

    /** 자오선 호 길이(Snyder 3-21) */
    private fun meridian(lat: Double): Double {
        val e4 = e2 * e2
        val e6 = e4 * e2
        return BES_A * (
            (1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * lat -
                (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * lat) +
                (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * lat) -
                (35 * e6 / 3072) * sin(6 * lat)
            )
    }

    private val m0 = meridian(Math.toRadians(LAT0))

    /** Snyder 8-9, 8-10 */
    private fun tmForward(latDeg: Double, lonDeg: Double): KatecPoint {
        val lat = Math.toRadians(latDeg)
        val n = BES_A / sqrt(1 - e2 * sin(lat).pow(2))
        val t = tan(lat).pow(2)
        val c = ep2 * cos(lat).pow(2)
        val a = cos(lat) * Math.toRadians(lonDeg - LON0)
        val m = meridian(lat)
        val x = K0 * n * (
            a + (1 - t + c) * a.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * ep2) * a.pow(5) / 120
            )
        val y = K0 * (
            m - m0 + n * tan(lat) * (
                a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * ep2) * a.pow(6) / 720
                )
            )
        return KatecPoint(X0 + x, Y0 + y)
    }

    /** Snyder 8-17 ~ 8-25 */
    private fun tmInverse(x: Double, y: Double): Pair<Double, Double> {
        val m = m0 + (y - Y0) / K0
        val mu = m / (BES_A * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256))
        val e1 = (1 - sqrt(1 - e2)) / (1 + sqrt(1 - e2))
        val lat1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)
        val c1 = ep2 * cos(lat1).pow(2)
        val t1 = tan(lat1).pow(2)
        val n1 = BES_A / sqrt(1 - e2 * sin(lat1).pow(2))
        val r1 = BES_A * (1 - e2) / (1 - e2 * sin(lat1).pow(2)).pow(1.5)
        val d = (x - X0) / (n1 * K0)
        val lat = lat1 - (n1 * tan(lat1) / r1) * (
            d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * d.pow(6) / 720
            )
        val lon = (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * d.pow(5) / 120) / cos(lat1)
        return Math.toDegrees(lat) to LON0 + Math.toDegrees(lon)
    }
}
