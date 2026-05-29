package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.GS1Parser
import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.example.scanner_sdk.customview.model.ParsedAuthBarcode

/**
 * Builds auth API payloads to match iOS: bracketed GS1 for FNC1 scans,
 * Digital Link base URL (no ?98=) for URL scans, encrypted_text from AI-98.
 */
object AuthBarcodeParser {

    private val FNC1 = '\u001D'

    fun parse(value: String, type: String): ParsedAuthBarcode {
        val raw = value.trim()
        if (raw.startsWith("http", ignoreCase = true)) {
            return parseDigitalLink(raw, type)
        }
        if (raw.contains("(98)")) {
            return parseBracketed(raw, type)
        }
        if (raw.contains(FNC1) || looksLikeFlatGs1(raw)) {
            return parseFnc1OrFlatGs1(raw, type)
        }
        return parsePlain(raw, type)
    }

    private fun parseDigitalLink(raw: String, type: String): ParsedAuthBarcode {
        val barcodeData = digitalLinkBaseUrl(raw)
        val encryptedText = extractQueryAi(raw, "98")
        val companyFromBarcode = extractQueryAi(raw, "97")
            .ifEmpty { extractTrailingPathAi(raw, "97") }

        val parsedResults = GS1URLParser.parseDigitalLink(barcodeData).map {
            GS1ParsedResult(ai = it.ai, value = it.value, description = it.description)
        }

        return ParsedAuthBarcode(
            parsedResults = parsedResults,
            barcodeData = barcodeData,
            encryptedText = encryptedText,
            isGeneratedBySystem = encryptedText.isNotEmpty(),
            companyId = companyFromBarcode,
            type = type,
        )
    }

    private fun parseBracketed(raw: String, type: String): ParsedAuthBarcode {
        val idx98 = raw.indexOf("(98)")
        val barcodePart = raw.substring(0, idx98)
        val after98 = raw.substring(idx98 + 4)
        val idx97 = after98.indexOf("(97)")
        val encryptedText = if (idx97 >= 0) after98.substring(0, idx97) else after98.substringBefore(FNC1)
        val companyId = if (idx97 >= 0) after98.substring(idx97 + 4).trim() else ""

        val parsedResults = GS1Parser.parseGS1(barcodePart)
        val barcodeData = formatBracketed(parsedResults).ifEmpty { barcodePart }

        return ParsedAuthBarcode(
            parsedResults = parsedResults,
            barcodeData = barcodeData,
            encryptedText = encryptedText.trim(),
            isGeneratedBySystem = true,
            companyId = companyId,
            type = type,
        )
    }

    private fun parseFnc1OrFlatGs1(raw: String, type: String): ParsedAuthBarcode {
        val split = GS1Utils.splitByAI98(raw) ?: return parsePlain(raw, type)

        val parsedResults = GS1Parser.parseGS1(split.barcodeData)
        val barcodeData = formatBracketed(parsedResults).ifEmpty { split.barcodeData }

        return ParsedAuthBarcode(
            parsedResults = parsedResults,
            barcodeData = barcodeData,
            encryptedText = split.encryptedText,
            isGeneratedBySystem = split.encryptedText.isNotEmpty(),
            companyId = split.companyId,
            type = type,
        )
    }

    private fun parsePlain(raw: String, type: String): ParsedAuthBarcode {
        val parsedResults = GS1Parser.parseGS1(raw)
        val barcodeData = formatBracketed(parsedResults).ifEmpty { raw }

        return ParsedAuthBarcode(
            parsedResults = parsedResults,
            barcodeData = barcodeData,
            encryptedText = "",
            isGeneratedBySystem = false,
            companyId = "",
            type = type,
        )
    }

    /** Base URL path only — no ?98=, &97=, trailing /97=, or FNC1 tail. */
    fun digitalLinkBaseUrl(raw: String): String {
        var url = raw.substringBefore(FNC1).trim()
        url = url.substringBefore("?98=").substringBefore("&98=")
        url = url.replace(Regex("""/97=[^/?]+$"""), "").trimEnd('/')
        return url
    }

    fun extractQueryAi(raw: String, ai: String): String {
        Regex("""[?&]$ai=([^&/\u001D]+)""").find(raw)?.groupValues?.getOrNull(1)?.let {
            return it.trim()
        }
        return ""
    }

    private fun extractTrailingPathAi(raw: String, ai: String): String {
        Regex("""/$ai=([^/?\u001D]+)""").find(raw)?.groupValues?.getOrNull(1)?.let {
            return it.trim()
        }
        return ""
    }

    fun formatBracketed(parsed: List<GS1ParsedResult>): String =
        parsed
            .filter { !GS1ParseSupport.isAuthTrailerAI(it.ai) }
            .joinToString("") { "(${it.ai})${it.value}" }

    private fun looksLikeFlatGs1(raw: String): Boolean {
        if (raw.contains("(")) return false
        return raw.startsWith("01") || raw.contains(FNC1)
    }
}
