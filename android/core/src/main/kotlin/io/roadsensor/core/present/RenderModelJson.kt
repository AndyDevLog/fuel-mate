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
        append(",\"route\":")
        val r = m.route
        if (r == null) append("null") else {
            append("{\"path\":[")
            r.path.forEachIndexed { i, p -> if (i > 0) append(','); append('[').append(p.lat).append(',').append(p.lon).append(']') }
            append("],\"lengthM\":").append(r.lengthM)
            append(",\"estimated\":").append(r.estimated)
            append(",\"segments\":[")
            r.segments.forEachIndexed { i, s ->
                if (i > 0) append(',')
                append("{\"fromM\":").append(s.fromM).append(",\"toM\":").append(s.toM)
                append(",\"kind\":").str(s.kind.name).append(",\"rainLevel\":").append(s.rainLevel.code).append('}')
            }
            append("]}")
        }
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
            append(",\"label\":").str(a.label)
            append(",\"headline\":").str(a.headline)
            append(",\"here\":").append(a.here)
            append(",\"cells\":[")
            a.cells.forEachIndexed { j, c -> if (j > 0) append(','); str(c) }
            append("],\"lengthText\":")
            if (a.lengthText == null) append("null") else str(a.lengthText)
            append('}')
        }
        val car = m.myCar
        append("],\"myCar\":{\"wiperOn\":").append(car.wiperOn)
        append(",\"wiperText\":"); if (car.wiperText == null) append("null") else str(car.wiperText)
        append(",\"rainLevel\":").append(car.rainLevel.code)
        append(",\"lowBeamOn\":").append(car.lowBeamOn)
        append(",\"tailLampOn\":").append(car.tailLampOn)
        append(",\"brakePressed\":").append(car.brakePressed)
        append(",\"gear\":").str(car.gear.name)
        append(",\"telltales\":")
        if (car.telltales == null) append("null")
        else { append('['); car.telltales.forEachIndexed { i, t -> if (i > 0) append(','); str(t) }; append(']') }
        append(",\"hardBrakes\":").append(car.hardBrakes).append('}')
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
