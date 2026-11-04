package io.fuelmate.energy

import android.content.Context

/**
 * 에너지 공급원 선택. 앱을 다시 켜도 유지한다.
 * 기본은 DEMO_FUEL이다. 시연은 지금 Pleos Connect가 탑재된 차(더 뉴 그랜저, 가솔린·하이브리드·LPG)에 맞춰 휘발유로 한다
 * (docs/pivot-fuel.md 12장 결정 변경). 에뮬레이터 차는 전기차라 Pleos 연료 API가 동작하지 않으므로 연료는 데모 공급원이다.
 * 바꾸기(개발·시연용):
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
                ?.let { runCatching { valueOf(it) }.getOrNull() } ?: DEMO_FUEL

        fun save(context: Context, mode: EnergyMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        }
    }
}
