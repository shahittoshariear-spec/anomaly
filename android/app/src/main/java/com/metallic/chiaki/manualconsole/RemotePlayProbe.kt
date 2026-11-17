// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.manualconsole

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class RemotePlayProbeResult(
	val hostName: String?,
	val hostType: String?,
	val roundTripMs: Long
)

/**
 * Sends the same UDP discovery request Chiaki uses locally, but to a specific
 * host, so it can be used to check a forwarded/remote console.
 */
object RemotePlayProbe
{
	private const val DISCOVERY_PORT = 9302
	private const val PROTOCOL_VERSION = "00030010"

	fun probe(host: String, timeoutMs: Int = 3000): RemotePlayProbeResult
	{
		val address = InetAddress.getByName(host)
		DatagramSocket().use { socket ->
			socket.soTimeout = timeoutMs
			// chiaki_discovery_packet_fmt() emits the request plus a trailing NUL
			val request = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:$PROTOCOL_VERSION\n\u0000"
			val payload = request.toByteArray(Charsets.UTF_8)
			val start = System.nanoTime()
			socket.send(DatagramPacket(payload, payload.size, address, DISCOVERY_PORT))

			val buffer = ByteArray(2048)
			val response = DatagramPacket(buffer, buffer.size)
			socket.receive(response)
			val roundTripMs = (System.nanoTime() - start) / 1_000_000

			val text = String(buffer, 0, response.length, Charsets.UTF_8)
			fun field(name: String) = text.lineSequence()
				.firstOrNull { it.startsWith("$name:", ignoreCase = true) }
				?.substringAfter(':')
				?.trim()

			return RemotePlayProbeResult(
				hostName = field("host-name"),
				hostType = field("host-type"),
				roundTripMs = roundTripMs
			)
		}
	}
}
