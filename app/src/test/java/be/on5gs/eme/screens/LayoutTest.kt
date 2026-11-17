package be.on5gs.eme.screens

import androidx.compose.ui.test.hasTestTag
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import be.on5gs.eme.AppClock
import be.on5gs.eme.EmeApp
import be.on5gs.eme.R
import be.on5gs.eme.chat.ChatCqList
import be.on5gs.eme.chat.ChatCqParser
import be.on5gs.eme.chat.ChatParser
import be.on5gs.eme.chat.ChatStore
import be.on5gs.eme.cq.Band
import be.on5gs.eme.cq.LiveCqParser
import be.on5gs.eme.cq.LiveCqStore
import be.on5gs.eme.n0uk.N0ukStore
import be.on5gs.eme.settings.SettingsRepository
import be.on5gs.eme.ui.theme.EmeTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.TimeZone

/**
 * Layout van elk scherm (issue #64): geen knop of tekst buiten het scherm, geen afgekapte tekst,
 * geen woord dat over twee regels gebroken wordt,
 * tikdoelen van minstens 48 dp en de tabbladen bovenaan zichtbaar.
 *
 * Alle 13 talen op een klein scherm met grote letters (de moeilijkste situatie), plus Engels op
 * een gewone telefoon en op een tablet. Vaste tijd en vaste gegevens, zonder netwerk.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LayoutTest(private val device: String, private val language: String, private val fontScale: Float) {

    companion object {
        private const val SMALL_PHONE = "w320dp-h640dp-xhdpi"
        private const val PHONE = "w411dp-h891dp-xxhdpi"
        private const val TABLET = "w800dp-h1280dp-xhdpi"
        private val LANGUAGES = listOf("en", "da", "de", "es", "fi", "fr", "it", "ja", "nl", "pt", "ru", "sv", "zh")

        /** Tijd van de N0UK-sample, zodat die berichten "recent" zijn. */
        private val NOW = Instant.parse("2026-11-10T16:20:00Z").toEpochMilli()

        /**
         * Bekende problemen op het kleine scherm, elk met een issue. Een pull request die er een
         * oplost, haalt zijn regel hier weg. Voeg geen nieuwe toe zonder issue.
         */
        private val KNOWN = emptyList<Known>()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1} {0} font {2}")
        fun parameters(): List<Array<Any>> =
            LANGUAGES.map { arrayOf<Any>(SMALL_PHONE, it, 1.3f) } +
                listOf(arrayOf<Any>(PHONE, "en", 1.0f), arrayOf<Any>(TABLET, "en", 1.0f))
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()

    /** Taal, schermmaat en lettergrootte instellen vóór de activity start. */
    private val deviceConfig = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers("$language-$device")
            RuntimeEnvironment.setFontScale(fontScale)
        }
    }

    private val name = TestName()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(deviceConfig).around(name).around(compose)

    private val defaultTimeZone = TimeZone.getDefault()
    private val clock = AppClock.nowMillis
    private val fetchW3sz = LiveCqStore.fetchW3sz
    private val fetchN6nu = LiveCqStore.fetchN6nu
    private val fetchN0uk = N0ukStore.fetchPage
    private val fetchHb9q = ChatStore.fetchMessages

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"))
        AppClock.nowMillis = { NOW }
        // Geen netwerk: elke aanvraag faalt, de schermen tonen de vaste gegevens hieronder
        LiveCqStore.fetchW3sz = { error("no network in LayoutTest") }
        LiveCqStore.fetchN6nu = { error("no network in LayoutTest") }
        ChatStore.fetchMessages = { _, _ -> error("no network in LayoutTest") }
        val n0ukPage = resource("n0uk_jt65emeA.html")
        N0ukStore.fetchPage = { n0ukPage }
        N0ukStore.resetForTest()

        // Echte W3SZ-export, verschoven zodat de laatste melding 5 minuten voor NOW valt
        val spots = LiveCqParser.parse(resource("livecq_sample.csv"))
        val shift = NOW - 5 * 60_000 - spots.maxOf { it.utcMillis }
        LiveCqStore.seedForTest(spots.map { it.copy(utcMillis = it.utcMillis + shift) }, lastFetch = NOW)

        SettingsRepository(ApplicationProvider.getApplicationContext()).apply {
            callsign = "ON5GS"
            locator = "JO21SC"
            cqBands = setOf(Band.B144, Band.B432, Band.B23CM)
        }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
        AppClock.nowMillis = clock
        LiveCqStore.fetchW3sz = fetchW3sz
        LiveCqStore.fetchN6nu = fetchN6nu
        N0ukStore.fetchPage = fetchN0uk
        ChatStore.fetchMessages = fetchHb9q
        LiveCqStore.resetForTest()
        N0ukStore.resetForTest()
    }

    @Test
    fun moon() = checkApp(null)

    /** Maan-tab met een eigen horizon: de regel "Maan achter je horizon" en "Vrij van je horizon" (stap 3 van #87). */
    @Test
    fun moonWithHorizon() {
        SettingsRepository(ApplicationProvider.getApplicationContext()).horizon =
            be.on5gs.eme.horizon.Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
        checkApp(null)
    }

    @Test
    fun degradation() = checkApp(R.string.tab_degradation)

    @Test
    fun hb9q() = checkApp(R.string.tab_chat)

    @Test
    fun n0uk() = checkApp(R.string.tab_n0uk)

    @Test
    fun liveCq() = checkApp(R.string.tab_livecq)

    @Test
    fun settings() {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { EmeApp() } } }
        compose.onAllNodes(hasContentDescription(string(R.string.settings)) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        checkLayout()
    }

    /** "Mijn horizon" (vanuit de instellingen) met de horizon van ON5GS: boom en boompjes, grafiek met maanbaan. */
    @Test
    fun horizon() {
        SettingsRepository(ApplicationProvider.getApplicationContext()).horizon =
            be.on5gs.eme.horizon.Horizon.decode("1;5;B 60 110 25;P 180 200 20")!!
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { EmeApp() } } }
        compose.onAllNodes(hasContentDescription(string(R.string.settings)) and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasText(string(R.string.horizon_edit)) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        checkLayout()
    }

    /** "Mutual window" (#119) met de vensters met FN32 en twee recente locators. */
    @Test
    fun mutualWindow() {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings.mutualLocators = listOf("FN32", "QF22")
        compose.setContent {
            EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { be.on5gs.eme.moon.MutualWindowContent(settings, "JO21SC") {} } }
        }
        compose.onAllNodes(hasTestTag("mutual-locator")).onFirst().assertExists()
        compose.onAllNodes(hasText(string(R.string.mutual_calculate)) and hasClickAction()).onFirst().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("mutual-row")).fetchSemanticsNodes().isNotEmpty() }
        checkLayout()
    }

    /** Chat CQ (subpagina van HB9Q) met de echte HB9Q-sample van 8 november. */
    @Test
    fun chatCq() {
        val t = Instant.parse("2026-11-08T09:10:00Z").toEpochMilli()
        val cqs = ChatCqParser.recent(ChatParser.parseRows(resource("hb9q_rows_8nov.html")), t, 24 * 60)
        compose.setContent {
            EmeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) { ChatCqList("1296", cqs) }
                }
            }
        }
        compose.waitForIdle()
        checkLayout()
    }

    /** Opent het tabblad [tab] (null: Maan, het eerste) en controleert het scherm met de tabbladen. */
    private fun checkApp(tab: Int?) {
        compose.setContent { EmeTheme { Surface(color = MaterialTheme.colorScheme.background) { EmeApp() } } }
        if (tab != null) {
            compose.onAllNodes(hasText(string(tab)) and hasClickAction()).onFirst().performClick()
        }
        compose.waitForIdle()
        val tabs = listOf(R.string.tab_moon, R.string.tab_degradation, R.string.tab_chat, R.string.tab_n0uk, R.string.tab_livecq)
        val missing = tabs.map(::string).filter { label ->
            compose.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().none { it.isFullyOnScreen() }
        }
        checkLayout(missing.map { "tab \"$it\" not fully on screen" })
    }

    private fun checkLayout(earlier: List<String> = emptyList()) {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val density = compose.activity.resources.displayMetrics.density
        val problems = earlier.toMutableList()
        val clickable = mutableListOf<Int>()
        fun visit(node: SemanticsNode, inHorizontalScroll: Boolean) {
            val scrollsSideways = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            val left = node.positionInRoot.x
            val right = left + node.size.width
            if (!inHorizontalScroll && (left < -1f || right > root.size.width + 1f)) {
                problems += "${node.describe()}: from x=${left.toInt()} to x=${right.toInt()}, screen width ${root.size.width}"
            }
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { getLayout ->
                val layouts = mutableListOf<TextLayoutResult>()
                getLayout(layouts)
                // Niet hasVisualOverflow: de layout uit de semantics heeft de volle beschikbare breedte,
                // dus die meldt bijna elke tekst. Wel: meer regels nodig dan toegelaten, een regel
                // met "…", of een regel breder dan het element. Breedte = rechts - links, omdat een
                // gecentreerde regel binnen die volle breedte verschoven staat.
                val cutOff = layouts.any { l ->
                    l.multiParagraph.didExceedMaxLines ||
                        (0 until l.lineCount).any { i -> l.isLineEllipsized(i) || l.getLineRight(i) - l.getLineLeft(i) > node.size.width + 1f }
                }
                if (cutOff) problems += "${node.describe()}: text cut off"
                // Een regeleinde midden in een woord: de kolom is smaller dan het langste woord
                val brokenWord = layouts.any { l ->
                    val text = l.layoutInput.text.text
                    (0 until l.lineCount - 1).any { i ->
                        val end = l.getLineEnd(i)
                        end in 1 until text.length && text[end - 1].isWordChar() && text[end].isWordChar()
                    }
                }
                if (brokenWord) problems += "${node.describe()}: word broken over two lines"
            }
            if (node.config.getOrNull(SemanticsActions.OnClick) != null) clickable += node.id
            node.children.forEach { visit(it, inHorizontalScroll || scrollsSideways) }
        }
        visit(root, false)

        // Tikgebied, niet de zichtbare grootte: Material 3 vergroot het tot 48 dp.
        // Een knop onder de rand van een scrollend scherm wordt eerst in beeld gescrold.
        val minPx = 48 * density - 1f
        clickable.forEach { id ->
            val target = compose.onNode(SemanticsMatcher("id $id") { it.id == id }, useUnmergedTree = true)
            if (!target.fetchSemanticsNode().isFullyOnScreen()) {
                // Geen scrollende ouder: de knop staat buiten het scherm en is niet te bereiken
                if (runCatching { target.performScrollTo() }.isFailure) {
                    problems += "${target.fetchSemanticsNode().describe()}: off screen, cannot scroll to it"
                    return@forEach
                }
                compose.waitForIdle()
            }
            val node = target.fetchSemanticsNode()
            val touch = node.touchBoundsInRoot
            if (touch.width < minPx || touch.height < minPx) {
                problems += "${node.describe()}: touch target ${(touch.width / density).toInt()}x${(touch.height / density).toInt()} dp"
            }
        }

        val test = name.methodName.substringBefore('[')
        problems.removeAll { p -> device == SMALL_PHONE && KNOWN.any { it.test == test && it.problem.matches(p) } }
        if (problems.isNotEmpty()) saveScreenshot()
        assertTrue("$language $device font $fontScale:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** Bij een fout: afbeelding van het scherm in app/build/layout-failures/, om na te kijken. */
    private fun saveScreenshot() {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/layout-failures").apply { mkdirs() }
        File(dir, "${name.methodName.replace(Regex("[^A-Za-z0-9.-]+"), "_")}.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun SemanticsNode.isFullyOnScreen(): Boolean {
        val root = compose.onRoot().fetchSemanticsNode()
        val left = positionInRoot.x
        val top = positionInRoot.y
        return left >= -1f && top >= -1f &&
            left + size.width <= root.size.width + 1f && top + size.height <= root.size.height + 1f
    }

    /** Tekst of beschrijving van het element, of van het eerste kind dat er een heeft. */
    private fun SemanticsNode.describe(): String {
        fun SemanticsNode.label(): String? =
            config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
                ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: children.firstNotNullOfOrNull { it.label() }
        return "\"${label() ?: "node $id"}\""
    }

    private fun string(id: Int) = compose.activity.getString(id)

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
}

/** Letter of cijfer van een schrift met spaties tussen de woorden (Chinees en Japans breken overal af). */
private fun Char.isWordChar(): Boolean {
    if (!isLetterOrDigit()) return false
    val script = Character.UnicodeScript.of(code)
    return script != Character.UnicodeScript.HAN && script != Character.UnicodeScript.HIRAGANA &&
        script != Character.UnicodeScript.KATAKANA && script != Character.UnicodeScript.HANGUL
}

/** Een bekend layoutprobleem: [issue] op GitHub, in test [test], meldingen die op [problem] passen. */
private data class Known(val issue: Int, val test: String, val problem: Regex)
