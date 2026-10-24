package io.roadsensor.core.protocol

/**
 * 업로드 큐를 디스크에 저장하는 한 줄 형식: `종류,격자,분,값`
 * 예) `rain,wydku4g,29880605,2` / `dark,wydku4g,29880605,1` / `brake,wydku4g,29880605,3`
 * 저장 파일에도 원본 좌표·임시 ID는 넣지 않는다(서버로 보내는 것과 같은 정보만).
 */
object ObservationCodec {

    fun encode(o: Observation): String = when (o) {
        is Observation.Rain -> "rain,${o.cell},${o.t},${o.level}"
        is Observation.Dark -> "dark,${o.cell},${o.t},${if (o.on) 1 else 0}"
        is Observation.Brake -> "brake,${o.cell},${o.t},${o.dir}"
    }

    /** 읽을 수 없는 줄(파일 손상, 형식 변경)은 null. 큐 전체를 버리지 않고 그 줄만 건너뛴다. */
    fun decode(line: String): Observation? {
        val parts = line.trim().split(',')
        if (parts.size != 4) return null
        val (kind, cell, tText, vText) = parts
        if (!GEOHASH7.matches(cell)) return null
        val t = tText.toLongOrNull() ?: return null
        val v = vText.toIntOrNull() ?: return null
        return when (kind) {
            "rain" -> if (v in RAIN_CODES) Observation.Rain(cell, t, v) else null
            "dark" -> if (v == 0 || v == 1) Observation.Dark(cell, t, v == 1) else null
            "brake" -> if (v in 0..7) Observation.Brake(cell, t, v) else null
            else -> null
        }
    }

    private val GEOHASH7 = Regex("^[0-9b-hjkmnp-z]{7}$")
    private val RAIN_CODES = setOf(0, 1, 2, 3, 9)
}
