package io.fuelmate.ui

import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.fuelmate.core.station.Station

/**
 * 주행 화면. 정차 화면과 같은 두 열(왼쪽 큰 카드, 오른쪽 연료·운행 카드)이라 P↔D를 오가도 같은 자리에 같은 것이 있다.
 * 정차 화면과 다른 점(docs/pleos/driving-restriction.md, UI Design Guide):
 * - 그림은 모두 마지막 장면으로 멈춰 있다. 채워지거나 올라가는 연출이 없다.
 * - 목록(다른 곳·바로 가기), 펼치기(계산 방법), 내비 검색 버튼이 없다. 조작은 한 번 누르면 끝나는 들르기 버튼 하나다(터치만, CCP 미지원 선언).
 * - 글자는 카드마다 큰 값 한 줄과 설명 한 줄만 둔다.
 * LIMIT_CONTENT 규제가 켜지면([ScreenModel.Driving.cards]가 null) 카드 없이 큰 글자 한 줄과 버튼만 가운데에 둔다.
 */
class DrivingScreen(private val ui: Ui, private val onAddWaypoint: (Station) -> Unit) {

    private var station: Station? = null

    // 카드 화면
    private val title = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val scene = RouteGainView(title.context, ui.p, durationMs = 0L, animateOnShow = false)
    private val pin = StationPinView(title.context, ui.p)
    private val main = ui.text(TextSize.DRIVE_MAIN, ui.p.text, bold = true).apply { maxLines = 2 }
    private val sub = ui.text(TextSize.DRIVE_SUB, ui.p.subtext).apply { maxLines = 1 }
    private val button = ui.button("여기 들르기", minHeightDp = BUTTON_DP)

    private val fuelTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val fuelTank = FuelTankView(title.context, ui.p, animateOnShow = false)
    private val fuelPercent = ui.text(TextSize.HERO, ui.p.text, bold = true)
    private val fuelDetail = ui.text(TextSize.BODY, ui.p.text)

    private val tripCard = ui.card()
    private val tripTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val tripCost = ui.text(TextSize.HERO, ui.p.text, bold = true)
    private val tripCaption = ui.text(TextSize.BODY, ui.p.subtext).apply { maxLines = 2 }

    private val cardsView: View

