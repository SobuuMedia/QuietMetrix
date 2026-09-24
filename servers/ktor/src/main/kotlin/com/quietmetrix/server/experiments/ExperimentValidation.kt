package com.quietmetrix.server.experiments

private val KEY_REGEX = Regex("^[a-z0-9_]{1,32}$")
private val VARIANT_NAME_REGEX = Regex("^[a-z0-9_]{1,32}$")
private val COUNTRY_REGEX = Regex("^[A-Z]{2}$")
private const val MAX_COUNTRIES = 50
private const val MAX_NAME_LENGTH = 255
private const val MAX_SCREEN_LENGTH = 128

/**
 * Validation rules for an experiment definition, mirrored exactly by
 * php-hosting/src/experimentValidation.php. Every rule here also gates the dashboard's create/
 * update forms client-side (see dashboard ExperimentsLogic.kt) for instant feedback, but the
 * server is authoritative.
 */
object ExperimentValidation {

    /** All violations of [definition], or an empty list when it is valid. */
    fun validate(definition: ExperimentDefinition): List<String> {
        val errors = mutableListOf<String>()

        if (!KEY_REGEX.matches(definition.experimentKey)) {
            errors.add("key must match ^[a-z0-9_]{1,32}$")
        }
        if (definition.name.isBlank() || definition.name.length > MAX_NAME_LENGTH) {
            errors.add("name must be 1-$MAX_NAME_LENGTH characters")
        }
        if (definition.screen != null && definition.screen.length > MAX_SCREEN_LENGTH) {
            errors.add("screen must be at most $MAX_SCREEN_LENGTH characters")
        }
        if (definition.trafficPercent < 1 || definition.trafficPercent > 100) {
            errors.add("traffic_percent must be between 1 and 100")
        }

        if (definition.variants.size != 2) {
            errors.add("exactly 2 variants are required")
        } else {
            val (a, b) = definition.variants
            for (v in definition.variants) {
                if (!VARIANT_NAME_REGEX.matches(v.name)) {
                    errors.add("variant name '${v.name}' must match ^[a-z0-9_]{1,32}$")
                }
                if (v.name == NONE_VARIANT) {
                    errors.add("variant name cannot be '$NONE_VARIANT'")
                }
                if (v.weight < 1 || v.weight > 99) {
                    errors.add("variant '${v.name}' weight must be between 1 and 99")
                }
            }
            if (a.name == b.name) {
                errors.add("variant names must be distinct")
            }
            if (a.weight + b.weight != 100) {
                errors.add("variant weights must sum to 100")
            }
        }

        if (definition.countries != null) {
            if (definition.countries.size > MAX_COUNTRIES) {
                errors.add("at most $MAX_COUNTRIES countries are allowed")
            }
            for (c in definition.countries) {
                if (!COUNTRY_REGEX.matches(c)) {
                    errors.add("country code '$c' must be a 2-letter uppercase ISO code")
                }
            }
        }

        return errors
    }

    /**
     * Uppercases, trims, dedupes and sorts a raw country-code list; blank entries are dropped.
     * Returns null (meaning "all countries") for a null or empty input — never an empty list.
     */
    fun normalizeCountries(raw: List<String>?): List<String>? {
        if (raw == null) return null
        val normalized = raw.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
        return normalized.ifEmpty { null }
    }
}

/** The variant name [com.quietmetrix.analytics] returns when a device is not enrolled. */
const val NONE_VARIANT = "none"
