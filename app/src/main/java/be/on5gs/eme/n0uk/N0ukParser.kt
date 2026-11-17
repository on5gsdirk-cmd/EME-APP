package be.on5gs.eme.n0uk

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Eén bericht uit de N0UK WSJT EME-chat (www.chris.org/cgi-bin/jt65emeA, alleen 144 MHz). */
data class N0ukMessage(
    val utcMillis: Long,
    val date: String,          // "13Nov" zoals op de pagina
    val time: String,          // "12:03" (UTC)
    val text: String,
    val callsign: String,      // "F4HBY"
    val station: String,       // "F4HBY/2X15/700W" (callsign met antennes/vermogen, zoals opgegeven)
    val name: String,          // "Laurent"
    val locator: String,       // "IN97at", of "" als onbekend
) {
    /** Datum en tijd zoals op de site: "10Nov 16:28" (UTC). */
    val dateTime: String get() = "$date $time"
}

/** Soort bericht, voor de CQ-lijst. */
enum class N0ukKind { CQ, STOP, OTHER }

/** Een CQ-aanroep, ontleed. Onbekend = "?". */
data class N0ukCq(
    val utcMillis: Long,
    val time: String,
    val freq: String,          // "144.131"
    val period: String,        // "1st" / "2nd" / "?"
    val callsign: String,
    val mode: String,          // "JT65B", "Q65-60A", ... of "?"
    val locator: String,
    val ended: Boolean,        // later "stop cq" van hetzelfde station
)

/**
 * Leest de N0UK-chatpagina. De berichten staan als platte tekst in een <pre>-blok, één per regel:
 *
 *   13Nov 12:03 tekst van het bericht ====== {<a href="javascript:mailto(...)">F4HBY/2X15/700W</a> Laurent xx IN97at }
 *
 * Na de link volgen naam, staat/provincie ("xx" als die er niet is) en de locator.
 */
object N0ukParser {

    private val LINE = Regex("""^(\d{1,2})([A-Za-z]{3}) (\d{2}):(\d{2}) (.*) ====== \{<a [^>]*>([^<]*)</a>\s*(.*?)\s*\}\s*$""")
    private val GRID = Regex("""^[A-R]{2}\d{2}([A-X]{2})?$""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Alle berichten van de pagina, in de volgorde van de pagina (nieuwste bovenaan). */
    fun parse(html: String, nowMillis: Long): List<N0ukMessage> {
        val pre = Regex("""<pre>(.*?)</pre>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC)
        return pre.lines().mapNotNull { raw ->
            val m = LINE.find(raw.trim()) ?: return@mapNotNull null
            val (day, mon, hh, mm, text, station, rest) = m.destructured
            val month = MONTHS.indexOf(mon.lowercase()) + 1
            if (month == 0) return@mapNotNull null
            val dayNr = day.toInt()
            var date = runCatching { LocalDate.of(now.year, month, dayNr) }.getOrNull() ?: return@mapNotNull null
            if (date.isAfter(now.toLocalDate().plusDays(1))) date = date.minusYears(1)   // jaarwissel
            val millis = date.atTime(hh.toInt(), mm.toInt()).toInstant(ZoneOffset.UTC).toEpochMilli()
            val parts = Parser.unescapeEntities(rest, false).trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val locator = parts.lastOrNull()?.takeIf { GRID.matches(it) } ?: ""
            val nameParts = if (locator.isNotEmpty()) parts.dropLast(1) else parts
            // Het voorlaatste veld is de staat/provincie ("xx" als die ontbreekt)
            val name = (if (nameParts.size >= 2) nameParts.dropLast(1) else nameParts).joinToString(" ")
            val stationText = Parser.unescapeEntities(station, false).trim()
            N0ukMessage(
                utcMillis = millis,
                date = "$day$mon",
                time = "$hh:$mm",
                text = Jsoup.parse(text).text().trim(),
                callsign = stationText.substringBefore('/').uppercase(),
                station = stationText,
                name = name,
                locator = locator,
            )
        }
    }
}

