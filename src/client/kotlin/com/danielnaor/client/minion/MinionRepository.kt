package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.phys.Vec3
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object MinionRepository {
	private val gson = GsonBuilder().setPrettyPrinting().create()
	private val recordListType = object : TypeToken<MutableList<MinionRecord>>() {}.type
	private val directory = FabricLoader.getInstance().configDir.resolve(MinionLastCollectedClient.MOD_ID)
	private val file = directory.resolve("minions.json")
	private val records = mutableListOf<MinionRecord>()

	val size: Int
		get() = records.size

	fun load() {
		records.clear()
		if (!Files.exists(file)) return

		runCatching {
			Files.newBufferedReader(file).use { reader ->
				val loaded: MutableList<MinionRecord>? = gson.fromJson(reader, recordListType)
				if (loaded != null) records.addAll(loaded)
			}
		}.onFailure {
			MinionLastCollectedClient.LOGGER.error("Could not load {}", file, it)
		}
	}

	fun ensure(
		context: String,
		position: Vec3,
		minionType: String? = null,
		minionLevel: Int? = null,
	): MinionRecord {
		val existing = find(context, position)
		if (existing != null) {
			var changed = false
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
	fun updateFuel(context: String, position: Vec3, fuel: String?, fuelCount: Int?): Boolean {
		val record = find(context, position) ?: return false
		if (record.fuel == fuel && record.fuelCount == fuelCount) return false
		record.fuel = fuel
		record.fuelCount = fuelCount
		save()
		return true
	}

	fun markCollected(context: String, position: Vec3) {
		ensure(context, position).lastCollectedEpochMillis = System.currentTimeMillis()
		save()
	}

	fun remove(context: String, position: Vec3) {
		records.removeIf {
			it.context == context && it.position().distanceToSqr(position) <= POSITION_TOLERANCE_SQUARED
		}
		save()
	}

	fun labelFor(context: String, position: Vec3): String? {
		val record = find(context, position) ?: return null
		return "Last Collected: ${MinionTimeFormatter.format(record.lastCollectedEpochMillis)}"
	}

	private fun find(context: String, position: Vec3): MinionRecord? {
		return records
			.asSequence()
			.filter { it.context == context }
			.map { it to it.position().distanceToSqr(position) }
			.filter { (_, distance) -> distance <= POSITION_TOLERANCE_SQUARED }
			.minByOrNull { (_, distance) -> distance }
			?.first
	}

	private fun save() {
		runCatching {
			Files.createDirectories(directory)
			val temporaryFile = directory.resolve("minions.json.tmp")
			Files.newBufferedWriter(temporaryFile).use { writer ->
				gson.toJson(records, recordListType, writer)
			}

			try {
				Files.move(
					temporaryFile,
					file,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING,
				)
			} catch (_: AtomicMoveNotSupportedException) {
				Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING)
			}
		}.onFailure {
			MinionLastCollectedClient.LOGGER.error("Could not save {}", file, it)
		}
	}

	private const val POSITION_TOLERANCE_SQUARED = 0.04
}