    // 한 줄 화면(LIMIT_CONTENT)
    private val compactMain = ui.text(TextSize.DRIVE_MAIN, ui.p.text, bold = true).apply {
        maxLines = 1
        gravity = Gravity.CENTER
    }
    private val compactButton = ui.button("여기 들르기", TextSize.DRIVE_SUB, minHeightDp = BUTTON_DP)
    private val compactView: View = ui.column().apply {
        gravity = Gravity.CENTER
        setPadding(ui.dp(48), ui.dp(48), ui.dp(48), ui.dp(48))
        addView(compactMain, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(ui.gap(48))
        addView(compactButton, LinearLayout.LayoutParams(ui.dp(COMPACT_BUTTON_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    val view: View

    init {
        button.setOnClickListener { station?.let(onAddWaypoint) }
        compactButton.setOnClickListener { station?.let(onAddWaypoint) }

        // 그림 자리: 경로 그림은 칸을 채우고, 핀은 가운데에 고정 크기로 둔다
        val picture = FrameLayout(title.context).apply {
            addView(scene, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(pin, FrameLayout.LayoutParams(ui.dp(PIN_W_DP), ui.dp(PIN_H_DP), Gravity.CENTER))
        }
        val mainCard = ui.card().apply {
            addView(title)
            addView(picture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(ui.gap(16))
            addView(main)
            addView(ui.gap(8))
            addView(sub)
            addView(ui.gap(24))
            // 큰 버튼은 손이 닿기 쉬운 맨 아래에 있다(정차 카드와 같은 자리)
            addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val fuelCard = ui.card().apply {
            addView(fuelTitle)
            addView(ui.gap(12))
            addView(ui.row().apply {
                addView(fuelTank, LinearLayout.LayoutParams(ui.dp(130), ui.dp(190)))
                addView(View(context), LinearLayout.LayoutParams(ui.dp(32), 1))
                addView(ui.column().apply {
                    addView(fuelPercent)
                    addView(ui.gap(4))
                    addView(fuelDetail)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        tripCard.apply {
            addView(tripTitle)
            addView(ui.gap(8))
            addView(tripCost)
            addView(tripCaption)
        }

        val c = title.context.resources.configuration
        val wide = c.orientation == Configuration.ORIENTATION_LANDSCAPE && c.screenWidthDp >= 900
        val body = if (wide) ui.row().apply {
            gravity = Gravity.TOP
            addView(mainCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f))
            addView(View(context), LinearLayout.LayoutParams(ui.dp(GUTTER_DP), 1))
            addView(ui.column().apply {
                addView(fuelCard)
                addView(ui.gap(GUTTER_DP))
                addView(tripCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else ui.column().apply {
            // 세로 화면은 스크롤하지 않고 한 화면에 넣는다: 큰 카드가 남는 높이를 갖고 연료·운행은 아래에 나란히
            addView(mainCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(ui.gap(GUTTER_DP))
            addView(ui.row().apply {
                gravity = Gravity.TOP
                addView(fuelCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(View(context), LinearLayout.LayoutParams(ui.dp(GUTTER_DP), 1))
                addView(tripCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            })
        }
        cardsView = ui.column().apply {
            setPadding(ui.dp(GUTTER_DP), ui.dp(GUTTER_DP), ui.dp(GUTTER_DP), ui.dp(GUTTER_DP))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        view = FrameLayout(title.context).apply {
            setBackgroundColor(ui.p.background)
            addView(cardsView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(compactView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    fun render(m: ScreenModel.Driving) {
        station = m.addWaypoint
        val cards = m.cards
        setVisible(cardsView, cards != null)
        setVisible(compactView, cards == null)
        if (cards == null) {
            compactMain.show(m.main)
            compactMain.setTextColor(if (m.mainGain) ui.p.gain else ui.p.text)
            renderButton(compactButton, m)
            return
        }

        title.show(cards.title)
        main.show(m.main)
        main.setTextColor(if (m.mainGain) ui.p.gain else ui.p.text)
        sub.show(m.sub)
        renderButton(button, m)

        val s = cards.scene
        setVisible(scene, s is ScreenModel.DriveScene.Route)
        setVisible(pin, s is ScreenModel.DriveScene.Pin)
        when (s) {
            is ScreenModel.DriveScene.Route -> scene.chipText = s.chip
            is ScreenModel.DriveScene.Pin -> pin.color = when (s.kind) {
                ScreenModel.DrivePin.ACTIVE -> ui.p.button
                ScreenModel.DrivePin.PLAIN -> ui.p.subtext
            }
            null -> Unit
        }

        val f = cards.fuel
        fuelTitle.show(f.title)
        fuelTank.set(f.percent, f.electric)
        fuelPercent.show(f.percentText)
        fuelDetail.show(f.detail)

        val t = cards.trip
        setVisible(tripCard, t != null)
        if (t != null) {
            tripTitle.show(t.title)
            tripCost.show(t.costLine)
            tripCaption.show(t.caption)
        }
    }

    private fun renderButton(b: android.widget.TextView, m: ScreenModel.Driving) {
        setVisible(b, m.addWaypoint != null)
        if (b.text.toString() != m.buttonLabel) b.text = m.buttonLabel
    }

    private fun setVisible(v: View, visible: Boolean) {
        val want = if (visible) View.VISIBLE else View.GONE
        if (v.visibility != want) v.visibility = want
    }

    private companion object {
        const val PIN_W_DP = 150
        const val PIN_H_DP = 200
        const val BUTTON_DP = 120
        const val COMPACT_BUTTON_W_DP = 640
        const val GUTTER_DP = 24
    }
}
