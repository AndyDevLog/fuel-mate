package io.fuelmate.net

import java.net.HttpURLConnection
import java.net.URL

/** 외부 API GET(오피넷). 수집 서비스의 IO 스레드에서만 부른다. 주소에 키가 들어 있으니 주소를 로그에 남기지 않는다 */
object HttpGet {
    private const val TIMEOUT_MS = 10_000

    fun text(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.requestMethod = "GET"
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
