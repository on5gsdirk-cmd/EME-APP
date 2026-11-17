package be.on5gs.eme.chat

import be.on5gs.eme.ExpectedCsv
import be.on5gs.eme.n0uk.N0ukCqParser
import be.on5gs.eme.n0uk.N0ukMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Chat CQ (#124): elk station één keer, zijn meest recente CQ. Zelfde tabel als web/test/chatcq-latest.test.js. */
class ChatCqLatestTest {

    private val rows = ExpectedCsv.read("chatcq_latest")

    /** Wat er getoond moet worden: "CALL HH:MM shown|ended", nieuwste bovenaan. */
    private fun expected(case: List<Map<String, String>>) =
        case.filter { it.getValue("expect").isNotEmpty() }
            .sortedByDescending { it.getValue("time_utc") }
            .map { "${it.getValue("callsign")} ${it.getValue("time_utc")} ${it.getValue("expect")}" }

    private fun cases(chat: String) = rows.filter { it.getValue("chat") == chat }.groupBy { it.getValue("case") }

    @Test
    fun hb9q() {
        val now = Instant.parse("2026-11-08T09:10:00Z").toEpochMilli()
        for ((name, case) in cases("hb9q")) {
            // De chat geeft de nieuwste eerst, met het hoogste id
            val messages = case.mapIndexed { i, r ->
                val time = r.getValue("time_utc")
                ChatMessage(i.toLong() + 1, time, "Sun 8th of Nov - $time", r.getValue("callsign"), "Op", null,
                    "***** CQ ${r.getValue("freq")} 1st Q65-60C CFOM 1500 *****", isCq = true, senderId = "1")
            }.reversed()
            val shown = ChatCqParser.recent(messages, now).map { "${it.callsign} ${it.time} shown" }
            assertEquals(name, expected(case), shown)
        }
    }

    @Test
    fun n0uk() {
        val now = Instant.parse("2026-11-10T16:20:00Z").toEpochMilli()
        for ((name, case) in cases("n0uk")) {
            val messages = case.map { r ->
                val time = r.getValue("time_utc")
                val call = r.getValue("callsign")
                val text = if (r.getValue("kind") == "stop") "stop cq" else "CQ ${r.getValue("freq")} 1st JT65B"
                N0ukMessage(Instant.parse("2026-11-10T${time}:00Z").toEpochMilli(), "10Nov", time, text, call, call, "Op", "JO21")
            }.reversed()
            val shown = N0ukCqParser.recent(messages, now, 60).map { "${it.callsign} ${it.time} ${if (it.ended) "ended" else "shown"}" }
            assertEquals(name, expected(case), shown)
        }
    }
}
