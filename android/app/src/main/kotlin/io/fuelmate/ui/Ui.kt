package io.fuelmate.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 색. UI Design Guide 대비 4.5:1 이상(docs/pleos/ui-design-guide.md). 주간·야간은 시스템 야간 모드를 따른다.
 * 대비(WCAG 식으로 계산, 카드 기준): 야간 본문 #F5F7FA/#1B2230 14.8:1, 보조 #B8C2D3 8.9:1, 이득 #4ADE80 9.1:1,
 * 버튼 흰 글자/#2563EB 5.2:1. 주간 본문 #111827/#FFFFFF 17.7:1, 보조 #4B5563 7.6:1(배경 #F2F4F8 위 6.9:1),
 * 이득 #15803D 5.0:1, 버튼 흰 글자/#1D4ED8 6.7:1.
 */
data class Palette(
    val background: Int,
    val card: Int,
    val text: Int,
    val subtext: Int,
    val gain: Int,
    val button: Int,
    val buttonPressed: Int,
    val buttonText: Int,
    val divider: Int,
    /** 비용이 생긴 곳(운행 타임라인의 급제동·긴 정차) */
    val alert: Int,
) {
    companion object {
        val NIGHT = Palette(
            background = Color.rgb(0x10, 0x14, 0x1C), card = Color.rgb(0x1B, 0x22, 0x30),
            text = Color.rgb(0xF5, 0xF7, 0xFA), subtext = Color.rgb(0xB8, 0xC2, 0xD3), gain = Color.rgb(0x4A, 0xDE, 0x80),
            button = Color.rgb(0x25, 0x63, 0xEB), buttonPressed = Color.rgb(0x1E, 0x40, 0xAF), buttonText = Color.WHITE,
            divider = Color.rgb(0x2C, 0x35, 0x46), alert = Color.rgb(0xF8, 0x71, 0x71),
        )
        val DAY = Palette(
            background = Color.rgb(0xF2, 0xF4, 0xF8), card = Color.WHITE,
            text = Color.rgb(0x11, 0x18, 0x27), subtext = Color.rgb(0x4B, 0x55, 0x63), gain = Color.rgb(0x15, 0x80, 0x3D),
            button = Color.rgb(0x1D, 0x4E, 0xD8), buttonPressed = Color.rgb(0x1E, 0x3A, 0x8A), buttonText = Color.WHITE,
            divider = Color.rgb(0xE5, 0xE7, 0xEB), alert = Color.rgb(0xDC, 0x26, 0x26),
        )

        fun of(context: Context): Palette =
            if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO) DAY
            else NIGHT
    }
}

/**
 * 글자 크기(sp). UI Design Guide: 주요 정보 5mm 이상, 보조 3mm 이상, 최소 12sp.
 * 에뮬레이터(160dpi)는 1sp = 1px, 1mm ≈ 6.3px라 5mm ≈ 32sp, 3mm ≈ 19sp다. 실차 밀도는 확인 필요.
 */
object TextSize {
    /** 주행 화면 큰 글자(약 10mm). 운전 중 한눈에 읽히게 정차 화면보다 크게 */
    const val DRIVE_MAIN = 64f
    /** 주행 화면 보조 한 줄(약 6mm) */
    const val DRIVE_SUB = 36f
    /** 추천 카드의 이득 금액. 화면에서 가장 크다 */
    const val GAIN = 76f
    /** 정차 카드에서 먼저 읽을 값(연료 %) */
    const val HERO = 64f
    const val TITLE = 24f
    const val MAIN = 40f
    /** 이름·가격·우회처럼 카드의 두 번째로 중요한 줄 */
    const val LEAD = 30f
    const val BODY = 26f
    const val SMALL = 22f
    const val BUTTON = 30f
}

/** 코드로 뷰를 만드는 작은 도우미. XML 레이아웃 없이 화면 두 장을 만든다 */
class Ui(private val context: Context, val p: Palette) {

    fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    fun text(sizeSp: Float, color: Int = p.text, bold: Boolean = false): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        // UI Design Guide: 줄 높이 1.5배
        setLineSpacing(0f, 1.3f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun column(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(): LinearLayout = column().apply {
        background = GradientDrawable().apply {
            setColor(p.card)
            cornerRadius = dp(20).toFloat()
        }
        setPadding(dp(32), dp(28), dp(32), dp(28))
    }

    /** 큰 터치 영역 버튼. 눌림 상태만 색으로 보인다(애니메이션 없음) */
    fun button(label: String, sizeSp: Float = TextSize.BUTTON, minHeightDp: Int = 88): TextView = text(sizeSp, p.buttonText, bold = true).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = dp(minHeightDp)
        setPadding(dp(32), dp(12), dp(32), dp(12))
        isClickable = true
        isFocusable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(p.buttonPressed))
            addState(intArrayOf(), rounded(p.button))
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(16).toFloat()
    }

    /** 남는 세로 공간을 나눠 갖는 빈 칸. 카드가 화면 높이까지 늘어날 때 내용 사이를 벌린다 */
    fun spacer(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
    }

    fun gap(heightDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    fun divider(): View = View(context).apply {
        setBackgroundColor(p.divider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(16); bottomMargin = dp(16)
        }
    }
}

/** 값이 바뀔 때만 글자를 바꾸고, null이면 숨긴다 */
fun TextView.show(value: String?) {
    if (value == null) {
        if (visibility != View.GONE) visibility = View.GONE
        return
    }
    if (visibility != View.VISIBLE) visibility = View.VISIBLE
    if (text?.toString() != value) text = value
}
