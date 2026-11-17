package be.on5gs.eme.moon

/**
 * Schuifbalk op de Maan-tab (#118): vooruitkijken tot 48 uur, in stappen van 10 minuten.
 * Stap 0 is "nu" (de klok loopt). Bij de eerste stap weg van 0 wordt de begintijd vastgelegd op
 * het vorige hele tiental minuten ([base]), zodat de gekozen tijd niet meeloopt met de klok.
 * Zelfde regels als web/src/lib/look-ahead.js; gedeelde waarden in expected/look_ahead.csv.
 */
object LookAhead {
    const val STEP_MS = 600_000L
    const val MAX_STEPS = 288   // 48 uur

    /** Begintijd: [now] afgerond naar beneden op 10 minuten. */
    fun base(now: Long): Long = now - Math.floorMod(now, STEP_MS)

    /** De getoonde tijd: [now] op stap 0, anders [base] plus de stappen. */
    fun shownMillis(now: Long, base: Long, steps: Int): Long =
        if (steps <= 0) now else base + steps.coerceAtMost(MAX_STEPS) * STEP_MS

    /** "+5:30" voor 33 stappen: uren en minuten vooruit. */
    fun label(steps: Int): String {
        val minutes = steps.coerceIn(0, MAX_STEPS) * 10
        return String.format(java.util.Locale.ROOT, "+%d:%02d", minutes / 60, minutes % 60)
    }
}
