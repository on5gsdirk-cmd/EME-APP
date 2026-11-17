package be.on5gs.eme.horizon

import be.on5gs.eme.moon.MoonCalculator

/**
 * Een gezamenlijk maanvenster: de maan staat tegelijk vrij bij jou (je eigen obstakels, ondergrens en
 * marge van de schotel) en boven 0° bij het andere station. [endMillis] hoort er niet meer bij.
 * [maxElDeg] is het hoogste punt van de laagste van de twee maanstanden, op [maxAtMillis].
 * [partial]: ergens in het venster staat de maan bij jou achter een gedeeltelijk obstakel.
 */
data class MutualWindow(
    val startMillis: Long,
    val endMillis: Long,
    val maxElDeg: Double,
    val maxAtMillis: Long,
    val partial: Boolean,
)

/** Mutual moon windows met een ander station (#119); zelfde regels als web/src/lib/mutual.js. */
object MutualWindows {

    private const val STEP_MS = 60_000L
    private const val DAY_MS = 86_400_000L

    /**
     * Zo ver zoekt [next] voorbij de randen naar het echte begin of einde van een venster. De maan staat
     * nergens langer dan ongeveer 14 dagen op, dus een venster sluit daarbinnen altijd.
     */
    const val EDGE_DAYS = 15

    /**
     * Alle gezamenlijke vensters die na [nowMillis] nog lopen en binnen [days] dagen beginnen, minuut per
     * minuut. Een venster dat nu al loopt, krijgt zijn echte begin (terug gezocht, ook verder dan een dag);
     * het laatste venster zijn echte einde, ook na de [days] dagen (#120). Beide hoogstens [EDGE_DAYS] ver.
     */
    fun next(
        horizon: Horizon,
        marginDeg: Double,
        homeLat: Double,
        homeLon: Double,
        dxLat: Double,
        dxLon: Double,
        nowMillis: Long,
        days: Int = 30,
    ): List<MutualWindow> {
        val start = nowMillis - nowMillis % STEP_MS
        val end = start + days * DAY_MS
        fun mutual(t: Long) = isMutual(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, t)
        // Loopt er nu een venster, dan terug tot zijn echte begin
        var from = start
        while (from > start - EDGE_DAYS * DAY_MS && mutual(from - STEP_MS)) from -= STEP_MS
        val windows = compute(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, from, end).toMutableList()
        // Is het laatste venster aan het einde nog open, dan verder tot het echt sluit
        val last = windows.lastOrNull()
        if (last != null && last.endMillis == end) {
            val rest = compute(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, end, end + EDGE_DAYS * DAY_MS).firstOrNull()
            if (rest != null && rest.startMillis == end) {
                val higher = if (rest.maxElDeg > last.maxElDeg) rest else last
                windows[windows.lastIndex] = MutualWindow(last.startMillis, rest.endMillis, higher.maxElDeg, higher.maxAtMillis, last.partial || rest.partial)
            }
        }
        return windows.filter { it.endMillis > nowMillis }
    }

    /** Staat de maan op [t] vrij bij jou en boven 0° bij het andere station? */
    private fun isMutual(horizon: Horizon, marginDeg: Double, homeLat: Double, homeLon: Double, dxLat: Double, dxLon: Double, t: Long): Boolean {
        val home = MoonCalculator.position(homeLat, homeLon, t)
        return horizon.visibility(home.azimuthDeg, home.elevationDeg, marginDeg) != Visibility.BLOCKED &&
            MoonCalculator.position(dxLat, dxLon, t).elevationDeg > 0.0
    }

    /** Alle gezamenlijke vensters tussen [startMillis] en [endMillis]. */
    fun compute(
        horizon: Horizon,
        marginDeg: Double,
        homeLat: Double,
        homeLon: Double,
        dxLat: Double,
        dxLon: Double,
        startMillis: Long,
        endMillis: Long,
    ): List<MutualWindow> {
        val out = mutableListOf<MutualWindow>()
        var openStart = -1L
        var max = 0.0
        var maxAt = 0L
        var partial = false
        var t = startMillis
        while (t < endMillis) {
            val home = MoonCalculator.position(homeLat, homeLon, t)
            val v = horizon.visibility(home.azimuthDeg, home.elevationDeg, marginDeg)
            val dx = MoonCalculator.position(dxLat, dxLon, t)
            val ok = v != Visibility.BLOCKED && dx.elevationDeg > 0.0
            if (ok) {
                val mutual = minOf(home.elevationDeg, dx.elevationDeg)
                if (openStart < 0) { openStart = t; max = mutual; maxAt = t; partial = false }
                if (mutual > max) { max = mutual; maxAt = t }
                if (v == Visibility.PARTIAL) partial = true
            } else if (openStart >= 0) {
                out += MutualWindow(openStart, t, max, maxAt, partial)
                openStart = -1
            }
            t += STEP_MS
        }
        if (openStart >= 0) out += MutualWindow(openStart, endMillis, max, maxAt, partial)
        return out
    }
}

/** De laatst gebruikte locators voor het mutual window: nieuwste vooraan, zonder dubbele, hoogstens [max]. */
fun addRecentLocator(recent: List<String>, locator: String, max: Int = 5): List<String> =
    (listOf(locator.uppercase()) + recent.filter { !it.equals(locator, ignoreCase = true) }).take(max)
