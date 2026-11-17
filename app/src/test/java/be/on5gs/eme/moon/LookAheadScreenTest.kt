package be.on5gs.eme.moon

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.AppClock
import be.on5gs.eme.horizon.Horizon
import be.on5gs.eme.horizon.Visibility
import be.on5gs.eme.location.Maidenhead
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/** Schuifbalk op de Maan-tab (#118). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LookAheadScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var settings: SettingsRepository
    private val now = Instant.parse("2026-11-20T12:00:00Z").toEpochMilli()
    private val horizon = Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!

    // Midden van JO21SC, zoals de app het gebruikt
    private val lat = Maidenhead.toLatLon("JO21SC")!!.latDeg
    private val lon = Maidenhead.toLatLon("JO21SC")!!.lonDeg

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"))
        AppClock.nowMillis = { now }
        settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings.cqBands = emptySet()
        settings.horizon = horizon
        compose.setContent {
            EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { MoonScreen(settings, "ON5GS", "JO21SC") } }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        AppClock.nowMillis = System::currentTimeMillis
        settings.horizon = Horizon()
    }

    private fun text(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }

    private fun deg(v: Double) = String.format(Locale.US, "%.1f°", v)

    private fun obstacleText(v: Visibility) = when (v) {
        Visibility.CLEAR -> "Moon clear of your obstacles"
        Visibility.PARTIAL -> "Moon partly behind your obstacles"
        Visibility.BLOCKED -> "Moon behind your obstacles"
    }

    private fun slideTo(steps: Int) {
        compose.onNodeWithTag("look-ahead").performSemanticsAction(SemanticsActions.SetProgress) { it(steps.toFloat()) }
        compose.waitForIdle()
    }

    @Test
    fun slidingSixHoursAheadMovesTheMoonButNotTheTimesCard() {
        assertEquals("Slide to look up to 48 h ahead", text("look-label"))
        compose.onNodeWithTag("look-now").assertIsNotEnabled()
        compose.onNodeWithTag("horizon-now").performScrollTo().assertTextEquals("Moon behind your obstacles")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("horizon-window"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val windowNow = compose.onNodeWithTag("horizon-window").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)

        // 36 stappen = 6 uur: 18:00 UTC, binnen het vrije venster van 15:50 tot 02:15 UTC
        slideTo(36)
        val later = MoonCalculator.position(lat, lon, now + 6 * 3_600_000L)
        assertEquals("Ahead: +6:00", text("look-label"))
        assertEquals(deg(later.azimuthDeg), text("value-AZ"))
        assertEquals(deg(later.elevationDeg), text("value-EL"))
        val v = horizon.visibility(later.azimuthDeg, later.elevationDeg)
        assertNotEquals(Visibility.BLOCKED, v)
        compose.onNodeWithTag("horizon-now").assertTextEquals(obstacleText(v))
        // De kaart met de tijden blijft over nu gaan
        assertEquals(windowNow, compose.onNodeWithTag("horizon-window").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
        compose.onNodeWithTag("look-now").assertIsEnabled()

        // "Nu" zet alles terug
        compose.onNodeWithTag("look-now").performClick()
        compose.waitForIdle()
        val atNow = MoonCalculator.position(lat, lon, now)
        assertEquals(deg(atNow.azimuthDeg), text("value-AZ"))
        assertEquals("Slide to look up to 48 h ahead", text("look-label"))
        compose.onNodeWithTag("horizon-now").assertTextEquals("Moon behind your obstacles")
    }
}
