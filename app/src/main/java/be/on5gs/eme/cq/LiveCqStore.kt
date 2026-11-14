package be.on5gs.eme.cq

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
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
        /** Start van de laatste aanvraag, ook een afgebroken: die bereikte W3SZ toch. */
        val lastAttempt: Long = 0L,
        val error: String? = null,
        val loading: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()

    /** Alleen voor tests (schermafdrukken): vaste gegevens instellen zonder netwerk. */
    internal fun seedForTest(spots: List<LiveCqSpot>, lastFetch: Long = System.currentTimeMillis()) {
        _state.value = State(spots = spots, lastFetch = lastFetch)
    }

    /** Haalt de spots op bij W3SZ. Alleen voor tests vervangbaar, zodat er geen netwerk nodig is. */
    @VisibleForTesting
    internal var fetcher: suspend () -> List<LiveCqSpot> = { LiveCqRepository.fetch() }

    /**
     * Haalt nieuwe gegevens op als de vorige ophaling ouder is dan [maxAgeMs].
     * Twee schermen die tegelijk vragen, leiden tot maar één aanvraag.
     */
    suspend fun refreshIfOlderThan(maxAgeMs: Long) = lock.withLock {
        val now = System.currentTimeMillis()
        val prev = _state.value
        if (now - prev.lastFetch < maxOf(maxAgeMs, MIN_FETCH_INTERVAL_MS)) return@withLock
        if (now - prev.lastAttempt < MIN_FETCH_INTERVAL_MS) return@withLock
        val s = prev.copy(lastAttempt = now)
        _state.value = s.copy(loading = true)
        _state.value = try {
            State(spots = fetcher(), lastFetch = System.currentTimeMillis(), lastAttempt = now)
        } catch (e: CancellationException) {
            // Scherm verlaten tijdens het ophalen: geen fout. De aanvraag liep wel, dus lastAttempt blijft.
            _state.value = s.copy(loading = false)
            throw e
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
