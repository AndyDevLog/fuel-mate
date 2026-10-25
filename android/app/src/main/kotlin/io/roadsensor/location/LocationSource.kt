package io.roadsensor.location

import io.roadsensor.core.model.GeoFix
import io.roadsensor.core.model.GeoPoint

/**
 * 위치 공급원. 실차는 NaviHelper, 데모는 궤적 재생이다.
 * 에뮬레이터는 NaviHelper 위치가 고정이라(GNSS NMEA 체크섬 버그) 데모 궤적이 필요하다(docs/pleos/open-questions.md).
 */
interface LocationSource {
    fun start(sink: LocationSink)
    fun stop()
}

interface LocationSink {
    /**
     * 위치와 그 순간 앞으로 갈 길(첫 점 = 지금 위치)을 함께 받는다. 앞길을 모르면 null.
     * 앞길은 화면용이고 저장·전송하지 않는다.
     */
    fun onFix(fix: GeoFix, routeAhead: List<GeoPoint>?)
    fun onDestination(destination: GeoPoint?)
}
