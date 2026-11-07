package io.fuelmate.ui

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.station.Station

/**
 * 정차 화면: 왼쪽에 첫 화면 또는 추천 카드(가장 크게), 오른쪽에 연료·단가와 운행 요약. 탐색·목록·펼치기는 정차 중에만 있다.
 * 가로가 넓으면 두 열로 나누고 카드를 화면 높이까지 늘린다. 아니면 한 열로 쌓고 스크롤한다(UI Design Guide: 가로·세로 레이아웃 모두).
 */
class ParkedScreen(
    private val ui: Ui,
    private val onAddWaypoint: (Station) -> Unit,
    private val onOpenNavi: () -> Unit,
    private val onStartRoute: (SavedPlace) -> Unit,
) {

    val view: View

    private val startCard = ui.card()
    private val startPlacesTitle = ui.text(TextSize.SMALL, ui.p.subtext, bold = true).apply { text = "바로 가기" }
    private val startPlaces = ui.column()
    private val startDivider = ui.divider()
    private var shownPlaces: List<ScreenModel.PlaceRow>? = null

    private val recCard = ui.card()
    private val recTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val recMessage = ui.text(TextSize.MAIN, ui.p.text)
    private val recGain = ui.text(TextSize.HERO, ui.p.gain, bold = true)
    private val recPlace = ui.text(TextSize.LEAD, ui.p.text, bold = true)
    private val recBasis = ui.text(TextSize.SMALL, ui.p.subtext)
    private val recCompare = ui.text(TextSize.BODY, ui.p.text)
    private val recButton = ui.button("경유지 추가", minHeightDp = BIG_BUTTON_DP)
    private val waypoint = ui.text(TextSize.BODY, ui.p.text)
    private val othersDivider = ui.divider()
    private val othersHeader = ui.row().also { row ->
        addColumns(row, "다른 곳", "가격", "우회", "이득", ui.p.subtext, TextSize.SMALL, bold = true)
        // 아래 줄의 [경유지] 버튼 자리만큼 비워 열을 맞춘다
        row.addView(View(row.context), LinearLayout.LayoutParams(ui.dp(BUTTON_COLUMN_DP), 1))
    }
    private val others = ui.column()
    private var shownOthers: List<ScreenModel.OtherOption>? = null

    private val fuelTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val fuelPercent = ui.text(TextSize.HERO, ui.p.text, bold = true)
    private val fuelFill = View(startCard.context).apply { background = rounded(ui.p.button) }
    private val fuelRest = View(startCard.context)
    private val fuelGauge = ui.row().apply {
        background = rounded(ui.p.divider)
        addView(fuelFill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f))
        addView(fuelRest, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }
    private val fuelDetail = ui.text(TextSize.BODY, ui.p.text)
    private val priceLine = ui.text(TextSize.SMALL, ui.p.subtext)
    private var shownPercent: Int? = null

    private val tripCard = ui.card()
    private val tripTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val tripSummary = ui.text(TextSize.LEAD, ui.p.text, bold = true)
    private val tripDetail = ui.text(TextSize.BODY, ui.p.subtext)
    private val tripEmpty = ui.text(TextSize.BODY, ui.p.subtext).apply {
        text = "운전이 끝나면 급제동·고속·긴 정차로 기름값을 약 얼마나 더 썼는지 보여 드려요"
    }
    private val explainButton = ui.text(TextSize.BODY, ui.p.text, bold = true).apply {
        minHeight = ui.dp(64)
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        setOnClickListener { expanded = !expanded; render(last ?: return@setOnClickListener) }
    }
    private val explanation = ui.text(TextSize.SMALL, ui.p.subtext)

    private var station: Station? = null
    private var expanded = false
    private var last: ScreenModel.Parked? = null

    init {
        recButton.setOnClickListener { station?.let(onAddWaypoint) }

        startCard.apply {
            // 그림이 남는 높이를 모두 갖고, 조작은 손이 닿기 쉬운 아래쪽에 모은다. [어디로 가세요?]는 바로 가기 수와 상관없이 맨 아래에 있다
            addView(RouteIntroView(context, ui.p), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(ui.gap(16))
            addView(ui.text(TextSize.MAIN, ui.p.text, bold = true).apply { text = "가는 길에 실제로 이득인 주유소를 찾아 드려요" })
            addView(ui.gap(8))
            addView(ui.text(TextSize.LEAD, ui.p.subtext).apply { text = "돌아가는 기름값까지 뺀 금액으로 골라요" })
            addView(startDivider)
            addView(startPlacesTitle)
            addView(startPlaces)
            addView(ui.gap(20))
            addView(
                ui.button("어디로 가세요?", minHeightDp = BIG_BUTTON_DP).apply { setOnClickListener { onOpenNavi() } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(ui.gap(12))
            addView(ui.text(TextSize.SMALL, ui.p.subtext).apply { text = "내비에서 목적지를 검색해요. 안내가 시작된 뒤 이 앱을 다시 열면 추천이 보여요." })
        }

        recCard.apply {
            addView(recTitle)
            addView(ui.gap(8))
            addView(recMessage)
            addView(recGain)
            addView(ui.gap(8))
            addView(recPlace)
            addView(ui.gap(4))
            addView(recBasis)
            addView(recCompare)
            addView(ui.gap(32))
            addView(recButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ui.gap(12))
            addView(waypoint)
            addView(ui.spacer())
            addView(othersDivider)
            addView(othersHeader)
            addView(others)
        }

        val fuelCard = ui.card().apply {
            addView(fuelTitle)
            addView(ui.gap(4))
            addView(fuelPercent)
            addView(ui.gap(12))
            addView(fuelGauge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(20)))
            addView(ui.gap(16))
            addView(fuelDetail)
            addView(ui.gap(8))
            addView(priceLine)
        }
        tripCard.apply {
            addView(tripTitle)
            addView(ui.gap(12))
            addView(tripSummary)
            addView(ui.gap(4))
            addView(tripDetail)
            addView(tripEmpty)
            addView(ui.gap(12))
            addView(explainButton)
            addView(explanation)
        }

        val c = startCard.context.resources.configuration
        val wide = c.orientation == Configuration.ORIENTATION_LANDSCAPE && c.screenWidthDp >= 900
        // 넓은 화면은 공간이 넉넉해 계산 방법을 처음부터 펼쳐 둔다
        expanded = wide
        val cardHeight = if (wide) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val main = ui.column().apply {
            addView(startCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight))
            addView(recCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight))
        }
        val side = ui.column().apply {
            addView(fuelCard)
            addView(ui.gap(GUTTER_DP))
            addView(tripCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (wide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, if (wide) 1f else 0f))
        }
        val body = if (wide) ui.row().apply {
            gravity = android.view.Gravity.TOP
            addView(main, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f))
            addView(View(context), LinearLayout.LayoutParams(ui.dp(GUTTER_DP), 1))
            addView(side, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else ui.column().apply {
            addView(main)
            addView(ui.gap(GUTTER_DP))
            addView(side)
        }
        val content = ui.column().apply {
            setPadding(ui.dp(GUTTER_DP), ui.dp(GUTTER_DP), ui.dp(GUTTER_DP), ui.dp(GUTTER_DP))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (wide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, if (wide) 1f else 0f))
        }
        view = ScrollView(startCard.context).apply {
            setBackgroundColor(ui.p.background)
            isFillViewport = true
            addView(content)
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = ui.dp(10).toFloat()
    }

    /** 다른 곳 표의 네 열. 이름 열이 남는 폭을 쓰고 숫자 열은 폭을 고정해 줄끼리 세로로 맞춘다 */
    private fun addColumns(row: LinearLayout, name: String, price: String, detour: String, gain: String, color: Int, size: Float, bold: Boolean) {
        row.addView(ui.text(size, color, bold).apply { text = name }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ui.text(size, color, bold).apply { text = price }, LinearLayout.LayoutParams(ui.dp(132), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(ui.text(size, color, bold).apply { text = detour }, LinearLayout.LayoutParams(ui.dp(100), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(ui.text(size, color, bold).apply { text = gain }, LinearLayout.LayoutParams(ui.dp(160), ViewGroup.LayoutParams.WRAP_CONTENT))
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
                setPadding(0, ui.dp(10), 0, ui.dp(10))
                addView(ui.text(TextSize.LEAD, ui.p.text).apply { text = row.name }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.text(TextSize.SMALL, ui.p.subtext).apply { text = row.kind; setPadding(ui.dp(16), 0, ui.dp(24), 0) })
                addView(
                    ui.button("안내", TextSize.BODY, minHeightDp = ROW_BUTTON_DP).apply {
                        setPadding(0, 0, 0, 0)
                        setOnClickListener { onStartRoute(row.place) }
                    },
                    LinearLayout.LayoutParams(ui.dp(BUTTON_COLUMN_DP), ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            })
        }
    }

    private fun renderFuel(f: ScreenModel.FuelCard) {
        fuelTitle.show(f.title)
        fuelPercent.show(f.percentText)
        fuelDetail.show(f.detail)
        priceLine.show(f.priceLine)
        fuelGauge.visibility = if (f.percent != null) View.VISIBLE else View.GONE
        if (f.percent != null && f.percent != shownPercent) {
            shownPercent = f.percent
            fuelFill.layoutParams = (fuelFill.layoutParams as LinearLayout.LayoutParams).apply { weight = f.percent.toFloat() }
            fuelRest.layoutParams = (fuelRest.layoutParams as LinearLayout.LayoutParams).apply { weight = (100 - f.percent).toFloat() }
        }
    }

    fun render(m: ScreenModel.Parked) {
        last = m
        renderFuel(m.fuel)

        val start = m.start
        startCard.visibility = if (start != null) View.VISIBLE else View.GONE
        recCard.visibility = if (start == null) View.VISIBLE else View.GONE
        if (start != null) renderPlaces(start.places)

        val r = m.rec
        station = r.station
        recTitle.show(r.title)
        recMessage.show(r.message)
        recPlace.show(r.placeLine)
        recGain.show(r.gainLine)
        recGain.setTextColor(if (r.gainPositive) ui.p.gain else ui.p.text)
        // 금액이 아닌 문장("가까운 곳에서 넣어도 차이가 작아요")은 줄이 넘치지 않게 작게
        recGain.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (r.gainPositive) TextSize.HERO else TextSize.MAIN)
        recBasis.show(r.basisLine)
        recCompare.show(r.compareLine)
        recButton.visibility = if (r.station != null) View.VISIBLE else View.GONE
        waypoint.show(r.waypointLine)
        val hasOthers = r.others.isNotEmpty()
        othersDivider.visibility = if (hasOthers) View.VISIBLE else View.GONE
        othersHeader.visibility = if (hasOthers) View.VISIBLE else View.GONE
        if (shownOthers != r.others) {
            shownOthers = r.others
            others.removeAllViews()
            r.others.forEach { o ->
                others.addView(ui.row().apply {
                    setPadding(0, ui.dp(10), 0, ui.dp(10))
                    addColumns(this, o.name, o.price, o.detour, o.gain, ui.p.text, TextSize.BODY, bold = false)
                    addView(
                        ui.button("경유지", TextSize.BODY, minHeightDp = ROW_BUTTON_DP).apply {
                            setPadding(0, 0, 0, 0)
                            setOnClickListener { onAddWaypoint(o.station) }
                        },
                        LinearLayout.LayoutParams(ui.dp(BUTTON_COLUMN_DP), ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                })
            }
        }

        val t = m.trip
        tripTitle.show(t?.title ?: "지난 운행")
        tripSummary.show(t?.summary)
        tripDetail.show(t?.detail)
        tripEmpty.visibility = if (t == null) View.VISIBLE else View.GONE
        explainButton.show(if (t == null) null else if (expanded) "계산 방법 닫기 ▲" else "계산 방법 보기 ▼")
        explanation.show(if (t != null && expanded) t.explanation.joinToString("\n") else null)
    }

    private companion object {
        const val GUTTER_DP = 24
        const val BIG_BUTTON_DP = 120
        const val ROW_BUTTON_DP = 72
        const val BUTTON_COLUMN_DP = 124
    }
}
