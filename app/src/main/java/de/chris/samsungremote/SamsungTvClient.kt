package de.chris.samsungremote

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SamsungTvClient(
    private val onToken: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var connecting = false
    private val pendingKeys = ConcurrentLinkedQueue<String>()

    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val secureClient: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private val legacyClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun connect(profile: TvProfile) {
        require(profile.ip.isNotBlank()) { "Bitte zuerst die IP-Adresse des TVs eintragen." }
        disconnect(clearPendingKeys = false)
        connecting = true
        onStatus("Verbinde mit ${profile.ip} …")
        open(profile, secure = true, allowFallback = true)
    }

    @Synchronized
    fun sendKeyWhenConnected(profile: TvProfile, key: String) {
        require(profile.ip.isNotBlank()) { "Bitte zuerst die IP-Adresse des TVs eintragen." }

        if (connected) {
            if (sendKeyNow(key)) return
            connected = false
        }

        pendingKeys.add(key)
        if (!connecting) connect(profile)
    }

    private fun open(profile: TvProfile, secure: Boolean, allowFallback: Boolean) {
        val rawName = Base64.encodeToString("Chris Samsung Remote".toByteArray(), Base64.NO_WRAP)
        val encodedName = URLEncoder.encode(rawName, StandardCharsets.UTF_8.name())
        val scheme = if (secure) "wss" else "ws"
        val port = if (secure) 8002 else 8001
        val tokenPart = if (secure && profile.token.isNotBlank()) {
            "&token=${URLEncoder.encode(profile.token, StandardCharsets.UTF_8.name())}"
        } else ""
        val url = "$scheme://${profile.ip}:$port/api/v2/channels/samsung.remote.control?name=$encodedName$tokenPart"

        val request = Request.Builder().url(url).build()
        val httpClient = if (secure) secureClient else legacyClient

        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus(if (secure) "Verbindung geöffnet – ggf. am TV Zulassen drücken …" else "Legacy-Verbindung geöffnet …")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    when (json.optString("event")) {
                        "ms.channel.connect" -> {
                            connected = true
                            connecting = false
                            val token = json.optJSONObject("data")?.optString("token").orEmpty()
                            if (token.isNotBlank()) onToken(token)
                            onStatus("Verbunden ✓")
                            flushPendingKeys()
                        }
                        "ms.channel.unauthorized" -> {
                            connected = false
                            connecting = false
                            pendingKeys.clear()
                            onStatus("TV hat die Verbindung nicht freigegeben.")
                        }
                    }
                }.onFailure {
                    onStatus("TV-Antwort erhalten.")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                onStatus("Verbindung geschlossen.")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                if (secure && allowFallback) {
                    onStatus("Port 8002 nicht erreichbar – versuche Port 8001 …")
                    open(profile, secure = false, allowFallback = false)
                } else {
                    connecting = false
                    pendingKeys.clear()
                    onStatus("Verbindung fehlgeschlagen: ${t.message ?: "unbekannter Fehler"}")
                }
            }
        })
    }

    @Synchronized
    fun disconnect(clearPendingKeys: Boolean = true) {
        connected = false
        connecting = false
        socket?.close(1000, "bye")
        socket = null
        if (clearPendingKeys) pendingKeys.clear()
    }

    private fun flushPendingKeys() {
        while (true) {
            val key = pendingKeys.poll() ?: break
            if (!sendKeyNow(key)) {
                onStatus("Ein Steuerbefehl konnte nicht gesendet werden.")
                break
            }
            Thread.sleep(80)
        }
    }

    private fun sendKeyNow(key: String): Boolean {
        val ws = socket ?: return false
        val payload = JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }
        return ws.send(payload.toString())
    }

    fun isConnected(): Boolean = connected

    fun isTvOnline(ip: String, timeoutMs: Int = 700): Boolean {
        if (ip.isBlank()) return false
        return listOf(8002, 8001).any { port ->
            runCatching {
                Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
                true
            }.getOrDefault(false)
        }
    }
}
