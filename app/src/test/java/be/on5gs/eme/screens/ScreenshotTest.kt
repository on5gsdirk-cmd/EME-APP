package be.on5gs.eme.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import be.on5gs.eme.EmeApp
import be.on5gs.eme.n0uk.N0ukCqList
import be.on5gs.eme.n0uk.N0ukCqParser
import be.on5gs.eme.n0uk.N0ukParser
import be.on5gs.eme.n0uk.N0ukStore
import be.on5gs.eme.moon.MoonPhase
import be.on5gs.eme.moon.drawMoonPhase
import be.on5gs.eme.chat.ChatCqList
import be.on5gs.eme.chat.ChatCqParser
import be.on5gs.eme.chat.ChatParser
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import be.on5gs.eme.cq.Band
import be.on5gs.eme.cq.LiveCqParser
import be.on5gs.eme.cq.LiveCqSource
import be.on5gs.eme.cq.LiveCqSpot
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.cq.LiveCqStore
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.After
import be.on5gs.eme.update.UpdateCheck
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Maakt schermafdrukken van de app op de pc (geen telefoon nodig).
 * Draait alleen met: ./gradlew testDebugUnitTest -Pscreenshots
 * De afbeeldingen komen in app/build/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

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

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("screenshots") == "true")
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Brussels"))
        SettingsRepository(ApplicationProvider.getApplicationContext()).apply { callsign = "ON5GS"; locator = "JO21SC" }
        // CQ-voorbeelden uit de echte W3SZ-export, verschoven naar "nu" zodat ze op de kaart komen
        val csv = javaClass.classLoader!!.getResource("livecq_sample.csv")!!.readText()
        val spots = LiveCqParser.parse(csv)
        val shift = System.currentTimeMillis() - spots.maxOf { it.utcMillis } - 5 * 60_000
        val now = System.currentTimeMillis() - 5 * 60_000
        // Testgeval van ON5GS: OK1AA (JO61AA) en OK2BB (JO71AA) vlak bij elkaar, plus extra drukte
        val extra = listOf(
            Triple("OK1AA", "JO61AA", 1296.065), Triple("OK2BB", "JO71AA", 1296.070),
            Triple("OK1DFC", "JO70FD", 432.065), Triple("DL7APV", "JO62QM", 144.120),
            Triple("SP5GDM", "KO02MD", 1296.060), Triple("OK1AA", "JO61AA", 144.115),
            Triple("UA3PTW", "KO93", 1296.050), Triple("VK7MO", "QE37", 1296.050),
        ).mapIndexed { i, (call, grid, f) ->
            // Een deel van N6NU of van beide sites, om de bron op het scherm te zien
            val sources = when (i % 3) {
                0 -> setOf(LiveCqSource.W3SZ, LiveCqSource.N6NU)
                1 -> setOf(LiveCqSource.N6NU)
                else -> setOf(LiveCqSource.W3SZ)
            }
            LiveCqSpot(now, call, grid, f, Band.forFrequency(f), "Q65-60C", "-20", "PA3DRK", "JO21", sources, "2.5", "WSJT-X")
        }
        LiveCqStore.seedForTest(spots.map { it.copy(utcMillis = it.utcMillis + shift) } + extra)
        SettingsRepository(ApplicationProvider.getApplicationContext()).cqBands = setOf(Band.B144, Band.B432, Band.B23CM)
    }

    @Test
    fun moonScreen() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.waitForIdle()
        save("maan")
    }

    @Test
    fun cqScreen() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithText("Live CQ").performClick()
        compose.waitForIdle()
        save("livecq")
    }

    @Test
    @Config(qualifiers = "+ru")
    fun moonScreenRussian() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.waitForIdle()
        save("maan-ru")
    }

    @Test
    @Config(qualifiers = "+fr")
    fun settingsFrench() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithContentDescription("Paramètres").performClick()
        compose.waitForIdle()
        save("instellingen-fr")
    }

    @Test
    @Config(qualifiers = "+ja")
    fun chatJapanese() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithText("HB9Q").performClick()
        compose.waitForIdle()
        save("chat-ja")
    }

    @Test
    @Config(fontScale = 1.3f, qualifiers = "+nl")
    fun chatCqListLargeFont() {
        val rows = javaClass.classLoader!!.getResource("hb9q_rows_8nov.html")!!.readText()
        val now = java.time.Instant.parse("2026-11-08T09:10:00Z").toEpochMilli()
        val cqs = ChatCqParser.recent(ChatParser.parseRows(rows), now, 24 * 60)
        compose.setContent {
            EmeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) { ChatCqList("1296", cqs) }
                }
            }
        }
        compose.waitForIdle()
        save("chatcq-groot")
    }

    @Test
    @Config(fontScale = 1.3f, qualifiers = "+nl")
    fun tabsLargeFont() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.waitForIdle()
        save("maan-groot")
    }

    /** Acht fasen van november 2026 naast elkaar (wassend rechts verlicht, afnemend links). */
    @Test
    fun moonPhases() {
        val dates = listOf("2026-11-09", "2026-11-12", "2026-11-17", "2026-11-20", "2026-11-24", "2026-11-28", "2026-12-01", "2026-12-05")
        val phases = dates.map { MoonPhase.at(java.time.Instant.parse("${it}T12:00:00Z").toEpochMilli()) }
        compose.setContent {
            EmeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        phases.forEachIndexed { i, p ->
                            drawMoonPhase(androidx.compose.ui.geometry.Offset(70f + i * 145f, 150f), 60f, p)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        save("maanfasen")
    }

    @Test
    @Config(qualifiers = "+nl")
    fun horizonScreen() = horizon("horizon")

    /** Engels, voor de README. */
    @Test
    fun horizonScreenEnglish() = horizon("readme-horizon")

    private fun horizon(name: String) {
        // "Mijn horizon" met de situatie van ON5GS, op 20 november 2026 (maan komt op achter de boom)
        be.on5gs.eme.AppClock.nowMillis = { java.time.Instant.parse("2026-11-20T12:00:00Z").toEpochMilli() }
        try {
            compose.setContent {
                EmeTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        be.on5gs.eme.horizon.HorizonScreen(
                            be.on5gs.eme.horizon.Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!, "JO21SC", 6.0, be.on5gs.eme.degradation.EmeBand.B1296,
                        ) { _, _, _ -> }
                    }
                }
            }
            compose.waitForIdle()
            save(name)
        } finally {
            be.on5gs.eme.AppClock.nowMillis = System::currentTimeMillis
        }
    }

    @Test
    @Config(qualifiers = "+nl")
    fun mutualWindowScreen() {
        // "Mutual window" (#119) JO21SC met FN32 op 20 november 2026, met de obstakels van ON5GS
        be.on5gs.eme.AppClock.nowMillis = { java.time.Instant.parse("2026-11-20T12:00:00Z").toEpochMilli() }
        try {
            val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
            settings.horizon = be.on5gs.eme.horizon.Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
            settings.dishDiameterM = 6.0
            settings.mutualLocators = listOf("FN32", "QF22", "RE78")
            compose.setContent {
                EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { be.on5gs.eme.moon.MutualWindowContent(settings, "JO21SC") {} } }
            }
            compose.onNodeWithText("Berekenen").performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("mutual-row")).fetchSemanticsNodes().isNotEmpty() }
            save("mutual-window")
        } finally {
            be.on5gs.eme.AppClock.nowMillis = System::currentTimeMillis
        }
    }

    @Test
    @Config(qualifiers = "+nl")
    fun degradationScreen() = degradation("degradatie")

    /** Engels, voor de README. */
    @Test
    fun degradationScreenEnglish() = degradation("readme-deg")

    private fun degradation(name: String) {
        SettingsRepository(ApplicationProvider.getApplicationContext()).degBand = be.on5gs.eme.degradation.EmeBand.B144
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithText("Deg").performClick()
        compose.waitForIdle()
        save(name)
    }

    @Test
    @Config(qualifiers = "+nl")
    fun degradationCalendarJanuary2030() {
        // Kalender van de Deg-tab, januari 2030, JO21 op 144 MHz: de 4 beste dagen in het groen
        val month = java.time.YearMonth.of(2030, 1)
        val best = be.on5gs.eme.degradation.Degradation.bestDaysOfMonth(
            month, 51.15, 4.29, be.on5gs.eme.degradation.EmeBand.B144, 80.0,
        )
        compose.setContent {
            EmeTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        be.on5gs.eme.degradation.DegCalendarMonth(
                            month, month.atDay(15), java.time.LocalDate.of(2030, 1, 9), best, onMonth = {}, onPick = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        save("deg-kalender")
    }

    @Test
    @Config(qualifiers = "+it")
    fun degradationItalian() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithText("Deg").performClick()
        compose.waitForIdle()
        save("degradatie-it")
    }

    @Test
    fun chatCqList() {
        val rows = javaClass.classLoader!!.getResource("hb9q_rows_8nov.html")!!.readText()
        val now = java.time.Instant.parse("2026-11-08T09:10:00Z").toEpochMilli()
        // Venster ruimer dan 30 min zodat alle voorbeelden van die dag zichtbaar zijn
        val cqs = ChatCqParser.recent(ChatParser.parseRows(rows), now, 24 * 60)
        compose.setContent {
            EmeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) { ChatCqList("1296", cqs) }
                }
            }
        }
        compose.waitForIdle()
        save("chatcq")
    }

    /** N0UK: berichten, met de opgeslagen pagina als bron (geen netwerk). */
    @Test
    @Config(qualifiers = "+nl")
    fun n0ukMessages() {
        val page = javaClass.classLoader!!.getResource("n0uk_jt65emeA.html")!!.readText()
        N0ukStore.resetForTest()
        N0ukStore.fetchPage = { page }
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) { EmeApp() } } } }
        compose.onNodeWithText("N0UK").performClick()
        compose.waitForIdle()
        save("n0uk-berichten")
    }

    /** N0UK Chat CQ als op 10 november 16:20 UTC, met een ruimer venster om meer regels te tonen. */
    @Test
    @Config(qualifiers = "+nl")
    fun n0ukCqTable() {
        val page = javaClass.classLoader!!.getResource("n0uk_jt65emeA.html")!!.readText()
        val t = java.time.Instant.parse("2026-11-10T16:20:00Z").toEpochMilli()
        val cqs = N0ukCqParser.recent(N0ukParser.parse(page, t), t, 24 * 60)
        compose.setContent {
            EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize().padding(12.dp)) { N0ukCqList(cqs, t) } } }
        }
        compose.waitForIdle()
        save("n0uk-chatcq")
    }

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(500)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
