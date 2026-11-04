package io.fuelmate.core.cost

import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** 외부(오피넷) XML 응답 해석. 외부 엔티티·DTD를 막는다. 지원하지 않는 기능 설정은 건너뛴다 */
object SafeXml {
    fun parse(xml: String): Document? = runCatching {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }.getOrNull()

    fun text(e: Element, tag: String): String? =
        e.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
