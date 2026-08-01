package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import com.google.gson.reflect.TypeToken
import net.minecraft.world.phys.Vec3

object MinionRepository {
	private val store = MinionJsonStore("minions.json")
	private val recordListType = object : TypeToken<MutableList<MinionRecord>>() {}.type
	private val records = mutableListOf<MinionRecord>()

	val size: Int
		get() = records.size

	fun all(): List<MinionRecord> = records.toList()

	fun load() {
		records.clear()
		store.load<MutableList<MinionRecord>>(recordListType)?.let { records.addAll(it) }
	}

	fun ensure(
		context: String,
		profile: String?,
		position: Vec3,
		minionType: String? = null,
		minionLevel: Int? = null,
	): MinionRecord {
		val existing = find(context, profile, position)
		if (existing != null) {
			var changed = false
			// Claims a record saved before profiles were tracked for the current profile.
			if (profile != null && existing.profile == null) {
				existing.profile = profile
				changed = true
				MinionLastCollectedClient.LOGGER.info(
					"Adopted a minion saved before profile tracking into profile {}",
					profile,
				)
			}
			if (minionType != null && existing.minionType != minionType) {
				existing.minionType = minionType
				changed = true
			}
			if (minionLevel != null && existing.minionLevel != minionLevel) {
				existing.minionLevel = minionLevel
				changed = true
			}
			if (changed) save()
			return existing
		}

		return MinionRecord(
			context = context,
			x = position.x,
			y = position.y,
			z = position.z,
			profile = profile,
			minionType = minionType,
			minionLevel = minionLevel,
		).also {
			records.add(it)
			save()
		}
	}

	/**
	 * Records the current contents of the fuel slot. A null [fuel] means the tank is
	 * empty, which is a real state worth saving, so this always overwrites.
	 */
	fun updateFuel(
		context: String,
		profile: String?,
		position: Vec3,
		fuel: String?,
		fuelCount: Int?,
	): Boolean {
		val record = find(context, profile, position) ?: return false
		if (record.fuel == fuel && record.fuelCount == fuelCount) return false
		record.fuel = fuel
		record.fuelCount = fuelCount
		save()
		return true
	}

	/**
	 * Records what is currently sitting in the minion's storage slots. Always overwrites,
	 * because an emptied minion is a real state and not a missing reading.
	 */
	fun updateContents(
		context: String,
		profile: String?,
		position: Vec3,
		snapshot: MinionSnapshot,
	): Boolean {
		val record = find(context, profile, position) ?: return false
		if (record.contents == snapshot.contents &&
			record.storageUpgrade == snapshot.storageUpgrade &&
			record.storageSlots == snapshot.storageSlots
		) {
			return false
		}
		record.contents = snapshot.contents.toMutableMap()
		record.storageUpgrade = snapshot.storageUpgrade
		record.storageSlots = snapshot.storageSlots
		save()
		return true
	}

	fun markCollected(context: String, profile: String?, position: Vec3) {
		ensure(context, profile, position).lastCollectedEpochMillis = System.currentTimeMillis()
		save()
	}

	fun remove(context: String, profile: String?, position: Vec3) {
		records.removeIf {
			it.context == context &&
				(it.profile == profile || it.profile == null) &&
				MinionPositions.matches(it.x, it.y, it.z, position)
		}
		save()
	}

	fun labelFor(context: String, profile: String?, position: Vec3): String? {
		val record = find(context, profile, position) ?: return null
		return "Last Collected: ${MinionTimeFormatter.format(record.lastCollectedEpochMillis)}"
	}

	/**
	 * Looks up the minion at [position] belonging to [profile]. This runs on the render
	 * path via [labelFor], so it stays a pure query: adopting a legacy record is left to
	 * [ensure], which already writes to disk.
	 */
	private fun find(context: String, profile: String?, position: Vec3): MinionRecord? {
		val nearby = records
			.asSequence()
			.filter { it.context == context }
			.filter { MinionPositions.matches(it.x, it.y, it.z, position) }
			.sortedBy { MinionPositions.distanceSquared(it.x, it.y, it.z, position) }

		// A record saved before profiles were tracked belongs to whichever profile looks
		// at it first; one saved against another profile is never reused.
		return nearby.firstOrNull { it.profile == profile }
			?: nearby.firstOrNull { it.profile == null }
	}

	private fun save() {
		store.save(records, recordListType)
	}
}
