package be.on5gs.eme.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Nieuwe versie op GitHub (#126). org.json en de instellingen komen van Android, daarom met Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateCheckTest {

    private val realFetch = UpdateCheck.fetchLatest
    private lateinit var settings: SettingsRepository
    private var calls = 0
    private var answer: () -> String = { """{"tag_name":"v1.4.0","draft":false,"prerelease":false}""" }

    @Before
    fun setUp() {
        settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        calls = 0
        UpdateCheck.fetchLatest = { calls++; answer() }
    }

    @After
    fun tearDown() {
        UpdateCheck.fetchLatest = realFetch
    }

    @Test
    fun readsTheVersionFromTheTag() {
        assertEquals("1.4.0", UpdateCheck.parseLatest("""{"tag_name":"v1.4.0","name":"EME APP 1.4.0","draft":false,"prerelease":false}"""))
        assertEquals("1.4.0", UpdateCheck.parseLatest("""{"tag_name":"1.4.0"}"""))
    }

    @Test
    fun noVersionFromATestReleaseDraftOrBrokenAnswer() {
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.4.0-test1","prerelease":false}"""))
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.4.0","prerelease":true}"""))
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.4.0","draft":true}"""))
        assertNull(UpdateCheck.parseLatest("""{"message":"API rate limit exceeded"}"""))
        assertNull(UpdateCheck.parseLatest("<html>"))
        assertNull(UpdateCheck.parseLatest(""))
    }

    @Test
    fun comparesNumberByNumber() {
        assertTrue(UpdateCheck.isNewer("1.4.0", "1.3.0"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.0"))
        assertTrue(UpdateCheck.isNewer("2.0.0", "1.99.99"))
        assertTrue(UpdateCheck.isNewer("1.3.1", "1.3"))
        assertFalse(UpdateCheck.isNewer("1.3.0", "1.3.0"))
        assertFalse(UpdateCheck.isNewer("1.3", "1.3.0"))
        assertFalse(UpdateCheck.isNewer("1.2.9", "1.3.0"))
        assertFalse(UpdateCheck.isNewer("", "1.3.0"))
    }

    /** Een testversie (1.4.0-test1) krijgt de gewone 1.4.0 aangeboden, een gewone 1.4.0 niet. */
    @Test
    fun theReleaseIsNewerThanItsTestVersion() {
        assertTrue(UpdateCheck.isNewer("1.4.0", "1.4.0-test1"))
        assertFalse(UpdateCheck.isNewer("1.3.0", "1.4.0-test1"))
    }

    @Test
    fun asksAtMostOnceADay() = runBlocking {
        val t0 = 1_800_000_000_000L
        assertEquals("1.4.0", UpdateCheck.checkIfDue(settings, t0))
        assertNull(UpdateCheck.checkIfDue(settings, t0 + UpdateCheck.INTERVAL_MS - 1))
        assertEquals(1, calls)
        assertEquals("1.4.0", UpdateCheck.checkIfDue(settings, t0 + UpdateCheck.INTERVAL_MS))
        assertEquals(2, calls)
        assertEquals("1.4.0", settings.latestVersion)
    }

    /** Ook een mislukte vraag telt: niet opnieuw proberen bij elke start. */
    @Test
    fun aFailedRequestAlsoWaitsADay() = runBlocking {
        val t0 = 1_800_000_000_000L
        answer = { error("offline") }
        assertNull(UpdateCheck.checkIfDue(settings, t0))
        assertNull(UpdateCheck.checkIfDue(settings, t0 + 60_000))
        assertEquals(1, calls)
        assertEquals("", settings.latestVersion)
    }

    /** Een klok die terugsprong (laatste vraag "in de toekomst") houdt het vragen niet voor altijd tegen. */
    @Test
    fun aClockThatWentBackAsksAgain() = runBlocking {
        settings.lastUpdateCheck = 1_900_000_000_000L
        assertEquals("1.4.0", UpdateCheck.checkIfDue(settings, 1_800_000_000_000L))
        assertEquals(1, calls)
    }

    @Test
    fun switchedOffAsksNothing() = runBlocking {
        settings.updateCheck = false
        assertNull(UpdateCheck.checkIfDue(settings, 1_800_000_000_000L))
        assertEquals(0, calls)
    }

    @Test
    fun releasePageOfTheVersion() {
        assertEquals("https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v1.4.0", UpdateCheck.releaseUrl("1.4.0"))
    }
}
