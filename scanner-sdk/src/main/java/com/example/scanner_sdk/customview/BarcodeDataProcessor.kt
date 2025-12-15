package com.example.scanner_sdk.customview

import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for processing barcode data and handling encryption/decryption
 * Works with ConvertToAuthentication results to extract and encrypt barcode information
 */
object BarcodeDataProcessor {

    data class BarcodeProcessingResult(
        val barcodeData: String,
        val encryptedText: String,
        val extractedValues: Map<String, String> = emptyMap()
    )

    data class ExtractedBarcodeInfo(
        val scheme: String,
        val domain: String,
        val gtin: String? = null,
        val batch: String? = null,
        val serial: String? = null,
        val expiryDate: String? = null,
        val rawData: String
    )

    /**
     * Splits input string by the last occurrence of AI 98
     * Converted from Swift GS1Utils.splitByAI98
     *
     * @param input The input string to split
     * @return Pair of (barcodeData, encryptedText) or null if no AI 98 found
     */
    fun splitByAI98(input: String): Pair<String, String>? {
        // Remove FNC1 character (equivalent to Swift's \u{001d})
        val cleanedInput = input.replace("\u001D", "")
        Log.d("BarcodeDataProcessor", "cleanedInput: $cleanedInput")

        // Find the last occurrence of "98"
        val last98Index = cleanedInput.lastIndexOf("98")
        if (last98Index == -1) {
            return null
        }

        // Calculate the index after "98" (equivalent to Swift's after98Index)
        val after98Index = last98Index + 2
        if (after98Index > cleanedInput.length) {
            return null
        }

        // Split the string
        val barcodeData = cleanedInput.substring(0, last98Index)
        val encryptedText = cleanedInput.substring(after98Index)

        Log.d("BarcodeDataProcessor", "barcodeData: $barcodeData")
        Log.d("BarcodeDataProcessor", "encryptedText: $encryptedText")

        return Pair(barcodeData, encryptedText)
    }

    /**
     * Processes the converted authentication result and creates encrypted barcode data
     * Now uses the Swift-equivalent splitting logic
     *
     * @param authResult The result from ConvertToAuthentication.convertDynamicPathToGS1()
     * @param encryptionKey Optional encryption key, if null a default will be used
     * @return BarcodeProcessingResult with barcode data and encrypted text
     */
    fun processConvertedResult(
        authResult: ConvertToAuthentication.AuthenticationResult,
        encryptionKey: String? = null
    ): BarcodeProcessingResult {
        Log.d("BarcodeDataProcessor", "Processing converted authentication result")
        Log.d("BarcodeDataProcessor", "Original: ${authResult.originalWithout98}")
        Log.d("BarcodeDataProcessor", "Flattened: ${authResult.flattenedGS1With98}")

        // Use the new splitByAI98 function on the flattened result
        val splitResult = splitByAI98(authResult.flattenedGS1With98)

        return if (splitResult != null) {
            val (barcodeData, encryptedText) = splitResult
            Log.d("BarcodeDataProcessor", "Split successful - Barcode: $barcodeData, Encrypted: $encryptedText")

            BarcodeProcessingResult(
                barcodeData = barcodeData,
                encryptedText = encryptedText,
                extractedValues = emptyMap()
            )
        } else {
            // Fallback to original logic if no AI 98 found
            val barcodeData = authResult.originalWithout98
            val encryptedText = encryptBarcodeData(barcodeData, encryptionKey)

            Log.d("BarcodeDataProcessor", "No AI 98 found, using fallback - Barcode: $barcodeData")

            BarcodeProcessingResult(
                barcodeData = barcodeData,
                encryptedText = encryptedText,
                extractedValues = emptyMap()
            )
        }
    }

    /**
     * Extracts detailed information from processed barcode data
     *
     * @param processedData The flattened GS1 data from authentication conversion
     * @return ExtractedBarcodeInfo with parsed components
     */
    private fun extractBarcodeInformation(processedData: String): ExtractedBarcodeInfo {
        Log.d("BarcodeDataProcessor", "Extracting information from: $processedData")

        // Extract scheme (first part before domain)
        val scheme = extractScheme(processedData)

        // Extract domain (after scheme, before GS1 data)
        val domain = extractDomain(processedData, scheme)

        // Extract data before and after AI 98
        val dataBefore98 = extractDataBefore98(processedData)
        val dataAfter98 = extractDataAfter98(processedData)

        Log.d("BarcodeDataProcessor", "Data before 98: $dataBefore98")
        Log.d("BarcodeDataProcessor", "Data after 98 (13 digits): $dataAfter98")

        // Extract GS1 Application Identifiers from the data before 98
        val gtin = extractGS1Value(dataBefore98, "01") // GTIN
        val batch = extractGS1Value(dataBefore98, "10") // Batch/Lot
        val serial = extractGS1Value(dataBefore98, "21") // Serial Number
        val expiryDate = extractGS1Value(dataBefore98, "17") // Expiry Date

        return ExtractedBarcodeInfo(
            scheme = scheme,
            domain = domain,
            gtin = gtin,
            batch = batch,
            serial = serial,
            expiryDate = expiryDate,
            rawData = processedData
        )
    }

    /**
     * Extracts the scheme from processed barcode data
     */
    private fun extractScheme(data: String): String {
        // Look for common schemes at the beginning
        val commonSchemes = listOf("https", "http", "ftp", "data")
        for (scheme in commonSchemes) {
            if (data.lowercase().startsWith(scheme.lowercase())) {
                return scheme
            }
        }
        return "unknown"
    }

