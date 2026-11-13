package be.on5gs.eme.chat

import be.on5gs.eme.UserAgent
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
class Hb9qClient internal constructor(private val baseUrl: String, nowMillis: () -> Long) {

    constructor() : this(BASE_URL, System::currentTimeMillis)

    class LoginFailedException(msg: String) : IOException(msg)

    /** Login wordt tijdelijk niet opnieuw geprobeerd: nog [retryAfterMs] ms te wachten. */
    class LoginOnCooldownException(val retryAfterMs: Long) : IOException("HB9Q: te veel mislukte logins, even wachten")

    private val pageUrl = "$baseUrl/"

    private var loggedIn = false
    private var loggedInAs: String? = null
    private val loginBackoff = LoginBackoff(nowMillis)

    init {
        // De sessie van HB9Q werkt met cookies; die houden we in het geheugen bij.
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
        }
    }

    suspend fun login(login: String, password: String) = withContext(Dispatchers.IO) {
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
            throw LoginFailedException("Inloggen bij HB9Q mislukt. Controleer login en wachtwoord.")
        }
        loggedIn = true
        loggedInAs = login
    }

    /** Berichten van een band. */
    suspend fun fetchMessages(band: Hb9qBand, login: String, password: String): List<ChatMessage> {
        val json = postJson("$baseUrl/rpc/logger/logger_lst.cfm?vBand=${band.id}&vSearch=&vSearchDt=0", "", login, password)
        return ChatParser.parseRows(loggerRows(json))
    }

    /** Uitloggen zoals de website ("Logout") en de sessie-cookies in de app wissen. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        if (loggedIn) runCatching { request("GET", "$pageUrl?srv=logout", null) }
        (CookieHandler.getDefault() as? CookieManager)?.cookieStore?.removeAll()
        loggedIn = false
        loggedInAs = null
    }

    // ---------- Intern ----------

    /**
     * De waarde van "logger" uit een geslaagde ("status":"success") respons. Een ONTBREKEND
     * "logger"-veld is een kapotte/onvolledige respons, geen eerlijk lege chat - zonder dit
     * onderscheid geeft `optString` stilzwijgend "" terug, identiek aan een echt lege chat,
     * en valt een server-glitch nooit op (zie #7). "logger" aanwezig maar leeg ("") blijft wél
     * gewoon een lege chat.
     */
    internal fun loggerRows(json: JSONObject): String {
        if (!json.has("logger")) throw IOException("HB9Q: antwoord zonder 'logger'-veld")
        return json.optString("logger")
    }

    private suspend fun ensureLoggedIn(login: String, password: String) {
        if (loggedIn && loggedInAs == login) return
        loginBackoff.retryAfterMs()?.let { throw LoginOnCooldownException(it) }
        try {
            login(login, password)
            loginBackoff.onSuccess()
        } catch (e: Exception) {
            loginBackoff.onFailure()
            throw e
        }
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
            setRequestProperty("User-Agent", UserAgent.VALUE)
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
    }
}
