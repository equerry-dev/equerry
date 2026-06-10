package dev.equerry.app.assistant

import kotlinx.serialization.Serializable

/**
 * One assist-probe observation, captured when Equerry is invoked as the assistant.
 *
 * Carries only facts and screenshot *dimensions* — deliberately NO bitmap / byte[]
 * field. The screenshot_retention decision (privacy is the product) is enforced here
 * at the type level: there is no place to put pixels, so they cannot be persisted.
 */
@Serializable
data class ProbeRecord(
    val packageName: String,
    val timestamp: Long,
    val structureProvided: Boolean,
    val nodeCount: Int,
    val screenshotArrived: Boolean,
    val screenshotBlocked: Boolean,
    val screenshotWidth: Int? = null,
    val screenshotHeight: Int? = null,
)
