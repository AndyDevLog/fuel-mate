package io.roadsensor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.roadsensor.core.present.Screen

/**
 * 정차 화면 오른쪽 위의 [도로 지도 | 내 차] 탭(Pleos 가이드의 segmented menu 형태).
 * 지금 화면이 칠해져 보여서, 라벨이 바뀌던 전환 버튼보다 어느 화면인지 바로 안다.
 * 길게 누르면 [onLongPress](개발·시연용 버튼 열기).
 */
class ScreenTabs(
    context: Context,
    private val onSelect: (Screen) -> Unit,
    private val onLongPress: () -> Unit,
) : LinearLayout(context) {

    private val tabs = linkedMapOf(Screen.MAP to "도로 지도", Screen.MY_CAR to "내 차").mapValues { (screen, label) ->
        TextView(context).apply {
            text = label
            textSize = TEXT_SP
            gravity = Gravity.CENTER
            minWidth = TAB_MIN_WIDTH
            setPadding(36, 0, 36, 0)
            setOnClickListener { onSelect(screen) }
            setOnLongClickListener { onLongPress(); true }
        }
    }

    init {
        orientation = HORIZONTAL
        setPadding(PAD, PAD, PAD, PAD)
        background = rounded(UiColors.SURFACE, RADIUS + PAD)
        tabs.values.forEach { addView(it, LayoutParams(LayoutParams.WRAP_CONTENT, TAB_HEIGHT)) }
    }

    fun select(screen: Screen) {
        for ((s, tab) in tabs) {
            val on = s == screen
            tab.background = if (on) rounded(UiColors.TEXT, RADIUS) else null
            tab.setTextColor(if (on) UiColors.BACKGROUND else UiColors.TEXT)
            tab.isSelected = on
        }
    }

    companion object {
        const val TEXT_SP = 26f
        const val TAB_HEIGHT = 88
        const val TAB_MIN_WIDTH = 180
        const val RADIUS = 20f
        const val PAD = 6

        fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }
}

/** 네이티브 화면 색. 지도 페이지(shared/src/car/index.html)의 --bg·--text·--panel과 맞춘다. */
object UiColors {
    val BACKGROUND = Color.rgb(0x0b, 0x0f, 0x14)
    val TEXT = Color.rgb(0xe8, 0xed, 0xf2)
    val SURFACE = Color.argb(0xEB, 0x0d, 0x12, 0x19)
}

/** 개발·시연용 버튼(위치 출처, 상태). 탭과 같은 모양의 어두운 버튼. */
fun devButton(context: Context, onClick: (View) -> Unit) = TextView(context).apply {
    textSize = 22f
    gravity = Gravity.CENTER
    setTextColor(UiColors.TEXT)
    setPadding(32, 0, 32, 0)
    minHeight = ScreenTabs.TAB_HEIGHT
    background = ScreenTabs.rounded(UiColors.SURFACE, ScreenTabs.RADIUS)
    setOnClickListener(onClick)
}
