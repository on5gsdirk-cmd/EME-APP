package be.on5gs.eme.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

/**
 * org.json is een stub in gewone JVM-tests; dit draait daarom met Robolectric.
 * Het netwerk gaat naar een nagebootste HB9Q op 127.0.0.1 (FakeHb9q).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Hb9qClientTest {

    private val client = Hb9qClient()

    @Test
    fun aGenuinelyEmptyChatHasAnEmptyLoggerField() {
        val json = JSONObject("""{"status":"success","logger":""}""")
        assertEquals("", client.loggerRows(json))
    }

    @Test
    fun rowsArePassedThroughUnchanged() {
        val json = JSONObject("""{"status":"success","logger":"<tr>...</tr>"}""")
        assertEquals("<tr>...</tr>", client.loggerRows(json))
    }

    /**
     * Zonder de fix geeft optString("logger") hier stilzwijgend "" terug, identiek aan een
     * eerlijk lege chat - deze test faalt dan omdat er geen IOException gegooid wordt.
     */
    @Test
    fun aMissingLoggerFieldIsTreatedAsAnErrorNotAnEmptyChat() {
        val json = JSONObject("""{"status":"success"}""")
        assertThrows(IOException::class.java) { client.loggerRows(json) }
    }

    // ---------- Inloggen tegen FakeHb9q ----------

    private val users = mapOf("ON0AAA" to "pw-a", "ON0BBB" to "pw-b")

    /** Klok voor de wachttijd van LoginBackoff; tests zetten hem vooruit. */
    private var now = 1_000_000L

    private fun clientFor(hb9q: FakeHb9q) = Hb9qClient(hb9q.baseUrl) { now }

    @Before
    fun freshCookies() {
        CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
    }

    private suspend fun expectLoginFailed(block: suspend () -> Unit) {
        try {
            block()
            fail("LoginFailedException verwacht")
        } catch (e: Hb9qClient.LoginFailedException) {
            // verwacht
        }
    }

    @Test
    fun logsInAndFetches() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = clientFor(hb9q)
            assertEquals(emptyList<ChatMessage>(), client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a"))
            assertEquals("ON0AAA", hb9q.lastLoggedIn)
        }
    }

    @Test
    fun wrongPasswordIsALoginFailure() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = clientFor(hb9q)
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
        }
    }

    /** Andere login in de instellingen terwijl de vorige sessie nog geldig is: opnieuw inloggen moet lukken. */
    @Test
    fun changingTheLoginWhileASessionIsActiveLogsInAgain() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = clientFor(hb9q)
            client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a")
            client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "pw-b")
            assertEquals("ON0BBB", hb9q.lastLoggedIn)
        }
    }

    /**
     * Na een mislukte login met een nog geldige sessie herstelt de volgende poging (na de
     * wachttijd van LoginBackoff) zonder de app te herstarten.
     */
    @Test
    fun afterAFailedLoginTheNextAttemptRecovers() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = clientFor(hb9q)
            client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a")
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "typo") }
            now += 5 * 60_000L
            client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "pw-b")
            assertEquals("ON0BBB", hb9q.lastLoggedIn)
        }
    }
}
