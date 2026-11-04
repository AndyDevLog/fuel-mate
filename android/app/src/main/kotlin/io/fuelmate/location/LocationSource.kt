package io.fuelmate.location

import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint

/**
 * 위치 공급원. 실차는 NaviHelper, 데모는 궤적 재생이다.
 * 에뮬레이터는 NaviHelper 위치가 고정이라(GNSS NMEA 체크섬 버그) 데모 궤적이 필요하다(docs/pleos/open-questions.md).
 */
interface LocationSource {
    fun start(sink: LocationSink)
    fun stop()
}

interface LocationSink {
    fun onFix(fix: GeoFix)
    fun onDestination(destination: GeoPoint?)
}
