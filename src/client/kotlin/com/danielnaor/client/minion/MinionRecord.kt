package com.danielnaor.client.minion

import net.minecraft.world.phys.Vec3

data class MinionRecord(
	val context: String,
	val x: Double,
	val y: Double,
	val z: Double,
	/** Null on records saved before profiles were tracked; adopted on first match. */
	var profile: String? = null,
	var minionType: String? = null,
	var minionLevel: Int? = null,
	var fuel: String? = null,
	var fuelCount: Int? = null,
	/** Item name to total count, as of the last time the menu was open. */
	var contents: MutableMap<String, Int> = mutableMapOf(),
	var storageUpgrade: String? = null,
	/** Extra slots the installed storage upgrade grants, null when unknown. */
	var storageSlots: Int? = null,
	var lastCollectedEpochMillis: Long = 0L,
) {
	fun position(): Vec3 = Vec3(x, y, z)
}
