package io.fuelmate.ui

import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.station.Station
import kotlin.math.max

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
    private val recGain = ui.text(TextSize.GAIN, ui.p.gain, bold = true)
    private val recScene = RouteGainView(recCard.context, ui.p, GAIN_SCENE_MS)
    private val recSpacer = ui.spacer()
    private var scenePlace: String? = null
    private val recPlace = ui.text(TextSize.LEAD, ui.p.text, bold = true)
    private val recBasis = ui.text(TextSize.SMALL, ui.p.subtext)
    private val recCompare = ui.text(TextSize.BODY, ui.p.text)
    private val recButton = ui.button("여기 들르기", minHeightDp = BIG_BUTTON_DP)
    private val waypoint = ui.text(TextSize.BODY, ui.p.text)
    private val othersDivider = ui.divider()
    private val othersHeader = ui.row().also { row ->
        addColumns(row, "대신 들를 수 있는 곳", "가격", "우회", "이득", ui.p.subtext, TextSize.SMALL, bold = true)
        // 아래 줄의 [경유지] 버튼 자리만큼 비워 열을 맞춘다
        row.addView(View(row.context), LinearLayout.LayoutParams(ui.dp(BUTTON_COLUMN_DP), 1))
    }
    private val others = ui.column()
    private var shownOthers: Pair<List<ScreenModel.OtherOption>, String>? = null

    private val fuelTitle = ui.text(TextSize.TITLE, ui.p.subtext, bold = true)
    private val fuelPercent = ui.text(TextSize.HERO, ui.p.text, bold = true)
    /** 정차 중이라 보일 때 한 번 채워지는 연출을 한다 */
    private val fuelTank = FuelTankView(startCard.context, ui.p, animateOnShow = true)
    private val fuelDetail = ui.text(TextSize.BODY, ui.p.text)
    private val priceLine = ui.text(TextSize.SMALL, ui.p.subtext)

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
        recScene.onFrame = ::showGain
        fuelTank.onFrame = ::showFuelPercent

        startCard.apply {
            // 그림이 남는 높이를 모두 갖고, 조작은 손이 닿기 쉬운 아래쪽에 모은다. [어디로 가세요?]는 바로 가기 수와 상관없이 맨 아래에 있다
            addView(RouteGainView(context, ui.p, START_SCENE_MS).apply { minimumHeight = ui.dp(240) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
            // 그림이 남는 높이를 갖고, 그림이 없을 때(이득 없음·안내 문구)는 빈 칸이 대신한다. 큰 버튼은 손이 닿기 쉬운 맨 아래에 있다
            addView(recScene, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(recSpacer)
            addView(othersDivider)
            addView(othersHeader)
            addView(others)
            addView(ui.gap(20))
            addView(recButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(ui.gap(12))
            addView(waypoint)
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
            addView(ui.gap(16))
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
        fuelTank.set(f.percent, f.electric)
        showFuelPercent()
    }

    /** 그림은 이득이 있을 때만 보인다. 뷰가 다시 보일 때와 주유소가 바뀔 때만 재생되고, 금액만 바뀌면 다시 재생하지 않는다 */
    private fun renderScene(r: ScreenModel.RecCard) {
        val has = r.gainWon != null
        recScene.chipText = r.gainLine ?: ""
        val visible = if (has) View.VISIBLE else View.GONE
        val wasVisible = recScene.visibility == View.VISIBLE
        if (recScene.visibility != visible) recScene.visibility = visible
        recSpacer.visibility = if (has) View.GONE else View.VISIBLE
        val place = r.gainStationId
        if (has && wasVisible && scenePlace != null && place != scenePlace) recScene.replay()
        if (has) scenePlace = place
        showGain()
    }

    /** 통이 채워지는 동안 % 숫자도 같이 올라간다. 멈춰 있으면 모델의 글자 그대로 */
    private fun showFuelPercent() {
        val f = last?.fuel ?: return
        val now = fuelTank.shownPercent
        fuelPercent.show(if (f.percent != null && now != null && now < f.percent) "$now%" else f.percentText)
    }

    /** 재생 중이면 올라가는 금액을, 멈춰 있으면 [ScreenModel.RecCard.gainLine]을 그대로 보인다 */
    private fun showGain() {
        val r = last?.rec ?: return
        val won = r.gainWon
        val up = recScene.countUp
        // 시작 값이 "0원", "10원 미만"으로 보이지 않게 10원부터 올린다
        recGain.show(if (won != null && up < 1f) "${HabitText.won(max(10.0, won * up))} 이득" else r.gainLine)
        val scale = if (won != null) 1f + 0.08f * recScene.pulse else 1f
        // 글자 왼쪽을 기준으로 커져 옆으로 밀려 보이지 않게 한다
        recGain.pivotX = 0f
        recGain.pivotY = recGain.height / 2f
        recGain.scaleX = scale
        recGain.scaleY = scale
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
        recGain.setTextColor(if (r.gainPositive) ui.p.gain else ui.p.text)
        // 금액이 아닌 문장("가까운 곳에서 넣어도 차이가 작아요")은 줄이 넘치지 않게 작게
        recGain.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (r.gainPositive) TextSize.GAIN else TextSize.MAIN)
        renderScene(r)
        recBasis.show(r.basisLine)
        recCompare.show(r.compareLine)
        recButton.visibility = if (r.station != null) View.VISIBLE else View.GONE
        if (recButton.text.toString() != r.buttonLabel) recButton.text = r.buttonLabel
        waypoint.show(r.waypointLine)
        val hasOthers = r.others.isNotEmpty()
        othersDivider.visibility = if (hasOthers) View.VISIBLE else View.GONE
        othersHeader.visibility = if (hasOthers) View.VISIBLE else View.GONE
        if (shownOthers != r.others to r.othersAction) {
            shownOthers = r.others to r.othersAction
            others.removeAllViews()
            r.others.forEach { o ->
                others.addView(ui.row().apply {
                    setPadding(0, ui.dp(10), 0, ui.dp(10))
                    addColumns(this, o.name, o.price, o.detour, o.gain, ui.p.text, TextSize.BODY, bold = false)
                    addView(
                        ui.button(r.othersAction, TextSize.BODY, minHeightDp = ROW_BUTTON_DP).apply {
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
        const val START_SCENE_MS = 3_600L
        /** 금액을 빨리 읽어야 해서 첫 화면보다 짧다(UI Design Guide: 1~2초 안에 파악) */
        const val GAIN_SCENE_MS = 2_600L
        const val GUTTER_DP = 24
        const val BIG_BUTTON_DP = 120
        const val ROW_BUTTON_DP = 72
        const val BUTTON_COLUMN_DP = 124
    }
}
