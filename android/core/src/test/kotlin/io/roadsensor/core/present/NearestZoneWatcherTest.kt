package io.roadsensor.core.present

import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.model.RoadAlert
import io.roadsensor.core.present.NearestZoneWatcher.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NearestZoneWatcherTest {

    private fun zone(kind: AlertKind, distanceM: Int, vararg cells: String) =
        RoadAlert(kind, cells.first(), distanceM, RainLevel.MODERATE.takeIf { kind == AlertKind.RAIN } ?: RainLevel.NONE, cells.toList())

    // 데모 경로처럼 어두움 → 급정거 → 비 순서
    private val dark = zone(AlertKind.DARK, 600, "d1", "d2", "d3")
    private val brake = zone(AlertKind.HARD_BRAKE, 1_700, "b1")
    private val rain = zone(AlertKind.RAIN, 2_100, "r1", "r2", "r3")

    @Test
    fun `가장 가까운 구간만 한 번 알리고 다가가도 다시 알리지 않는다`() {
        val w = NearestZoneWatcher()
        assertEquals(Decision.Show(dark), w.onAlerts(listOf(dark, brake, rain), notify = true))
        // 다가가서 거리가 줄고 첫 격자를 지남
        val closer = zone(AlertKind.DARK, 0, "d2", "d3").copy(here = true)
        assertEquals(Decision.Keep, w.onAlerts(listOf(closer, brake, rain), notify = true))
    }

    @Test
    fun `가장 가까운 구간을 지나면 다음 구간을 차례로 알리고, 알린 구간이 사라지면 지운다`() {
        val w = NearestZoneWatcher()
        w.onAlerts(listOf(dark, brake, rain), notify = true)
        // 어두운 구간을 지남 → 급정거가 가장 가까워짐(알림이 바뀐다)
        assertEquals(Decision.Show(brake), w.onAlerts(listOf(brake, rain), notify = true))
        assertEquals(Decision.Show(rain), w.onAlerts(listOf(rain), notify = true))
        // 비 구간도 지남 → 지운다. 그다음 아무것도 없으면 그대로
        assertEquals(Decision.Cancel, w.onAlerts(emptyList(), notify = true))
        assertEquals(Decision.Keep, w.onAlerts(emptyList(), notify = true))
    }

    @Test
    fun `화면이 보이는 동안 가장 가까워진 구간은 나중에 뒤로 가도 알리지 않는다`() {
        val w = NearestZoneWatcher()
        assertEquals(Decision.Keep, w.onAlerts(listOf(dark, brake), notify = false))
        assertEquals(Decision.Keep, w.onAlerts(listOf(dark, brake), notify = true))
        // 다음 구간은 뒤에 있을 때 가장 가까워졌으니 알린다
        assertIs<Decision.Show>(w.onAlerts(listOf(brake), notify = true))
    }

    @Test
    fun `앱 화면이 다시 보이면 띄운 알림을 지운다`() {
        val w = NearestZoneWatcher()
        w.onAlerts(listOf(dark), notify = true)
        assertEquals(Decision.Cancel, w.onAlerts(listOf(dark), notify = false))
        // 다시 뒤로 가도 이미 본 구간이라 알리지 않는다
        assertEquals(Decision.Keep, w.onAlerts(listOf(dark), notify = true))
    }

    @Test
    fun `가장 가까운 구간이 잠깐 바뀌었다 돌아와도 같은 구간을 두 번 알리지 않는다`() {
        val w = NearestZoneWatcher()
        w.onAlerts(listOf(dark, brake), notify = true)
        // 서버 갱신으로 급정거가 잠깐 더 가까워졌다가
        val brakeNear = brake.copy(distanceM = 500)
        assertEquals(Decision.Show(brakeNear), w.onAlerts(listOf(brakeNear, dark), notify = true))
        // 다시 어두움이 가장 가까워짐: 이미 알린 구간
        assertEquals(Decision.Keep, w.onAlerts(listOf(dark, brakeNear), notify = true))
    }

    @Test
    fun `종류가 다르면 격자가 겹쳐도 다른 구간이다`() {
        val w = NearestZoneWatcher()
        val darkHere = zone(AlertKind.DARK, 300, "x1")
        val rainHere = zone(AlertKind.RAIN, 300, "x1")
        w.onAlerts(listOf(darkHere), notify = true)
        assertEquals(Decision.Show(rainHere), w.onAlerts(listOf(rainHere), notify = true))
    }

    @Test
    fun `알림 문구는 주행 배너와 같다`() {
        assertEquals("약 600m 앞 어두운 구간", DriveLine.lineOf(dark, limitStringLength = false))
        assertEquals("600m 어두움", DriveLine.lineOf(dark, limitStringLength = true))
        val withCars = rain.copy(reporters = 5)
        assertEquals("앞서 간 차 5대가 와이퍼를 켰습니다", DriveLine.detailOf(withCars, limitStringLength = false))
        assertEquals(null, DriveLine.detailOf(withCars, limitStringLength = true))
    }
}
