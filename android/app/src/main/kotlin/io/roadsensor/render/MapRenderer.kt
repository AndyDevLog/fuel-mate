package io.roadsensor.render

import android.view.View
import io.roadsensor.core.present.RenderModel

/**
 * 정차 화면 렌더러(docs/architecture.md 5장). 구현은 WebView + Pixi(render/web)이고,
 * 막히면 이 인터페이스 아래만 Compose로 바꾼다. 주행 모드 한 줄 화면은 렌더러 밖(네이티브)이다.
 */
interface MapRenderer {
    val view: View

    /** 바뀐 모델만 넘긴다(부르는 쪽이 distinctUntilChanged). 멈춘 동안 온 것은 resume()에서 그린다. */
    fun render(model: RenderModel)

    /** 주행 중이거나 화면이 가려지면 멈춘다. 렌더러는 숨김·자원 정지를 스스로 한다. */
    fun pause()
    fun resume()

    fun destroy()
}
