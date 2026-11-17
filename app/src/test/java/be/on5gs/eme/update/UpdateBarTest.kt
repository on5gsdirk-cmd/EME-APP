package be.on5gs.eme.update

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.AppClock
import be.on5gs.eme.settings.CredentialStore
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.settings.SettingsScreen
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** De balk "Version ... is available" en de schakelaar in de instellingen (#126). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdateBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val realFetch = UpdateCheck.fetchLatest
    private val clock = AppClock.nowMillis
    private val now = 1_800_000_000_000L
    private lateinit var settings: SettingsRepository
    private var opened: String? = null
    private var calls = 0

    @Before
    fun setUp() {
        settings = SettingsRepository(compose.activity)
        AppClock.nowMillis = { now }
        UpdateCheck.fetchLatest = { calls++; """{"tag_name":"v1.4.0","draft":false,"prerelease":false}""" }
    }

    @After
    fun tearDown() {
        UpdateCheck.fetchLatest = realFetch
        AppClock.nowMillis = clock
    }

    private fun show(installed: String = "1.3.0") {
        val uris = object : UriHandler { override fun openUri(uri: String) { opened = uri } }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides uris) {
                EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { UpdateBar(settings, installed) } }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aNewerVersionOnGitHubShowsTheBarAndOpensTheReleasePage() {
        show()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("update-bar")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Version 1.4.0 is available").assertExists()
        compose.onNodeWithText("Download").performClick()
        assertEquals("https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v1.4.0", opened)
        assertEquals(1, calls)
    }

    @Test
    fun noBarForTheInstalledVersion() {
        show(installed = "1.4.0")
        compose.waitUntil(5_000) { settings.latestVersion == "1.4.0" }
        compose.waitForIdle()
        compose.onNodeWithTag("update-bar").assertDoesNotExist()
    }

    /** Sluiten: de balk blijft weg voor deze versie, ook na een nieuwe start. */
    @Test
    fun closingHidesTheBarForThatVersion() {
        settings.latestVersion = "1.4.0"
        settings.lastUpdateCheck = now
        show()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("update-bar").assertDoesNotExist()
        assertEquals("1.4.0", settings.dismissedVersion)
        assertEquals(0, calls)   // vandaag al gevraagd
    }

    @Test
    fun aNewerVersionThanTheClosedOneShowsAgain() {
        settings.latestVersion = "1.5.0"
        settings.dismissedVersion = "1.4.0"
        settings.lastUpdateCheck = now
        show()
        compose.onNodeWithText("Version 1.5.0 is available").assertExists()
    }

    @Test
    fun switchedOffShowsNothingAndAsksNothing() {
        settings.updateCheck = false
        settings.latestVersion = "1.4.0"
        show()
        compose.onNodeWithTag("update-bar").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test
    fun theSwitchInSettingsTurnsTheCheckOff() {
        compose.setContent {
            EmeTheme { SettingsScreen("ON5GS", "JO21SC", CredentialStore(compose.activity), settings) { _, _ -> } }
        }
        val switch = compose.onNodeWithText("Check for new versions")
        switch.performScrollTo().assertIsOn()
        switch.performClick()
        switch.assertIsOff()
        assertFalse(settings.updateCheck)
    }
}
