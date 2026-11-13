package be.on5gs.eme.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Praat met de HB9Q-logger (logger.hb9q.ch) zoals de website zelf doet:
 *  1. startpagina openen   -> sessiecookie + verborgen formulierveld "swordfisch"
 *  2. inloggen (POST)      -> txtLoginName + txtPwd
 *  3. berichten ophalen    -> POST /rpc/logger/logger_lst.cfm?vBand=..    (JSON met HTML-rijen)
 *  4. uitloggen            -> GET /?srv=logout
 * ALLEEN LEZEN (afgesproken met HB9Q): de app verstuurt geen berichten en wijzigt niets op de site.
 * Deelnemers = wie recent in de chat schreef; zie ChatStore.recentParticipants.
 */
class Hb9qClient(private val baseUrl: String = BASE_URL) {

    class LoginFailedException(msg: String) : IOException(msg)

    private val pageUrl = "$baseUrl/"

    private var loggedIn = false
    private var loggedInAs: String? = null

    /** Klok voor de wachttijd na een geweigerde login; alleen tests vervangen hem. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Laatste login die HB9Q weigerde. Met dezelfde gegevens niet opnieuw proberen binnen [LOGIN_RETRY_MS]. */
    private class Rejected(val login: String, val password: String, val at: Long)
    private var rejected: Rejected? = null
    private val LOGIN_RETRY_MS = 5 * 60_000L
    private val LOGIN_FAILED = "Inloggen bij HB9Q mislukt. Controleer login en wachtwoord."

    init {
        // De sessie van HB9Q werkt met cookies; die houden we in het geheugen bij.
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
        }
    }

    suspend fun login(login: String, password: String) = withContext(Dispatchers.IO) {
        // Pas geweigerd met dezelfde gegevens: HB9Q niet bij elke verversing opnieuw belasten
        rejected?.let {
            if (it.login == login && it.password == password && clock() - it.at < LOGIN_RETRY_MS) {
                throw LoginFailedException(LOGIN_FAILED)
            }
        }
        loggedIn = false
        // Altijd met een nieuwe sessie beginnen: met een oude, nog geldige sessiecookie toont HB9Q geen loginformulier
        (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
        val page = request("GET", pageUrl, null).body
        val token = Jsoup.parse(page).selectFirst("input[name=swordfisch]")?.attr("value")
            ?: throw IOException("HB9Q: login form not found")
        val form = formEncode(
            "swordfisch" to token,
            "txtLoginName" to login,
            "txtPwd" to password,
        )
        val result = request("POST", pageUrl, form).body
        // Na een geslaagde login staat er een "logout"-link op de pagina
        if (!result.contains("srv=logout")) {
            rejected = Rejected(login, password, clock())
            throw LoginFailedException(LOGIN_FAILED)
        }
        rejected = null
        loggedIn = true
        loggedInAs = login
    }

    /** Berichten van een band. */
    suspend fun fetchMessages(band: Hb9qBand, login: String, password: String): List<ChatMessage> {
        val json = postJson("$baseUrl/rpc/logger/logger_lst.cfm?vBand=${band.id}&vSearch=&vSearchDt=0", "", login, password)
        return ChatParser.parseRows(json.optString("logger"))
    }

    /** Uitloggen zoals de website ("Logout") en de sessie-cookies in de app wissen. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        if (loggedIn) runCatching { request("GET", "$pageUrl?srv=logout", null) }
        (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
        loggedIn = false
        loggedInAs = null
        rejected = null
    }

    // ---------- Intern ----------

    private suspend fun ensureLoggedIn(login: String, password: String) {
        if (!loggedIn || loggedInAs != login) login(login, password)
    }

    /** POST zonder inhoud, antwoord als JSON. Logt (opnieuw) in als dat nodig is. */
    private suspend fun postJson(url: String, body: String, login: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureLoggedIn(login, password)
            var resp = request("POST", url, body)
            if (resp.code in 300..399 || !resp.body.trimStart().startsWith("{")) {
                // Sessie verlopen: één keer opnieuw inloggen
                login(login, password)
                resp = request("POST", url, body)
            }
            val json = runCatching { JSONObject(resp.body) }.getOrNull()
                ?: throw IOException("HB9Q: unexpected answer")
            if (json.optString("status") != "success") throw IOException("HB9Q: data not available")
            json
        }

    private data class Response(val code: Int, val body: String)

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun request(method: String, url: String, formBody: String?): Response {
        val conn = open(method, url)
        if (formBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            if (formBody != null) conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code >= 400) throw IOException("${shortName(url)}: HTTP $code")
            val body = if (code in 300..399) "" else conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // Een redirect na het inloggen volgen we zelf, zodat de cookies behouden blijven
            if (code in 300..399 && method == "POST" && url == pageUrl) {
                val location = conn.getHeaderField("Location") ?: pageUrl
                return request("GET", URL(URL(url), location).toString(), null)
            }
            return Response(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /** "https://logger.hb9q.ch/rpc/logger/logger_lst.cfm?x" -> "logger_lst" (voor foutmeldingen) */
    private fun shortName(url: String): String =
        url.substringBefore("?").substringAfterLast("/").removeSuffix(".cfm").ifEmpty { "HB9Q" }

    private fun formEncode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    companion object {
        private const val BASE_URL = "https://logger.hb9q.ch"
        private const val USER_AGENT = "EME-ON5GS-Android/0.16"
    }
}
