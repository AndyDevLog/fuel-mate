package io.fuelmate.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.fuelmate.core.station.Station

/**
 * 주행 화면: 큰 글자 한 줄 + 보조 한 줄 + 경유지 추가 하나. 목록·펼치기·설정·애니메이션은 없다(docs/pleos/driving-restriction.md).
 * 버튼은 한 번 누르면 끝나는 단순한 동작이고 터치만으로 된다(CCP 미지원 선언).
 */
class DrivingScreen(private val ui: Ui, private val onAddWaypoint: (Station) -> Unit) {

    private val main = ui.text(TextSize.DRIVE_MAIN, ui.p.text, bold = true).apply {
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private val sub = ui.text(TextSize.DRIVE_SUB, ui.p.subtext).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }
    private val button = ui.button("경유지 추가", TextSize.DRIVE_SUB, minHeightDp = 120)
    private var station: Station? = null

    val view: View = ui.column().apply {
        gravity = Gravity.CENTER
        setBackgroundColor(ui.p.background)
        setPadding(ui.dp(48), ui.dp(48), ui.dp(48), ui.dp(48))
        addView(main)
        addView(ui.gap(16))
        addView(sub)
        addView(ui.gap(40))
        addView(button, LinearLayout.LayoutParams(ui.dp(520), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    init {
        button.setOnClickListener { station?.let(onAddWaypoint) }
    }

    fun render(m: ScreenModel.Driving) {
        main.show(m.main)
        sub.show(m.sub)
        station = m.addWaypoint
        button.visibility = if (m.addWaypoint != null) View.VISIBLE else View.GONE
    }
}
