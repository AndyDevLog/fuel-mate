package io.fuelmate.station

import android.os.SystemClock
import io.fuelmate.BuildConfig
import io.fuelmate.Log
import io.fuelmate.core.model.AppState
import io.fuelmate.core.station.OpinetAround
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.StationRecommender
import io.fuelmate.core.station.StationSearch
import io.fuelmate.core.station.StationSearchRule
import io.fuelmate.core.store.Action
import io.fuelmate.net.HttpGet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 오피넷 반경 검색(수집 서비스 안에서만 돈다). 언제 찾을지는 :core [StationSearchRule]이 정한다.
 * 지금 위치와 진행 방향 5km 앞에서 반경 5km씩 두 번 찾고 합친다.
 * 오피넷에는 검색 중심 좌표만 보내고, 로그에는 좌표·키를 남기지 않는다.
 */
class StationSearchSource(
    private val scope: CoroutineScope,
    private val state: () -> AppState,
    private val dispatch: (Action) -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (BuildConfig.OPINET_CERT_KEY.isBlank()) {
            Log.w("오피넷 인증키가 없어 주유소 검색을 하지 않는다")
            return
        }
        job = scope.launch {
            while (isActive) {
                val s = state()
                val now = SystemClock.elapsedRealtime()
                val wait = if (StationSearchRule.shouldSearch(s, now)) {
                    if (search(s, now)) CHECK_MS else RETRY_MS
                } else CHECK_MS
                delay(wait)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun search(s: AppState, now: Long): Boolean {
        val here = s.location ?: return false
        val ahead = StationRecommender.aheadPoint(here.point, here.headingDeg.toDouble(), s.destination)
        val results = withContext(Dispatchers.IO) {
            listOf(here.point, ahead).map { center ->
                async {
                    runCatching { OpinetAround.parse(HttpGet.text(OpinetAround.requestUrl(BuildConfig.OPINET_CERT_KEY, center))) }
                        .onFailure { Log.w("주유소 검색 실패: ${it.javaClass.simpleName}") }
                        .getOrNull()
                }
            }.awaitAll()
        }
        if (results.all { it == null }) {
            Log.w("주유소 검색 응답을 해석하지 못함")
            return false
        }
        val merged: List<Station> = results.filterNotNull().flatten().distinctBy { it.id }
        Log.i("주유소 ${merged.size}곳 찾음(지금 위치 ${results[0]?.size ?: "실패"}, 앞쪽 ${results[1]?.size ?: "실패"})")
        dispatch(Action.StationsFound(StationSearch(merged, here.point, now)))
        return true
    }

    private companion object {
        const val CHECK_MS = 10_000L
        const val RETRY_MS = 5 * 60 * 1000L
    }
}
