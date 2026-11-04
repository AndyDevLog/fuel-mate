package io.fuelmate.price

import android.content.Context
import io.fuelmate.core.cost.ChargeTier

/**
 * 전기 단가로 쓸 공공 충전요금 단계. 공공 API가 없어서 사용자가 고르고, 앱을 다시 켜도 유지한다.
 * 기본은 급속 100~200kW. 정차 화면 설정이 생기기 전까지는 실행 인텐트로 바꾼다(개발·시연용):
 * `adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es chargeTier SLOW|FAST_30|FAST_50|FAST_100|ULTRA`
 */
object ChargeTierSetting {
    private const val PREFS = "charge_tier"
    private const val KEY = "tier"

    fun load(context: Context): ChargeTier =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { ChargeTier.valueOf(it) }.getOrNull() } ?: ChargeTier.FAST_100

    fun save(context: Context, tier: ChargeTier) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tier.name).apply()
    }
}
