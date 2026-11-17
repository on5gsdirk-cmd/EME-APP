package be.on5gs.eme.update

import be.on5gs.eme.AppClock
import be.on5gs.eme.UserAgent
import be.on5gs.eme.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Is er een nieuwere versie op GitHub (#126)? De app staat niet in Google Play; nieuwe versies zijn
 * alleen GitHub-releases. Hooguit één GET per 24 uur, en alleen als de gebruiker het niet uitzette.
 */
object UpdateCheck {

    const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** Geeft de laatste gewone release: drafts en pre-releases (bv. v1.3.0-test1) slaat GitHub over. */
    private const val LATEST_URL = "https://api.github.com/repos/on5gsdirk-cmd/EME-APP/releases/latest"

    fun releaseUrl(version: String) = "https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v$version"

    /** Testnaad: in de app de echte aanvraag. */
    internal var fetchLatest: () -> String = ::fetch

    private val VERSION = Regex("""\d+(\.\d+)*""")

    /** De versie uit het antwoord van releases/latest ("v1.4.0" geeft "1.4.0"); null bij een draft, pre-release of onleesbaar antwoord. */
    fun parseLatest(json: String): String? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name").removePrefix("v")
        return tag.takeIf { VERSION.matches(it) }
    }

    /**
     * Is [latest] nieuwer dan de geïnstalleerde [installed]? Per getal vergeleken, dus 1.10.0 is nieuwer dan 1.9.0.
     * Een testversie (1.4.0-test1) is ouder dan de gewone 1.4.0.
     */
    fun isNewer(latest: String, installed: String): Boolean {
        if (!VERSION.matches(latest)) return false
        val base = VERSION.find(installed)?.takeIf { it.range.first == 0 }?.value ?: return false
        val a = latest.split('.').map { it.toInt() }
        val b = base.split('.').map { it.toInt() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return base != installed   // zelfde getallen: nieuwer dan een testversie, niet dan zichzelf
    }

    /** Moet er opnieuw gevraagd worden? Ook een klok die terugsprong (laatste vraag "in de toekomst") telt als tijd om te vragen. */
    fun isDue(lastCheck: Long, now: Long): Boolean = now - lastCheck !in 0 until INTERVAL_MS

    /**
     * Vraagt GitHub naar de laatste versie als dat aan is en de vorige vraag 24 uur oud is, en bewaart ze.
     * Ook een mislukte vraag telt: daarna wacht de app weer 24 uur. Geeft de nieuw gelezen versie, of null.
     */
    suspend fun checkIfDue(settings: SettingsRepository, now: Long = AppClock.now()): String? = withContext(Dispatchers.IO) {
        if (!settings.updateCheck || !isDue(settings.lastUpdateCheck, now)) return@withContext null
        settings.lastUpdateCheck = now
        val version = runCatching { parseLatest(fetchLatest()) }.getOrNull() ?: return@withContext null
        settings.latestVersion = version
        version
    }

    private fun fetch(): String {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", UserAgent.VALUE)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) error("GitHub: HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
