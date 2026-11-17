package be.on5gs.eme.horizon

import be.on5gs.eme.ExpectedCsv
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.moon.MoonCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/** Mutual moon window (#119); zelfde tabel als de webtests. */
class MutualWindowsTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun asInTheSharedTable() {
        val rows = ExpectedCsv.read("mutual_window")
        assertEquals(7, rows.size)
        rows.forEach { r ->
            val home = Maidenhead.toLatLon(r.getValue("home"))!!
            val dx = Maidenhead.toLatLon(r.getValue("dx"))!!
            val w = MutualWindows.next(
                Horizon.decode(r.getValue("horizon"))!!, r.getValue("margin").toDouble(),
                home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, ms(r.getValue("now_utc")),
            )
            assertEquals("$r count", r.getValue("count").toInt(), w.size)
            val f = w.first()
            assertTrue("$r start", abs(f.startMillis - ms(r.getValue("first_start_utc"))) <= 60_000)
            assertTrue("$r end", abs(f.endMillis - ms(r.getValue("first_end_utc"))) <= 60_000)
            assertEquals("$r max", r.getValue("first_max_el").toDouble(), f.maxElDeg, 0.05)
            assertTrue("$r max at", abs(f.maxAtMillis - ms(r.getValue("first_max_at_utc"))) <= 60_000)
            assertEquals("$r partial", r.getValue("first_partial").toBoolean(), f.partial)
        }
    }

    /** Met jezelf als ander station: precies je eigen vrije vensters (MoonWindows), onafhankelijk berekend. */
    @Test
    fun withYourselfItIsYourOwnClearWindow() {
        val p = Maidenhead.toLatLon("JO21SC")!!
        val h = Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
        val start = ms("2026-11-20T00:00:00Z")
        val end = start + 3 * 86_400_000L
        val mutual = MutualWindows.compute(h, 0.0, p.latDeg, p.lonDeg, p.latDeg, p.lonDeg, start, end)
        val own = MoonWindows.usable(h, p.latDeg, p.lonDeg, start, end)
        assertEquals(own.map { it.startMillis to it.endMillis }, mutual.map { it.startMillis to it.endMillis })
    }

    /** In elke minuut van een venster staat de maan bij beide op, en de minuut ervoor en erna niet. */
    @Test
    fun everyMinuteOfAWindowIsMutualAndTheEdgesAreNot() {
        val home = Maidenhead.toLatLon("JO21SC")!!
        val dx = Maidenhead.toLatLon("FN32")!!
        val h = Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
        fun ok(t: Long): Boolean {
            val a = MoonCalculator.position(home.latDeg, home.lonDeg, t)
            val b = MoonCalculator.position(dx.latDeg, dx.lonDeg, t)
            return h.visibility(a.azimuthDeg, a.elevationDeg, 1.349) != Visibility.BLOCKED && b.elevationDeg > 0.0
        }
        val start = ms("2026-11-20T12:00:00Z")
        val ws = MutualWindows.compute(h, 1.349, home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, start, start + 3 * 86_400_000L)
        assertTrue(ws.size >= 3)
        ws.forEach { w ->
            var t = w.startMillis
            while (t < w.endMillis) { assertTrue(ok(t)); t += 60_000 }
            if (w.startMillis > start) assertTrue(!ok(w.startMillis - 60_000))
            assertTrue(!ok(w.endMillis))
        }
    }

    /** Geen gezamenlijk venster: de maan nooit tegelijk op (een obstakel rondom bij jou). */
    @Test
    fun noWindowWhenTheMoonIsNeverUpAtBoth() {
        val home = Maidenhead.toLatLon("JO21SC")!!
        val dx = Maidenhead.toLatLon("FN32")!!
        assertEquals(0, MutualWindows.next(Horizon.decode("1;0;B 0 360 90")!!, 0.0, home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, ms("2026-11-20T12:00:00Z")).size)
    }

    @Test
    fun recentLocatorsNewestFirstNoDuplicatesAtMostFive() {
        assertEquals(listOf("FN32"), addRecentLocator(emptyList(), "fn32"))
        assertEquals(listOf("QF22", "FN32"), addRecentLocator(listOf("FN32"), "QF22"))
        assertEquals(listOf("FN32", "QF22"), addRecentLocator(listOf("QF22", "FN32"), "fn32"))
        assertEquals(listOf("A", "B", "C", "D", "E"), addRecentLocator(listOf("B", "C", "D", "E", "F"), "A"))
    }
}
