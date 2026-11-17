package be.on5gs.eme

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.cq.LiveCqStore
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.After
import be.on5gs.eme.update.UpdateCheck
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fetchUpdate = UpdateCheck.fetchLatest

    /** Geen aanvraag aan GitHub vanuit de tests (#126). */
    @org.junit.Before
    fun noUpdateRequest() {
        UpdateCheck.fetchLatest = { error("no network in tests") }
    }

    @After
    fun restoreUpdateRequest() {
        UpdateCheck.fetchLatest = fetchUpdate
    }

    /** Systeem-terug in de instellingen gaat terug naar de tabbladen en sluit de app niet. */
    @Test
    fun systemBackInSettingsReturnsToTabs() {
        LiveCqStore.seedForTest(emptyList())   // net "opgehaald": de test vraagt niets aan W3SZ
        compose.setContent { EmeTheme { EmeApp() } }
        val settings = compose.activity.getString(R.string.settings)
        val moon = compose.activity.getString(R.string.tab_moon)

        compose.onNodeWithContentDescription(settings).performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertFalse(compose.activity.isFinishing)
        compose.onAllNodesWithText(moon).onFirst().assertExists()
    }

    /** Het versienummer staat naast de titel, zoals in de release (bv. "v1.0.0"), niet in de instellingen. */
    @Test
    fun versionShownNextToTitle() {
        LiveCqStore.seedForTest(emptyList())
        compose.setContent { EmeTheme { EmeApp() } }
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertExists()

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertDoesNotExist()
    }
}
