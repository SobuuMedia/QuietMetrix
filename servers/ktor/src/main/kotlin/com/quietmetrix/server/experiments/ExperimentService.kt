package com.quietmetrix.server.experiments

import com.quietmetrix.server.persistence.ExperimentRecord
import com.quietmetrix.server.persistence.ExperimentRepository
import com.quietmetrix.server.persistence.ExperimentUpdate
import com.quietmetrix.server.persistence.ProjectRepository
import com.quietmetrix.server.ratelimit.QuotaEnforcer

/** The result of one [ExperimentService] operation: either a value, or an HTTP-shaped error. */
sealed class ExperimentOutcome<out T> {
    data class Ok<out T>(val value: T) : ExperimentOutcome<T>()
    data class Error(val status: Int, val code: String, val message: String) : ExperimentOutcome<Nothing>()
}

/** `list()` result: the project's experiments plus its current plan limit (null = unlimited). */
data class ExperimentListResult(val experiments: List<ExperimentRecord>, val limit: Int?)

/**
 * Orchestrates experiment CRUD: validation ([ExperimentValidation]), status-transition and
 * field-editability rules ([ExperimentStatus]), and the free-plan quota
 * ([QuotaEnforcer.experimentLimit]) — kept separate from [ExperimentRepository] (pure
 * persistence) and from the HTTP routes (thin request/response mapping only), so every rule
 * here is testable without standing up a server.
 */
class ExperimentService(
    private val repo: ExperimentRepository,
    private val projectRepo: ProjectRepository,
    private val quotaEnforcer: QuotaEnforcer?,
    private val isCloud: Boolean,
) {

    private fun planIdFor(projectId: Long): String? =
        projectRepo.findById(projectId)?.get("planId") as? String

    /** All of the project's experiments, newest first, with the plan's current limit. */
    fun list(projectId: Long): ExperimentListResult {
        val limit = quotaEnforcer?.experimentLimit(planIdFor(projectId), isCloud)
        return ExperimentListResult(repo.listForProject(projectId), limit)
    }

    /** Active tests and shipped rollouts, projected by toConfigDto for the SDK endpoint. */
    fun activeConfig(projectId: Long): List<ExperimentRecord> = repo.listActive(projectId)

    fun create(projectId: Long, request: CreateExperimentRequest): ExperimentOutcome<ExperimentRecord> {
        val definition = ExperimentDefinition(
            experimentKey = request.key,
            name = request.name,
            screen = request.screen?.trim()?.ifBlank { null },
            trafficPercent = request.trafficPercent ?: 100,
            variants = request.variants ?: DEFAULT_VARIANTS,
            countries = ExperimentValidation.normalizeCountries(request.countries),
        )

        val errors = ExperimentValidation.validate(definition)
        if (errors.isNotEmpty()) {
            return ExperimentOutcome.Error(400, "schema_violation", errors.joinToString("; "))
        }

        if (repo.findByKey(projectId, definition.experimentKey) != null) {
            return ExperimentOutcome.Error(409, "experiment_exists", "An experiment with this key already exists")
        }

        val limit = quotaEnforcer?.experimentLimit(planIdFor(projectId), isCloud)
        if (limit != null && repo.countNonEnded(projectId) >= limit) {
            return ExperimentOutcome.Error(
                403,
                "experiment_limit_reached",
                "You have reached the maximum number of experiments allowed for your plan.",
            )
        }

        return ExperimentOutcome.Ok(repo.create(projectId, definition))
    }

    fun update(projectId: Long, experimentKey: String, request: UpdateExperimentRequest): ExperimentOutcome<ExperimentRecord> {
        val existing = repo.findByKey(projectId, experimentKey)
            ?: return ExperimentOutcome.Error(404, "not_found", "Experiment not found")

        if (existing.status in setOf("shipped", "closed")) {
            return ExperimentOutcome.Error(409, "not_editable", "Resolved experiments are immutable")
        }

        if (request.status != null && !ExperimentStatus.canTransition(existing.status, request.status)) {
            return ExperimentOutcome.Error(409, "invalid_transition", "Cannot move from ${existing.status} to ${request.status}")
        }

        if ((request.status == "shipped" && request.selectedVariant !in existing.variants.map { it.name }) ||
            (request.selectedVariant != null && request.status != "shipped")) {
            return ExperimentOutcome.Error(400, "schema_violation", "Shipping requires a declared selected_variant; other transitions cannot select one")
        }

        val editable = ExperimentStatus.editableFields(existing.status)
        val requestedFieldEdits = buildList {
            if (request.name != null) add("name")
            if (request.screen != null) add("screen")
            if (request.trafficPercent != null) add("traffic_percent")
            if (request.variants != null) add("variants")
            if (request.countries != null) add("countries")
        }
        val disallowed = requestedFieldEdits.filterNot { it in editable }
        if (disallowed.isNotEmpty()) {
            return ExperimentOutcome.Error(409, "not_editable", "Cannot edit ${disallowed.joinToString(", ")} while ${existing.status}")
        }

        val mergedDefinition = ExperimentDefinition(
            experimentKey = existing.experimentKey,
            name = request.name ?: existing.name,
            screen = (request.screen ?: existing.screen)?.ifBlank { null },
            trafficPercent = request.trafficPercent ?: existing.trafficPercent,
            variants = request.variants ?: existing.variants,
            countries = if (request.countries != null) ExperimentValidation.normalizeCountries(request.countries) else existing.countries,
        )
        val errors = ExperimentValidation.validate(mergedDefinition)
        if (errors.isNotEmpty()) {
            return ExperimentOutcome.Error(400, "schema_violation", errors.joinToString("; "))
        }

        val updated = repo.update(
            projectId,
            experimentKey,
            ExperimentUpdate(
                name = request.name,
                screen = request.screen,
                trafficPercent = request.trafficPercent,
                variants = request.variants,
                countries = if (request.countries != null) ExperimentValidation.normalizeCountries(request.countries) ?: emptyList() else null,
                status = request.status,
                selectedVariant = request.selectedVariant,
                expectedStatus = existing.status,
            ),
        ) ?: return ExperimentOutcome.Error(409, "invalid_transition", "Experiment changed; refresh before retrying")

        return ExperimentOutcome.Ok(updated)
    }

    fun delete(projectId: Long, experimentKey: String): ExperimentOutcome<Unit> {
        val existing = repo.findByKey(projectId, experimentKey)
            ?: return ExperimentOutcome.Error(404, "not_found", "Experiment not found")
        if (existing.status != "draft") {
            return ExperimentOutcome.Error(409, "not_editable", "Only a draft experiment can be deleted; end it instead")
        }
        repo.deleteDraft(projectId, experimentKey)
        return ExperimentOutcome.Ok(Unit)
    }
}
