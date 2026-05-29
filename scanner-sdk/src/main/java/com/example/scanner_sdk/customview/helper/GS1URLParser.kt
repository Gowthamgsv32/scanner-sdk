package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.GS1URLParsedResult
import java.net.URI

object GS1URLParser {

    private val aiMap = mapOf(
        "00" to "SSCC",
        "01" to "GTIN",
        "10" to "Batch/Lot Number",
        "11" to "Production Date",
        "12" to "Due Date",
        "13" to "Packaging Date",
        "15" to "Best Before Date",
        "16" to "Sell By Date",
        "17" to "Expiration Date",
        "20" to "Internal Product Variant",
        "21" to "Serial Number",
        "22" to "Consumer Product Variant",
        "30" to "Variable Count",
        "37" to "Count of Trade Items",
        "240" to "Additional Product Information",
        "422" to "Country of Origin",
    )

    fun parseDigitalLink(urlString: String): List<GS1URLParsedResult> {
        val baseUrl = GS1DigitalLinkAuth.cleanDigitalLinkUrl(urlString)
        val results = mutableListOf<GS1URLParsedResult>()

        val pathSegments = runCatching {
            URI(baseUrl).path?.trim('/')?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
        }.getOrDefault(emptyList())

        var i = 0
        while (i + 1 < pathSegments.size) {
            val ai = pathSegments[i]
            if (!aiMap.containsKey(ai)) {
                i++
                continue
            }
            results.add(
                GS1URLParsedResult(
                    ai = ai,
                    value = pathSegments[i + 1],
                    description = aiMap[ai] ?: "Unknown AI",
                ),
            )
            i += 2
        }

        GS1DigitalLinkAuth.rawQueryString(baseUrl)?.let { rawQuery ->
            Regex("""(^|&)(\d{2,4})=([^&]+)""").findAll(rawQuery).forEach { m ->
                val ai = m.groupValues[2]
                if (!aiMap.containsKey(ai) || GS1ParseSupport.isAuthTrailerAI(ai)) return@forEach
                if (results.any { it.ai == ai }) return@forEach
                results.add(
                    GS1URLParsedResult(
                        ai = ai,
                        value = m.groupValues[3],
                        description = aiMap[ai] ?: "Unknown AI",
                    ),
                )
            }
        }

        return results
    }

    fun removeHiddenAIBlocks(from: String): String =
        GS1DigitalLinkAuth.cleanDigitalLinkUrl(from)
}
