package com.example.scanner_sdk.customview

import android.util.Log
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Authentication conversion utility for converting dynamic paths to GS1 format
 * This utility can be used across multiple screens for authentication processing
 */
object ConvertToAuthentication {

    data class AuthenticationResult(
        val originalWithout98: String,
        val flattenedGS1With98: String
    )

    /**
     * Converts dynamic path input to GS1 format
     * Supports both GS1-formatted strings with (AI)(value) patterns and standard URLs
     *
     * @param input The input string to convert (URL or GS1 format)
     * @return AuthenticationResult containing both original and flattened formats
     */
    fun convertDynamicPathToGS1(input: String): AuthenticationResult {
        Log.d("ConvertToAuthentication", "Converting input to GS1 format: $input")

        // Case 1: GS1-formatted input with (AI)(value) patterns
        if (input.contains("(") && input.contains(")")) {
            Log.d("ConvertToAuthentication", "Processing GS1-formatted string")
            return parseGS1FormattedString(input)
        }

        // Case 2: Standard URL with query parameters
        return try {
            parseUrlString(input)
        } catch (e: Exception) {
            Log.e("ConvertToAuthentication", "Error parsing URL: ${e.message}")
            AuthenticationResult("Invalid input", "Invalid input")
        }
    }

    /**
     * Parses standard URL strings and extracts components for GS1 conversion
     *
     * @param input The URL string to parse
     * @return AuthenticationResult with processed URL components
     */
    private fun parseUrlString(input: String): AuthenticationResult {
        Log.d("ConvertToAuthentication", "Parsing URL string: $input")

        val url = URL(input)
        var value98: String? = null

        // Parse query parameters
        val queryParams = mutableMapOf<String, String>()
        url.query?.split("&")?.forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                queryParams[key] = value

                if (key == "98") {
                    value98 = value
                }
            }
        }

        // Remove 98 parameter for originalWithout98
        val filteredParams = queryParams.filterKeys { it != "98" }

        // Build the result strings
        var originalWith98 = ""
        var flattened = ""

        // Add capitalized scheme
        val scheme = url.protocol
        originalWith98 += scheme
        flattened += scheme.lowercase()

        // Add host without dots
        val host = url.host
        if (host != null) {
            val cleanedHost = host.replace(".", "")
            originalWith98 += cleanedHost
            flattened += cleanedHost
        }

        // Add path components
        val pathComponents = url.path.split("/").filter { it.isNotEmpty() }
        pathComponents.forEach { component ->
            val clean = component.replace(Regex("[:\\-]"), "")
            originalWith98 += clean
            flattened += clean
        }

        // Add query parameters (excluding 98)
        filteredParams.forEach { (key, value) ->
            val clean = value.replace(Regex("[:\\-]"), "")
            originalWith98 += key + clean
            flattened += key + clean
        }

        // Add 98 parameter if it exists
        value98?.let { value ->
            originalWith98 += "98$value"
            flattened += "98$value"
        }

        Log.d("ConvertToAuthentication", "URL parsing result - Original: $originalWith98, Flattened: $flattened")
        return AuthenticationResult(originalWith98, flattened)
    }

    /**
     * Parses GS1-formatted strings with (AI)(value) patterns
     *
     * @param input The GS1-formatted string to parse
     * @return AuthenticationResult with extracted AI-value pairs
     */
    private fun parseGS1FormattedString(input: String): AuthenticationResult {
        Log.d("ConvertToAuthentication", "Parsing GS1 formatted string: $input")

        // Extract scheme and host manually
        val urlPattern = Pattern.compile("^([a-zA-Z]+)://([^()]+)")
        val matcher = urlPattern.matcher(input)

        var prefix = ""
        if (matcher.find()) {
            val scheme = matcher.group(1) ?: ""
            val host = (matcher.group(2) ?: "").replace(".", "")
            prefix = scheme + host
        }

        // Parse (AI)(value) pairs
        val aiPattern = Pattern.compile("\\((\\d{2,4})\\)([^()]+)")
        val aiMatcher = aiPattern.matcher(input)

        var flattened = ""
        var originalWith98 = prefix

        while (aiMatcher.find()) {
            val ai = aiMatcher.group(1) ?: ""
            val value = aiMatcher.group(2) ?: ""

            flattened += ai + value
            originalWith98 += ai + value
        }

        Log.d("ConvertToAuthentication", "GS1 parsing result - Original: $originalWith98")
        Log.d("ConvertToAuthentication", "GS1 parsing result - Flattened: $flattened")

        return AuthenticationResult(originalWith98, flattened)
    }

    /**
     * Validates if the input string is a valid URL format
     *
     * @param input The string to validate
     * @return true if the input is a valid URL, false otherwise
     */
    fun isValidUrl(input: String): Boolean {
        return try {
            URL(input)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates if the input string contains GS1 format patterns
     *
     * @param input The string to validate
     * @return true if the input contains GS1 patterns, false otherwise
     */
    fun isGS1Format(input: String): Boolean {
        val aiPattern = Pattern.compile("\\((\\d{2,4})\\)([^()]+)")
        return aiPattern.matcher(input).find()
    }

    /**
     * Extracts all Application Identifiers (AIs) from a GS1-formatted string
     *
     * @param input The GS1-formatted string
     * @return List of AI codes found in the input
     */
    fun extractApplicationIdentifiers(input: String): List<String> {
        val aiPattern = Pattern.compile("\\((\\d{2,4})\\)")
        val matcher = aiPattern.matcher(input)
        val ais = mutableListOf<String>()

        while (matcher.find()) {
            val ai = matcher.group(1)
            if (ai != null) {
                ais.add(ai)
            }
        }

        Log.d("ConvertToAuthentication", "Extracted AIs: $ais from input: $input")
        return ais
    }
}
