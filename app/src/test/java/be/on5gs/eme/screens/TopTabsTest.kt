package be.on5gs.eme.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.EmeApp
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import be.on5gs.eme.update.UpdateCheck
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** De tabbladen bovenaan: Maan, Deg, HB9Q, N0UK, Live CQ, in die volgorde (issues #33 en #35). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class TopTabsTest {

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

    @Test
    fun topTabsInOrderWithoutChatCq() {
        compose.setContent { EmeTheme { EmeApp() } }
        val expected = listOf("Moon", "Deg", "HB9Q", "N0UK", "Live CQ")
        val lefts = expected.map { label ->
            compose.onNodeWithText(label).assertIsDisplayed().getBoundsInRoot().left
        }
        assertEquals("volgorde van links naar rechts", lefts.sorted(), lefts)
        // Chat CQ is geen tabblad bovenaan meer (het is een subpagina van HB9Q)
        compose.onAllNodesWithText("Chat CQ").assertCountEquals(0)
        compose.onAllNodes(hasText("HB9Q Chat") and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun hb9qTabOpens() {
        compose.setContent { EmeTheme { EmeApp() } }
        compose.onNodeWithText("HB9Q").performClick()
        compose.waitForIdle()
    }
}
