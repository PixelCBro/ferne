package de.chris.samsungremote

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object WakeOnLan {
    fun wake(macAddress: String) {
        val mac = parseMac(macAddress)
        val packet = ByteArray(6 + 16 * mac.size)

        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 6 until packet.size) packet[i] = mac[(i - 6) % mac.size]

        val destinations = linkedSetOf<InetAddress>()
        destinations += InetAddress.getByName("255.255.255.255")

        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val nif = interfaces.nextElement()
            if (!nif.isUp || nif.isLoopback) continue
            for (address in nif.interfaceAddresses) {
                address.broadcast?.let(destinations::add)
            }
        }

        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (destination in destinations) {
                repeat(3) {
                    socket.send(DatagramPacket(packet, packet.size, destination, 9))
                    Thread.sleep(120)
                }
            }
        }
    }

    fun isValidMac(value: String): Boolean = runCatching { parseMac(value); true }.getOrDefault(false)

    private fun parseMac(value: String): ByteArray {
        val clean = value.trim().replace("-", ":")
        val parts = clean.split(":")
        require(parts.size == 6) { "MAC-Adresse muss z. B. AA:BB:CC:DD:EE:FF sein." }
        return ByteArray(6) { index -> parts[index].toInt(16).toByte() }
    }
}