    /**
     * Extracts the domain from processed barcode data
     */
    private fun extractDomain(data: String, scheme: String): String {
        val afterScheme = data.substring(scheme.length)

        // Look for domain patterns
        val domainPattern = "([a-zA-Z0-9]+(?:[a-zA-Z0-9]*)*)"
        val regex = Regex(domainPattern)
        val match = regex.find(afterScheme)

        return match?.value ?: "unknown"
    }

    /**
     * Extracts GS1 Application Identifier values from processed data
     * Updated logic: Before 98 and After 98 (13 digits)
     */
    private fun extractGS1Value(data: String, ai: String): String? {
        // Special handling for AI 98 - extract 13 digits after it
        if (ai == "98") {
            val ai98Pattern = "98(\\d{13})"
            val regex = Regex(ai98Pattern)
            val match = regex.find(data)
            return match?.groupValues?.get(1)
        }

        // For other AIs, use the original logic
        val aiPattern = "$ai([^98]*?)(?=98|$)"
        val regex = Regex(aiPattern)
        val match = regex.find(data)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts data before AI 98
     */
    private fun extractDataBefore98(data: String): String {
        val before98Index = data.indexOf("98")
        return if (before98Index != -1) {
            data.substring(0, before98Index)
        } else {
            data
        }
    }

    /**
     * Extracts 13 digits after AI 98
     */
    private fun extractDataAfter98(data: String): String? {
        val ai98Pattern = "98(\\d{13})"
        val regex = Regex(ai98Pattern)
        val match = regex.find(data)
        return match?.groupValues?.get(1)
    }

    /**
     * Encrypts barcode data using AES encryption
     *
     * @param data The data to encrypt
     * @param encryptionKey Optional encryption key
     * @return Base64 encoded encrypted string
     */
    private fun encryptBarcodeData(data: String, encryptionKey: String?): String {
        return try {
            val key = encryptionKey ?: "TNTBarcodeKey123" // Default key
            val secretKey = generateSecretKey(key)

            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val encodedString = Base64.encodeToString(encryptedBytes, Base64.DEFAULT).trim()

            Log.d("BarcodeDataProcessor", "Data encrypted successfully")
            encodedString
        } catch (e: Exception) {
            Log.e("BarcodeDataProcessor", "Encryption failed: ${e.message}")
            // Return a simple base64 encoding as fallback
            Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.DEFAULT).trim()
        }
    }

    /**
     * Decrypts barcode data using AES decryption
     *
     * @param encryptedData Base64 encoded encrypted string
     * @param encryptionKey Optional encryption key
     * @return Decrypted string
     */
    fun decryptBarcodeData(encryptedData: String, encryptionKey: String? = null): String {
        return try {
            val key = encryptionKey ?: "TNTBarcodeKey123" // Default key
            val secretKey = generateSecretKey(key)

            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val decryptedString = String(decryptedBytes, Charsets.UTF_8)
            Log.d("BarcodeDataProcessor", "Data decrypted successfully")
            decryptedString
        } catch (e: Exception) {
            Log.e("BarcodeDataProcessor", "Decryption failed: ${e.message}")
            // Return base64 decoding as fallback
            try {
                String(Base64.decode(encryptedData, Base64.DEFAULT), Charsets.UTF_8)
            } catch (ex: Exception) {
                "Decryption failed"
            }
        }
    }

    /**
     * Generates a secret key for AES encryption
     */
    private fun generateSecretKey(keyString: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(keyString.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes.copyOf(16), "AES") // Use first 16 bytes for AES-128
    }

    /**
     * Creates a JSON representation of the barcode processing result
     *
     * @param result The BarcodeProcessingResult to convert
     * @return JSON string representation
     */
    fun toJson(result: BarcodeProcessingResult): String {
        val jsonObject = JSONObject()
        jsonObject.put("barcode_data", result.barcodeData)
        jsonObject.put("encrypted_text", result.encryptedText)

        // Add extracted values if available
        if (result.extractedValues.isNotEmpty()) {
            val extractedJson = JSONObject()
            result.extractedValues.forEach { (key, value) ->
                extractedJson.put(key, value)
            }
            jsonObject.put("extracted_values", extractedJson)
        }

        return jsonObject.toString()
    }

    /**
     * Parses JSON string back to BarcodeProcessingResult
     *
     * @param jsonString The JSON string to parse
     * @return BarcodeProcessingResult object
     */
    fun fromJson(jsonString: String): BarcodeProcessingResult? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val barcodeData = jsonObject.getString("barcode_data")
            val encryptedText = jsonObject.getString("encrypted_text")

            val extractedValues = mutableMapOf<String, String>()
            if (jsonObject.has("extracted_values")) {
                val extractedJson = jsonObject.getJSONObject("extracted_values")
                extractedJson.keys().forEach { key ->
                    extractedValues[key] = extractedJson.getString(key)
                }
            }

            BarcodeProcessingResult(barcodeData, encryptedText, extractedValues)
        } catch (e: Exception) {
            Log.e("BarcodeDataProcessor", "Failed to parse JSON: ${e.message}")
            null
        }
    }

    /**
     * Validates if a barcode data string matches expected patterns
     *
     * @param barcodeData The barcode data to validate
     * @return true if valid, false otherwise
     */
    fun validateBarcodeData(barcodeData: String): Boolean {
        // Basic validation - check if it contains expected patterns
        val hasScheme = barcodeData.lowercase().contains("http") ||
                       barcodeData.lowercase().contains("ftp") ||
                       barcodeData.lowercase().contains("data")

        val hasMinimumLength = barcodeData.length >= 10

        return hasScheme && hasMinimumLength
    }
}
