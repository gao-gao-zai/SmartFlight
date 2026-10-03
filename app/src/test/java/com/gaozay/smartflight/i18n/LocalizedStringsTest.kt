package com.gaozay.smartflight.i18n

import com.gaozay.smartflight.R
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Loads real resource text for plain JVM tests, without requiring Android stubs. */
abstract class LocalizedStringsTest {
    init {
        useLocale(Locale.CHINESE)
    }

    protected fun useLocale(locale: Locale) {
        AppStrings.installResolver(CatalogStringResolver(locale))
    }
}

private class CatalogStringResolver(private val locale: Locale) : StringResolver {
    override fun get(id: Int, vararg args: Any): String =
        format(catalog(locale).getValue(id).getValue("string"), args)

    override fun quantity(id: Int, count: Int, vararg args: Any): String {
        val values = catalog(locale).getValue(id)
        val key = if (locale.language == "en" && count == 1) "one" else "other"
        return format(values.getValue(key), args)
    }

    override fun inLocale(id: Int, locale: Locale): String =
        catalog(locale).getValue(id).getValue("string")

    private fun format(text: String, args: Array<out Any>): String =
        if (args.isEmpty()) text else String.format(locale, text, *args)

    private fun catalog(locale: Locale) = if (locale.language == "en") english else chinese

    companion object {
        private val chinese = readCatalog("values/strings.xml")
        private val english = readCatalog("values-en/strings.xml")

        private fun readCatalog(path: String): Map<Int, Map<String, String>> {
            val stream = checkNotNull(CatalogStringResolver::class.java.classLoader!!.getResourceAsStream(path))
            val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            val result = mutableMapOf<Int, Map<String, String>>()
            val elements = document.documentElement.childNodes
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val name = element.getAttribute("name")
                val type = if (element.tagName == "plurals") R.plurals::class.java else R.string::class.java
                val id = type.getField(name).getInt(null)
                if (element.tagName == "string") {
                    result[id] = mapOf("string" to decode(element.textContent))
                } else {
                    val items = element.getElementsByTagName("item")
                    result[id] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to decode(item.textContent)
                    }
                }
            }
            return result
        }

        private fun decode(text: String): String = text.removeSurrounding("\"")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
    }
}
