package io.fuelmate.ui

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.station.Station

/**
 * 정차 화면: 추천 카드(가장 크게) + 이번 운행 요약 + 계산 방법. 탐색·목록·펼치기는 정차 중에만 있다.
 * 가로가 넓으면 두 열(추천 | 운행), 아니면 한 열로 쌓는다(UI Design Guide: 가로·세로 레이아웃 모두).
 */
class ParkedScreen(
    private val ui: Ui,
    private val onAddWaypoint: (Station) -> Unit,
    private val onOpenNavi: () -> Unit,
    private val onStartRoute: (SavedPlace) -> Unit,
) {

    val view: View

    private val fuel = ui.text(TextSize.BODY, ui.p.subtext)
    private val startCard = ui.card()
    private val startPlacesTitle = ui.text(TextSize.SMALL, ui.p.subtext, bold = true).apply { text = "바로 가기" }
    private val startPlaces = ui.column()
    private val startDivider = ui.divider()
    private var shownPlaces: List<ScreenModel.PlaceRow>? = null
    private val recTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val recMessage = ui.text(TextSize.MAIN, ui.p.text)
    private val recName = ui.text(TextSize.MAIN, ui.p.text, bold = true)
    private val recPrice = ui.text(TextSize.BODY, ui.p.text)
    private val recGain = ui.text(TextSize.MAIN, ui.p.gain, bold = true)
    private val recBasis = ui.text(TextSize.SMALL, ui.p.subtext)
    private val recCompare = ui.text(TextSize.BODY, ui.p.text)
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

    private lateinit var recCardView: View
    private var station: Station? = null
    private var expanded = false
    private var last: ScreenModel.Parked? = null
    private var shownOthers: List<ScreenModel.OtherOption>? = null

    init {
        recButton.setOnClickListener { station?.let(onAddWaypoint) }

        startCard.apply {
            addView(ui.text(TextSize.MAIN, ui.p.text, bold = true).apply { text = "가는 길에 실제로 이득인 주유소를 찾아 드려요" })
            addView(ui.gap(12))
            addView(ui.text(TextSize.BODY, ui.p.text).apply { text = "내 차 연료와 목적지를 보고, 돌아가는 기름값까지 뺀 금액으로 주유소를 골라요. 탭 한 번이면 내비 경유지로 들어가요." })
            addView(ui.gap(8))
            addView(ui.text(TextSize.SMALL, ui.p.subtext).apply { text = "운전이 끝나면 급제동·고속·긴 정차로 기름값을 약 얼마나 더 썼는지도 한 줄로 알려 드려요." })
            addView(ui.gap(24))
            addView(
                ui.button("어디로 가세요?").apply { setOnClickListener { onOpenNavi() } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(ui.gap(8))
            addView(ui.text(TextSize.SMALL, ui.p.subtext).apply { text = "내비에서 목적지를 검색해요. 안내가 시작된 뒤 이 앱을 다시 열면 추천이 보여요." })
            addView(startDivider)
            addView(startPlacesTitle)
            addView(startPlaces)
        }

        val recCard = ui.card().apply {
            addView(recTitle)
            addView(ui.gap(8))
            addView(recMessage)
            addView(recName)
            addView(recPrice)
            addView(ui.gap(12))
            addView(recGain)
            addView(recBasis)
            addView(recCompare)
            addView(ui.gap(20))
            addView(recButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ui.gap(12))
            addView(waypoint)
            addView(ui.divider())
            addView(othersTitle)
            addView(others)
        }
        recCardView = recCard
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

        val main = ui.column().apply {
            addView(startCard)
            addView(recCard)
        }
        val c = recCard.context.resources.configuration
        val wide = c.orientation == Configuration.ORIENTATION_LANDSCAPE && c.screenWidthDp >= 900
        val body = if (wide) ui.row().apply {
            gravity = android.view.Gravity.TOP
            addView(main, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            addView(View(context), LinearLayout.LayoutParams(ui.dp(24), 1))
            addView(side, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else ui.column().apply {
            addView(main)
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

    private fun renderPlaces(rows: List<ScreenModel.PlaceRow>) {
        val has = rows.isNotEmpty()
        startDivider.visibility = if (has) View.VISIBLE else View.GONE
        startPlacesTitle.visibility = if (has) View.VISIBLE else View.GONE
        if (shownPlaces == rows) return
        shownPlaces = rows
        startPlaces.removeAllViews()
        rows.forEach { row ->
            startPlaces.addView(ui.row().apply {
                setPadding(0, ui.dp(8), 0, ui.dp(8))
                addView(ui.text(TextSize.SMALL, ui.p.text).apply { text = row.line }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.button("안내", TextSize.SMALL, minHeightDp = 56).apply { setOnClickListener { onStartRoute(row.place) } })
            })
        }
    }

    fun render(m: ScreenModel.Parked) {
        last = m
        fuel.show(m.fuelLine)
        priceLine.show(m.priceLine)

        val start = m.start
        startCard.visibility = if (start != null) View.VISIBLE else View.GONE
        recCardView.visibility = if (start == null) View.VISIBLE else View.GONE
        if (start != null) renderPlaces(start.places)

        val r = m.rec
        station = r.station
        recTitle.show(r.title)
        recMessage.show(r.message)
        recName.show(r.name)
        recPrice.show(r.priceLine)
        recGain.show(r.gainLine)
        recGain.setTextColor(if (r.gainPositive) ui.p.gain else ui.p.text)
        recBasis.show(r.basisLine)
        recCompare.show(r.compareLine)
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
