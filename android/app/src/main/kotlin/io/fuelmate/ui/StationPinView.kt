package io.fuelmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * 주행 화면의 주유소 핀 그림(주유기 표시). 움직이지 않는다.
 * 첫 화면·추천 카드 그림([RouteGainView])의 핀과 같은 모양이라, 정차 화면에서 본 "이득인 주유소"와 이어져 보인다.
 */
class StationPinView(context: Context, private val p: Palette) : View(context) {

    /** 핀 색. 이득이면 초록, 경유 중이면 파랑, 차이가 작으면 회색 */
    var color: Int = p.gain
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val pin = Path()
    private val hose = Path()
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // 핀 전체 높이는 반지름의 2.7배, 너비는 2배
        val r = min(w / 2f, h / 2.7f) * 0.96f
        val tipX = w / 2f
        val tipY = (h + r * 2.7f) / 2f

        pin.rewind()
        pin.moveTo(tipX, tipY)
        pin.cubicTo(tipX - r * 0.35f, tipY - r * 0.6f, tipX - r, tipY - r * 1.05f, tipX - r, tipY - r * 1.7f)
        rect.set(tipX - r, tipY - r * 2.7f, tipX + r, tipY - r * 0.7f)
        pin.arcTo(rect, 180f, 180f, false)
        pin.cubicTo(tipX + r, tipY - r * 1.05f, tipX + r * 0.35f, tipY - r * 0.6f, tipX, tipY)
        pin.close()
        fill.color = color
        canvas.drawPath(pin, fill)

        // 주유기: 머리 중심 기준
        val cx = tipX
        val cy = tipY - r * 1.7f
        val u = r / 10f
        fill.color = p.card
        rect.set(cx - 5f * u, cy - 6f * u, cx + 2f * u, cy + 5f * u)
        canvas.drawRoundRect(rect, u, u, fill)
        rect.set(cx - 6f * u, cy + 4.5f * u, cx + 3f * u, cy + 6.5f * u)
        canvas.drawRoundRect(rect, u * 0.5f, u * 0.5f, fill)
        fill.color = color
        rect.set(cx - 3.4f * u, cy - 4.4f * u, cx + 0.4f * u, cy - u)
        canvas.drawRoundRect(rect, u * 0.5f, u * 0.5f, fill)
        hose.rewind()
        hose.moveTo(cx + 2f * u, cy - u)
        hose.lineTo(cx + 3.5f * u, cy - u)
        hose.lineTo(cx + 3.5f * u, cy + 2.5f * u)
        hose.quadTo(cx + 3.5f * u, cy + 4f * u, cx + 4.8f * u, cy + 4f * u)
        hose.quadTo(cx + 6f * u, cy + 4f * u, cx + 6f * u, cy + 2.5f * u)
        hose.lineTo(cx + 6f * u, cy - 2.5f * u)
        hose.lineTo(cx + 4f * u, cy - 4.5f * u)
        stroke.color = p.card
        stroke.strokeWidth = u * 1.1f
        canvas.drawPath(hose, stroke)
    }
}
