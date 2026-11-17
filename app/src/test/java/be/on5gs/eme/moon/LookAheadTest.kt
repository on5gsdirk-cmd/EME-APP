package be.on5gs.eme.moon

import be.on5gs.eme.ExpectedCsv
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Schuifbalk op de Maan-tab (#118); zelfde tabel als web/test/look-ahead.test.js. */
class LookAheadTest {

    private fun ms(s: String) = Instant.parse(s).toEpochMilli()

    @Test
    fun sharedTable() {
        val rows = ExpectedCsv.read("look_ahead")
        assertEquals(8, rows.size)
        for (r in rows) {
            val now = ms(r.getValue("now_utc"))
            val steps = r.getValue("steps").toInt()
            val base = LookAhead.base(now)
            assertEquals(r.toString(), ms(r.getValue("base_utc")), base)
            assertEquals(r.toString(), ms(r.getValue("shown_utc")), LookAhead.shownMillis(now, base, steps))
            assertEquals(r.toString(), r.getValue("label"), LookAhead.label(steps))
        }
    }

    @Test
    fun fortyEightHoursInTenMinuteSteps() {
        assertEquals(48 * 3_600_000L, LookAhead.MAX_STEPS * LookAhead.STEP_MS)
    }
}
