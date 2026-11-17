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
     * Alle gezamenlijke vensters die na [nowMillis] nog lopen, tot [days] dagen vooruit. Gezocht vanaf
     * 24 uur terug, zodat een venster dat nu loopt zijn echte begin krijgt; minuut per minuut.
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
        return compute(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, start - DAY_MS, start + days * DAY_MS)
            .filter { it.endMillis > nowMillis }
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
