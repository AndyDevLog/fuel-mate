package io.fuelmate.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.fuelmate.core.station.Station

/**
 * 주행 화면: 큰 글자 한 줄 + 보조 한 줄 + 들르기 버튼 하나. 목록·펼치기·설정·애니메이션은 없다(docs/pleos/driving-restriction.md).
 * 큰 글자 옆에 움직이지 않는 그림 하나를 둔다. 추천이 있으면 주유소 핀(이득이면 초록), 없으면 연료 통.
 * 글자를 다 읽지 않아도 무엇에 관한 화면인지 보이게 하려는 것이다. 채워지는 연출은 정차 화면에서만 한다.
 * 버튼은 한 번 누르면 끝나는 단순한 동작이고 터치만으로 된다(CCP 미지원 선언).
 */
class DrivingScreen(private val ui: Ui, private val onAddWaypoint: (Station) -> Unit) {

    private val main = ui.text(TextSize.DRIVE_MAIN, ui.p.text, bold = true).apply { maxLines = 2 }
    private val sub = ui.text(TextSize.DRIVE_SUB, ui.p.subtext).apply { maxLines = 1 }
    private val button = ui.button("여기 들르기", TextSize.DRIVE_SUB, minHeightDp = 120)
    private var station: Station? = null

    private val tank = FuelTankView(main.context, ui.p, animateOnShow = false)
    private val pin = StationPinView(main.context, ui.p)
    private val graphicGap = View(main.context)

    val view: View = ui.column().apply {
        gravity = Gravity.CENTER
        setBackgroundColor(ui.p.background)
        setPadding(ui.dp(48), ui.dp(48), ui.dp(48), ui.dp(48))
        addView(ui.row().apply {
            gravity = Gravity.CENTER
            addView(tank, LinearLayout.LayoutParams(ui.dp(TANK_W_DP), ui.dp(TANK_H_DP)))
            addView(pin, LinearLayout.LayoutParams(ui.dp(PIN_W_DP), ui.dp(PIN_H_DP)))
            addView(graphicGap, LinearLayout.LayoutParams(ui.dp(48), 1))
            // 글자는 WRAP_CONTENT로 넣는다. 기본값(MATCH_PARENT)이면 폭을 감싸는 열이 1px 간격에 맞춰 줄어 글자가 안 보인다
            addView(ui.column().apply {
                addView(main, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(ui.gap(12))
                addView(sub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        addView(ui.gap(48))
        addView(button, LinearLayout.LayoutParams(ui.dp(BUTTON_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        button.setOnClickListener { station?.let(onAddWaypoint) }
    }

    fun render(m: ScreenModel.Driving) {
        main.show(m.main)
        main.setTextColor(if (m.mainGain) ui.p.gain else ui.p.text)
        sub.show(m.sub)
        station = m.addWaypoint
        button.visibility = if (m.addWaypoint != null) View.VISIBLE else View.GONE
        if (button.text.toString() != m.buttonLabel) button.text = m.buttonLabel

        val p = m.gaugePercent
        setVisible(tank, p != null)
        tank.set(p, m.gaugeElectric)
        setVisible(pin, m.pin != null)
        pin.color = when (m.pin) {
            ScreenModel.DrivePin.GAIN, null -> ui.p.gain
            ScreenModel.DrivePin.ACTIVE -> ui.p.button
            ScreenModel.DrivePin.PLAIN -> ui.p.subtext
        }
        setVisible(graphicGap, p != null || m.pin != null)
    }

    private fun setVisible(v: View, visible: Boolean) {
        val want = if (visible) View.VISIBLE else View.GONE
        if (v.visibility != want) v.visibility = want
    }

    private companion object {
        const val TANK_W_DP = 150
        const val TANK_H_DP = 210
        const val PIN_W_DP = 150
        const val PIN_H_DP = 200
        const val BUTTON_W_DP = 640
    }
}
