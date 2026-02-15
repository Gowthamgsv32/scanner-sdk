package com.example.scanner_sdk.customview.helper

import com.example.scanner_sdk.customview.model.ConvertedGS1Result

object ConvertToAuthentication {

    fun convertDynamicPathToGS1(input: String): ConvertedGS1Result {

        // ✅ Case 1: GS1 formatted with (AI)
        if (input.contains("(") && input.contains(")")) {
            return parseGS1FormattedString(input)
        }

        val uri = runCatching { android.net.Uri.parse(input) }.getOrNull()
            ?: return ConvertedGS1Result("Invalid input", "Invalid input")

        var value98: String? = null

        uri.queryParameterNames.forEach {
            if (it == "98") {
                value98 = uri.getQueryParameter("98")
            }
        }

        val flattened = StringBuilder()
        val original = StringBuilder()

        // Scheme
        Regex("^[a-zA-Z]+").find(input)?.value?.let {
            original.append(it)
            flattened.append(it.lowercase())
        }

        // Host (remove dots)
        uri.host?.replace(".", "")?.let {
            original.append(it)
            flattened.append(it)
        }

        // Path components
        uri.pathSegments.forEach { segment ->
            val clean = segment.replace(Regex("[:\\-]"), "")
            original.append(clean)
            flattened.append(clean)
        }

        // Query params except 98
        uri.queryParameterNames
            .filter { it != "98" }
            .forEach { key ->
                val value = uri.getQueryParameter(key) ?: return@forEach
                val clean = value.replace(Regex("[:\\-]"), "")
                original.append(key).append(clean)
                flattened.append(key).append(clean)
            }

        // Append AI-98 last
        value98?.let {
            original.append("98").append(it)
            flattened.append("98").append(it)
        }

        return ConvertedGS1Result(
            originalWithout98 = original.toString(),
            flattenedGS1With98 = flattened.toString()
        )
    }

    private fun parseGS1FormattedString(input: String): ConvertedGS1Result {

        val prefix = StringBuilder()

        // Extract scheme + host
        Regex("^([a-zA-Z]+)://([^()]+)")
            .find(input)
            ?.let {
                val scheme = it.groupValues[1]
                val host = it.groupValues[2].replace(".", "")
                prefix.append(scheme).append(host)
            }

        val flattened = StringBuilder()
        val original = StringBuilder(prefix.toString())

        val regex = Regex("\\((\\d{2,4})\\)([^()]+)")
        regex.findAll(input).forEach { match ->
            val ai = match.groupValues[1]
            val value = match.groupValues[2]

            flattened.append(ai).append(value)
            original.append(ai).append(value)
        }

        return ConvertedGS1Result(
            originalWithout98 = original.toString(),
            flattenedGS1With98 = flattened.toString()
        )
    }
}
