package io.fuelmate.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import io.fuelmate.core.cost.HabitTimeline
import io.fuelmate.core.cost.TimelineMarkKind

/**
 * "지난 운행" 카드의 운행 타임라인: 출발에서 도착까지 한 줄 위에 급제동·급감속(위, 빨강), 급가속(아래, 회색),
 * 긴 정차(줄 위 빨간 구간)를 운행 안 시각 순서로 둔다. 위치가 아니라 시간 순서다(운행 기록에 위치가 없다).
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
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextSize.SMALL, resources.displayMetrics)
        color = p.subtext
    }
    private val triangle = Path()
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
        contentDescription = "운행 중 급제동·급가속·긴 정차가 생긴 때를 출발부터 도착까지 보여 주는 그림"
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
        val mark = 13f * density
        val left = carR + 4f * density
        val right = w - carR - 4f * density
        val fm = label.fontMetrics
        val labelH = fm.descent - fm.ascent
        // 위로 쌓이는 표시(최대 MAX_STACK층 + 횟수 글자) + 줄 + 출발·도착 글자를 가운데로
        val above = trackW + mark * 1.3f * MAX_STACK + labelH
        val below = mark * 2.4f + labelH + 4f * density
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

        // 긴 정차: 차가 지나는 만큼 줄 위에 빨간 구간
        stroke.strokeWidth = trackW * 1.8f
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.color = withAlpha(p.alert, 0.6f)
        t.marks.filter { it.kind == TimelineMarkKind.IDLE && car > it.at }.forEach {
            val end = minOf(car, maxOf(it.end, it.at + 0.01f))
            canvas.drawLine(x(it.at), y, x(end), y, stroke)
        }
        stroke.strokeCap = Paint.Cap.ROUND

        // 급제동·급감속은 줄 위에서 아래를 가리키고, 급가속은 줄 아래에서 위를 가리킨다. 차가 지날 때 튀어나온다.
        // 가까이 몰린 표시는 겹치지 않게 줄에서 멀어지는 쪽으로 쌓고(최대 MAX_STACK층), 둘 이상이면 끝에 횟수를 쓴다
        drawMarks(canvas, t, TimelineMarkKind.BRAKING, y - trackW, mark, car, ::x)
        drawMarks(canvas, t, TimelineMarkKind.ACCEL, y + trackW, mark, car, ::x)

        // 출발·도착
        fill.color = withAlpha(p.subtext, appear)
        canvas.drawCircle(left, y, trackW * 0.9f, fill)
        canvas.drawCircle(right, y, trackW * 0.9f, fill)
        label.color = withAlpha(p.subtext, appear)
        val labelY = y + mark * 2.4f + 4f * density - fm.ascent
        label.textAlign = Paint.Align.LEFT
        canvas.drawText("출발", left - carR * 0.5f, labelY, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("도착", right + carR * 0.5f, labelY, label)

        drawCar(canvas, x(car), y, carR * appear)
    }

    /**
     * [kind] 표시를 그린다. [base]는 줄 가장자리(위 표시는 줄 위쪽, 아래 표시는 줄 아래쪽).
     * 앞 묶음의 첫 표시와 가로 거리가 표시 폭보다 가까우면 같은 묶음으로 보고 한 층씩 쌓는다
     */
    private fun drawMarks(canvas: Canvas, t: HabitTimeline, kind: TimelineMarkKind, base: Float, mark: Float, car: Float, x: (Float) -> Float) {
        val down = kind == TimelineMarkKind.BRAKING
        val dir = if (down) -1f else 1f
        val step = mark * 1.3f
        fill.color = if (down) p.alert else p.subtext
        val marks = t.marks.filter { it.kind == kind }
        var i = 0
        while (i < marks.size) {
            val cx = x(marks[i].at)
            var j = i
            while (j + 1 < marks.size && x(marks[j + 1].at) - cx < mark * 1.6f) j++
            // 이 묶음에서 차가 지나간 표시만 쌓는다. 멈춘 장면에서는 모두 다 자란 크기
            var shown = 0
            for (k in i..j) {
                val since = if (progress >= 1f) 1f else phase(car, marks[k].at, marks[k].at + POP_SPAN)
                if (since <= 0f) continue
                if (shown < MAX_STACK) {
                    val s = mark * pop.getInterpolation(since)
                    buildTriangle(cx, base + dir * step * shown, s, down)
                    canvas.drawPath(triangle, fill)
                }
                shown++
            }
            if (shown > 1) {
                label.textAlign = Paint.Align.CENTER
                label.color = fill.color
                label.isFakeBoldText = true
                val fm = label.fontMetrics
                val edge = base + dir * (step * minOf(shown, MAX_STACK) + 4f * density)
                val baseline = if (down) edge - fm.descent else edge - fm.ascent
                canvas.drawText("×$shown", cx, baseline, label)
                label.isFakeBoldText = false
            }
            i = j + 1
        }
    }

    private fun buildTriangle(cx: Float, tip: Float, s: Float, down: Boolean) {
        val dir = if (down) -1f else 1f
        triangle.rewind()
        triangle.moveTo(cx, tip)
        triangle.lineTo(cx - s * 0.75f, tip + dir * s * 1.3f)
        triangle.lineTo(cx + s * 0.75f, tip + dir * s * 1.3f)
        triangle.close()
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
        /** 몰린 표시를 쌓는 최대 층. 그보다 많으면 횟수 글자로만 센다 */
        const val MAX_STACK = 3
    }
}
