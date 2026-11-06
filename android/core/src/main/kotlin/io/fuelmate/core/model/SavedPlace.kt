package io.fuelmate.core.model

import io.fuelmate.core.geo.GeoMath

enum class PlaceKind { HOME, OFFICE, BOOKMARK, RECENT }

/**
 * 내비에 저장된 곳(즐겨찾기·최근 목적지). 첫 화면에서 탭 한 번으로 안내를 시작한다.
 * 내비가 이미 가진 값을 메모리에만 들고 있고, 파일·로그·서버로 보내지 않는다.
 */
data class SavedPlace(
    val name: String,
    val point: GeoPoint,
    val kind: PlaceKind,
    val poiId: String = "",
    val poiSubId: String = "",
    val address: String = "",
    val poiProvider: String = "",
)

object SavedPlaces {
    const val START_SCREEN_LIMIT = 4
    private const val SAME_PLACE_M = 50.0

    /** 첫 화면 목록: 집, 회사, 즐겨찾기, 최근 순. 이름 없음·좌표 없음은 빼고 같은 곳은 하나만 */
    fun forStartScreen(bookmarks: List<SavedPlace>, recents: List<SavedPlace>): List<SavedPlace> {
        val ordered = bookmarks.sortedBy { it.kind.ordinal } + recents
        val out = mutableListOf<SavedPlace>()
        for (p in ordered) {
            if (p.name.isBlank() || (p.point.lat == 0.0 && p.point.lon == 0.0)) continue
            if (out.any { it.name == p.name || GeoMath.distanceM(it.point, p.point) < SAME_PLACE_M }) continue
            out += p
            if (out.size == START_SCREEN_LIMIT) break
        }
        return out
    }
}
