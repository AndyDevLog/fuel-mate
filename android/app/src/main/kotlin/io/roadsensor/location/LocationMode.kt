package io.roadsensor.location

import android.content.Context

/**
 * 위치 출처 선택. 정차 중 화면에서 바꾸고, 앱을 다시 켜도 유지한다.
 * 기본은 DEMO다. 심사·시연 환경(에뮬레이터)에서는 NaviHelper 위치가 움직이지 않기 때문이다.
 */
enum class LocationMode(val label: String) {
    DEMO("데모 경로"),
    NAVI("내비 위치");

    companion object {
        private const val PREFS = "location"
        private const val KEY = "mode"

        fun load(context: Context): LocationMode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEMO

        fun save(context: Context, mode: LocationMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
