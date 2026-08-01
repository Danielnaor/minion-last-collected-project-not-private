package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

/**
 * Tracks which SkyBlock profile the player is currently playing on, so minions saved on
 * one profile are not confused with minions standing in the same spot on another.
 *
 * The Hypixel Mod API cannot supply this: its location packet only carries the server,
 * lobby, mode and map, and has no notion of a SkyBlock profile. Hypixel publishes the
 * profile name in exactly two places, so both are read here:
 *
 *  - the chat messages sent when joining or switching profiles, which are authoritative
 *    but only arrive at that moment, and
 *  - the "Profile:" line of the tab list widget, which is present the whole time but only
 *    when the player has that widget enabled in `/widget`.
 *
 * Names are stored lowercase so the two sources can never disagree on capitalisation and
 * split one profile into two sets of records.
 */
object MinionProfile {
	var current: String? = null
		private set

	private var ticksUntilPoll = 0

	fun register() {
		ClientReceiveMessageEvents.GAME.register { message, overlay ->
			if (!overlay) readChatMessage(message.string)
		}

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			if (ticksUntilPoll-- > 0) return@register
			ticksUntilPoll = TAB_LIST_POLL_INTERVAL_TICKS
			readTabList(client)
		}

		// Otherwise the last profile would linger and be applied to a different server.
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
			current = null
			ticksUntilPoll = 0
		}
	}

	private fun readChatMessage(rawMessage: String) {
		val message = rawMessage.trim()
		val prefix = PROFILE_MESSAGE_PREFIXES.firstOrNull { message.startsWith(it, ignoreCase = true) }
			?: return
		update(message.substring(prefix.length))
	}

	private fun readTabList(client: Minecraft) {
		val connection = client.connection ?: return
		for (entry in connection.onlinePlayers) {
			val line = entry.tabListDisplayName?.string?.trim() ?: continue
			val match = TAB_LIST_PROFILE.matchEntire(line) ?: continue
			update(match.groupValues[1])
			return
		}
	}

	private fun update(rawName: String) {
		val name = normalise(rawName) ?: return
		if (current == name) return
		current = name
		MinionLastCollectedClient.LOGGER.info("SkyBlock profile detected: {}", name)
	}

	/**
	 * Strips the decoration Hypixel appends to the profile name: the "(Co-op)" suffix on
	 * the chat messages, and the gamemode symbols on the tab list line.
	 */
	private fun normalise(rawName: String): String? {
		val name = rawName
			.replace(COOP_SUFFIX, "")
			.trim()
			.trimEnd('.', ',')
			.trim()
			.lowercase()
		return name.ifEmpty { null }
	}

	private val PROFILE_MESSAGE_PREFIXES = listOf(
		"You are playing on profile:",
		"Your profile was changed to:",
	)

	private val COOP_SUFFIX = Regex("""\(Co-op\)""", RegexOption.IGNORE_CASE)

	// Trailing symbols mark Ironman / Bingo / Stranded profiles.
	private val TAB_LIST_PROFILE = Regex("""Profile:\s*([\w\s]+?)\s*[♲Ⓑ☀]*""")

	private const val TAB_LIST_POLL_INTERVAL_TICKS = 20
}
