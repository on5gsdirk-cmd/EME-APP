package be.on5gs.eme.n0uk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class N0ukParserTest {

    /** Echte N0UK-pagina van 13 november 2026; e-mailadressen vervangen door xxxx@test.com. */
    private val page = javaClass.classLoader!!.getResource("n0uk_jt65emeA.html")!!.readText()
    private val now = Instant.parse("2026-11-13T15:02:00Z").toEpochMilli()
    private val messages = N0ukParser.parse(page, now)

    private fun msg(text: String, call: String = "F4HBY", time: String = "16:11") =
        N0ukMessage(Instant.parse("2026-11-10T${time}:00Z").toEpochMilli(), "10Nov", time, text, call, "$call/2X15/700W", "Op", "IN97at")

    @Test
    fun parsesEveryLine() {
        assertEquals(50, messages.size)
        val first = messages.first()
        assertEquals("13Nov", first.date)
        assertEquals("12:03", first.time)
        assertEquals("OM4CW", first.callsign)
        assertEquals("OM4CW/1X18H/750", first.station)
        assertEquals("Vlado", first.name)
        assertEquals("JN88un", first.locator)
        assertEquals(Instant.parse("2026-11-13T12:03:00Z").toEpochMilli(), first.utcMillis)
        assertTrue(first.text.startsWith("DF2ZC Hi Bernd"))
    }

    @Test
    fun stationWithoutSlashAndHtmlEntities() {
        val w0pr = messages.first { it.callsign == "W0PR" }
        assertEquals("W0PR", w0pr.station)
        assertEquals("Larry", w0pr.name)
        assertEquals("EN34hl", w0pr.locator)
        // "&amp;" in de pagina wordt gewoon "&"
        assertTrue(messages.any { it.text.contains("73 & CUL aloha") })
    }

    @Test
    fun noEmailAddressesInParsedData() {
        messages.forEach { assertFalse(it.toString(), Regex("""[\w.]+@[\w-]+\.\w+""").containsMatchIn(it.toString())) }
    }

    @Test
    fun cqVariantsAreRecognised() {
        fun fields(t: String) = N0ukCqParser.parse(msg(t))!!.let { "${it.freq} ${it.period} ${it.mode}" }
        assertEquals("144.131 1st JT65B", fields("============== CQ CQ .131 JT65B 1st====="))
        assertEquals("144.124 2nd JT65B", fields("....CQ .124 JT65b 2nd"))
        assertEquals("144.130 1st Q65-60A", fields("     . CQ CQ CQ .130 1st Q65-60A --- CQ Q65-60A 1st .130 NH6V Hawaii"))
        assertEquals("144.120 ? ?", fields("CQ 144.120"))
        assertEquals("144.110 2nd JT65C", fields("cq .11 2nd jt65c"))
    }

    @Test
    fun decodesStopsAndChatAreNotCq() {
        assertEquals(N0ukKind.OTHER, N0ukCqParser.kind(msg("1544 -30  2.7 1586 #* CQ F4HBY IN97")))      // decode van iemand anders
        assertEquals(N0ukKind.OTHER, N0ukCqParser.kind(msg("1551 -24  2.8 1158 #* F4HBY KB9TVR EM49")))
        assertEquals(N0ukKind.STOP, N0ukCqParser.kind(msg("=== stop  cq .131")))
        assertEquals(N0ukKind.STOP, N0ukCqParser.kind(msg("Stop CQ .130")))
        assertEquals(N0ukKind.OTHER, N0ukCqParser.kind(msg("F4HBY Laurent I didn't know you were CQing")))
        assertEquals(N0ukKind.OTHER, N0ukCqParser.kind(msg("KB9TVR Robin Where is he transmitting?")))
        assertNull(N0ukCqParser.parse(msg("=== stop  cq .131")))
    }

    @Test
    fun recentCqsFromRealPageWithStops() {
        // Op 10 november om 16:20 UTC: venster 60 minuten
        val t = Instant.parse("2026-11-10T16:20:00Z").toEpochMilli()
        val cqs = N0ukCqParser.recent(N0ukParser.parse(page, t), t, 60)
        // F4HBY riep om 15:30 (om 15:57 gestopt) en om 16:11: alleen de meest recente (#124)
        assertEquals(listOf("16:11"), cqs.map { it.time })
        assertTrue(cqs.all { it.callsign == "F4HBY" && it.freq == "144.131" && it.mode == "JT65B" && it.period == "1st" })
        assertFalse("16:11 is nog bezig", cqs[0].ended)
        assertEquals("IN97at", cqs[0].locator)
    }

    @Test
    fun duplicatePostIsShownOnce() {
        val t = Instant.parse("2026-11-10T13:50:00Z").toEpochMilli()
        val cqs = N0ukCqParser.recent(N0ukParser.parse(page, t), t, 60)
        assertEquals(1, cqs.count { it.time == "13:40" })    // twee keer gepost om 13:40
    }

    @Test
    fun recentWritersNewestFirstWithoutMe() {
        val w = N0ukCqParser.recentWriters(messages, "OM4CW")
        assertEquals("WA4MYO", w.first().callsign)
        assertEquals(w.size, w.map { it.callsign }.toSet().size)
        assertFalse(w.any { it.callsign == "OM4CW" })
        // Met de datum zie je dat WA4MYO het laatst op 11 november schreef
        assertEquals("11Nov 15:44", w.first().dateTime)
        assertEquals("10Nov 16:28", w.first { it.callsign == "F4HBY" }.dateTime)
    }
}
