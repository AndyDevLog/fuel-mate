package io.fuelmate.core.station

import io.fuelmate.core.cost.SafeXml
import io.fuelmate.core.geo.Katec
import io.fuelmate.core.geo.KatecPoint
import io.fuelmate.core.model.GeoPoint
import org.w3c.dom.Element

/** 주유소 하나(오피넷 반경 검색 결과). 가격은 휘발유 원/L */
data class Station(
    val id: String,
    val name: String,
    /** 상표 코드(SKE, GSC, HDO, SOL, RTE, RTX, NHO, ETC, E1G, SKG) */
    val brand: String?,
    val priceWonPerL: Double,
    val point: GeoPoint,
)

/** 한 번의 검색(지금 위치 + 앞쪽 지점) 결과 */
data class StationSearch(
    val stations: List<Station>,
    /** 검색할 때 차가 있던 곳. 다음 검색을 할지(얼마나 움직였나) 정할 때 쓴다 */
    val fromPoint: GeoPoint,
    val timeMs: Long,
    /** 검색할 때의 목적지. 목적지가 바뀌면 앞쪽 지점이 달라지므로 다시 찾는다 */
    val destination: GeoPoint? = null,
)

/**
 * 오피넷 "반경 내 주유소" 오픈 API(https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=3).
 * 요청: `aroundAll.do?out=xml&x=<KATEC X>&y=<KATEC Y>&radius=<최대 5000m>&sort=1&prodcd=B027&certkey=<키>`.
 * 외부로 나가는 것은 검색 중심 좌표 하나뿐이다(CLAUDE.md 차량 정책: 검색에 필요한 위치만).
 * 응답 `RESULT/OIL`: UNI_ID, POLL_DIV_CD(문서 예시에는 POLL_DIV_CO), OS_NM, PRICE, DISTANCE(m), GIS_X_COOR, GIS_Y_COOR(KATEC)
 */
object OpinetAround {
    const val URL = "https://www.opinet.co.kr/api/aroundAll.do"
    const val MAX_RADIUS_M = 5_000
    const val GASOLINE = "B027"

    fun requestUrl(certKey: String, center: GeoPoint, radiusM: Int = MAX_RADIUS_M): String {
        val k = Katec.fromWgs84(center)
        return "$URL?out=xml&x=${"%.1f".format(java.util.Locale.ROOT, k.x)}&y=${"%.1f".format(java.util.Locale.ROOT, k.y)}" +
            "&radius=${radiusM.coerceAtMost(MAX_RADIUS_M)}&sort=1&prodcd=$GASOLINE&certkey=$certKey"
    }

    /** 해석할 수 없는 항목(가격·좌표 없음)은 건너뛴다. 응답 자체가 깨졌으면 null */
    fun parse(xml: String): List<Station>? {
        val doc = SafeXml.parse(xml) ?: return null
        val oils = doc.getElementsByTagName("OIL")
        return (0 until oils.length).mapNotNull { i ->
            val o = oils.item(i) as? Element ?: return@mapNotNull null
            val id = SafeXml.text(o, "UNI_ID") ?: return@mapNotNull null
            val price = SafeXml.text(o, "PRICE")?.toDoubleOrNull()?.takeIf { it in 500.0..5_000.0 } ?: return@mapNotNull null
            val x = SafeXml.text(o, "GIS_X_COOR")?.toDoubleOrNull() ?: return@mapNotNull null
            val y = SafeXml.text(o, "GIS_Y_COOR")?.toDoubleOrNull() ?: return@mapNotNull null
            Station(
                id = id,
                name = SafeXml.text(o, "OS_NM") ?: id,
                brand = SafeXml.text(o, "POLL_DIV_CD") ?: SafeXml.text(o, "POLL_DIV_CO"),
                priceWonPerL = price,
                point = Katec.toWgs84(KatecPoint(x, y)),
            )
        }
    }
}

/**
 * 내비에 넣은 주유소. [detourM]은 내비가 다시 계산한 목적지 거리 차이(실제 우회, m).
 * 경로가 없어서 주유소를 목적지로 안내했으면 [asDestination]이 true이고 우회 거리는 없다.
 * [added]는 내비가 경유지 추가를 알려 왔는지. 추가됐는데 [detourM]이 null이면 내비 값이 맞지 않아 실제 우회를 모르는 것이다.
 */
data class WaypointState(
    val stationId: String,
    val stationName: String,
    val asDestination: Boolean,
    val detourM: Double? = null,
    val added: Boolean = false,
) {
    companion object {
        /** 내비가 준 넣기 전·후 거리로 실제 우회를 구한다. 말이 안 되는 값(음수, 20km 초과, 넣기 전 거리 없음)이면 null */
        fun detourOrNull(beforeM: Int?, afterM: Int): Double? {
            if (beforeM == null || beforeM <= 0 || afterM <= 0) return null
            val d = (afterM - beforeM).toDouble()
            return d.takeIf { it in 0.0..MAX_PLAUSIBLE_DETOUR_M }
        }

        const val MAX_PLAUSIBLE_DETOUR_M = 20_000.0
    }
}
