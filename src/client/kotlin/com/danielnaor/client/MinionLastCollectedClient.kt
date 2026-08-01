package com.danielnaor.client

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
		MinionProfile.register()
		MinionTracker.register()
		LOGGER.info("Minion Last Collected initialized with {} saved minion(s)", MinionRepository.size)
	}
}
