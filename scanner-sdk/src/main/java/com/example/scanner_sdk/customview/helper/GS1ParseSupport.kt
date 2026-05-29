package com.example.scanner_sdk.customview.helper

/**
 * Shared GS1 parsing helpers — kept in sync with iOS `ParseGs1.swift` / `GS1AIMetadata.swift`.
 */
internal object GS1ParseSupport {

    const val FNC1 = '\u001D'

    /** End-anchored AI 98 + base64-style encrypted payload (avoids false match inside dates). */
    val AI98_ENCRYPTED_SUFFIX = Regex("""98([A-Za-z0-9+/=]+)$""")

    private val numericFixedLengthAIs = setOf(
        "00", "01", "02",
        "410", "411", "412", "413", "414", "415", "416", "417",
        "422", "424", "426", "402",
        "8001", "8005", "8006", "8026", "8017", "8018",
        "7001", "7003", "7006", "8111",
    )

    private val dateAIs = setOf(
        "11", "12", "13", "15", "16", "17", "7006",
    )

    private val falsePositiveAfterVariableLot = setOf("00", "01", "02")

    private val dateAIsAfterBatch = setOf("11", "12", "13", "15", "16", "17")

    fun isValidGS1Date(value: String): Boolean {
        if (value.length != 6 || !value.all { it.isDigit() }) return false
        val month = value.substring(2, 4).toIntOrNull() ?: return false
        val day = value.substring(4, 6).toIntOrNull() ?: return false
        return month in 1..12 && day in 1..31
    }

    fun isNumericGS1FixedValue(ai: String, value: String, fixedLen: Int): Boolean {
        if (value.length != fixedLen) return false
        if (ai in numericFixedLengthAIs) return value.all { it.isDigit() }
        if (ai in dateAIs) return isValidGS1Date(value)
        return true
    }

    fun isAuthTrailerAI(ai: String): Boolean = ai == "97" || ai == "98"

    /**
     * When ending AI 10 (batch/lot), only accept a real next field — e.g. valid date AI
     * (13)250404 — not false positives like AI 21 inside `…002132504…` after `BATCH00`.
     */
    fun isPlausibleNextAI(
        input: String,
        pos: Int,
        nextAi: String?,
        currentVariableAi: String?,
    ): Boolean {
        if (nextAi == null) return false
        if (currentVariableAi == "10") {
            if (nextAi in falsePositiveAfterVariableLot) return false
            if (nextAi in dateAIsAfterBatch) {
                val valueStart = pos + nextAi.length
                if (valueStart + 6 > input.length) return false
                return isValidGS1Date(input.substring(valueStart, valueStart + 6))
            }
            return false
        }
        return true
    }

    /** Payload after AI 98 from a regex match on [AI98_ENCRYPTED_SUFFIX]. */
    fun encryptedPayloadFromMatch(token: String, match: MatchResult): String =
        match.value.drop(2)
}
