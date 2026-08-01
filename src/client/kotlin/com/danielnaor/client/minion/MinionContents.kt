package com.danielnaor.client.minion

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

/** What a minion menu looked like at one moment. */
data class MinionSnapshot(
	val contents: Map<String, Int>,
	val storageUpgrade: String?,
	val storageSlots: Int?,
)

/**
 * Reads the contents of an open minion menu.
 *
 * Only [MinionSlots.STORAGE] is read, so menu furniture and upgrade items are never counted
 * as something the minion produced.
 */
object MinionContents {
	fun read(screen: AbstractContainerScreen<*>): MinionSnapshot {
		val slots = screen.menu.slots
		val contents = mutableMapOf<String, Int>()

		for (index in MinionSlots.STORAGE) {
			val stack = slots.getOrNull(index)?.item ?: continue
			val name = itemName(stack) ?: continue
			// The same item can occupy several slots once one stack fills up.
			contents[name] = (contents[name] ?: 0) + stack.count
		}

		val upgrade = readStorageUpgrade(screen)
		return MinionSnapshot(
			contents = contents,
			storageUpgrade = upgrade,
			storageSlots = upgrade?.let { STORAGE_UPGRADE_SLOTS[it.lowercase()] },
		)
	}

	/**
	 * Returns the name of the storage upgrade installed in one of the upgrade slots, or null
	 * if neither holds one. Upgrades other than storage (Flycatcher, Diamond Spreading, and
	 * so on) are ignored.
	 */
	fun readStorageUpgrade(screen: AbstractContainerScreen<*>): String? {
		val slots = screen.menu.slots
		for (index in MinionSlots.UPGRADES) {
			val stack = slots.getOrNull(index)?.item ?: continue
			val name = itemName(stack) ?: continue
			if (name.lowercase() in STORAGE_UPGRADE_SLOTS) return name
		}
		return null
	}

	/**
	 * Reads every item in the upper container, for menus whose layout is not the minion one.
	 *
	 * The separate Minion Storage menu is an ordinary chest of items rather than the fixed
	 * minion layout, so there are no meaningful slot indices to key off; anything that is not
	 * filler or a navigation button counts as stored.
	 */
	fun readAll(screen: AbstractContainerScreen<*>): Map<String, Int> {
		val slots = screen.menu.slots
		val contents = mutableMapOf<String, Int>()

		for (index in 0 until containerSlotCount(screen)) {
			val stack = slots.getOrNull(index)?.item ?: continue
			val name = itemName(stack) ?: continue
			if (IGNORED_NAMES.any { it.equals(name, ignoreCase = true) }) continue
			contents[name] = (contents[name] ?: 0) + stack.count
		}
		return contents
	}

	/**
	 * Number of slots belonging to the opened container rather than to the player.
	 *
	 * The player's own inventory is always the last 36 slots of a container menu, so
	 * subtracting it works for any menu size instead of assuming six rows.
	 */
	fun containerSlotCount(screen: AbstractContainerScreen<*>): Int {
		return (screen.menu.slots.size - PLAYER_INVENTORY_SLOTS).coerceAtLeast(0)
	}

	/**
	 * True once the server has sent the menu contents. The menu is empty for the first few
	 * ticks after it opens, and treating that as a minion with nothing in it would record a
	 * false collection and corrupt the production totals.
	 */
	fun isLoaded(screen: AbstractContainerScreen<*>): Boolean {
		val slots = screen.menu.slots
		for (index in 0 until containerSlotCount(screen)) {
			if (slots[index].hasItem()) return true
		}
		return false
	}

	/** True when this is a real minion menu rather than the separate Minion Storage menu. */
	fun isMinionMenu(screen: AbstractContainerScreen<*>): Boolean {
		val stack = screen.menu.slots.getOrNull(MinionSlots.COLLECT_ALL)?.item ?: return false
		return itemName(stack).equals(COLLECT_ALL_NAME, ignoreCase = true)
	}

	/** The display name of a real item, or null for an empty slot or menu filler. */
	fun itemName(stack: ItemStack): String? {
		if (stack.isEmpty) return null
		// Hypixel pads its menus with glass panes whose name is only formatting codes, which
		// come back as an empty string once the formatting is stripped.
		val name = stack.hoverName.string.trim()
		if (name.isEmpty()) return null
		return name
	}

	private const val COLLECT_ALL_NAME = "Collect All"
	private const val PLAYER_INVENTORY_SLOTS = 36

	/** Menu buttons that would otherwise be counted as stored items. */
	private val IGNORED_NAMES = setOf(
		"Go Back",
		"Close",
		"Next Page",
		"Previous Page",
		"Collect All",
		"Pickup Minion",
	)

	/**
	 * Extra slots each storage upgrade grants, keyed lowercase.
	 *
	 * Small, Medium and Large are from the wiki. X-Large and XX-Large are listed so they are
	 * still recognised as storage upgrades, but their slot counts are not confirmed, so they
	 * map to null rather than to a guessed number.
	 */
	private val STORAGE_UPGRADE_SLOTS: Map<String, Int?> = mapOf(
		"small storage" to 3,
		"medium storage" to 9,
		"large storage" to 15,
		"x-large storage" to null,
		"xx-large storage" to null,
	)
}
