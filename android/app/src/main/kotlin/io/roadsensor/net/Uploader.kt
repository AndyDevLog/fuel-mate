package io.roadsensor.net

import io.roadsensor.Log
import io.roadsensor.core.protocol.Observation
import io.roadsensor.core.protocol.Protocol
import io.roadsensor.core.store.Action
import io.roadsensor.core.store.Store
import io.roadsensor.core.upload.ObservationBuilder
import io.roadsensor.core.upload.TempIdRotator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * 관측값 업로더 (docs/architecture.md 3-4).
 * 상태가 바뀔 때 ObservationBuilder로 관측값을 만들어 큐에 쌓고, 10초마다 묶어서 보낸다.
 * 실패하면 큐에 남겨 두고 간격을 늘려 다시 보낸다(주행 중 네트워크 끊김 대비, Pleos 정책).
 * 지금은 메모리 큐다. 포그라운드 서비스라 앱이 뒤로 가도 유지되지만, 프로세스가 죽으면 사라진다.
 */
class Uploader(
    private val store: Store,
    private val baseUrl: String,
    private val scope: CoroutineScope,
) {
    private val builder = ObservationBuilder()
    private val tempId = TempIdRotator(::randomId)
    private val queue = ArrayDeque<Observation>()
    @Volatile private var lastOk = false

    fun start() {
        scope.launch {
            store.state.collect { s ->
                val obs = builder.onState(s, System.currentTimeMillis())
                if (obs.isNotEmpty()) enqueue(obs)
            }
        }
        scope.launch(Dispatchers.IO) {
            var wait = FLUSH_MS
            while (isActive) {
                delay(wait)
                wait = if (flush()) FLUSH_MS else (wait * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private fun enqueue(obs: List<Observation>) {
        val pending = synchronized(queue) {
            queue.addAll(obs)
            while (queue.size > MAX_QUEUE) queue.removeFirst()
            queue.size
        }
        store.dispatch(Action.UploadStatusChanged(lastOk, pending))
    }

    /** 성공했거나 보낼 게 없으면 true. */
    private fun flush(): Boolean {
        val nowMin = System.currentTimeMillis() / 60_000
        val batch = synchronized(queue) {
            // 서버 창(15분)을 넘긴 관측은 어차피 거부되므로 버린다
            queue.removeAll { it.t <= nowMin - MAX_AGE_MIN }
            queue.take(MAX_BATCH)
        }
        if (batch.isEmpty()) return true

        val ok = runCatching { post(batch) }
            .onFailure { Log.w("업로드 실패 (${batch.size}개 대기): ${it.javaClass.simpleName} ${it.message}") }
            .getOrDefault(false)

        val pending = synchronized(queue) {
            if (ok) repeat(batch.size) { queue.removeFirstOrNull() }
            queue.size
        }
        if (ok) Log.i("업로드 ${batch.size}개 (대기 $pending)")
        lastOk = ok
        store.dispatch(Action.UploadStatusChanged(ok, pending))
        return ok
    }

    private fun post(batch: List<Observation>): Boolean {
        val body = JSONObject()
            .put("v", Protocol.VERSION)
            .put("tid", tempId.current(System.currentTimeMillis()))
            .put("src", Protocol.SOURCE_CAR)
            .put("obs", JSONArray().apply { batch.forEach { put(it.toJson()) } })
            .toString()

        val conn = URL("$baseUrl/v1/observations").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) Log.w("업로드 거부 HTTP $code")
            return code in 200..299
        } finally {
            conn.disconnect()
        }
    }

    private fun Observation.toJson(): JSONObject {
        val o = JSONObject().put("cell", cell).put("t", t)
        return when (this) {
            is Observation.Rain -> o.put("kind", "rain").put("level", level)
            is Observation.Dark -> o.put("kind", "dark").put("on", on)
            is Observation.Brake -> o.put("kind", "brake").put("dir", dir)
        }
    }

    private companion object {
        const val FLUSH_MS = 10_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MAX_QUEUE = 500
        const val MAX_BATCH = 500
        const val MAX_AGE_MIN = 14
        const val TIMEOUT_MS = 5_000

        private val random = SecureRandom()

        fun randomId(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    }
}
