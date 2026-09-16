package de.chris.samsungremote

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQ_LOCAL_NETWORK = 1701
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    private lateinit var prefs: TvPreferences
    private lateinit var client: SamsungTvClient
    private val executor = Executors.newCachedThreadPool()
    private var actionAfterLanPermission: (() -> Unit)? = null

    private lateinit var nameInput: EditText
    private lateinit var ipInput: EditText
    private lateinit var macInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = TvPreferences(this)
        client = SamsungTvClient(
            onToken = { token ->
                prefs.saveToken(token)
                runOnUiThread {
                    tokenInput.setText(token)
                    setStatus("Verbunden ✓ Freigabe wurde gespeichert.")
                }
            },
            onStatus = { text -> runOnUiThread { setStatus(text) } }
        )
        setContentView(buildUi())
        loadProfile()
    }

    override fun onDestroy() {
        client.disconnect()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCAL_NETWORK) return

        val action = actionAfterLanPermission
        actionAfterLanPermission = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setStatus("Lokales Netzwerk erlaubt ✓")
            action?.invoke()
        } else {
            setStatus("Ohne Zugriff auf das lokale Netzwerk kann die App den TV nicht steuern.")
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(245, 247, 250)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Samsung Universal Remote"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(25, 32, 43))
        })
        root.addView(TextView(this).apply {
            text = "Samsung Smart TV · WLAN/LAN · Wake-on-LAN"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(2), 0, dp(16))
        })

        nameInput = input("TV-Name", InputType.TYPE_CLASS_TEXT)
        ipInput = input("IP-Adresse, z. B. 192.168.178.40", InputType.TYPE_CLASS_PHONE)
        macInput = input("MAC-Adresse, z. B. AA:BB:CC:DD:EE:FF", InputType.TYPE_CLASS_TEXT)
        tokenInput = input("Pairing-Token (wird automatisch gespeichert)", InputType.TYPE_CLASS_TEXT)
        root.addView(nameInput)
        root.addView(ipInput)
        root.addView(macInput)
        root.addView(tokenInput)

        val setupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setupRow.addView(actionButton("TV suchen", 1f) { withLanPermission { scanForTvs() } })
        setupRow.addView(actionButton("Speichern", 1f) {
            saveProfile()
            setStatus("TV-Daten gespeichert.")
        })
        root.addView(setupRow)
        root.addView(actionButton("Verbinden / Pairing", 1f) {
            withLanPermission { connectToTv() }
        })

        statusText = TextView(this).apply {
            text = "Bereit"
            textSize = 14f
            setTextColor(Color.rgb(45, 70, 100))
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        root.addView(statusText)

        root.addView(sectionTitle("POWER"))
        root.addView(actionButton("⏻  Ein / Aus", 1f) {
            withLanPermission { powerToggle() }
        }.apply {
            textSize = 20f
            setPadding(0, dp(14), 0, dp(14))
        })

        root.addView(sectionTitle("NAVIGATION"))
        root.addView(remoteGrid(listOf(
            "▲" to "KEY_UP", "OK" to "KEY_ENTER", "Zurück" to "KEY_RETURN",
            "◀" to "KEY_LEFT", "▼" to "KEY_DOWN", "▶" to "KEY_RIGHT",
            "Home" to "KEY_HOME", "Menü" to "KEY_MENU", "Quelle" to "KEY_SOURCE"
        )))

        root.addView(sectionTitle("LAUTSTÄRKE & SENDER"))
        root.addView(remoteGrid(listOf(
            "Vol +" to "KEY_VOLUP", "Mute" to "KEY_MUTE", "CH +" to "KEY_CHUP",
            "Vol −" to "KEY_VOLDOWN", "Guide" to "KEY_GUIDE", "CH −" to "KEY_CHDOWN"
        )))

        root.addView(sectionTitle("ZAHLEN"))
        root.addView(remoteGrid(listOf(
            "1" to "KEY_1", "2" to "KEY_2", "3" to "KEY_3",
            "4" to "KEY_4", "5" to "KEY_5", "6" to "KEY_6",
            "7" to "KEY_7", "8" to "KEY_8", "9" to "KEY_9",
            "−" to "KEY_PRECH", "0" to "KEY_0", "Enter" to "KEY_ENTER"
        )))

        root.addView(sectionTitle("MEDIA"))
        root.addView(remoteGrid(listOf(
            "⏪" to "KEY_REWIND", "▶ Play" to "KEY_PLAY", "⏩" to "KEY_FF",
            "⏹ Stop" to "KEY_STOP", "⏸ Pause" to "KEY_PAUSE", "Info" to "KEY_INFO"
        )))

        root.addView(TextView(this).apply {
            text = "Einschalten: Die App sendet ein Wake-on-LAN-Paket an die gespeicherte MAC-Adresse. Am TV muss – sofern vorhanden – „Power On with Mobile“ / „Mit Mobilgerät einschalten“ aktiviert sein."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(18), 0, 0)
        })

        return scroll
    }

    private fun withLanPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 37 || checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            action()
            return
        }

        actionAfterLanPermission = action
        if (shouldShowRequestPermissionRationale(ACCESS_LOCAL_NETWORK)) {
            AlertDialog.Builder(this)
                .setTitle("TV im lokalen Netzwerk steuern")
                .setMessage("Die App braucht Zugriff auf dein lokales Netzwerk, um Samsung-TVs zu finden, Steuerbefehle zu senden und Wake-on-LAN zu verwenden.")
                .setPositiveButton("Zulassen") { _, _ ->
                    requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), REQ_LOCAL_NETWORK)
                }
                .setNegativeButton("Nicht jetzt") { _, _ ->
                    actionAfterLanPermission = null
                    setStatus("Netzwerkzugriff wurde nicht erlaubt.")
                }
                .show()
        } else {
            requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK), REQ_LOCAL_NETWORK)
        }
    }

    private fun remoteGrid(items: List<Pair<String, String>>): GridLayout = GridLayout(this).apply {
        columnCount = 3
        useDefaultMargins = true
        items.forEach { (label, key) ->
            addView(actionButton(label, 1f) {
                withLanPermission { sendKey(key) }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(58)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun input(hintText: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        inputType = type
        setTextColor(Color.BLACK)
        setHintTextColor(Color.GRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
    }

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(70, 80, 95))
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun actionButton(label: String, weight: Float, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
            bottomMargin = dp(6)
        }
    }

    private fun loadProfile() {
        val p = prefs.load()
        nameInput.setText(p.name)
        ipInput.setText(p.ip)
        macInput.setText(p.mac)
        tokenInput.setText(p.token)
    }

    private fun currentProfile() = TvProfile(
        name = nameInput.text.toString().trim().ifBlank { "Mein Samsung TV" },
        ip = ipInput.text.toString().trim(),
        mac = macInput.text.toString().trim(),
        token = tokenInput.text.toString().trim()
    )

    private fun saveProfile() = prefs.save(currentProfile())

    private fun connectToTv() {
        saveProfile()
        val profile = currentProfile()
        if (profile.ip.isBlank()) {
            setStatus("Bitte zuerst einen TV suchen oder seine IP-Adresse eintragen.")
            return
        }
        executor.execute {
            runCatching { client.connect(profile) }
                .onFailure { runOnUiThread { setStatus(it.message ?: "Verbindung fehlgeschlagen.") } }
        }
    }

    private fun sendKey(key: String) {
        saveProfile()
        val profile = currentProfile()
        if (profile.ip.isBlank()) {
            setStatus("Bitte zuerst einen TV suchen oder seine IP-Adresse eintragen.")
            return
        }
        executor.execute {
            runCatching { client.sendKeyWhenConnected(profile, key) }
                .onFailure { runOnUiThread { setStatus(it.message ?: "Befehl konnte nicht gesendet werden.") } }
        }
    }

    private fun powerToggle() {
        saveProfile()
        val profile = currentProfile()
        if (profile.ip.isBlank()) {
            setStatus("Bitte zuerst die IP-Adresse des TVs eintragen.")
            return
        }

        executor.execute {
            val online = client.isTvOnline(profile.ip)
            if (online) {
                runOnUiThread { setStatus("TV ist online – sende Ausschalten …") }
                runCatching { client.sendKeyWhenConnected(profile, "KEY_POWER") }
                    .onFailure { runOnUiThread { setStatus("Ausschalten fehlgeschlagen: ${it.message}") } }
            } else {
                if (!WakeOnLan.isValidMac(profile.mac)) {
                    runOnUiThread { setStatus("Zum Einschalten fehlt eine gültige MAC-Adresse.") }
                    return@execute
                }
                runOnUiThread { setStatus("TV ist offline – sende Wake-on-LAN …") }
                runCatching { WakeOnLan.wake(profile.mac) }
                    .onSuccess { runOnUiThread { setStatus("Wake-Paket gesendet. Der TV sollte jetzt starten.") } }
                    .onFailure { runOnUiThread { setStatus("Wake-on-LAN fehlgeschlagen: ${it.message}") } }
            }
        }
    }

    private fun scanForTvs() {
        setStatus("Suche Samsung-TVs im WLAN/LAN …")
        executor.execute {
            val found = runCatching { SsdpDiscovery.discover(this) }.getOrElse {
                runOnUiThread { setStatus("Suche fehlgeschlagen: ${it.message}") }
                return@execute
            }
            runOnUiThread {
                when {
                    found.isEmpty() -> setStatus("Kein Samsung-TV gefunden. Die IP kann manuell eingetragen werden.")
                    found.size == 1 -> {
                        ipInput.setText(found.first().ip)
                        setStatus("TV gefunden: ${found.first().ip}. Für Einschalten bitte noch die MAC-Adresse eintragen.")
                    }
                    else -> showTvPicker(found)
                }
            }
        }
    }

    private fun showTvPicker(found: List<SsdpDiscovery.Candidate>) {
        val labels = found.map { it.ip }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Samsung-TV auswählen")
            .setItems(labels) { _, which ->
                ipInput.setText(found[which].ip)
                setStatus("TV ausgewählt: ${found[which].ip}. Für Einschalten bitte noch die MAC-Adresse eintragen.")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
