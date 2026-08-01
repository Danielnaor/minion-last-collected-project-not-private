package com.danielnaor.client.minion

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.network.chat.Component

object MinionCommands {
	fun register() {
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(
				LiteralArgumentBuilder.literal<FabricClientCommandSource>("minions")
					.executes { context -> summary(context) },
			)
			dispatcher.register(
				LiteralArgumentBuilder.literal<FabricClientCommandSource>("minionsdump")
					.executes { context -> dump(context) },
			)
		}
	}

	private fun summary(context: CommandContext<FabricClientCommandSource>): Int {
		val records = MinionRepository.all()
		if (records.isEmpty()) {
			context.source.reply("No minions tracked yet. Open one to start tracking.")
			return 1
		}

		val history = MinionHistoryRepository.all()
			.filter { it.kind == MinionHistoryEntry.KIND_MINION }

		context.source.reply("Tracked minions (${records.size}):")
		for (record in records) {
			val name = describe(record)
			val stored = record.contents.values.sum()
			val collected = MinionTimeFormatter.format(record.lastCollectedEpochMillis)

			context.source.reply(" $name - $stored item(s) stored, collected $collected")

			val entry = history.firstOrNull {
				it.context == record.context &&
					it.profile == record.profile &&
					MinionPositions.matches(it.x, it.y, it.z, record.position())
			} ?: continue

			val rates = entry.ratePerHour()
			if (rates.isEmpty()) {
				context.source.reply("   rate: not enough observations yet")
				continue
			}
			val top = rates.entries.sortedByDescending { it.value }.take(TOP_ITEMS)
			val formatted = top.joinToString(", ") { (item, rate) -> "$item ${format(rate)}/h" }
			context.source.reply("   rate: $formatted")
		}
		return 1
	}

	private fun dump(context: CommandContext<FabricClientCommandSource>): Int {
		val (title, lines) = MinionTracker.lastDump()
		if (title == null || lines.isEmpty()) {
			context.source.reply("No minion menu captured yet. Open one, then run this again.")
			return 1
		}

		context.source.reply("Slot dump of \"$title\":")
		for (line in lines) {
			context.source.reply("  $line")
		}
		context.source.reply(
			"Slots marked storage? come from MinionSlots.STORAGE. If the real storage " +
				"slots differ, correct that set.",
		)
		return 1
	}

	private fun describe(record: MinionRecord): String {
		val type = record.minionType ?: "Unknown"
		val level = record.minionLevel?.let { " $it" } ?: ""
		val profile = record.profile?.let { " ($it)" } ?: ""
		return "$type$level$profile"
	}

	private fun format(value: Double): String {
		return if (value >= 10) value.toLong().toString() else String.format("%.1f", value)
	}

	private fun FabricClientCommandSource.reply(message: String) {
		sendFeedback(Component.literal(message))
	}

	private const val TOP_ITEMS = 3
}
