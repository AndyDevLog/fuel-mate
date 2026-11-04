package io.fuelmate.core.cost

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.reduce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitPricesTest {

    /** 오피넷 문서(apiId=4)의 예시 응답 */
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <RESULT>
          <OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>B034</PRODCD><PRODNM>고급휘발유</PRODNM><PRICE>1919.44</PRICE><DIFF>-0.10</DIFF></OIL>
          <OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>B027</PRODCD><PRODNM>휘발유</PRODNM><PRICE>1667.33</PRICE><DIFF>-0.23</DIFF></OIL>
          <OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>D047</PRODCD><PRODNM>자동차용경유</PRODNM><PRICE>1532.22</PRICE><DIFF>+0.39</DIFF></OIL>
          <OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>C004</PRODCD><PRODNM>실내등유</PRODNM><PRICE>1295.23</PRICE><DIFF>-0.72</DIFF></OIL>
          <OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>K015</PRODCD><PRODNM>자동차용부탄</PRODNM><PRICE>1052.64</PRICE><DIFF>-0.07</DIFF></OIL>
        </RESULT>
    """.trimIndent()

    @Test
    fun `오피넷 예시 응답에서 유종별 평균가와 기준일을 읽는다`() {
        val p = assertNotNull(OpinetAvgPrice.parse(sample))
        assertEquals("20250723", p.tradeDate)
        assertEquals(1667.33, p.gasoline)
        assertEquals(1919.44, p.premiumGasoline)
        assertEquals(1532.22, p.diesel)
        assertEquals(1052.64, p.autoLpg)
    }

    @Test
    fun `휘발유가 없거나 형식이 깨진 응답, 말이 안 되는 가격은 쓰지 않는다`() {
        assertNull(OpinetAvgPrice.parse("<RESULT></RESULT>"))
        assertNull(OpinetAvgPrice.parse("인증키 오류"))
        assertNull(OpinetAvgPrice.parse(sample.replace("1667.33", "1.66733")))
        assertNull(OpinetAvgPrice.parse(sample.replace("<TRADE_DT>20250723</TRADE_DT><PRODCD>B027", "<TRADE_DT>2025</TRADE_DT><PRODCD>B027")))
    }

    @Test
    fun `외부 엔티티를 넣은 응답은 해석하지 않는다`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
            "<RESULT><OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>B027</PRODCD><PRICE>&x;</PRICE></OIL></RESULT>"
        assertNull(OpinetAvgPrice.parse(xxe))
    }

    @Test
    fun `평균가를 받으면 연료 단가와 근거가 바뀌고, 충전 단계는 따로 바뀐다`() {
        val s0 = AppState()
        assertTrue(s0.prices.fuelAssumed)
        assertEquals(1_700.0, s0.prices.of(EnergyKind.FUEL))
        assertEquals(348.4, s0.prices.of(EnergyKind.ELECTRIC))

        val s1 = reduce(s0, Action.FuelPriceUpdated(OpinetAvgPrice.parse(sample)!!))
        assertEquals(1667.33, s1.prices.of(EnergyKind.FUEL))
        assertEquals("오피넷 전국 평균 휘발유(2025-07-23)", s1.prices.basis(EnergyKind.FUEL))

        val s2 = reduce(s1, Action.ChargeTierChanged(ChargeTier.ULTRA))
        assertEquals(393.1, s2.prices.of(EnergyKind.ELECTRIC))
        assertEquals(1667.33, s2.prices.of(EnergyKind.FUEL))
        assertEquals("공공 충전요금 초급속 200kW 이상", s2.prices.basis(EnergyKind.ELECTRIC))
    }
}
