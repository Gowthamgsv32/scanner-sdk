package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.AI98SplitResult

/**
 * Splits auth trailer AIs (98 encrypted payload, 97 company) from GS1 scan data.
 * Logic aligned with iOS `GS1Utils.splitByAI98`.
 */
object GS1Utils {

    fun splitByAI98(input: String): AI98SplitResult? {
        val value = input.replace('\u001D', GS1ParseSupport.FNC1)

        // CASE 1: Bracketed GS1 — (01)...(98)ENCRYPTED(97)COMPANY
        if (value.contains("(98)")) {
            val idx98 = value.indexOf("(98)")
            val barcodeData = value.substring(0, idx98)
            val after98 = value.substring(idx98 + 4)
            val idx97 = after98.indexOf("(97)")
            val encryptedText =
                if (idx97 >= 0) after98.substring(0, idx97) else after98
            val companyId =
                if (idx97 >= 0) after98.substring(idx97 + 4) else ""

            return AI98SplitResult(
                strip(barcodeData),
                strip(encryptedText),
                strip(companyId),
            )
        }

        // CASE 2: FNC1-separated GS1
        if (value.contains(GS1ParseSupport.FNC1)) {
            val tokens = value.split(GS1ParseSupport.FNC1).filter { it.isNotEmpty() }
            val barcodeParts = mutableListOf<String>()
            var encryptedText = ""
            var companyId = ""

            for (token in tokens) {
                when {
                    token.startsWith("97") -> companyId = token.drop(2)
                    token.startsWith("98") -> encryptedText = token.drop(2)
                    else -> {
                        val match = GS1ParseSupport.AI98_ENCRYPTED_SUFFIX.find(token)
                        if (match != null) {
                            val before98 = token.substring(0, match.range.first)
                            if (before98.isNotEmpty()) barcodeParts.add(before98)
                            encryptedText = GS1ParseSupport.encryptedPayloadFromMatch(token, match)
                        } else {
                            barcodeParts.add(token)
                        }
                    }
                }
            }

            val barcodeData = barcodeParts.joinToString(GS1ParseSupport.FNC1.toString())
            return AI98SplitResult(barcodeData, encryptedText, companyId)
        }

        // CASE 3: Flattened raw GS1 — …98ENCRYPTED97COMPANY
        val r97 = value.lastIndexOf("97")
        if (r97 < 0) return null

        val companyId = value.substring(r97 + 2)
        val before97 = value.substring(0, r97)
        val match98 = GS1ParseSupport.AI98_ENCRYPTED_SUFFIX.find(before97) ?: return null

        val encryptedText = GS1ParseSupport.encryptedPayloadFromMatch(before97, match98)
        val barcodeData = before97.substring(0, match98.range.first)

        if (barcodeData.isEmpty() || encryptedText.isEmpty()) return null

        return AI98SplitResult(barcodeData, encryptedText, companyId)
    }

    private fun strip(s: String): String =
        s.replace("(", "").replace(")", "")
}
