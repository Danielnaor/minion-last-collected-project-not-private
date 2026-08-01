package com.danielnaor.client.minion

import com.google.gson.reflect.TypeToken
import net.minecraft.world.phys.Vec3

/**
 * Stores per-minion production history in its own file, so `minions.json` stays a compact
 * snapshot of the current state and remains easy to read and share.
 */
object MinionHistoryRepository {
	private val store = MinionJsonStore("minion-history.json")
	private val entryListType = object : TypeToken<MutableList<MinionHistoryEntry>>() {}.type
	private val entries = mutableListOf<MinionHistoryEntry>()

	val size: Int
		get() = entries.size

	fun load() {
		entries.clear()
		store.load<MutableList<MinionHistoryEntry>>(entryListType)?.let { entries.addAll(it) }
	}

	fun all(): List<MinionHistoryEntry> = entries.toList()

	/**
	 * Folds one observation of a menu's contents into the history.
	 *
	 * Only increases count towards [MinionHistoryEntry.totalProduced]. A decrease means the
	 * items were taken out, and that quantity was already counted when it first appeared, so
	 * adding nothing is what keeps a collection from subtracting from the totals.
	 */
	fun observe(
		context: String,
		profile: String?,
		position: Vec3,
		kind: String,
		contents: Map<String, Int>,
		minionType: String? = null,
		minionLevel: Int? = null,
	) {
		val now = System.currentTimeMillis()
		val existing = find(context, profile, position, kind)
		val entry = existing ?: MinionHistoryEntry(
			context = context,
			x = position.x,
			y = position.y,
			z = position.z,
			profile = profile,
			kind = kind,
			firstObservedEpochMillis = now,
		).also { entries.add(it) }

		if (profile != null && entry.profile == null) entry.profile = profile
		if (minionType != null) entry.minionType = minionType
		if (minionLevel != null) entry.minionLevel = minionLevel

		// Whatever is already inside a minion the first time it is seen was produced before
		// tracking began, so it seeds the baseline instead of counting towards production.
		if (existing != null) {
			for ((item, amount) in contents) {
				val previous = entry.lastContents[item] ?: 0
				if (amount > previous) {
					entry.totalProduced[item] = (entry.totalProduced[item] ?: 0L) + (amount - previous)
				}
			}
		}

		entry.lastContents = contents.toMutableMap()
		entry.lastObservedEpochMillis = now
		entry.observationCount++
		save()
	}

	/**
	 * Clears the remembered snapshot after the player collects a minion.
	 *
	 * Without this the emptied menu would be read as a decrease, and everything the minion
	 * produced afterwards would be measured from the pre-collection amounts instead of from
	 * zero, silently losing a cycle's worth of production.
	 */
	fun onCollected(context: String, profile: String?, position: Vec3) {
		val entry = find(context, profile, position, MinionHistoryEntry.KIND_MINION) ?: return
		if (entry.lastContents.isEmpty()) return
		entry.lastContents = mutableMapOf()
		save()
	}

	fun remove(context: String, profile: String?, position: Vec3) {
		val removed = entries.removeIf {
			it.context == context &&
				(it.profile == profile || it.profile == null) &&
				MinionPositions.matches(it.x, it.y, it.z, position)
		}
		if (removed) save()
	}

	private fun find(
		context: String,
		profile: String?,
		position: Vec3,
		kind: String,
	): MinionHistoryEntry? {
		val nearby = entries
			.asSequence()
			.filter { it.context == context && it.kind == kind }
			.filter { MinionPositions.matches(it.x, it.y, it.z, position) }
			.sortedBy { MinionPositions.distanceSquared(it.x, it.y, it.z, position) }

		// Matches MinionRepository: entries written before profiles were tracked have no
		// profile and are claimed by whichever profile looks at them first.
		return nearby.firstOrNull { it.profile == profile }
			?: nearby.firstOrNull { it.profile == null }
	}

	private fun save() {
		store.save(entries, entryListType)
	}
}
