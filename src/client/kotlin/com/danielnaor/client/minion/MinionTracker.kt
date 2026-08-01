package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.Slot
import net.minecraft.world.phys.Vec3

object MinionTracker {
	private data class Pending(val position: Vec3, val clickedAt: Long)
	private data class ActiveTarget(val context: String, val profile: String?, val position: Vec3)

	private var pendingEntity: Pending? = null
	private var pendingBlock: Pending? = null

	/** Set once the open menu has been identified as a minion. */
	private var activeMinion: ActiveTarget? = null

	/** Set once the open menu has been identified as a Minion Storage screen. */
	private var activeStorage: ActiveTarget? = null

	private var menuIdentified = false

	/**
	 * Whether this menu has been folded into the history yet. Polling runs every tick, so
	 * without this an unchanged menu would rewrite the history file continuously.
	 */
	private var observedThisMenu = false
	private var minionType: String? = null
	private var minionLevel: Int? = null

	/**
	 * Snapshot of the last minion menu seen, for `/minionsdump`. Opening chat closes the
	 * container, so a command can never read a live menu; it has to be captured here.
	 */
	private var lastDump: List<String> = emptyList()
	private var lastDumpTitle: String? = null

	fun register() {
		UseEntityCallback.EVENT.register(UseEntityCallback { _, world, _, entity, _ ->
			if (world.isClientSide && entity is ArmorStand) {
				pendingEntity = Pending(entity.position(), System.currentTimeMillis())
			}
			InteractionResult.PASS
		})

		// The Minion Storage screen is opened from a block, not from a minion, so it needs
		// its own most-recent-interaction to be attributed to anything.
		UseBlockCallback.EVENT.register(UseBlockCallback { _, world, _, hitResult ->
			if (world.isClientSide) {
				pendingBlock = Pending(hitResult.blockPos.center, System.currentTimeMillis())
			}
			InteractionResult.PASS
		})

		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*>) return@register

			if (isMinionTitle(screen.title.string)) {
				val parsed = parseMinionTitle(screen.title.string)
				minionType = parsed.first
				minionLevel = parsed.second
				menuIdentified = false

				// Hypixel fills the container a few ticks after the screen opens, so both the
				// menu contents and the check that tells a minion from its storage screen have
				// to be polled rather than read once during init.
				ScreenEvents.afterTick(screen).register {
					pollScreen(screen)
				}
			} else {
				clearActiveMenu()
			}

