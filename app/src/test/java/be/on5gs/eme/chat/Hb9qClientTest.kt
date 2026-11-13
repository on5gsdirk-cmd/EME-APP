package be.on5gs.eme.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

/** Robolectric voor een echte org.json; het netwerk gaat naar een nagebootste HB9Q op 127.0.0.1 (FakeHb9q). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Hb9qClientTest {

    private val users = mapOf("ON0AAA" to "pw-a", "ON0BBB" to "pw-b")

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
            val client = Hb9qClient(hb9q.baseUrl)
            assertEquals(emptyList<ChatMessage>(), client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a"))
            assertEquals("ON0AAA", hb9q.lastLoggedIn)
        }
    }

    @Test
    fun wrongPasswordIsALoginFailure() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = Hb9qClient(hb9q.baseUrl)
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
        }
    }

    /** Andere login in de instellingen terwijl de vorige sessie nog geldig is: opnieuw inloggen moet lukken. */
    @Test
    fun changingTheLoginWhileASessionIsActiveLogsInAgain() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = Hb9qClient(hb9q.baseUrl)
            client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a")
            client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "pw-b")
            assertEquals("ON0BBB", hb9q.lastLoggedIn)
        }
    }

    /** HB9Q weigert de login: met dezelfde gegevens pas na 5 minuten opnieuw proberen, niet bij elke verversing. */
    @Test
    fun rejectedLoginIsNotRetriedWithTheSameCredentialsForFiveMinutes() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            var now = 1_000_000L
            val client = Hb9qClient(hb9q.baseUrl).apply { clock = { now } }
            repeat(3) { expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") } }
            assertEquals(1, hb9q.loginPosts)

            now += 5 * 60_000L - 1
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
            assertEquals(1, hb9q.loginPosts)

            now += 1
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
            assertEquals(2, hb9q.loginPosts)
        }
    }

    /** Een verbeterd wachtwoord (of andere login) wordt meteen geprobeerd, ook vlak na een geweigerde login. */
    @Test
    fun correctedPasswordIsTriedRightAway() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = Hb9qClient(hb9q.baseUrl).apply { clock = { 1_000_000L } }
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
            client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a")
            assertEquals(2, hb9q.loginPosts)
            assertEquals("ON0AAA", hb9q.lastLoggedIn)
        }
    }

    /** "Log out" en opnieuw inloggen: de wachttijd na een geweigerde login vervalt. */
    @Test
    fun logoutClearsTheWait() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = Hb9qClient(hb9q.baseUrl).apply { clock = { 1_000_000L } }
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
            client.logout()
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "wrong") }
            assertEquals(2, hb9q.loginPosts)
        }
    }

    /** Na een mislukte login met een nog geldige sessie herstelt de volgende poging zonder de app te herstarten. */
    @Test
    fun afterAFailedLoginTheNextAttemptRecovers() = runBlocking {
        FakeHb9q(users).use { hb9q ->
            val client = Hb9qClient(hb9q.baseUrl)
            client.fetchMessages(Hb9qBand.B1296, "ON0AAA", "pw-a")
            expectLoginFailed { client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "typo") }
            client.fetchMessages(Hb9qBand.B1296, "ON0BBB", "pw-b")
            assertEquals("ON0BBB", hb9q.lastLoggedIn)
        }
    }
}
