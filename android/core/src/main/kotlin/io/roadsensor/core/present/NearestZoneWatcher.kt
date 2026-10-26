package io.roadsensor.core.present

import io.roadsensor.core.model.RoadAlert

/**
 * 앞길 알림(안드로이드 알림)을 언제 띄우고 지울지 정한다. 순수 로직이라 단위 테스트로 확인한다.
 *
 * - 가장 가까운 구간이 **처음 보는 구간으로 바뀔 때** 한 번 알린다. 다가가며 거리가 줄어드는 건 다시 알리지 않는다.
 *   그래서 데모 경로에서는 어두움 → 급정거 → 비가 차례로 하나씩 알려진다.
 * - 같은 종류이고 격자가 하나라도 겹치면 같은 구간으로 본다(지나가면서 구간의 격자 목록이 줄어도 같은 구간).
 * - 화면이 보이는 동안(notify = false) 가장 가까워진 구간은 "이미 본 것"으로만 표시한다. 앱 화면에 같은 정보가 있다.
 * - 알린 구간이 앞길에서 사라지면(지나갔거나 서버에서 내려감) 알림을 지운다.
 */
class NearestZoneWatcher {

    sealed interface Decision {
        /** 이 구간으로 알림을 띄운다(이전 알림을 바꾼다). */
        data class Show(val alert: RoadAlert) : Decision
        /** 띄운 알림을 지운다. */
        data object Cancel : Decision
        data object Keep : Decision
    }

    /** 가장 가까웠던 적이 있고 아직 앞길에 남은 구간들. 다시 가장 가까워져도 알리지 않는다. */
    private var seen = emptyList<RoadAlert>()
    /** 지금 알림에 띄운 구간. */
    private var posted: RoadAlert? = null

    /** [alerts]는 거리순(AheadFilter). [notify] = 앱 화면이 안 보여서 알림을 띄워도 되는지. */
    fun onAlerts(alerts: List<RoadAlert>, notify: Boolean): Decision {
        // 앞길에서 사라진 구간은 잊고, 남은 구간은 지금 격자 목록으로 바꿔 둔다
        seen = seen.mapNotNull { s -> alerts.firstOrNull { sameZone(it, s) } }
        var cancel = false
        val p = posted
        if (p != null && (!notify || alerts.none { sameZone(it, p) })) {
            posted = null
            cancel = true
        }

        val nearest = alerts.firstOrNull()
        if (nearest != null && seen.none { sameZone(it, nearest) }) {
            seen = seen + nearest
            if (notify) {
                posted = nearest
                return Decision.Show(nearest)
            }
        }
        return if (cancel) Decision.Cancel else Decision.Keep
    }

    // 기상청 시뮬레이터에서는 구간 하나가 격자 수백 개라 집합으로 본다
    private fun sameZone(a: RoadAlert, b: RoadAlert): Boolean {
        if (a.kind != b.kind) return false
        val cells = b.cells.toHashSet()
        return a.cells.any(cells::contains)
    }
}
