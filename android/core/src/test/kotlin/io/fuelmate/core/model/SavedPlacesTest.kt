package io.fuelmate.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SavedPlacesTest {
    private fun place(name: String, lat: Double, kind: PlaceKind) = SavedPlace(name, GeoPoint(lat, 127.0), kind)

    @Test
    fun `집, 회사, 즐겨찾기, 최근 순으로 4곳까지`() {
        val bookmarks = listOf(place("카페", 37.1, PlaceKind.BOOKMARK), place("회사", 37.2, PlaceKind.OFFICE), place("집", 37.3, PlaceKind.HOME))
        val recents = listOf(place("강남", 37.4, PlaceKind.RECENT), place("판교", 37.5, PlaceKind.RECENT))
        assertEquals(listOf("집", "회사", "카페", "강남"), SavedPlaces.forStartScreen(bookmarks, recents).map { it.name })
    }

    @Test
    fun `같은 이름이나 같은 자리는 하나만, 이름·좌표 없는 항목은 뺀다`() {
        val bookmarks = listOf(place("집", 37.3, PlaceKind.HOME))
        val recents = listOf(
            place("집", 37.9, PlaceKind.RECENT),
            place("우리 아파트", 37.3001, PlaceKind.RECENT),   // 약 11m
            place("", 37.5, PlaceKind.RECENT),
            SavedPlace("좌표 없음", GeoPoint(0.0, 0.0), PlaceKind.RECENT),
            place("판교", 37.5, PlaceKind.RECENT),
        )
        assertEquals(listOf("집", "판교"), SavedPlaces.forStartScreen(bookmarks, recents).map { it.name })
    }

    @Test
    fun `아무것도 없으면 빈 목록`() {
        assertEquals(emptyList(), SavedPlaces.forStartScreen(emptyList(), emptyList()))
    }
}
