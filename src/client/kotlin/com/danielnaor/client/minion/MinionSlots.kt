package com.danielnaor.client.minion

/**
 * Slot indices of the Hypixel minion menu, which is a six row chest (54 slots).
 *
 * ```
 * row 1:  0  1  2  3  4  5  6  7  8
 * row 2:  9 10 11 12 13 14 15 16 17
 * row 3: 18 19 20 21 22 23 24 25 26
 * row 4: 27 28 29 30 31 32 33 34 35
 * row 5: 36 37 38 39 40 41 42 43 44
 * row 6: 45 46 47 48 49 50 51 52 53
 * ```
 *
 * Everything in here is in one place on purpose: if Hypixel moves something, or if the
 * provisional values below turn out to be wrong, this file is the only thing to correct.
 */
object MinionSlots {
	const val CONTAINER_SIZE = 54

	/** Confirmed: SkyHanni's `MinionFeatures.MINION_FUEL_SLOT`. */
	const val FUEL = 19

	/** Confirmed: SkyHanni's `MinionFeatures.MINION_PICKUP_SLOT`. */
	const val PICKUP = 53

	/**
	 * Confirmed: SkyHanni reads this slot to tell a real minion menu apart from the
	 * separate Minion Storage menu, which shares the "... Minion ..." title.
	 */
	const val COLLECT_ALL = 48

	/**
	 * PROVISIONAL — not verified against the live server.
	 *
	 * The wiki describes a minion as having one skin slot, one fuel slot, one automated
	 * shipping slot, two upgrade slots and up to fifteen storage slots. Fifteen slots as a
	 * 5x3 block, with the known control column on the left at 19 and 28, gives the block
	 * below. Run `/minionsdump` in game and compare before trusting any recorded contents.
	 */
	val STORAGE: Set<Int> = buildSet {
		for (row in 2..4) {
			for (column in 3..7) {
				add(row * 9 + column)
			}
		}
	}

	/**
	 * Confirmed to hold something: SkyHanni reads the "Held Coins" lore line from slot 28.
	 * That makes it the automated shipping slot, sitting directly under the fuel slot.
	 */
	const val AUTOMATED_SHIPPING = 28

	/**
	 * PROVISIONAL — not verified. The two upgrade slots, where a storage upgrade item sits.
	 * Deliberately excluded from [STORAGE] so an upgrade item is never counted as contents.
	 */
	val UPGRADES: Set<Int> = setOf(20, 29)

	/** Slots that hold menu furniture rather than anything the minion produced. */
	val FURNITURE: Set<Int> = setOf(FUEL, AUTOMATED_SHIPPING, PICKUP, COLLECT_ALL) + UPGRADES
}
