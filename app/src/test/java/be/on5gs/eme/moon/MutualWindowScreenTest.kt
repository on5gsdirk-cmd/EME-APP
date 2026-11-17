package be.on5gs.eme.moon

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.AppClock
import be.on5gs.eme.degradation.EmeBand
import be.on5gs.eme.horizon.Horizon
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.TimeZone

/** "Mutual window" (#119): locator ingeven, 30 dagen vensters met max. elevatie en degradatie, recente locators. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MutualWindowScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"))
        AppClock.nowMillis = { Instant.parse("2026-11-20T12:00:00Z").toEpochMilli() }
        settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings.horizon = Horizon()
        settings.degBand = EmeBand.B1296
        settings.mutualLocators = emptyList()
        compose.setContent {
            EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { MutualWindowContent(settings, "JO21SC") {} } }
        }
    }

    @After
    fun tearDown() {
        AppClock.nowMillis = System::currentTimeMillis
    }

    private fun waitForRows() = compose.waitUntil(20_000) { compose.onAllNodesWithTag("mutual-row").fetchSemanticsNodes().isNotEmpty() }

    /** FN32 vanuit JO21SC, 20 november 2026 (zie expected/mutual_window.csv): 18:58–02:49 UTC, max. 36,5°. */
    @Test
    fun fn32GivesThe30DayTableAndIsRemembered() {
        compose.onNodeWithTag("mutual-locator").performTextReplacement("fn32")
        compose.onNodeWithText("Calculate").performClick()
        waitForRows()
        assertEquals(29, compose.onAllNodesWithTag("mutual-row").fetchSemanticsNodes().size)
        compose.onAllNodesWithTag("mutual-row")[0]
            .assertTextContains("19:58 – 03:49 (+1)   (7:51)", substring = true)
            .assertTextContains("Max. EL 36.5°", substring = true)
            .assertTextContains("(1296 MHz)", substring = true)
        compose.onNodeWithTag("mutual-pair").assertTextContains("JO21SC  ↔  FN32")
        assertEquals(listOf("FN32"), settings.mutualLocators)                  // onthouden
    }

    /** 30 dagen vanaf 20 maart 2027 gaan over de zomertijd (28 maart): die vensters tonen hun eigen tijdzone (#120). */
    @Test
    fun afterTheChangeToSummerTimeARowShowsItsOwnTimeZone() {
        // De klok wordt gelezen bij het rekenen; in november en op 20 maart is het allebei wintertijd (CET)
        AppClock.nowMillis = { Instant.parse("2027-03-20T12:00:00Z").toEpochMilli() }
        compose.onNodeWithText("(CET)", substring = true).assertExists()
        compose.onNodeWithTag("mutual-locator").performTextReplacement("FN32")
        compose.onNodeWithText("Calculate").performClick()
        waitForRows()
        val rows = compose.onAllNodesWithTag("mutual-row").fetchSemanticsNodes().map { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.joinToString(" ") { it.text }
        }
        assertFalse(rows.first(), rows.first().contains("CEST"))
        assertTrue(rows.last(), rows.last().contains(" CEST"))
    }

    @Test
    fun aWrongLocatorCannotBeCalculated() {
        compose.onNodeWithTag("mutual-locator").performTextReplacement("XX9")
        compose.onNodeWithText("Calculate").assertIsNotEnabled()
        compose.onNodeWithTag("mutual-locator").performTextReplacement("QF22")
        compose.onNodeWithText("Calculate").assertIsEnabled()
    }

    /** Een recente locator is één tik: hij rekent meteen, en komt vooraan in de lijst. */
    @Test
    fun aRecentLocatorIsOneTap() {
        compose.onNodeWithTag("mutual-locator").performTextReplacement("QF22")
        compose.onNodeWithText("Calculate").performClick()
        waitForRows()
        compose.onNodeWithTag("mutual-locator").performTextReplacement("FN32")
        compose.onNodeWithText("Calculate").performScrollTo().performClick()
        waitForPair("FN32")
        assertEquals(listOf("FN32", "QF22"), settings.mutualLocators)
        compose.onNodeWithText("QF22").performScrollTo().performClick()
        waitForPair("QF22")
        assertEquals(listOf("QF22", "FN32"), settings.mutualLocators)
    }

    private fun waitForPair(dx: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithTag("mutual-pair").fetchSemanticsNodes()
            .any { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text.endsWith(dx) } == true }
    }

    /** Met een obstakel rondom bij jou: geen gezamenlijk venster. */
    @Test
    fun noMutualWindow() {
        settings.horizon = Horizon.decode("1;0;B 0 360 90")!!
        compose.onNodeWithTag("mutual-locator").performTextReplacement("FN32")
        compose.onNodeWithText("Calculate").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("mutual-none")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No mutual window in the next 30 days.").performScrollTo()
    }
}
