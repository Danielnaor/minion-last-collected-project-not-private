package com.danielnaor.client

import com.danielnaor.client.minion.MinionCommands
import com.danielnaor.client.minion.MinionHistoryRepository
import com.danielnaor.client.minion.MinionProfile
import com.danielnaor.client.minion.MinionRepository
import com.danielnaor.client.minion.MinionTracker
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object MinionLastCollectedClient : ClientModInitializer {
	const val MOD_ID = "minion-last-collected"
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		MinionRepository.load()
		MinionHistoryRepository.load()
		MinionProfile.register()
		MinionTracker.register()
		MinionCommands.register()
		LOGGER.info(
			"Minion Last Collected initialized with {} saved minion(s) and {} history entries",
			MinionRepository.size,
			MinionHistoryRepository.size,
		)
	}
}
