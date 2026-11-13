package be.on5gs.eme.cq

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gedeelde Live CQ-gegevens voor de hele app (CQ-lijst én wereldkaart),
 * zodat W3SZ maar één keer per interval bevraagd wordt, welk scherm ook open staat.
 */
object LiveCqStore {

    /** Hoe vaak de lijst automatisch opgehaald wordt (zelfde als MoonTrack). */
    const val AUTO_REFRESH_MS = 120_000L
    /** Minimale tijd tussen twee ophaalacties, ook met de vernieuwknop. */
    const val MIN_FETCH_INTERVAL_MS = 30_000L

    data class State(
        val spots: List<LiveCqSpot> = emptyList(),
        val lastFetch: Long = 0L,
        val error: String? = null,
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()

    /** Alleen voor tests (schermafdrukken): vaste gegevens instellen zonder netwerk. */
    internal fun seedForTest(spots: List<LiveCqSpot>) {
        _state.value = State(spots = spots, lastFetch = System.currentTimeMillis())
    }

    /**
     * Haalt nieuwe gegevens op als de vorige ophaling ouder is dan [maxAgeMs].
     * Twee schermen die tegelijk vragen, leiden tot maar één aanvraag.
     *
     * [manual] onderscheidt een bewuste tik op de vernieuwknop van een geplande, automatische
     * verversing: alleen de geplande verversing wordt tegen [MIN_FETCH_INTERVAL_MS] aangehouden
     * (om W3SZ niet te vaak te bevragen). Een manuele tik haalt altijd meteen opnieuw op, ook
     * binnen die 30s, zodat de knop nooit stilzwijgend niets doet.
     */
    suspend fun refreshIfOlderThan(
        maxAgeMs: Long,
        manual: Boolean = false,
        fetch: suspend () -> List<LiveCqSpot> = LiveCqRepository::fetch,
    ) = lock.withLock {
        val s = _state.value
        val effectiveMaxAgeMs = if (manual) maxAgeMs else maxOf(maxAgeMs, MIN_FETCH_INTERVAL_MS)
        if (System.currentTimeMillis() - s.lastFetch < effectiveMaxAgeMs) return@withLock
        _state.value = s.copy(loading = true)
        _state.value = try {
            State(spots = fetch(), lastFetch = System.currentTimeMillis())
        } catch (e: Exception) {
            // Vorige lijst blijft staan, met een foutmelding erbij
            s.copy(
                lastFetch = System.currentTimeMillis(),
                error = e.message ?: e.javaClass.simpleName,   // technisch detail; het scherm vertaalt de melding
                loading = false,
            )
        }
    }
}
