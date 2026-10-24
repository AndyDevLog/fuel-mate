package io.roadsensor.net

import io.roadsensor.Log
import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.CellState
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 주변 격자 구독 (docs/architecture.md 3-5).
 * 내 위치가 든 타일(geohash 5자리)과 둘레 8칸을 WebSocket으로 구독하고, 받은 격자 상태를 스토어에 넣는다.
 * 타일이 바뀌면 다시 구독하고, 끊기면 간격을 늘려 다시 붙는다.
 */
class MapFeed(
    private val store: Store,
    baseUrl: String,
    private val scope: CoroutineScope,
) {
    private val url = baseUrl.replaceFirst("http", "ws") + "/v1/stream"
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var tiles: Set<String> = emptySet()
    @Volatile private var stopped = false
    private var retryMs = MIN_RETRY_MS

    fun start() {
        connect()
        scope.launch {
            store.state
                .map { s -> s.location?.point?.let { Geohash.tilesAround(it) } ?: emptySet() }
                .distinctUntilChanged()
                .collect { t ->
                    tiles = t
                    subscribe()
                }
        }
    }

    fun stop() {
        stopped = true
        socket?.close(1000, null)
        client.dispatcher.executorService.shutdown()
    }

    private fun connect() {
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private fun subscribe() {
        val t = tiles
        if (t.isEmpty()) return
        val msg = JSONObject().put("type", "subscribe").put("tiles", JSONArray(t.toList()))
        socket?.send(msg.toString())
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i("지도 구독 연결됨")
            retryMs = MIN_RETRY_MS
            store.dispatch(Action.FeedStatusChanged(true))
            subscribe()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { parse(text) }
                .onSuccess { (updated, expired) ->
                    if (updated.isNotEmpty() || expired.isNotEmpty()) {
                        Log.i("격자 수신 ${updated.size}개, 만료 ${expired.size}개")
                        store.dispatch(Action.CellsUpdated(updated, expired))
                    }
                }
                .onFailure { Log.w("구독 메시지를 읽지 못함", it) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            disconnected("${t.javaClass.simpleName} ${t.message}")

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = disconnected("닫힘 $code")
    }

    private fun disconnected(why: String) {
        store.dispatch(Action.FeedStatusChanged(false))
        if (stopped) return
        Log.w("지도 구독 끊김 ($why), ${retryMs / 1000}초 뒤 재연결")
        val wait = retryMs
        retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
        scope.launch {
            delay(wait)
            if (!stopped) connect()
        }
    }

    private fun parse(text: String): Pair<List<CellState>, List<String>> {
        val json = JSONObject(text)
        if (json.optString("type") != "cells") return emptyList<CellState>() to emptyList()
        val updated = json.getJSONArray("updated").let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CellState(
                    cell = c.getString("cell"),
                    cars = c.getInt("n"),
                    rainRatio = c.getDouble("rain").toFloat(),
                    rainLevel = RainLevel.fromCode(c.getInt("rainLevel")),
                    darkRatio = c.getDouble("dark").toFloat(),
                    hardBrakes = c.getInt("brake"),
                    updatedAtMin = c.getLong("updatedAt"),
                )
            }
        }
        val expired = json.getJSONArray("expired").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        return updated to expired
    }

    private companion object {
        const val MIN_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
    }
}
