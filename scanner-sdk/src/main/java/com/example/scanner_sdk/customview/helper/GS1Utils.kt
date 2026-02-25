package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.AI98SplitResult

object GS1Utils {

    private const val FNC1 = '\u001D'

    fun splitByAI98(input: String): AI98SplitResult? {

        val value = input

        // ─────────────────────────────
        // CASE 1: BRACKETED GS1
        // ─────────────────────────────
        if (value.contains("(98)")) {

            val idx98 = value.indexOf("(98)")
            val barcodeData = value.take(idx98)

            val after98 = value.substring(idx98 + 4)

            val idx97 = after98.indexOf("(97)")

            val encryptedText =
                if (idx97 >= 0) after98.take(idx97)
                else after98

            val companyId =
                if (idx97 >= 0) after98.substring(idx97 + 4)
                else ""

            return AI98SplitResult(
                strip(barcodeData),
                strip(encryptedText),
                strip(companyId)
            )
        }

        // ─────────────────────────────
        // CASE 2: FNC1 GS1  (🔥 FIXED)
        // ─────────────────────────────
        if (value.contains(FNC1)) {

            val tokens = value.split(FNC1).filter { it.isNotEmpty() }

            var barcodeData = StringBuilder()
            var encryptedText = ""
            var companyId = ""

            tokens.forEach { token ->

                when {

                    token.startsWith("98") -> {
                        encryptedText = token.drop(2)
                    }

                    token.startsWith("97") -> {
                        companyId = token.drop(2)
                    }

                    else -> {
                        barcodeData.append(token)
                    }
                }
            }

            if (encryptedText.isEmpty()) return null

            return AI98SplitResult(
                barcodeData.toString(),
                encryptedText,
                companyId
            )
        }

        // ─────────────────────────────
        // CASE 3: FLATTENED RAW GS1
        // ─────────────────────────────
        val r97 = value.lastIndexOf("97")
        if (r97 < 0) return null

        val companyId = value.substring(r97 + 2)
        val before97 = value.substring(0, r97)

        val r98 = before97.lastIndexOf("98")
        if (r98 < 0) return null

        val encryptedText = before97.substring(r98 + 2)
        val barcodeData = before97.substring(0, r98)

        if (encryptedText.isEmpty()) return null

        return AI98SplitResult(barcodeData, encryptedText, companyId)
    }

    private fun strip(s: String): String =
        s.replace("(", "").replace(")", "")
}

/*object GS1Utils {

    fun splitByAI98(input: String): AI98SplitResult? {

        // Normalize FNC1
        val value = input.replace('\u001D', '\u001D')

        // ─────────────────────────────
        // CASE 1: BRACKETED GS1
        // ─────────────────────────────
        if (value.contains("(98)")) {

            var barcodeData = ""
            var encryptedText = ""
            var companyId = ""

            val idx98 = value.indexOf("(98)")
            barcodeData = value.substring(0, idx98)

            val after98 = value.substring(idx98 + 4)

            val idx97 = after98.indexOf("(97)")
            if (idx97 >= 0) {
                encryptedText = after98.substring(0, idx97)
                companyId = after98.substring(idx97 + 4)
            } else {
                encryptedText = after98
            }

            return AI98SplitResult(
                strip(barcodeData),
                strip(encryptedText),
                strip(companyId)
            )
        }

        // ─────────────────────────────
        // CASE 2: FNC1 GS1
        // ─────────────────────────────
        if (value.contains('\u001D')) {

            val tokens = value.split('\u001D').filter { it.isNotEmpty() }

            var barcodeData = ""
            var encryptedText = ""
            var companyId = ""

            tokens.forEach { token ->
                when {
                    token.startsWith("97") ->
                        companyId = token.drop(2)

                    token.contains("98") -> {
                        val idx = token.indexOf("98")
                        barcodeData += token.substring(0, idx)
                        encryptedText = token.substring(idx + 2)
                    }

                    else -> barcodeData += token
                }
            }

            return AI98SplitResult(barcodeData, encryptedText, companyId)
        }

        // ─────────────────────────────
        // CASE 3: FLATTENED RAW GS1
        // ─────────────────────────────
        val r97 = value.lastIndexOf("97")
        if (r97 < 0) return null

        val companyId = value.substring(r97 + 2)
        val before97 = value.substring(0, r97)

        val r98 = before97.lastIndexOf("98")
        if (r98 < 0) return null

        val encryptedText = before97.substring(r98 + 2)
        val barcodeData = before97.substring(0, r98)

        if (barcodeData.isEmpty()) return null

        return AI98SplitResult(barcodeData, encryptedText, companyId)
    }

    private fun strip(s: String): String =
        s.replace("(", "").replace(")", "")
}*/
