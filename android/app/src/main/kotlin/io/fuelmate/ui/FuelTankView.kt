package io.fuelmate.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 연료 통 그림: 통 안을 남은 양만큼 채운다. 전기차는 마개를 가운데 둔 배터리 모양에 번개 표시를 쓴다.
 *
 * [animateOnShow]가 true(정차 화면)면 보일 때 한 번 0에서 지금 양까지 채워지고 멈춘다.
 * false(주행 화면)면 움직이지 않는다(UI Design Guide: 운전 중 시선을 빼앗는 애니메이션 금지, docs/pleos/driving-restriction.md).
 * 값이 바뀌면(달리며 연료가 줄면) 움직임 없이 새 높이로 다시 그린다.
 */
class FuelTankView(
    context: Context,
    private val p: Palette,
    private val animateOnShow: Boolean,
) : View(context) {

    private var percent: Int? = null
    private var electric = false
    /** 채움 비율(0~1). 멈춰 있으면 1 */
    private var progress = 1f
    private var shown = false

    /** 채워지는 동안 매 장면마다 불린다. 옆의 % 숫자를 그림과 같이 올린다 */
    var onFrame: (() -> Unit)? = null

    /** 지금 그림에 보이는 양(%). 채워지는 중이면 올라가는 값 */
    val shownPercent: Int? get() = percent?.let { (it * progress).roundToInt() }

    private val density = resources.displayMetrics.density
    private val body = RectF()
    private val cap = RectF()
    private val inner = RectF()
    private val liquid = Path()
    private val icon = Path()
    private val clip = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = FILL_MS
        interpolator = DecelerateInterpolator(1.2f)
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
            onFrame?.invoke()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** 남은 양(0~100). 모르면 null(빈 통) */
    fun set(percent: Int?, electric: Boolean) {
        if (percent == this.percent && electric == this.electric) return
        val first = this.percent == null && percent != null
        this.percent = percent
        this.electric = electric
        // 처음 값이 들어온 순간에 보이는 중이면 그때 채운다. 그 뒤로는 움직이지 않고 새 높이로 그린다
        if (first && animateOnShow && shown) play() else invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible == shown) return
        shown = isVisible
        if (isVisible && animateOnShow && percent != null) play()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val line = (w * 0.06f).coerceIn(3f * density, 6f * density)
        val capH = h * 0.08f
        body.set(line / 2, capH + line / 2, w - line / 2, h - line / 2)
        val radius = w * 0.16f
        // 마개: 연료는 오른쪽 위(주유구), 전기는 가운데(배터리)
        val capW = w * 0.34f
        val capLeft = if (electric) (w - capW) / 2 else w - capW - w * 0.14f
        cap.set(capLeft, 0f, capLeft + capW, capH + line)

        fill.color = p.subtext
        canvas.drawRoundRect(cap, capH / 2, capH / 2, fill)
        fill.color = p.card
        canvas.drawRoundRect(body, radius, radius, fill)

        // 안쪽 채움: 위가 물결진 액체. 채워지는 동안만 물결이 움직이고, 멈추면 작은 물결로 남는다
        val pad = line * 1.6f
        inner.set(body.left + pad, body.top + pad, body.right - pad, body.bottom - pad)
        val level = (percent ?: 0) / 100f * progress
        val innerRadius = radius - pad / 2
        if (level > 0f) {
            val top = inner.bottom - inner.height() * level
            val amp = inner.height() * 0.018f * (1f + 2.5f * (1f - progress))
            val phase = (1f - progress) * 2f * PI.toFloat()
            liquid.rewind()
            liquid.moveTo(inner.left, inner.bottom)
            liquid.lineTo(inner.left, top)
            val steps = 24
            for (i in 0..steps) {
                val x = inner.left + inner.width() * i / steps
                val y = top + amp * sin(phase + i / steps.toFloat() * 2f * PI.toFloat())
                liquid.lineTo(x, y)
            }
            liquid.lineTo(inner.right, inner.bottom)
            liquid.close()
            canvas.save()
            clip.rewind()
            clip.addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW)
            canvas.clipPath(clip)
            fill.color = p.button
            canvas.drawPath(liquid, fill)
            canvas.restore()
        }

        // 눈금 25·50·75%
        stroke.color = withAlpha(p.subtext, 0.45f)
        stroke.strokeWidth = line * 0.5f
        for (q in 1..3) {
            val y = inner.bottom - inner.height() * q / 4f
            canvas.drawLine(inner.left, y, inner.left + inner.width() * 0.22f, y, stroke)
        }

        // 가운데 표시(물방울·번개): 빈 곳에서는 보조 색, 채운 곳에서는 카드 색으로 보인다
        buildIcon(inner.centerX(), inner.centerY(), inner.width() * 0.26f)
        fill.color = withAlpha(p.subtext, 0.55f)
        canvas.drawPath(icon, fill)
        if (level > 0f) {
            canvas.save()
            canvas.clipPath(liquid)
            fill.color = p.card
            canvas.drawPath(icon, fill)
            canvas.restore()
        }

        // 통 테두리
        stroke.color = p.subtext
        stroke.strokeWidth = line
        canvas.drawRoundRect(body, radius, radius, stroke)
    }

    private fun buildIcon(cx: Float, cy: Float, s: Float) {
        icon.rewind()
        if (electric) {
            icon.moveTo(cx + s * 0.15f, cy - s)
            icon.lineTo(cx - s * 0.55f, cy + s * 0.12f)
            icon.lineTo(cx - s * 0.02f, cy + s * 0.12f)
            icon.lineTo(cx - s * 0.15f, cy + s)
            icon.lineTo(cx + s * 0.55f, cy - s * 0.12f)
            icon.lineTo(cx + s * 0.02f, cy - s * 0.12f)
            icon.close()
        } else {
            // 물방울: 위가 뾰족하고 아래가 둥글다
            icon.moveTo(cx, cy - s)
            icon.cubicTo(cx + s * 0.25f, cy - s * 0.55f, cx + s * 0.75f, cy - s * 0.05f, cx + s * 0.75f, cy + s * 0.32f)
            icon.cubicTo(cx + s * 0.75f, cy + s * 0.75f, cx + s * 0.4f, cy + s, cx, cy + s)
            icon.cubicTo(cx - s * 0.4f, cy + s, cx - s * 0.75f, cy + s * 0.75f, cx - s * 0.75f, cy + s * 0.32f)
            icon.cubicTo(cx - s * 0.75f, cy - s * 0.05f, cx - s * 0.25f, cy - s * 0.55f, cx, cy - s)
            icon.close()
        }
    }

    private fun withAlpha(color: Int, alpha: Float) =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val FILL_MS = 1_800L
    }
}
