package io.fuelmate.ui

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import io.fuelmate.core.station.Station

/**
 * 정차 화면: 추천 카드(가장 크게) + 이번 운행 요약 + 계산 방법. 탐색·목록·펼치기는 정차 중에만 있다.
 * 가로가 넓으면 두 열(추천 | 운행), 아니면 한 열로 쌓는다(UI Design Guide: 가로·세로 레이아웃 모두).
 */
class ParkedScreen(private val ui: Ui, private val onAddWaypoint: (Station) -> Unit) {

    val view: View

    private val fuel = ui.text(TextSize.BODY, ui.p.subtext)
    private val recTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val recMessage = ui.text(TextSize.MAIN, ui.p.text)
    private val recName = ui.text(TextSize.MAIN, ui.p.text, bold = true)
    private val recPrice = ui.text(TextSize.BODY, ui.p.text)
    private val recGain = ui.text(TextSize.MAIN, ui.p.gain, bold = true)
    private val recBasis = ui.text(TextSize.SMALL, ui.p.subtext)
    private val recButton = ui.button("경유지 추가")
    private val waypoint = ui.text(TextSize.BODY, ui.p.text)
    private val othersTitle = ui.text(TextSize.SMALL, ui.p.subtext, bold = true).apply { text = "다른 곳" }
    private val others = ui.column()
    private val tripTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val tripSummary = ui.text(TextSize.BODY, ui.p.text, bold = true)
    private val tripDetail = ui.text(TextSize.SMALL, ui.p.subtext)
    private val explainButton = ui.text(TextSize.SMALL, ui.p.text, bold = true).apply {
        minHeight = ui.dp(56)
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        setOnClickListener { expanded = !expanded; render(last ?: return@setOnClickListener) }
    }
    private val explanation = ui.text(TextSize.SMALL, ui.p.subtext)
    private val tripCard = ui.card()
    private val priceLine = ui.text(TextSize.SMALL, ui.p.subtext)

    private var station: Station? = null
    private var expanded = false
    private var last: ScreenModel.Parked? = null
    private var shownOthers: List<ScreenModel.OtherOption>? = null

    init {
        recButton.setOnClickListener { station?.let(onAddWaypoint) }

        val recCard = ui.card().apply {
            addView(recTitle)
            addView(ui.gap(8))
            addView(recMessage)
            addView(recName)
            addView(recPrice)
            addView(ui.gap(12))
            addView(recGain)
            addView(recBasis)
            addView(ui.gap(20))
            addView(recButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ui.gap(12))
            addView(waypoint)
            addView(ui.divider())
            addView(othersTitle)
            addView(others)
        }
        tripCard.apply {
            addView(tripTitle)
            addView(ui.gap(8))
            addView(tripSummary)
            addView(tripDetail)
            addView(ui.gap(8))
            addView(explainButton)
            addView(explanation)
        }
        val side = ui.column().apply {
            addView(tripCard)
            addView(ui.gap(16))
            addView(priceLine)
        }

        val c = recCard.context.resources.configuration
        val wide = c.orientation == Configuration.ORIENTATION_LANDSCAPE && c.screenWidthDp >= 900
        val body = if (wide) ui.row().apply {
            gravity = android.view.Gravity.TOP
            addView(recCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            addView(View(context), LinearLayout.LayoutParams(ui.dp(24), 1))
            addView(side, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else ui.column().apply {
            addView(recCard)
            addView(ui.gap(20))
            addView(side)
        }
        val content = ui.column().apply {
            setPadding(ui.dp(40), ui.dp(32), ui.dp(40), ui.dp(40))
            addView(fuel)
            addView(ui.gap(16))
            addView(body)
        }
        view = ScrollView(recCard.context).apply {
            setBackgroundColor(ui.p.background)
            isFillViewport = true
            addView(content)
        }
    }

    fun render(m: ScreenModel.Parked) {
        last = m
        fuel.show(m.fuelLine)
        priceLine.show(m.priceLine)

        val r = m.rec
        station = r.station
        recTitle.show(r.title)
        recMessage.show(r.message)
        recName.show(r.name)
        recPrice.show(r.priceLine)
        recGain.show(r.gainLine)
        recGain.setTextColor(if (r.gainPositive) ui.p.gain else ui.p.text)
        recBasis.show(r.basisLine)
        recButton.visibility = if (r.station != null) View.VISIBLE else View.GONE
        waypoint.show(r.waypointLine)
        othersTitle.visibility = if (r.others.isNotEmpty()) View.VISIBLE else View.GONE
        if (shownOthers != r.others) {
            shownOthers = r.others
            others.removeAllViews()
            r.others.forEach { o ->
                others.addView(ui.row().apply {
                    setPadding(0, ui.dp(8), 0, ui.dp(8))
                    addView(ui.text(TextSize.SMALL, ui.p.text).apply { text = o.line }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(ui.button("경유지", TextSize.SMALL, minHeightDp = 56).apply { setOnClickListener { onAddWaypoint(o.station) } })
                })
            }
        }

        val t = m.trip
        tripCard.visibility = if (t != null) View.VISIBLE else View.GONE
        if (t != null) {
            tripTitle.show(t.title)
            tripSummary.show(t.summary)
            tripDetail.show(t.detail)
            explainButton.show(if (expanded) "계산 방법 닫기 ▲" else "계산 방법 보기 ▼")
            explanation.show(if (expanded) t.explanation.joinToString("\n") else null)
        }
    }
}
