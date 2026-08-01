package com.danielnaor.client.minion

import net.minecraft.world.phys.Vec3

/**
 * Shared rule for deciding whether two coordinates refer to the same minion.
 *
 * A minion's armor stand does not sit at exactly the same position every time it is read,
 * so records are matched within a small tolerance rather than by equality. The current
 * state and history files must agree on this, or the same minion would key differently
 * in each.
 */
object MinionPositions {
	const val TOLERANCE_SQUARED = 0.04

	fun matches(x: Double, y: Double, z: Double, position: Vec3): Boolean {
		return distanceSquared(x, y, z, position) <= TOLERANCE_SQUARED
	}

	fun distanceSquared(x: Double, y: Double, z: Double, position: Vec3): Double {
		val dx = x - position.x
		val dy = y - position.y
		val dz = z - position.z
		return dx * dx + dy * dy + dz * dz
	}
}
