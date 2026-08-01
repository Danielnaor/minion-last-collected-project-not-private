package com.danielnaor.client.minion

/**
 * Everything observed about one minion (or one Minion Storage menu) over time.
 *
 * [totalProduced] only ever grows. It is built by comparing successive observations and
 * adding the increases, which is what keeps a collection from reading as negative
 * production — see [MinionHistoryRepository.observe].
 *
 * Two honest limits on the numbers here:
 *  - production is only visible while the menu is open, so a minion that filled up and was
 *    collected between two openings is undercounted, and
 *  - a minion stops producing once its storage is full, so a long gap between openings
 *    understates the real rate rather than overstating it.
 */
data class MinionHistoryEntry(
	val context: String,
	val x: Double,
	val y: Double,
	val z: Double,
	var profile: String? = null,
	var kind: String = KIND_MINION,
	var minionType: String? = null,
	var minionLevel: Int? = null,
	var firstObservedEpochMillis: Long = 0L,
	var lastObservedEpochMillis: Long = 0L,
	var observationCount: Int = 0,
	/** The previous snapshot, kept only so the next one can be diffed against it. */
	var lastContents: MutableMap<String, Int> = mutableMapOf(),
	var totalProduced: MutableMap<String, Long> = mutableMapOf(),
) {
	/** Hours spanned by the observations, used as the denominator for production rates. */
	fun observedHours(): Double {
		val span = lastObservedEpochMillis - firstObservedEpochMillis
		if (span <= 0L) return 0.0
		return span / 3_600_000.0
	}

	/** Items produced per hour, or an empty map until there are two observations to span. */
	fun ratePerHour(): Map<String, Double> {
		val hours = observedHours()
		if (hours <= 0.0) return emptyMap()
		return totalProduced.mapValues { (_, total) -> total / hours }
	}

	companion object {
		const val KIND_MINION = "minion"
		const val KIND_STORAGE = "storage"
	}
}
