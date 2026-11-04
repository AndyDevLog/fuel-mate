package io.fuelmate.core.cost

import org.w3c.dom.Element

/**
 * 오피넷 "주유소 전국 평균가격(현재)" 오픈 API(https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=4).
 * 요청: `https://www.opinet.co.kr/api/avgAllPrice.do?out=xml&certkey=<인증키>`. 위치를 보내지 않는다.
 * 응답(문서 예시): `<RESULT><OIL><TRADE_DT>20250723</TRADE_DT><PRODCD>B027</PRODCD><PRODNM>휘발유</PRODNM>
 * <PRICE>1667.33</PRICE><DIFF>-0.23</DIFF></OIL>...</RESULT>`
 * JSON 응답 모양은 문서에 예시가 없어서 문서에 예시가 있는 XML을 쓴다.
 */
object OpinetAvgPrice {
    const val URL = "https://www.opinet.co.kr/api/avgAllPrice.do"

    /** 제품구분코드(문서 코드표) */
    const val GASOLINE = "B027"
    const val PREMIUM_GASOLINE = "B034"
    const val DIESEL = "D047"
    const val AUTO_LPG = "K015"

    fun requestUrl(certKey: String) = "$URL?out=xml&certkey=$certKey"

    /** 응답을 해석한다. 휘발유 가격이나 기준일이 없으면 null(키 오류 응답 등) */
    fun parse(xml: String): AvgFuelPrices? {
        val doc = SafeXml.parse(xml) ?: return null
        val oils = doc.getElementsByTagName("OIL")
        val prices = mutableMapOf<String, Double>()
        var date: String? = null
        for (i in 0 until oils.length) {
            val oil = oils.item(i) as? Element ?: continue
            val code = SafeXml.text(oil, "PRODCD") ?: continue
            val price = SafeXml.text(oil, "PRICE")?.toDoubleOrNull() ?: continue
            prices[code] = price
            if (code == GASOLINE) date = SafeXml.text(oil, "TRADE_DT")
        }
        val tradeDate = date?.takeIf { it.length == 8 && it.all(Char::isDigit) } ?: return null
        val gasoline = prices[GASOLINE]?.takeIf { it in PLAUSIBLE_WON } ?: return null
        return AvgFuelPrices(
            tradeDate = tradeDate,
            gasoline = gasoline,
            premiumGasoline = prices[PREMIUM_GASOLINE],
            diesel = prices[DIESEL],
            autoLpg = prices[AUTO_LPG],
        )
    }

    /** 리터당 이 범위 밖이면 단위·형식이 바뀐 것으로 보고 쓰지 않는다 */
    private val PLAUSIBLE_WON = 500.0..5_000.0

}
