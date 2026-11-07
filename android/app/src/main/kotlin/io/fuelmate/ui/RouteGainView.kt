package io.fuelmate.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

/**
 * 첫 화면과 추천 카드의 그림: 내 차가 경로를 따라 가다가, 지나친 주유소 대신 이득인 곳에 닿는다. 실제 지도가 아니라 설명 그림이다.
 * 첫 화면은 목적지를 넣기 전이라 숫자를 보이면 실제 값으로 오해할 수 있어 [chipText]가 "이득"뿐이고, 추천 카드는 실제 금액을 쓴다.
 *
 * 화면에 보일 때 한 번만 재생하고 마지막 장면에서 멈춘다. 멈춘 뒤에는 다시 그리지 않는다.
 * 주행 화면으로 바뀌거나 내비가 앞으로 나오면 바로 멈춘다(UI Design Guide: 운전 중 시선을 빼앗는 애니메이션 금지).
 * 상태 갱신과 무관하다: 뷰를 새로 만들지 않으므로 속도·위치가 바뀌어도 다시 재생되지 않는다.
 */
class RouteGainView(
    context: Context,
    private val p: Palette,
    durationMs: Long,
) : View(context) {

    /** 이득인 핀 위에 붙는 글자 */
    var chipText: String = "이득"
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 재생되는 동안 매 장면마다 불린다. 금액 숫자를 그림과 같은 시계로 맞춘다 */
    var onFrame: (() -> Unit)? = null

    /** 금액이 올라간 정도(0~1). 멈춰 있으면 1 */
    val countUp: Float get() = drive.getInterpolation(phase(progress, 0.08f, 0.88f))

    /** 차가 도착한 순간 금액을 한 번 키울 비율(0이면 원래 크기) */
    val pulse: Float get() = sin(PI.toFloat() * phase(progress, 0.84f, 1f))


    private val road = Path()
    private val roadSegment = Path()
    private val measure = PathMeasure()
    private val pin = Path()
    private val hose = Path()
    private val arrow = Path()
    private val rect = RectF()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextSize.BODY, resources.displayMetrics)
        color = p.card
    }
    private val argb = ArgbEvaluator()
    private val pop = OvershootInterpolator(2f)
    private val drive = AccelerateDecelerateInterpolator()

    private var pinR = 0f
    private var roadWidth = 0f
    private var roadLength = 0f

    /** 0~1. 멈춰 있을 때는 마지막 장면(1) */
    private var progress = 1f
    private var shown = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
            onFrame?.invoke()
        }
    }

    init {
        contentDescription = "경로 위의 주유소 가운데 이득인 곳을 고르는 그림"
        minimumHeight = (MIN_HEIGHT_DP * resources.displayMetrics.density).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(suggestedMinimumHeight, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val width = w.toFloat()
        val height = h.toFloat()
        val density = resources.displayMetrics.density
        pinR = (height * 0.085f).coerceIn(20f * density, 42f * density)
        roadWidth = pinR * 0.5f

        road.reset()
        road.moveTo(width * 0.04f, height * 0.62f)
        road.cubicTo(width * 0.20f, height * 0.84f, width * 0.32f, height * 0.82f, width * 0.46f, height * 0.66f)
        road.cubicTo(width * 0.58f, height * 0.52f, width * 0.66f, height * 0.60f, width * 0.74f, height * 0.68f)
        road.cubicTo(width * 0.82f, height * 0.76f, width * 0.90f, height * 0.68f, width * 0.94f, height * 0.50f)
        measure.setPath(road, false)
        roadLength = measure.length

        // 끝이 (0, 0)이고 머리가 위로 가는 핀
        val r = pinR
        pin.reset()
        pin.moveTo(0f, 0f)
        pin.cubicTo(-r * 0.35f, -r * 0.6f, -r, -r * 1.05f, -r, -r * 1.7f)
        rect.set(-r, -r * 2.7f, r, -r * 0.7f)
        pin.arcTo(rect, 180f, 180f, false)
        pin.cubicTo(r, -r * 1.05f, r * 0.35f, -r * 0.6f, 0f, 0f)
        pin.close()

        // 머리 중심이 (0, 0)인 주유기 호스
        val u = r / 10f
        hose.reset()
        hose.moveTo(2f * u, -u)
        hose.lineTo(3.5f * u, -u)
        hose.lineTo(3.5f * u, 2.5f * u)
        hose.quadTo(3.5f * u, 4f * u, 4.8f * u, 4f * u)
        hose.quadTo(6f * u, 4f * u, 6f * u, 2.5f * u)
        hose.lineTo(6f * u, -2.5f * u)
        hose.lineTo(4f * u, -4.5f * u)

        // 중심이 (0, 0)이고 오른쪽을 보는 내비 화살표
        val a = r * 0.72f
        arrow.reset()
        arrow.moveTo(a * 0.6f, 0f)
        arrow.lineTo(-a * 0.42f, a * 0.48f)
        arrow.lineTo(-a * 0.14f, 0f)
        arrow.lineTo(-a * 0.42f, -a * 0.48f)
        arrow.close()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible == shown) return
        shown = isVisible
        if (isVisible) {
            play()
        } else {
            animator.cancel()
            progress = 1f
            onFrame?.invoke()
        }
    }

    /** 보이는 중이면 처음부터 한 번 더 재생한다(추천 주유소가 바뀌었을 때) */
    fun replay() {
        if (shown) play()
    }

    private fun play() {
        progress = 0f
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (roadLength <= 0f) return
        val f = progress
        val highlight = phase(f, 0.80f, 0.92f)

        drawRoad(canvas, phase(f, 0f, 0.20f), drive.getInterpolation(phase(f, 0.30f, 0.88f)))
        drawFlag(canvas, phase(f, 0.16f, 0.26f))
        drawPlainPin(canvas, STOP_NEAR, phase(f, 0.18f, 0.28f), highlight)
        drawPlainPin(canvas, STOP_MIDDLE, phase(f, 0.24f, 0.34f), highlight)
        drawBestPin(canvas, phase(f, 0.30f, 0.40f), highlight, phase(f, 0.82f, 1f))
        drawCar(canvas, phase(f, 0.28f, 0.34f), drive.getInterpolation(phase(f, 0.30f, 0.88f)))
    }

    private fun drawRoad(canvas: Canvas, reveal: Float, traveled: Float) {
        if (reveal <= 0f) return
        stroke.strokeWidth = roadWidth
        stroke.color = withAlpha(p.subtext, 0.28f)
        roadSegment.rewind()
        measure.getSegment(0f, roadLength * reveal, roadSegment, true)
        canvas.drawPath(roadSegment, stroke)
        if (traveled > 0f) {
            stroke.color = p.button
            roadSegment.rewind()
            measure.getSegment(0f, roadLength * STOP_BEST * traveled, roadSegment, true)
            canvas.drawPath(roadSegment, stroke)
        }
    }

    private fun drawFlag(canvas: Canvas, appear: Float) {
        if (appear <= 0f) return
        measure.getPosTan(roadLength, pos, tan)
        val x = pos[0]
        val y = pos[1]
        val h = pinR * 2.2f
        val s = pop.getInterpolation(appear)
        canvas.save()
        canvas.scale(s, s, x, y)
        stroke.strokeWidth = pinR * 0.2f
        stroke.color = p.text
        canvas.drawLine(x, y, x, y - h, stroke)
        fill.color = p.text
        roadSegment.rewind()
        roadSegment.moveTo(x, y - h)
        roadSegment.lineTo(x - pinR * 1.2f, y - h + pinR * 0.5f)
        roadSegment.lineTo(x, y - h + pinR)
        roadSegment.close()
        canvas.drawPath(roadSegment, fill)
        canvas.restore()
    }

    private fun drawPlainPin(canvas: Canvas, at: Float, appear: Float, dim: Float) {
        if (appear <= 0f) return
        // 지나친 곳은 카드 색 쪽으로 흐려진다. 불투명 색이라 주유기 그림의 창이 비치지 않는다
        drawPin(canvas, at, pop.getInterpolation(appear), argb.evaluate(dim * 0.55f, p.subtext, p.card) as Int)
    }

    private fun drawBestPin(canvas: Canvas, appear: Float, highlight: Float, chip: Float) {
        if (appear <= 0f) return
        val color = argb.evaluate(highlight, p.subtext, p.gain) as Int
        // 강조되는 순간 한 번 커졌다가 조금 큰 크기로 남는다
        val grow = 1f + 0.22f * pop.getInterpolation(highlight)
        val scale = pop.getInterpolation(appear) * grow
        drawPin(canvas, STOP_BEST, scale, color)

        measure.getPosTan(roadLength * STOP_BEST, pos, tan)
        val x = pos[0]
        val tipY = pos[1] - roadWidth / 2f
        val headY = tipY - pinR * 1.7f * grow
        if (highlight in 0.01f..0.99f) {
            stroke.strokeWidth = pinR * 0.14f
            stroke.color = withAlpha(p.gain, 1f - highlight)
            canvas.drawCircle(x, headY, pinR * (1.2f + 1.3f * highlight), stroke)
        }
        drawChip(canvas, x, tipY - pinR * 2.7f * grow, chip)
    }

    private fun drawChip(canvas: Canvas, x: Float, pinTop: Float, appear: Float) {
        if (appear <= 0f) return
        val alpha = min(1f, appear * 2f)
        val fm = label.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val halfWidth = label.measureText(chipText) / 2f + pinR * 0.5f
        val height = textHeight + pinR * 0.3f
        val bottom = pinTop - pinR * 0.25f + (1f - alpha) * pinR * 0.5f
        rect.set(x - halfWidth, bottom - height, x + halfWidth, bottom)
        fill.color = withAlpha(p.gain, alpha)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, fill)
        label.alpha = (alpha * 255).toInt()
        canvas.drawText(chipText, x, rect.centerY() - (fm.ascent + fm.descent) / 2f, label)
    }

    private fun drawPin(canvas: Canvas, at: Float, scale: Float, color: Int) {
        measure.getPosTan(roadLength * at, pos, tan)
        canvas.save()
        canvas.translate(pos[0], pos[1] - roadWidth / 2f)
        canvas.scale(scale, scale)
        fill.color = color
        canvas.drawPath(pin, fill)
        canvas.translate(0f, -pinR * 1.7f)
        drawPump(canvas, color)
        canvas.restore()
    }

    /** 원점이 핀 머리 중심 */
    private fun drawPump(canvas: Canvas, pinColor: Int) {
        val u = pinR / 10f
        fill.color = p.card
        rect.set(-5f * u, -6f * u, 2f * u, 5f * u)
        canvas.drawRoundRect(rect, u, u, fill)
        rect.set(-6f * u, 4.5f * u, 3f * u, 6.5f * u)
        canvas.drawRoundRect(rect, u * 0.5f, u * 0.5f, fill)
        fill.color = pinColor
        rect.set(-3.4f * u, -4.4f * u, 0.4f * u, -1f * u)
        canvas.drawRoundRect(rect, u * 0.5f, u * 0.5f, fill)
        stroke.strokeWidth = u * 1.1f
        stroke.color = p.card
        canvas.drawPath(hose, stroke)
    }

    private fun drawCar(canvas: Canvas, appear: Float, traveled: Float) {
        if (appear <= 0f) return
        measure.getPosTan(roadLength * STOP_BEST * traveled, pos, tan)
        val r = pinR * 0.72f * pop.getInterpolation(appear)
        canvas.save()
        canvas.translate(pos[0], pos[1])
        canvas.rotate(Math.toDegrees(atan2(tan[1], tan[0]).toDouble()).toFloat())
        fill.color = p.card
        canvas.drawCircle(0f, 0f, r + pinR * 0.12f, fill)
        fill.color = p.button
        canvas.drawCircle(0f, 0f, r, fill)
        fill.color = p.buttonText
        canvas.scale(r / (pinR * 0.72f), r / (pinR * 0.72f))
        canvas.drawPath(arrow, fill)
        canvas.restore()
    }

    private fun phase(f: Float, from: Float, to: Float) = ((f - from) / (to - from)).coerceIn(0f, 1f)

    private fun withAlpha(color: Int, alpha: Float) =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val MIN_HEIGHT_DP = 180
        /** 경로 위 위치(0~1). 차는 앞의 두 곳을 지나 [STOP_BEST]에서 멈춘다 */
        const val STOP_NEAR = 0.20f
        const val STOP_MIDDLE = 0.46f
        const val STOP_BEST = 0.74f
    }
}
