package com.danielnaor.client.minion

import com.danielnaor.client.MinionLastCollectedClient
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.Type
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads and writes one JSON file in the mod's config directory.
 *
 * Writes go to a temporary file that is then moved over the real one, so a crash midway
 * through a save cannot leave a half-written file behind and lose every tracked minion.
 */
class MinionJsonStore(private val fileName: String) {
	private val directory = FabricLoader.getInstance().configDir.resolve(MinionLastCollectedClient.MOD_ID)
	private val file = directory.resolve(fileName)

	fun <T> load(type: Type): T? {
		if (!Files.exists(file)) return null

		return runCatching {
			Files.newBufferedReader(file).use { reader ->
				gson.fromJson<T>(reader, type)
			}
		}.onFailure {
			MinionLastCollectedClient.LOGGER.error("Could not load {}", file, it)
		}.getOrNull()
	}

	fun save(value: Any, type: Type) {
		runCatching {
			Files.createDirectories(directory)
			val temporaryFile = directory.resolve("$fileName.tmp")
			Files.newBufferedWriter(temporaryFile).use { writer ->
				gson.toJson(value, type, writer)
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

	private companion object {
		private val gson = GsonBuilder().setPrettyPrinting().create()
	}
}
