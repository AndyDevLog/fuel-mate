package io.fuelmate.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import io.fuelmate.core.cost.HabitTimeline
import io.fuelmate.core.cost.TimelineMarkKind

/**
 * "지난 운행" 카드의 운행 타임라인: 출발에서 도착까지 한 줄 위에 급제동·급감속(위, 빨간 경고등 배지 (!)), 급가속(아래, 회색 » 배지)을
 * 운행 안 시각 순서로 둔다. 위치가 아니라 시간 순서다(운행 기록에 위치가 없다).
 *
 * 보일 때 한 번 차 아이콘이 출발에서 도착까지 지나가며 표시가 하나씩 튀어나오고 멈춘다. 카드 위의 금액은 [carAt]으로
 * [HabitTimeline.wonAt]을 읽어 같이 올라간다. 정차 화면에만 있어 주행 중에는 보이지 않는다(주행은 정지, 정차는 연출).
 * 운행 중(지금 운행)이면 움직이지 않는다.
 */
class TripTimelineView(context: Context, private val p: Palette) : View(context) {

    /** 재생되는 동안 매 장면마다 불린다 */
    var onFrame: (() -> Unit)? = null

    /** 차 아이콘이 지나온 비율(0~1). 멈춰 있으면 1 */
    val carAt: Float get() = drive.getInterpolation(phase(progress, CAR_FROM, CAR_TO))

    val playing: Boolean get() = animator.isRunning

    private var timeline: HabitTimeline? = null
    private var key: Long? = null
    private var animate = false
    private var progress = 1f
    private var shown = false