/**
 * Herkent CQ-aanroepen in de N0UK-chat. Die zijn niet gestandaardiseerd, bv.
 *   "============== CQ CQ .131 JT65B 1st====="
 *   "....CQ .124 JT65b 2nd"
 *   "CQ CQ CQ .130 1st Q65-60A --- CQ Q65-60A 1st .130 NH6V Hawaii"
 * ".131" betekent 144.131 MHz. Een gepost decode van iemand anders ("1544 -30 2.7 1586 #* CQ F4HBY IN97")
 * is geen CQ van de afzender. "stop cq .131" beëindigt een CQ.
 */
object N0ukCqParser {

    private val CQ = Regex("""(?i)\bcq\b""")
    private val STOP = Regex("""(?i)\bstop\b""")
    private val DECODE = Regex("""^\s*\d{4}\s+-?\d+\s+-?\d+(\.\d+)?\s+-?\d+\s""")
    private val FREQ = Regex("""(?<![\d.])(?:144)?\.(\d{2,3})(?!\d)""")
    private val PERIOD = Regex("""(?i)\b(1st|2nd)\b""")
    private val MODE = Regex("""(?i)\b(Q65-?\d{2,3}[A-E]|JT65[A-C]?|JT4[A-G]?|FST4|FT8|MSK144|CW)\b""")

    fun kind(msg: N0ukMessage): N0ukKind {
        val t = msg.text
        if (!CQ.containsMatchIn(t) || DECODE.containsMatchIn(t)) return N0ukKind.OTHER
        if (STOP.containsMatchIn(t)) return N0ukKind.STOP
        // Zonder frequentie en zonder mode is het geen bruikbare CQ-aanroep
        return if (FREQ.containsMatchIn(t) || MODE.containsMatchIn(t)) N0ukKind.CQ else N0ukKind.OTHER
    }

    fun parse(msg: N0ukMessage): N0ukCq? {
        if (kind(msg) != N0ukKind.CQ) return null
        val t = msg.text
        val freq = FREQ.find(t)?.groupValues?.get(1)?.padEnd(3, '0')?.let { "144.$it" } ?: "?"
        val mode = MODE.find(t)?.value?.uppercase()?.let { m ->
            // "Q6560A" -> "Q65-60A"
            if (m.startsWith("Q65") && !m.startsWith("Q65-")) "Q65-" + m.removePrefix("Q65") else m
        } ?: "?"
        return N0ukCq(
            utcMillis = msg.utcMillis,
            time = msg.time,
            freq = freq,
            period = PERIOD.find(t)?.value?.lowercase() ?: "?",
            callsign = msg.callsign,
            mode = mode,
            locator = msg.locator,
            ended = false,
        )
    }

    /**
     * CQ's van de laatste [minutes] minuten, nieuwste bovenaan. Elk station één keer: zijn meest
     * recente CQ (#124), hoofdletters of kleine letters maken niet uit. Schreef het station daarna
     * "stop cq", dan krijgt die CQ ended = true.
     */
    fun recent(messages: List<N0ukMessage>, nowMillis: Long, minutes: Int): List<N0ukCq> {
        // Alleen "stop cq" tot nu telt (een stop "uit de toekomst" beëindigt niets)
        val stops = messages.filter { kind(it) == N0ukKind.STOP && it.utcMillis <= nowMillis + 120_000L }
        return messages.mapNotNull { parse(it) }
            .filter { nowMillis - it.utcMillis in -120_000L..minutes * 60_000L }
            .sortedByDescending { it.utcMillis }
            .distinctBy { it.callsign.uppercase() }
            .map { cq ->
                cq.copy(ended = stops.any { it.callsign.equals(cq.callsign, true) && it.utcMillis >= cq.utcMillis })
            }
    }

    /** Wie recent in de chat schreef (behalve jijzelf), met het tijdstip van het laatste bericht. */
    fun recentWriters(messages: List<N0ukMessage>, myCallsign: String): List<N0ukMessage> =
        messages.filter { !it.callsign.equals(myCallsign, ignoreCase = true) }
            .distinctBy { it.callsign.uppercase() }
}
