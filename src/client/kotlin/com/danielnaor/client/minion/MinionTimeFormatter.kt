package com.danielnaor.client.minion

object MinionTimeFormatter {
	fun format(timestamp: Long, now: Long = System.currentTimeMillis()): String {
		if (timestamp <= 0L) return "Never"

		var seconds = ((now - timestamp).coerceAtLeast(0L)) / 1_000L
		val days = seconds / 86_400L
		seconds %= 86_400L
		val hours = seconds / 3_600L
		seconds %= 3_600L
		val minutes = seconds / 60L
		seconds %= 60L

		return when {
			days > 0 -> "${days}d ${hours}h ago"
			hours > 0 -> "${hours}h ${minutes}m ago"
			minutes > 0 -> "${minutes}m ${seconds}s ago"
			else -> "${seconds}s ago"
		}
	}
}
