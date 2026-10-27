package io.roadsensor.render.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.roadsensor.BuildConfig
import io.roadsensor.Log
import io.roadsensor.core.present.RenderModel
import io.roadsensor.core.present.RenderModelJson
import io.roadsensor.render.MapRenderer

/**
 * A안 렌더러: assets/web(shared/ 번들, Pixi)을 WebView로 띄우고 RenderModel JSON을 postWebMessage로 보낸다.
 * 오프라인으로 동작한다(외부 CDN 없음). 페이지 쪽도 모델이 올 때만 그리고, 애니메이션이 끝나면 렌더 루프를 멈춘다.
 *
 * 멈춤(pause): 숨기고 WebView.onPause(). 그동안 온 모델은 가장 최근 것 하나만 들고 있다가 resume()에서 보낸다.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebMapRenderer(context: Context) : MapRenderer {

    private val webView = WebView(context)
    override val view: View get() = webView

    private var latest: RenderModel? = null
    private var sent: RenderModel? = null
    private var ready = false
    private var paused = false

    init {
        // 디버그 빌드만 chrome://inspect로 들여다볼 수 있게 한다(fps 측정용)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.setBackgroundColor(Color.rgb(0x0b, 0x0f, 0x14))
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                ready = true
                flush()
            }

            // 지도 페이지 밖으로 나가지 않는다
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            // 페이지가 부르는 system-fonts/sans-<굵기>에 차량 기본 글꼴 파일을 돌려준다(SystemSansFont).
            // 없으면 null → 없는 asset이라 실패하고, 페이지는 다음 글꼴로 넘어간다
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val weight = request.url.toString().removePrefix(FONT_PREFIX)
                    .takeIf { request.url.toString().startsWith(FONT_PREFIX) }?.toIntOrNull() ?: return null
                val file = SystemSansFont.file(weight) ?: return null
                val mime = if (file.extension.equals("ttf", ignoreCase = true)) "font/ttf" else "font/otf"
                return WebResourceResponse(mime, null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), file.inputStream())
            }
        }
        if (BuildConfig.DEBUG) {
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.i("web ${m.messageLevel()}: ${m.message()}")
                    return true
                }
            }
        }
        webView.loadUrl(if (BuildConfig.DEBUG) "$PAGE?debug" else PAGE)
    }

    override fun render(model: RenderModel) {
        latest = model
        flush()
    }

    private fun flush() {
        val model = latest ?: return
        if (!ready || paused || model == sent) return
        // file:// 페이지는 출처가 불투명해서 대상 출처를 "*"로 둔다. 같은 앱 안의 페이지에만 간다.
        webView.postWebMessage(WebMessage(RenderModelJson.encode(model)), Uri.parse("*"))
        sent = model
    }

    override fun pause() {
        if (paused) return
        paused = true
        webView.visibility = View.GONE
        webView.onPause()
    }

    override fun resume() {
        if (!paused) return
        paused = false
        webView.visibility = View.VISIBLE
        webView.onResume()
        flush()
    }

    override fun destroy() {
        webView.destroy()
    }

    private companion object {
        const val PAGE = "file:///android_asset/web/index.html"
        const val FONT_PREFIX = "file:///android_asset/web/system-fonts/sans-"
    }
}
