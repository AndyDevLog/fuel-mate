package io.roadsensor.core.present

import io.roadsensor.core.geo.AheadProfile
import io.roadsensor.core.geo.AheadSegment
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.RainLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriveStripTest {

    private fun seg(from: Int, to: Int, kind: AlertKind, level: RainLevel = RainLevel.NONE) =
        AheadSegment(from, to, kind, level, cells = listOf("c$from"))

    private fun profile(vararg s: AheadSegment) = AheadProfile(emptyList(), 5_000, estimated = false, segments = s.toList())

    @Test
    fun `데모 경로처럼 어두움 안에서 급정거와 비가 이어진다`() {
        val items = DriveStrip.of(profile(
            seg(1_600, 4_000, AlertKind.RAIN, RainLevel.MODERATE),
            seg(0, 900, AlertKind.DARK),
            seg(1_200, 1_350, AlertKind.HARD_BRAKE),
        ))
        assertEquals(listOf("지금 어두움 ~900m", "급정거 1.2km", "비(중) 1.6km~"), items.map { it.label })
        // 지금 들어선 구간은 뒤 여유(150m)부터 칠한다
        assertEquals(-DriveStrip.BEHIND_M, items[0].fromM)
        // 앞 3km 밖은 잘라 낸다
        assertEquals(DriveStrip.AHEAD_M, items[2].toM)
    }

    @Test
    fun `앞 3km 밖에서 시작하는 구간은 띠에 없다`() {
        val items = DriveStrip.of(profile(seg(3_000, 3_600, AlertKind.RAIN, RainLevel.HEAVY), seg(500, 800, AlertKind.DARK)))
        assertEquals(listOf("어두움 500m~"), items.map { it.label })
    }

    @Test
    fun `지금 들어선 구간이 띠 끝까지 이어지면 거리를 말하지 않는다`() {
        val items = DriveStrip.of(profile(seg(0, 4_200, AlertKind.RAIN, RainLevel.HEAVY)))
        assertEquals("지금 비(강)", items.single().label)
    }

    @Test
    fun `앞길을 모르거나 구간이 없으면 빈 띠다`() {
        assertTrue(DriveStrip.of(null).isEmpty())
        assertTrue(DriveStrip.of(profile()).isEmpty())
    }
}
