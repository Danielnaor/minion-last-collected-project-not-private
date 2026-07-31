package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.Slot
import net.minecraft.world.phys.Vec3

object MinionTracker {
	private data class PendingTarget(val position: Vec3, val clickedAt: Long)
	private data class ActiveTarget(val context: String, val position: Vec3)

	private var pendingTarget: PendingTarget? = null
	private var activeTarget: ActiveTarget? = null

	fun register() {
		UseEntityCallback.EVENT.register(UseEntityCallback { _, world, _, entity, _ ->
			if (world.isClientSide && entity is ArmorStand) {
				pendingTarget = PendingTarget(entity.position(), System.currentTimeMillis())
				MinionLastCollectedClient.LOGGER.debug(
					"Clicked armor stand at {}, {}, {}",
					entity.x,
					entity.y,
					entity.z,
				)
			}
			InteractionResult.PASS
		})

		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*>) return@register

			if (isMinionTitle(screen.title.string)) {
				val pending = pendingTarget
				?.takeIf { System.currentTimeMillis() - it.clickedAt <= PENDING_TARGET_TIMEOUT_MS }

				if (pending != null) {
					val target = ActiveTarget(currentContext(), pending.position)
					activeTarget = target
					pendingTarget = null
					MinionRepository.ensure(target.context, target.position)
					MinionLastCollectedClient.LOGGER.info(
						"Opened minion at {}, {}, {}",
						target.position.x,
						target.position.y,
						target.position.z,
					)
				} else {
					MinionLastCollectedClient.LOGGER.warn(
						"Opened a minion screen without a recent armor stand interaction",
					)
				}
			} else {
				activeTarget = null
			}

			ScreenEvents.remove(screen).register {
				activeTarget = null
			}
		}
	}

	@JvmStatic
	fun onSlotClicked(slot: Slot?, slotId: Int) {
		val target = activeTarget ?: return
		if (slot == null || slotId < 0 || !slot.hasItem()) return

		val itemName = slot.item.hoverName.string
		when {
			itemName.contains("Pickup Minion", ignoreCase = true) -> {
				MinionRepository.remove(target.context, target.position)
				activeTarget = null
				showStatus("Removed saved minion")
			}
			itemName.contains("Collect All", ignoreCase = true) ||
				itemName.contains("Hopper", ignoreCase = true) -> {
				MinionRepository.markCollected(target.context, target.position)
				showStatus("Collection time saved")
			}
		}
	}

	@JvmStatic
	fun labelFor(position: Vec3): String? {
		return MinionRepository.labelFor(currentContext(), position)
	}

	private fun currentContext(): String {
		val client = Minecraft.getInstance()
		val server = client.currentServer?.ip ?: "singleplayer"
		val dimension = client.level?.dimension()?.identifier()?.toString() ?: "unknown"
		return "$server|$dimension"
	}

	private fun isMinionTitle(title: String): Boolean {
		return MINION_TITLE.matches(title.trim())
	}

	private fun showStatus(message: String) {
		Minecraft.getInstance().player?.displayClientMessage(
			Component.literal("[Minion Last Collected] $message"),
			true,
		)
	}

	private val MINION_TITLE = Regex(""".+\sMinion\s+[IVXLCDM]+""", RegexOption.IGNORE_CASE)
	private const val PENDING_TARGET_TIMEOUT_MS = 5_000L
}
