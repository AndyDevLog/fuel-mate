package io.fuelmate.core.geo

import io.fuelmate.core.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertTrue

class KatecTest {

    private data class Ref(val name: String, val lat: Double, val lon: Double, val x: Double, val y: Double)

    /**
     * PROJ 9.3.0(pyproj 3.6.1)으로 만든 기준값. KATEC 정의는 [Katec] 주석과 같다.
     * 공개 장소의 대략 좌표이고, 값이 맞는지(변환 식)만 본다.
     */
    private val refs = listOf(
        Ref("판교역", 37.3947, 127.1112, 321497.598, 532887.535),
        Ref("서울시청", 37.5663, 126.9779, 309901.063, 552051.249),
        Ref("부산역", 35.1151, 129.0422, 495197.516, 280087.001),
        Ref("제주공항", 33.507, 126.493, 260184.322, 102236.756),
        Ref("시흥동", 37.4345, 127.118, 322140.946, 537298.844),
    )

    @Test
    fun `WGS84에서 KATEC으로 - PROJ 기준값과 0점1m 안에서 맞는다`() {
        for (r in refs) {
            val k = Katec.fromWgs84(GeoPoint(r.lat, r.lon))
            val err = kotlin.math.hypot(k.x - r.x, k.y - r.y)
            assertTrue(err < 0.1, "${r.name}: (${k.x}, ${k.y}) 오차 ${err}m")
        }
    }

    @Test
    fun `KATEC에서 WGS84로 - PROJ 기준값과 0점1m 안에서 맞는다`() {
        for (r in refs) {
            val p = Katec.toWgs84(KatecPoint(r.x, r.y))
            val err = GeoMath.distanceM(p, GeoPoint(r.lat, r.lon))
            assertTrue(err < 0.1, "${r.name}: (${p.lat}, ${p.lon}) 오차 ${err}m")
        }
    }

    @Test
    fun `오피넷 문서 예시 좌표는 서울 역삼동이다`() {
        // PROJ: 37.501758, 127.032849
        val p = Katec.toWgs84(KatecPoint(314681.8, 544837.0))
        assertTrue(GeoMath.distanceM(p, GeoPoint(37.501758, 127.032849)) < 0.5, "$p")
    }
}
