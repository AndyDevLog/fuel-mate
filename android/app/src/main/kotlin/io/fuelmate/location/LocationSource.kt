package io.fuelmate.location

import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint

/**
 * 위치 공급원. 기본은 NaviHelper, 데모는 궤적 재생이다.
 * 에뮬레이터는 NaviHelper 위치가 고정이라(GNSS NMEA 체크섬 버그, docs/pleos/open-questions.md) NaviHelper 공급원이 직선 이동으로 대신한다.
 */
interface LocationSource {
    fun start(sink: LocationSink)
    fun stop()
}

interface LocationSink {
    fun onFix(fix: GeoFix)
    fun onDestination(destination: GeoPoint?)
}
