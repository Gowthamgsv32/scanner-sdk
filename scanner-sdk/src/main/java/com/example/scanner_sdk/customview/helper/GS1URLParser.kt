package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.GS1URLParsedResult

object GS1URLParser {

    // Same AI map you use elsewhere
    private val aiMap = mapOf(
        "00" to "SSCC",
        "01" to "GTIN",
        "10" to "Batch/Lot Number",
        "11" to "Production Date",
        "15" to "Best Before Date",
        "17" to "Expiration Date",
        "21" to "Serial Number",
        "24" to "Additional Product Identification",
        "240" to "Additional Product Information",
        "422" to "Country of Origin",
        "98" to "Internal / Encrypted Data",
        "97" to "Company Identification"
    )

    fun parseDigitalLink(urlString: String): List<GS1URLParsedResult> {
        val uri = runCatching { android.net.Uri.parse(urlString) }.getOrNull()
            ?: return emptyList()

        val results = mutableListOf<GS1URLParsedResult>()

        // ✅ PATH components
        val components = uri.pathSegments
        var i = 0

        while (i + 1 < components.size) {
            val aiCandidate = components[i]

            if (!aiMap.containsKey(aiCandidate)) {
                i++
                continue
            }

            val value = components[i + 1]
            val description = aiMap[aiCandidate] ?: "Unknown AI"

            results.add(
                GS1URLParsedResult(
                    ai = aiCandidate,
                    value = value,
                    description = description
                )
            )

            i += 2
        }

        // ✅ QUERY params (?17=221231)
        uri.queryParameterNames.forEach { key ->
            val value = uri.getQueryParameter(key) ?: return@forEach
            if (!aiMap.containsKey(key)) return@forEach

            results.add(
                GS1URLParsedResult(
                    ai = key,
                    value = value,
                    description = aiMap[key] ?: "Unknown AI"
                )
            )
        }

        return results
    }
}
