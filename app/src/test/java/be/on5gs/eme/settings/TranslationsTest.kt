package be.on5gs.eme.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Controleert dat elke taal alle teksten heeft, met dezelfde invulplaatsen (%1$s ...) als het Engels. */
class TranslationsTest {

    private val res = File("src/main/res")
    private val languages = listOf("nl", "fr", "de", "es", "it", "pt", "sv", "da", "fi", "cs", "ru", "uk", "zh", "ja")

    private fun read(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun placeholders(s: String) = Regex("%\\d\\$[sd]").findAll(s).map { it.value }.sorted().toList()

    @Test
    fun allLanguagesComplete() {
        val en = read("values")
        assertTrue(en.size > 60)
        for (lang in languages) {
            val t = read("values-$lang")
            assertEquals("ontbrekende/extra teksten in $lang", en.keys, t.keys)
            for ((key, text) in t) {
                assertEquals("invulplaatsen $lang/$key", placeholders(en.getValue(key)), placeholders(text))
                assertTrue("lege tekst $lang/$key", text.isNotBlank())
            }
        }
    }

    /** De kolomkoppen van de Chat CQ-tabel komen uit chatcq_header: in elke taal 7 delen. */
    @Test
    fun chatCqHeaderHasEightColumns() {
        for (folder in listOf("values") + languages.map { "values-$it" }) {
            assertEquals(folder, 7, read(folder).getValue("chatcq_header").split(" - ").size)
        }
    }

    @Test
    fun everyLanguageInPickerHasFolder() {
        val codes = AppLanguage.options.map { it.first }
        assertEquals(15, codes.size)
        for (c in codes) assertTrue("map voor $c", c == "en" || File(res, "values-$c/strings.xml").exists())
    }
}
