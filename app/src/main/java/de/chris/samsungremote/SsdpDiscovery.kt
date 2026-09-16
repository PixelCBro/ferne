package de.chris.samsungremote

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI

object SsdpDiscovery {
    data class Candidate(val ip: String, val location: String?)

    fun discover(context: Context, durationMs: Int = 3500): List<Candidate> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("SamsungRemoteDiscovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        return try {
            val found = linkedMapOf<String, Candidate>()
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val target = InetAddress.getByName("239.255.255.250")
                val requests = listOf("ssdp:all", "urn:samsung.com:device:RemoteControlReceiver:1")

                for (st in requests) {
                    val message = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n"
                        ).toByteArray()
                    socket.send(DatagramPacket(message, message.size, target, 1900))
                }

                val deadline = System.currentTimeMillis() + durationMs
                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, packet.offset, packet.length)
                        val samsungLike = text.contains("samsung", ignoreCase = true) ||
                            text.contains("RemoteControlReceiver", ignoreCase = true)
                        if (!samsungLike) continue

                        val location = Regex("(?im)^LOCATION:\\s*(.+)$")
                            .find(text)?.groupValues?.getOrNull(1)?.trim()
                        val ipFromLocation = location?.let {
                            runCatching { URI(it).host }.getOrNull()
                        }
                        val ip = ipFromLocation ?: packet.address.hostAddress ?: continue
                        found[ip] = Candidate(ip, location)
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
            found.values.toList()
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }
}