			ScreenEvents.remove(screen).register {
				clearActiveMenu()
			}
		}
	}

	private fun clearActiveMenu() {
		activeMinion = null
		activeStorage = null
		menuIdentified = false
		observedThisMenu = false
		minionType = null
		minionLevel = null
	}

	private fun pollScreen(screen: AbstractContainerScreen<*>) {
		if (!MinionContents.isLoaded(screen)) return

		if (!menuIdentified) {
			menuIdentified = true
			identifyMenu(screen)
		}

		activeMinion?.let { recordMinion(screen, it) }
		activeStorage?.let { recordStorage(screen, it) }
	}

	/**
	 * Decides whether the open menu is a minion or the separate Minion Storage screen. Both
	 * share the "... Minion ..." title, so the presence of the Collect All button is what
	 * tells them apart, the same discriminator SkyHanni uses.
	 */
	private fun identifyMenu(screen: AbstractContainerScreen<*>) {
		captureDump(screen)

		if (MinionContents.isMinionMenu(screen)) {
			val pending = freshPending(pendingEntity)
			if (pending == null) {
				MinionLastCollectedClient.LOGGER.warn(
					"Opened a minion screen without a recent armor stand interaction",
				)
				return
			}

			val target = ActiveTarget(currentContext(), MinionProfile.current, pending.position)
			activeMinion = target
			pendingEntity = null
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
			return
		}

		// Attribution here is best effort: the storage screen does not say which minion it
		// belongs to, so it is keyed by the block that was just used to open it.
		val pending = freshPending(pendingBlock)
		if (pending == null) {
			MinionLastCollectedClient.LOGGER.warn(
				"Opened a minion storage screen without a recent block interaction",
			)
			return
		}

		activeStorage = ActiveTarget(currentContext(), MinionProfile.current, pending.position)
		pendingBlock = null
		MinionLastCollectedClient.LOGGER.info("Opened minion storage")
	}

	private fun recordMinion(screen: AbstractContainerScreen<*>, target: ActiveTarget) {
		val snapshot = MinionContents.read(screen)
		val changed = MinionRepository.updateContents(
			target.context,
			target.profile,
			target.position,
			snapshot,
		)

		// Record on the first poll even when nothing changed, otherwise a minion that is
		// always read with the same contents would never start its observation window.
		if (changed || !observedThisMenu) {
			observedThisMenu = true
			MinionHistoryRepository.observe(
				context = target.context,
				profile = target.profile,
				position = target.position,
				kind = MinionHistoryEntry.KIND_MINION,
				contents = snapshot.contents,
				minionType = minionType,
				minionLevel = minionLevel,
			)
			MinionLastCollectedClient.LOGGER.info(
				"Minion contents: {} ({} slot upgrade: {})",
				snapshot.contents,
				snapshot.storageSlots ?: "?",
				snapshot.storageUpgrade ?: "none",
			)
		}

		val (fuel, fuelCount) = readFuel(screen)
		if (MinionRepository.updateFuel(target.context, target.profile, target.position, fuel, fuelCount)) {
			MinionLastCollectedClient.LOGGER.info(
				"Minion fuel detected: {} x{}",
				fuel ?: "none",
				fuelCount ?: 0,
			)
		}
	}

	private fun recordStorage(screen: AbstractContainerScreen<*>, target: ActiveTarget) {
		if (observedThisMenu) return
		observedThisMenu = true
		MinionHistoryRepository.observe(
			context = target.context,
			profile = target.profile,
			position = target.position,
			kind = MinionHistoryEntry.KIND_STORAGE,
			contents = MinionContents.readAll(screen),
		)
	}

	private fun captureDump(screen: AbstractContainerScreen<*>) {
		val slots = screen.menu.slots
		val dump = mutableListOf<String>()
		for (index in 0 until MinionContents.containerSlotCount(screen)) {
			val stack = slots.getOrNull(index)?.item ?: continue
			val name = MinionContents.itemName(stack) ?: continue
			val role = when {
				index in MinionSlots.STORAGE -> "storage?"
				index in MinionSlots.FURNITURE -> "furniture"
				else -> "-"
			}
			dump += "$index [$role] ${stack.count}x $name"
		}
		lastDump = dump
		lastDumpTitle = screen.title.string
	}

	fun lastDump(): Pair<String?, List<String>> = lastDumpTitle to lastDump

	private fun freshPending(pending: Pending?): Pending? {
		return pending?.takeIf { System.currentTimeMillis() - it.clickedAt <= PENDING_TARGET_TIMEOUT_MS }
	}

	private fun readFuel(screen: AbstractContainerScreen<*>): Pair<String?, Int?> {
		val slot = screen.menu.slots.getOrNull(MinionSlots.FUEL) ?: return null to null
		val name = MinionContents.itemName(slot.item) ?: return null to null
		if (FUEL_PLACEHOLDER_NAMES.any { it.equals(name, ignoreCase = true) }) return null to null
		return name to slot.item.count
	}

	@JvmStatic
	fun onSlotClicked(slot: Slot?, slotId: Int) {
		val target = activeMinion ?: return
		if (slot == null || slotId < 0 || !slot.hasItem()) return

		val itemName = slot.item.hoverName.string
		when {
			itemName.contains("Pickup Minion", ignoreCase = true) -> {
				MinionRepository.remove(target.context, target.profile, target.position)
				MinionHistoryRepository.remove(target.context, target.profile, target.position)
				activeMinion = null
				showStatus("Removed saved minion")
			}
			itemName.contains("Collect All", ignoreCase = true) ||
				itemName.contains("Hopper", ignoreCase = true) -> {
				MinionRepository.markCollected(target.context, target.profile, target.position)
				// The emptied menu must not be read as a decrease, or the next cycle would be
				// measured from the pre-collection amounts instead of from zero.
				MinionHistoryRepository.onCollected(target.context, target.profile, target.position)
				showStatus("Collection time saved")
			}
		}
	}

	@JvmStatic
	fun labelFor(position: Vec3): String? {
		return MinionRepository.labelFor(currentContext(), MinionProfile.current, position)
	}

	fun currentContext(): String {
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

	// Shown by Hypixel when the fuel slot is empty, rather than a blank slot.
	private val FUEL_PLACEHOLDER_NAMES = setOf("Minion Fuel", "Empty")
}
