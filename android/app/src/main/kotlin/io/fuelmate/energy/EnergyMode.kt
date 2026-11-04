package io.fuelmate.energy

import android.content.Context

/**
 * 에너지 공급원 선택. 앱을 다시 켜도 유지한다.
 * 기본은 DEMO_EV다. 에뮬레이터 차는 전기차지만 달려도 잔량이 줄지 않아서(docs/pivot-fuel.md 9장)
 * 소비를 보여 주려면 데모가 필요하다. 바꾸기(개발·시연용):
 * `adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es energy VEHICLE|DEMO_EV|DEMO_FUEL`
 */
enum class EnergyMode(val label: String) {
    /** 차가 주는 값(Pleos Fuel·EvBattery). 연료·전기는 AAOS `INFO_FUEL_TYPE`으로 고른다 */
    VEHICLE("차량 값"),
    DEMO_EV("데모 전기차"),
    DEMO_FUEL("데모 연료차");

    companion object {
        private const val PREFS = "energy"
        private const val KEY = "mode"

        fun load(context: Context): EnergyMode =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEMO_EV

        fun save(context: Context, mode: EnergyMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
