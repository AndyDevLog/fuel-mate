package io.roadsensor.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObservationCodecTest {

    @Test
    fun `저장했다가 읽으면 그대로 돌아온다`() {
        val all = listOf(
            Observation.Rain("wydku4g", 29_880_605, 2),
            Observation.Rain("wydku4g", 29_880_605, 9),
            Observation.Dark("wydks2n", 29_880_606, true),
            Observation.Dark("wydks2n", 29_880_606, false),
            Observation.Brake("wydksx8", 29_880_607, 7),
        )
        assertEquals(all, all.map { ObservationCodec.decode(ObservationCodec.encode(it)) })
    }

    @Test
    fun `저장 형식`() {
        assertEquals("rain,wydku4g,29880605,2", ObservationCodec.encode(Observation.Rain("wydku4g", 29_880_605, 2)))
        assertEquals("dark,wydku4g,1,1", ObservationCodec.encode(Observation.Dark("wydku4g", 1, true)))
    }

    @Test
    fun `손상되거나 이상한 줄은 건너뛴다`() {
        listOf(
            "",
            "rain,wydku4g,29880605",          // 값 없음
            "rain,wydku4g,abc,2",             // 시각이 숫자가 아님
            "rain,wydku,29880605,2",          // 격자 7자리가 아님
            "rain,wydku4a,29880605,2",        // geohash에 없는 문자
            "rain,wydku4g,29880605,5",        // 없는 강도
            "brake,wydku4g,29880605,8",       // 방향 범위 밖
            "snow,wydku4g,29880605,1",        // 모르는 종류
            "rain,wydku4g,29880605,2,37.4",   // 필드가 더 있음(좌표 같은 것)
        ).forEach { assertNull(ObservationCodec.decode(it), it) }
    }
}