    private val density = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    /** 배지 옆 횟수(×6). 배지와 같은 색, 굵게 */
    private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextSize.BODY, resources.displayMetrics)
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextSize.SMALL, resources.displayMetrics)
        color = p.subtext
    }
    private val arrow = Path()
    private val pop = OvershootInterpolator(2.5f)
    private val drive = AccelerateDecelerateInterpolator()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SCENE_MS
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
            onFrame?.invoke()
        }
    }

    init {
        contentDescription = "운행 중 급제동·급가속이 생긴 때를 출발부터 도착까지 보여 주는 그림"
        minimumHeight = (MIN_HEIGHT_DP * density).toInt()
    }

    /**
     * [key]는 운행마다 다른 값(시작 시각). 새 운행이 들어오면 보이는 중일 때 다시 재생한다.
     * [animate]가 false(지금 운행)면 움직이지 않고 마지막 장면을 그린다.
     */
    fun set(timeline: HabitTimeline?, key: Long?, animate: Boolean) {
        val newTrip = key != this.key
        this.timeline = timeline
        this.key = key
        this.animate = animate
        if (!animate) {
            if (animator.isRunning) animator.cancel()
            progress = 1f
            invalidate()
            return
        }
        if (newTrip && shown && timeline != null) play() else if (!animator.isRunning) invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible == shown) return
        shown = isVisible
        if (isVisible && animate && timeline != null) play()
        if (!isVisible) {
            animator.cancel()
            progress = 1f
            onFrame?.invoke()
        }
    }

    private fun play() {
        progress = 0f
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = timeline ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val trackW = 10f * density
        val carR = 16f * density
        val badgeR = BADGE_DP * density
        val stem = 8f * density
        val left = carR + 4f * density
        val right = w - carR - 4f * density
        val fm = label.fontMetrics
        val labelH = fm.descent - fm.ascent
        // 위 급제동 배지 + 줄 + 아래 급가속 배지 + 출발·도착 글자를 가운데로
        val above = trackW / 2 + stem + badgeR * 2
        val below = trackW / 2 + stem + badgeR * 2 + 6f * density + labelH
        val y = (h - above - below) / 2f + above
        fun x(at: Float) = left + (right - left) * at

        val appear = phase(progress, 0f, CAR_FROM)
        val car = carAt

        // 줄: 지나온 곳은 파랑, 남은 곳은 회색
        stroke.strokeWidth = trackW
        stroke.color = withAlpha(p.divider, appear)
        canvas.drawLine(left, y, right, y, stroke)
        stroke.color = withAlpha(p.button, appear)
        if (car > 0f) canvas.drawLine(left, y, x(car), y, stroke)

        // 급제동·급감속은 줄 위에 빨간 경고등 배지 (!)(계기판 브레이크 경고등 모양), 급가속은 줄 아래 회색 » 배지.
        // 차가 지날 때 튀어나온다. 한 구간에 몰린 사건은 배지 하나에 옆으로 횟수(×6)를 쓴다
        drawMarks(canvas, t, TimelineMarkKind.BRAKING, y - trackW / 2, stem, badgeR, car, ::x)
        drawMarks(canvas, t, TimelineMarkKind.ACCEL, y + trackW / 2, stem, badgeR, car, ::x)

        // 출발·도착
        fill.color = withAlpha(p.subtext, appear)
        canvas.drawCircle(left, y, trackW * 0.9f, fill)
        canvas.drawCircle(right, y, trackW * 0.9f, fill)
        label.color = withAlpha(p.subtext, appear)
        val labelY = y + trackW / 2 + stem + badgeR * 2 + 6f * density - fm.ascent
        label.textAlign = Paint.Align.LEFT
        canvas.drawText("출발", left - carR * 0.5f, labelY, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("도착", right + carR * 0.5f, labelY, label)

        drawCar(canvas, x(car), y, carR * appear)
    }

    /**
     * [kind] 배지를 그린다. [edge]는 줄 가장자리(급제동은 줄 위쪽, 급가속은 줄 아래쪽). 줄에서 [stem]만큼 짧은 선으로 이어
     * 언제 일어났는지 가리킨다. 앞 묶음의 첫 배지와 가로 거리가 배지 폭보다 가까우면 한 구간으로 보고
     * 배지는 하나만, 둘 이상이면 옆에 횟수(×N)를 쓴다. 배지를 쌓으면 "쌓인 수 × N번"으로 읽혀 쓰지 않는다(2026-11-09 사용자 의견).
     * 연출 중에는 차가 지나간 사건만 세어 횟수가 1, 2, 3…으로 올라간다
     */
    private fun drawMarks(
        canvas: Canvas, t: HabitTimeline, kind: TimelineMarkKind, edge: Float, stem: Float, r: Float, car: Float, x: (Float) -> Float,
    ) {
        val braking = kind == TimelineMarkKind.BRAKING
        val dir = if (braking) -1f else 1f
        val color = if (braking) p.alert else p.subtext
        val marks = t.marks.filter { it.kind == kind }
        var i = 0
        while (i < marks.size) {
            val cx = x(marks[i].at)
            var j = i
            while (j + 1 < marks.size && x(marks[j + 1].at) - cx < r * 2.2f) j++
            // 멈춘 장면에서는 모두 센다
            var shown = 0
            var grow = 0f
            for (k in i..j) {
                val since = if (progress >= 1f) 1f else phase(car, marks[k].at, marks[k].at + POP_SPAN)
                if (since <= 0f) continue
                if (shown == 0) grow = since
                shown++
            }
            if (shown > 0) {
                stroke.strokeWidth = 2f * density
                stroke.color = color
                canvas.drawLine(cx, edge, cx, edge + dir * stem * grow, stroke)
                val cy = edge + dir * (stem + r)
                val rr = r * pop.getInterpolation(grow)
                if (rr > 0f) {
                    fill.color = p.card
                    canvas.drawCircle(cx, cy, rr + 2f * density, fill)
                    TimelineBadge.draw(canvas, cx, cy, rr, color, p.buttonText, braking)
                }
                if (shown > 1) {
                    // 배지 오른쪽. 오른쪽 끝을 넘으면 왼쪽
                    count.color = color
                    val text = "×$shown"
                    val tw = count.measureText(text)
                    val gap = 4f * density
                    val tx = if (cx + r + gap + tw <= width) cx + r + gap else cx - r - gap - tw
                    val fm = count.fontMetrics
                    canvas.drawText(text, tx, cy - (fm.ascent + fm.descent) / 2f, count)
                }
            }
            i = j + 1
        }
    }

    private fun drawCar(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        if (r <= 0f) return
        fill.color = p.card
        canvas.drawCircle(cx, cy, r + 3f * density, fill)
        fill.color = p.button
        canvas.drawCircle(cx, cy, r, fill)
        val a = r * 1.1f
        arrow.rewind()
        arrow.moveTo(cx + a * 0.6f, cy)
        arrow.lineTo(cx - a * 0.42f, cy + a * 0.48f)
        arrow.lineTo(cx - a * 0.14f, cy)
        arrow.lineTo(cx - a * 0.42f, cy - a * 0.48f)
        arrow.close()
        fill.color = p.buttonText
        canvas.drawPath(arrow, fill)
    }

    private fun phase(f: Float, from: Float, to: Float) = ((f - from) / (to - from)).coerceIn(0f, 1f)

    private fun withAlpha(color: Int, alpha: Float) =
        Color.argb((Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        /** 추천 카드 금액 연출과 같은 길이(1~2초 안에 파악, 너무 길지 않게) */
        const val SCENE_MS = 2_600L
        const val CAR_FROM = 0.08f
        const val CAR_TO = 0.92f
        /** 표시가 튀어나오는 데 걸리는 차 이동 비율 */
        const val POP_SPAN = 0.06f
        const val MIN_HEIGHT_DP = 150
        const val BADGE_DP = 21f
    }
}

/**
 * 타임라인 배지 그림. 급제동은 계기판 브레이크 경고등 모양(양쪽 괄호 + 느낌표), 급가속은 앞으로 향한 겹화살표(»).
 * 글자 대신 선으로 그려 작은 크기에서도 또렷하다. 타임라인과 범례가 같이 쓴다.
 */
object TimelineBadge {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val oval = RectF()
    private val path = Path()

    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, fg: Int, braking: Boolean) {
        if (r <= 0f) return
        fill.color = color
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = fg
        line.color = fg
        line.strokeWidth = r * 0.15f
        if (braking) {
            // 괄호: 가운데 원보다 조금 큰 원의 왼쪽·오른쪽 호
            val a = r * 0.62f
            oval.set(cx - a, cy - a, cx + a, cy + a)
            canvas.drawArc(oval, 140f, 80f, false, line)
            canvas.drawArc(oval, -40f, 80f, false, line)
            // 느낌표: 세로 막대 + 점
            line.strokeWidth = r * 0.17f
            canvas.drawLine(cx, cy - r * 0.40f, cx, cy + r * 0.08f, line)
            canvas.drawCircle(cx, cy + r * 0.34f, r * 0.10f, fill)
        } else {
            line.strokeWidth = r * 0.16f
            val h = r * 0.34f
            for (dx in floatArrayOf(-r * 0.26f, r * 0.14f)) {
                path.rewind()
                path.moveTo(cx + dx - h * 0.55f, cy - h)
                path.lineTo(cx + dx + h * 0.45f, cy)
                path.lineTo(cx + dx - h * 0.55f, cy + h)
                canvas.drawPath(path, line)
            }
        }
    }
}

/** 범례 글자 안의 원 배지. 타임라인 배지와 같은 그림이고 크기는 글자 높이에 맞춘다 */
class TimelineBadgeSpan(private val color: Int, private val fg: Int, private val braking: Boolean) : ReplacementSpan() {

    private fun radius(paint: Paint) = paint.textSize * 0.6f

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fm?.let { paint.getFontMetricsInt(it) }
        return (radius(paint) * 2).toInt() + 1
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val r = radius(paint)
        val fm = paint.fontMetrics
        TimelineBadge.draw(canvas, x + r, y + (fm.ascent + fm.descent) / 2f, r, color, fg, braking)
    }
}
