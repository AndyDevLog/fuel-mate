package io.fuelmate.location

import android.content.Context

/**
 * 위치 출처 선택. 앱을 다시 켜도 유지한다.
 * 기본은 NAVI다: 운전자가 내비에 넣은 목적지 기준으로 추천한다. 에뮬레이터처럼 내비 위치가 움직이지 않으면
 * NaviHelperSource가 차량 속도로 목적지 쪽 직선을 따라 움직인다(core StuckNaviFallback).
 * DEMO는 실제 도로 궤적(시흥동 → 판교역)을 재생한다: `adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es location DEMO`
 */
enum class LocationMode(val label: String) {
    DEMO("데모 경로"),
    NAVI("내비 위치");

    companion object {
        private const val PREFS = "location"
        private const val KEY = "mode"

        fun load(context: Context): LocationMode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: NAVI

        fun save(context: Context, mode: LocationMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
