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
	private data class ActiveTarget(val context: String, val profile: String?, val position: Vec3)

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
					val (minionType, minionLevel) = parseMinionTitle(screen.title.string)
					val target = ActiveTarget(currentContext(), MinionProfile.current, pending.position)
					activeTarget = target
					pendingTarget = null
					MinionRepository.ensure(
						target.context,
						target.profile,
						target.position,
						minionType,
						minionLevel,
					)
					MinionLastCollectedClient.LOGGER.info(
						"Opened minion at {}, {}, {}",
						target.position.x,
						target.position.y,
						target.position.z,
					)

					// Hypixel fills the container a few ticks after the screen opens, so
					// the fuel slot has to be polled rather than read once during init.
					ScreenEvents.afterTick(screen).register {
						pollFuel(screen)
					}
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

	private fun pollFuel(screen: AbstractContainerScreen<*>) {
		val target = activeTarget ?: return
		if (!isContainerLoaded(screen)) return

		val (fuel, fuelCount) = readFuel(screen)
		if (MinionRepository.updateFuel(target.context, target.profile, target.position, fuel, fuelCount)) {
			MinionLastCollectedClient.LOGGER.info(
				"Minion fuel detected: {} x{}",
				fuel ?: "none",
				fuelCount ?: 0,
			)
		}
	}

	/**
	 * The container starts out empty and is populated by the server shortly after the
	 * screen opens. Treat any item in the upper container as proof it has arrived, so an
	 * unloaded screen is never mistaken for a minion with an empty fuel tank.
	 */
	private fun isContainerLoaded(screen: AbstractContainerScreen<*>): Boolean {
		val slots = screen.menu.slots
		for (index in 0 until minOf(MINION_CONTAINER_SIZE, slots.size)) {
			if (slots[index].hasItem()) return true
		}
		return false
	}

	private fun readFuel(screen: AbstractContainerScreen<*>): Pair<String?, Int?> {
		val slot = screen.menu.slots.getOrNull(MINION_FUEL_SLOT) ?: return null to null
		if (!slot.hasItem()) return null to null

		val name = slot.item.hoverName.string.trim()
		if (name.isEmpty()) return null to null
		if (FUEL_PLACEHOLDER_NAMES.any { it.equals(name, ignoreCase = true) }) return null to null
		return name to slot.item.count
	}

	@JvmStatic
	fun onSlotClicked(slot: Slot?, slotId: Int) {
		val target = activeTarget ?: return
		if (slot == null || slotId < 0 || !slot.hasItem()) return

		val itemName = slot.item.hoverName.string
		when {
			itemName.contains("Pickup Minion", ignoreCase = true) -> {
				MinionRepository.remove(target.context, target.profile, target.position)
				activeTarget = null
				showStatus("Removed saved minion")
			}
			itemName.contains("Collect All", ignoreCase = true) ||
				itemName.contains("Hopper", ignoreCase = true) -> {
				MinionRepository.markCollected(target.context, target.profile, target.position)
				showStatus("Collection time saved")
			}
		}
	}

	@JvmStatic
	fun labelFor(position: Vec3): String? {
		return MinionRepository.labelFor(currentContext(), MinionProfile.current, position)
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

	private fun parseMinionTitle(title: String): Pair<String?, Int?> {
		val match = MINION_TITLE.matchEntire(title.trim()) ?: return null to null
		val (type, level) = match.destructured
		return type.trim() to romanToInt(level)
	}

	private fun romanToInt(roman: String): Int? {
		var total = 0
		var previous = 0
		for (char in roman.uppercase().reversed()) {
			val value = ROMAN_VALUES[char] ?: return null
			total += if (value < previous) -value else value
			previous = value
		}
		return total
	}

	private fun showStatus(message: String) {
		Minecraft.getInstance().player?.displayClientMessage(
			Component.literal("[Minion Last Collected] $message"),
			true,
		)
	}

	private val MINION_TITLE = Regex("""^(.+)\sMinion\s+([IVXLCDM]+)$""", RegexOption.IGNORE_CASE)
	private val ROMAN_VALUES = mapOf(
		'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000,
	)
	private const val PENDING_TARGET_TIMEOUT_MS = 5_000L

	// Slot indices match the Hypixel minion menu layout.
	private const val MINION_FUEL_SLOT = 19
	private const val MINION_CONTAINER_SIZE = 54

	// Shown by Hypixel when the fuel slot is empty, rather than a blank slot.
	private val FUEL_PLACEHOLDER_NAMES = setOf("Minion Fuel", "Empty")
}
