package io.fuelmate.price

import android.content.Context
import io.fuelmate.BuildConfig
import io.fuelmate.Log
import io.fuelmate.core.cost.AvgFuelPrices
import io.fuelmate.core.cost.OpinetAvgPrice
import io.fuelmate.core.store.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 오피넷 전국 주유소 평균가격을 하루 한 번 받는다(수집 서비스 안에서만 돈다).
 * - 위치를 보내지 않는다. 요청에는 인증키와 출력 형식만 들어간다. 로그에 키를 남기지 않는다
 * - 받은 값은 앱 전용 저장소에 두고 다음에 켤 때 먼저 넣는다(네트워크가 없어도 마지막 값을 쓴다)
 * - 키가 없으면(빌드에 안 넣음) 요청하지 않고 가정 단가가 남는다
 * - 하루 1,500건 한도(2차 자료, docs/pivot-fuel.md 5장)를 키 하나로 모든 차가 나눠 쓰므로 자주 부르지 않는다
 */
class FuelPriceSource(
    context: Context,
    private val scope: CoroutineScope,
    private val dispatch: (Action) -> Unit,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var job: Job? = null

    fun start() {
        cached()?.let {
            Log.i("유가 캐시: 휘발유 ${it.gasoline}원/L (${it.tradeDate})")
            dispatch(Action.FuelPriceUpdated(it))
        }
        if (BuildConfig.OPINET_CERT_KEY.isBlank()) {
            Log.w("오피넷 인증키가 빌드에 없음: 가정 단가를 쓴다(~/.android-keys/fuel-mate/api-keys.properties)")
            return
        }
        job = scope.launch {
            while (isActive) {
                val wait = if (isFresh()) CHECK_MS else if (fetchOnce()) CHECK_MS else RETRY_MS
                delay(wait)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun isFresh() = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0) < FRESH_MS

    /** @return 받아서 넣었으면 true */
    private suspend fun fetchOnce(): Boolean {
        val xml = withContext(Dispatchers.IO) { runCatching { get(OpinetAvgPrice.requestUrl(BuildConfig.OPINET_CERT_KEY)) } }
            .onFailure { Log.w("유가 요청 실패: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return false
        val prices = OpinetAvgPrice.parse(xml)
        if (prices == null) {
            // 키 오류 등. 응답 본문에 키가 들어 있을 수 있어 앞부분 길이만 남긴다
            Log.w("유가 응답을 해석하지 못함(${xml.length}자)")
            return false
        }
        save(prices)
        Log.i("유가 받음: 휘발유 ${prices.gasoline}원/L, 경유 ${prices.diesel}, 부탄 ${prices.autoLpg} (${prices.tradeDate})")
        dispatch(Action.FuelPriceUpdated(prices))
        return true
    }

    private fun get(url: String): String {
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

    private fun cached(): AvgFuelPrices? {
        val date = prefs.getString(KEY_DATE, null) ?: return null
        fun d(key: String) = prefs.getString(key, null)?.toDoubleOrNull()
        return AvgFuelPrices(date, d(KEY_GASOLINE) ?: return null, d(KEY_PREMIUM), d(KEY_DIESEL), d(KEY_LPG))
    }

    private fun save(p: AvgFuelPrices) {
        prefs.edit()
            .putString(KEY_DATE, p.tradeDate)
            .putString(KEY_GASOLINE, p.gasoline?.toString())
            .putString(KEY_PREMIUM, p.premiumGasoline?.toString())
            .putString(KEY_DIESEL, p.diesel?.toString())
            .putString(KEY_LPG, p.autoLpg?.toString())
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    private companion object {
        const val PREFS = "fuel_price"
        const val KEY_DATE = "tradeDate"
        const val KEY_GASOLINE = "gasoline"
        const val KEY_PREMIUM = "premium"
        const val KEY_DIESEL = "diesel"
        const val KEY_LPG = "lpg"
        const val KEY_FETCHED_AT = "fetchedAt"

        /** 받은 지 이 시간이 안 지났으면 다시 받지 않는다. 평균가는 하루 단위 값이다 */
        const val FRESH_MS = 12 * 60 * 60 * 1000L
        const val CHECK_MS = 60 * 60 * 1000L
        const val RETRY_MS = 15 * 60 * 1000L
        const val TIMEOUT_MS = 10_000
    }
}
