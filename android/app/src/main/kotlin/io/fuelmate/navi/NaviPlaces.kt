package io.fuelmate.navi

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.fuelmate.Log
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.PlaceKind
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.store.Action

/**
 * 내비의 즐겨찾기·최근 목적지를 읽고, 고른 곳으로 안내를 시작한다. NaviHelper에는 장소 이름 검색이 없어(javap 확인) 이름 검색은 내비 앱이 맡는다.
 * 저장된 곳은 개인정보라 이름·좌표를 로그에 남기지 않는다.
 * 안내를 시작하면 내비가 앞으로 나온다. 일반 앱은 백그라운드에서 스스로 앞으로 나올 수 없어(Android 제한, 2026-11-06 확인)
 * 사용자가 작업 표시줄의 앱 아이콘으로 돌아온다.
 */
class NaviPlaces(context: Context, private val dispatch: (Action) -> Unit) {
    private val navi = NaviHelper(context.applicationContext)
    private var connected = false

    private val listener = object : NaviHelperEventListener {
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {
            Log.i("즐겨찾기 ${bookmarkInfoList.size}곳")
            dispatch(Action.BookmarksLoaded(bookmarkInfoList.map { it.toPlace() }))
        }

        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {
            Log.i("최근 목적지 ${recentDestinationInfoList.size}곳")
            dispatch(Action.RecentsLoaded(recentDestinationInfoList.map { it.toPlace() }))
        }

        override fun onBookMarkChanged() = navi.getBookmarkInfo()

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = navi.getRecentDestinationInfo()

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = refresh()
    }

    fun start() {
        navi.initialize({
            connected = true
            navi.addListener(listener)
            refresh()
        }, { Log.w("NaviHelper(저장된 곳) 연결 실패", it) })
    }

    fun stop() {
        runCatching { navi.removeListener(listener) }
        navi.release()
    }

    /** 앱이 앞으로 올 때 부른다: 내비에서 즐겨찾기를 바꿨거나 최근 목적지가 늘었을 수 있다 */
    fun refresh() {
        if (!connected) return
        navi.getBookmarkInfo()
        navi.getRecentDestinationInfo()
    }

    fun startRoute(place: SavedPlace) {
        if (!connected) return Log.w("내비에 아직 연결되지 않음")
        val p = place.point
        Log.i("저장된 곳으로 안내 요청")
        navi.requestRoute(RouteInfo(p.lat, p.lon, null, null, place.name, place.poiId, place.address, place.poiSubId, RouteOption.RECOMMENDED, place.poiProvider, false))
    }

    private fun BookmarkInfo.toPlace() = SavedPlace(
        name = name,
        point = GeoPoint(latitude, longitude),
        kind = when (category) {
            BookmarkType.HOME -> PlaceKind.HOME
            BookmarkType.OFFICE -> PlaceKind.OFFICE
            else -> PlaceKind.BOOKMARK
        },
        poiId = poiId,
        poiSubId = poiSubId.orEmpty(),
        address = address,
        poiProvider = poiProvider,
    )

    private fun RecentDestinationInfo.toPlace() = SavedPlace(
        name = name,
        point = GeoPoint(latitude, longitude),
        kind = PlaceKind.RECENT,
        poiId = poiId,
        poiSubId = poiSubId.orEmpty(),
        address = address,
        poiProvider = poiProvider,
    )
}
