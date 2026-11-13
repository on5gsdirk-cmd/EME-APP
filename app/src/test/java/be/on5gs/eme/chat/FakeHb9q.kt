package be.on5gs.eme.chat

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Nagebootste HB9Q-logger op 127.0.0.1, voor tests zonder netwerk.
 * Gedraagt zich zoals Hb9qClient de echte site ziet:
 *  - GET /        zonder ingelogde sessie: loginformulier met verborgen veld "swordfisch" (en een sessiecookie)
 *                 met ingelogde sessie: pagina met "srv=logout" en GEEN loginformulier (zoals hb9q_page.html)
 *  - POST /       juiste login: pagina met "srv=logout"; anders weer het loginformulier
 *  - POST /rpc/logger/logger_lst.cfm   ingelogd: JSON; anders een redirect naar /
 *  - GET /?srv=logout   sessie uitloggen
 * Eenvoudige HTTP/1.1-server op een ServerSocket (com.sun.net.httpserver zit niet in android.jar).
 */
class FakeHb9q(private val users: Map<String, String>) : AutoCloseable {

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val sessions = mutableMapOf<String, String?>()   // sessie-id -> ingelogde gebruiker
    private var nextSession = 1

    /** Aantal login-pogingen (POST van het loginformulier). */
    @Volatile
    var loginPosts = 0
        private set

    /** Laatste gebruiker die met succes inlogde. */
    @Volatile
    var lastLoggedIn: String? = null
        private set

    val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

    init {
        thread(isDaemon = true, name = "FakeHb9q") {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { runCatching { serve(it) } }
            }
        }
    }

    override fun close() = server.close()

    private class Request(val method: String, val path: String, val query: String, val headers: Map<String, String>, val body: String)
    private class Response(val code: Int, val body: String, val headers: List<Pair<String, String>> = emptyList())

    private fun serve(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val (method, target) = requestLine.split(" ")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toInt() ?: 0
        val body = String(ByteArray(length).also { var n = 0; while (n < length) n += input.read(it, n, length - n) }, Charsets.UTF_8)
        val request = Request(method, target.substringBefore("?"), target.substringAfter("?", ""), headers, body)

        val response = handle(request)
        val bytes = response.body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ${response.code} X\r\n")
            response.headers.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(c.toChar())
        }
    }

    @Synchronized
    private fun handle(req: Request): Response {
        val sid = req.headers["cookie"].orEmpty()
            .let { Regex("""CFID=(\w+)""").find(it)?.groupValues?.get(1) }
            ?.takeIf { it in sessions }
        val user = sid?.let { sessions[it] }

        return when {
            req.path == "/" && req.method == "GET" && req.query == "srv=logout" -> {
                if (sid != null) sessions[sid] = null
                Response(200, LOGIN_PAGE)
            }
            req.path == "/" && req.method == "GET" -> when {
                user != null -> Response(200, LOGGED_IN_PAGE)
                sid != null -> Response(200, LOGIN_PAGE)
                else -> {
                    val id = "S${nextSession++}"
                    sessions[id] = null
                    Response(200, LOGIN_PAGE, listOf("Set-Cookie" to "CFID=$id; Path=/"))
                }
            }
            req.path == "/" && req.method == "POST" -> {
                loginPosts++
                val form = parseForm(req.body)
                val name = form["txtLoginName"]
                if (sid != null && form["swordfisch"] == TOKEN && name != null && users[name] == form["txtPwd"]) {
                    sessions[sid] = name
                    lastLoggedIn = name
                    Response(200, LOGGED_IN_PAGE)
                } else {
                    Response(200, LOGIN_PAGE)
                }
            }
            req.path == "/rpc/logger/logger_lst.cfm" ->
                if (user != null) Response(200, """{"status":"success","logger":""}""")
                else Response(302, "", listOf("Location" to "/"))
            else -> Response(404, "")
        }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split("&").filter { "=" in it }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    companion object {
        const val TOKEN = "fish123"
        const val LOGIN_PAGE = """<html><form method="post"><input type="hidden" name="swordfisch" value="$TOKEN">
            <input name="txtLoginName"><input name="txtPwd" type="password"></form></html>"""
        const val LOGGED_IN_PAGE = """<html><a href="/?srv=logout">Logout</a></html>"""
    }
}
