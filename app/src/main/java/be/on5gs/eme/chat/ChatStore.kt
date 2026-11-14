package be.on5gs.eme.chat

import androidx.annotation.VisibleForTesting
import be.on5gs.eme.R
import be.on5gs.eme.settings.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gedeelde HB9Q-gegevens voor de tabbladen "Chat" en "Chat CQ":
 * één login-sessie, één keer ophalen per interval, welk tabblad ook open staat.
 */
object ChatStore {

    /** Berichten verversen om de 20 s. */
    const val MESSAGES_REFRESH_MS = 20_000L

    data class State(
        val band: Hb9qBand? = null,
        val messages: List<ChatMessage> = emptyList(),
        /** Foutmelding als (tekst-id, technisch detail); het scherm zet ze in de gekozen taal. */
        val error: Pair<Int, String>? = null,
        val loading: Boolean = false,
        val lastFetch: Long = 0L,
        /** Start van de laatste aanvraag, ook een afgebroken: die bereikte HB9Q toch. */
        val lastAttempt: Long = 0L,
        /** Uitgelogd met de knop "Log out": niets meer ophalen tot je opnieuw inlogt. */
        val loggedOut: Boolean = false,
    )

    /** Een station dat recent in de chat schreef, met de tijd (UTC) van zijn laatste bericht. */
    data class Participant(val time: String, val callsign: String, val name: String)

    private val client by lazy { Hb9qClient() }

    /** Haalt de berichten op bij HB9Q. Alleen voor tests vervangbaar, zodat er geen netwerk nodig is. */
    @VisibleForTesting
    internal var fetchMessages: suspend (Hb9qBand, CredentialStore.Credentials) -> List<ChatMessage> =
        { band, creds -> client.fetchMessages(band, creds.login, creds.password) }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()

    /** Haalt nieuwe gegevens op voor [band] als ze ouder zijn dan [maxAgeMs] (of als de band wisselde). */
    suspend fun refreshIfOlderThan(maxAgeMs: Long, band: Hb9qBand, creds: CredentialStore.Credentials) = lock.withLock {
        var s = _state.value
        if (s.loggedOut) return@withLock
        if (s.band != band) {
            s = State(band = band)            // andere band: lijst leegmaken
            _state.value = s
        }
        val now = System.currentTimeMillis()
        if (now - s.lastAttempt < maxAgeMs) return@withLock
        s = s.copy(lastAttempt = now)
        _state.value = s.copy(loading = true)
        s = try {
            val msgs = fetchMessages(band, creds)
            s.copy(messages = msgs, error = null, lastFetch = now)
        } catch (e: CancellationException) {
            // Tabblad verlaten tijdens het ophalen: geen fout. De aanvraag liep wel, dus lastAttempt blijft.
            _state.value = s.copy(loading = false)
            throw e
        } catch (e: Hb9qClient.LoginFailedException) {
            s.copy(error = R.string.login_failed to "", lastFetch = now)
        } catch (e: Exception) {
            s.copy(error = R.string.hb9q_error to (e.message ?: e.javaClass.simpleName), lastFetch = now)
        }
        _state.value = s.copy(loading = false)
    }

    /** Uitloggen bij HB9Q (zoals "Logout" op de website) en alles wissen wat de app van de chat bijhield. */
    suspend fun logout() = lock.withLock {
        client.logout()
        _state.value = State(loggedOut = true)
    }

    /** Opnieuw inloggen na "Log out": de volgende verversing logt automatisch in. */
    fun logInAgain() {
        _state.value = State()
    }

    /**
     * De foutmelding van [s], maar alleen als ze bij [band] hoort. Zonder dit kan een gebruiker die
     * van band wisselt tijdens een foutmelding, die oude foutmelding nog even bij de nieuwe band zien.
     */
    fun errorFor(s: State, band: Hb9qBand): Pair<Int, String>? = s.error.takeIf { s.band == band }

    /** Deelnemers = de stations die recent in de chat schreven (behalve jijzelf), nieuwste eerst. */
    fun recentParticipants(s: State, myCallsign: String): List<Participant> =
        s.messages   // de berichten staan al met het nieuwste bovenaan
            .filter { !it.callsign.equals(myCallsign, ignoreCase = true) }
            .distinctBy { it.callsign.uppercase() }
            .map { Participant(it.time, it.callsign, it.name) }
}
