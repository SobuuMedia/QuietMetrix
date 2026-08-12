package com.quietmetrix.server.funnels

import com.quietmetrix.server.persistence.FunnelRepository

data class RegisterFunnelsResult(
    val registered: List<String>,
    val skippedLocked: List<String>,
    val rejected: Map<String, String>,
)

/**
 * The SDK-registration algorithm, called once per app launch when the app's declared funnels
 * change. Per funnel: skip silently if a dashboard edit has locked it (an analyst's edit must
 * never be silently overwritten); otherwise upsert by key with `source = "sdk"`. One invalid
 * or over-cap definition in a batch is rejected on its own — it does not fail the rest.
 */
class FunnelRegistrationService(private val funnelRepo: FunnelRepository) {

    fun register(projectId: Long, definitions: List<FunnelDefinition>): RegisterFunnelsResult {
        val registered = mutableListOf<String>()
        val skippedLocked = mutableListOf<String>()
        val rejected = mutableMapOf<String, String>()

        for (definition in definitions) {
            val existing = funnelRepo.findByKey(projectId, definition.funnelKey)
            if (existing != null && existing.locked) {
                skippedLocked += definition.funnelKey
                continue
            }

            val sdkDefinition = definition.copy(source = "sdk", locked = false)
            val existingCountExcludingThis = funnelRepo.countActive(projectId) - (if (existing != null) 1 else 0)
            val result = FunnelValidation.validate(sdkDefinition, existingCountExcludingThis)
            if (result is FunnelValidationResult.Invalid) {
                rejected[definition.funnelKey] = result.errors.joinToString("; ")
                continue
            }

            if (existing == null) {
                // A deleted SDK funnel retains its unique row. Restore it instead of trying
                // to insert the same key again (which used to fail permanently after delete).
                if (!funnelRepo.restore(projectId, sdkDefinition)) {
                    funnelRepo.create(projectId, sdkDefinition)
                }
            } else {
                funnelRepo.replaceSdkDefinition(projectId, sdkDefinition)
            }
            registered += definition.funnelKey
        }

        return RegisterFunnelsResult(registered, skippedLocked, rejected)
    }
}
