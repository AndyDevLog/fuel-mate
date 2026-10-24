package io.roadsensor.core.present

import io.roadsensor.core.model.GeoPoint

/**
 * RenderModel → JSON. WebView 렌더러에 postWebMessage로 보낸다.
 * 형식은 shared/src/render-model.ts와 맞춘다. 키가 몇 개 안 되고 :core에 직렬화 라이브러리를 들이지 않으려고 직접 쓴다.
 */
object RenderModelJson {
    const val VERSION = 1

    fun encode(m: RenderModel): String = buildString(256 + m.cells.size * 72) {
        append("{\"v\":").append(VERSION)
        append(",\"screen\":").str(m.screen.name)
        append(",\"center\":").point(m.center)
        append(",\"heading\":").append(m.heading)
        append(",\"destination\":").point(m.destination)
        append(",\"ahead\":{\"radiusM\":").append(m.ahead.radiusM)
        append(",\"halfAngleDeg\":").append(m.ahead.halfAngleDeg)
        append(",\"corridorHalfWidthM\":").append(m.ahead.corridorHalfWidthM).append('}')
        append(",\"cells\":[")
        m.cells.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append("{\"cell\":").str(c.cell)
            append(",\"rain\":").append(c.rain)
            append(",\"rainLevel\":").append(c.rainLevel.code)
            append(",\"dark\":").append(c.dark)
            append(",\"brake\":").append(c.brake).append('}')
        }
        append("],\"alerts\":[")
        m.alerts.forEachIndexed { i, a ->
            if (i > 0) append(',')
            append("{\"kind\":").str(a.kind.name)
            append(",\"cell\":").str(a.cell)
            append(",\"distanceM\":").append(a.distanceM)
            append(",\"distanceText\":").str(a.distanceText)
            append(",\"label\":").str(a.label).append('}')
        }
        append("],\"myCar\":{\"wiperOn\":").append(m.myCar.wiperOn)
        append(",\"rainLevel\":").append(m.myCar.rainLevel.code)
        append(",\"lowBeamOn\":").append(m.myCar.lowBeamOn)
        append(",\"brakePressed\":").append(m.myCar.brakePressed).append('}')
        append(",\"online\":").append(m.online)
        append('}')
    }

    private fun StringBuilder.point(p: GeoPoint?): StringBuilder =
        if (p == null) append("null") else append("{\"lat\":").append(p.lat).append(",\"lon\":").append(p.lon).append('}')

    private fun StringBuilder.str(s: String): StringBuilder {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch < ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
        return append('"')
    }
}
