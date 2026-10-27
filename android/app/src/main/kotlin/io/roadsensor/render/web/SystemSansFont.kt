package io.roadsensor.render.web

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.SystemFonts
import android.os.Build
import io.roadsensor.Log
import java.io.File

/**
 * 차량 기본 글꼴(sans-serif)의 실제 파일을 굵기별로 찾는다. WebView는 Pleos가 바꿔 둔 기본 글꼴(Asta Sans)을
 * 보지 못하고 Roboto·Noto로 그리기 때문에, 이 파일을 WebView에 넘겨 같은 글꼴로 그린다(WebMapRenderer).
 *
 * 파일 이름이나 Pleos 설정 파일에 기대지 않는다: 시스템 글꼴 가운데 같은 글자의 폭이 기본 글꼴과 똑같은 파일을 고른다.
 * 그래서 차량 글꼴이 바뀌어도 따라간다. 글꼴을 앱에 넣지 않으니 앱 크기도 그대로다.
 */
object SystemSansFont {

    private val cache = mutableMapOf<Int, File?>()

    /** 없으면 null(안드로이드 10 미만, 또는 같은 폭의 파일이 없을 때). 그때 페이지는 기본 글꼴로 돌아간다. */
    @Synchronized
    fun file(weight: Int): File? = cache.getOrPut(weight) {
        runCatching { find(weight) }
            .onFailure { Log.w("기본 글꼴 파일을 찾지 못함 (굵기 $weight)", it) }
            .getOrNull()
            .also { Log.i("기본 글꼴 굵기 $weight → ${it?.name ?: "없음(페이지 기본 글꼴 사용)"}") }
    }

    private fun find(weight: Int): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val target = width(Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false))
        return SystemFonts.getAvailableFonts()
            .filter { it.style.weight == weight && it.style.slant == 0 && it.file?.canRead() == true }
            .firstOrNull { width(typefaceOf(it)) == target }
            ?.file
    }

    private fun typefaceOf(f: Font): Typeface =
        Typeface.CustomFallbackBuilder(FontFamily.Builder(f).build()).build()

    private fun width(t: Typeface): Float = Paint().apply { typeface = t; textSize = 100f }.measureText(SAMPLE)

    // 한글·영문·숫자를 섞어야 한글만 대체 글꼴로 그려지는 파일까지 가려진다
    private const val SAMPLE = "약 600m 앞 어두운 구간 Road 123"
}
