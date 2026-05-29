package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Parses `POST /scan/auth-bc` responses; prefer `gs1_data` over local GS1 parsing for display. */
object AuthBcResponseParser {

    data class AuthBcItem(
        val barcodeData: String,
        val encryptedText: String,
        val quality: String,
        val gs1Results: List<GS1ParsedResult>,
    )

    /**
     * Supports `{ "success": true, "data": [ { … } ] }` and plain `[ { … } ]`.
     */
    fun parse(responseBody: String): AuthBcItem? {
        return try {
            val root = JsonParser.parseString(responseBody.trim())
            val row = firstDataRow(root) ?: return null
            parseRow(row)
        } catch (_: Exception) {
            null
        }
    }

    private fun firstDataRow(root: JsonElement): JsonObject? = when {
        root.isJsonArray -> root.asJsonArray.firstOrNull()?.asJsonObject
        root.isJsonObject -> {
            val obj = root.asJsonObject
            obj.getAsJsonArray("data")?.firstOrNull()?.asJsonObject ?: obj
        }
        else -> null
    }

    private fun parseRow(obj: JsonObject): AuthBcItem {
        val gs1Object = obj.getAsJsonObject("gs1_data")
        return AuthBcItem(
            barcodeData = obj.get("barcode_data")?.asString ?: "",
            encryptedText = obj.get("encrypted_text")?.asString ?: "",
            quality = obj.get("quality")?.asString ?: "Unknown",
            gs1Results = gs1DataToParsedResults(gs1Object),
        )
    }

    fun gs1DataToParsedResults(gs1Object: JsonObject?): List<GS1ParsedResult> {
        if (gs1Object == null) return emptyList()
        val results = mutableListOf<GS1ParsedResult>()
        for ((ai, el) in gs1Object.entrySet()) {
            if (GS1ParseSupport.isAuthTrailerAI(ai)) continue
            if (!el.isJsonObject) continue
            val field = el.asJsonObject
            results.add(
                GS1ParsedResult(
                    ai = ai,
                    value = field.get("value")?.asString ?: "",
                    description = field.get("name")?.asString ?: ai,
                ),
            )
        }
        return results.sortedBy { it.ai.padStart(4, '0') }
    }

    /** Build display JSONArray matching auth-bc `gs1_data` shape for SDK callbacks. */
    fun toResultArray(item: AuthBcItem): JsonArray {
        val gs1Data = JsonObject()
        item.gs1Results.forEach { r ->
            gs1Data.add(
                r.ai,
                JsonObject().apply {
                    addProperty("name", r.description)
                    addProperty("value", r.value)
                },
            )
        }
        val row = JsonObject().apply {
            addProperty("barcode_data", item.barcodeData)
            addProperty("encrypted_text", item.encryptedText)
            addProperty("quality", item.quality)
            add("gs1_data", gs1Data)
        }
        return JsonArray().apply { add(row) }
    }
}
