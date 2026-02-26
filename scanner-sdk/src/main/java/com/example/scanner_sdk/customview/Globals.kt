package com.example.scanner_sdk.customview

import android.util.Log
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.helper.GS1URLParser
import com.example.scanner_sdk.customview.helper.GS1Utils
import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.example.scanner_sdk.customview.model.ParsedAuthBarcode
import com.google.mlkit.vision.barcode.common.Barcode

const val LOG = "BARCODESCANNERLOG"
fun log(message: Any) {
    Log.d(LOG, message.toString())
}

enum class ScanMode { SINGLE, MULTIPLE }

fun toggleFlash(isFlashEnabled: Boolean, view: ImageView) {
    val flashMode = if (isFlashEnabled) {
        ImageCapture.FLASH_MODE_ON
    } else {
        ImageCapture.FLASH_MODE_OFF
    }

    // Update flash icon
    val flashIcon = if (isFlashEnabled) {
        R.drawable.ic_flash_on
    } else {
        R.drawable.ic_flash_off
    }
    view.setImageResource(flashIcon)
}

fun getBarcodeTypeName(format: Int): String {
    return when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "Unknown Format ($format)"
    }
}

fun parseBarcodeLikeMultiScan(value: String): Triple<
        List<GS1ParsedResult>,
        String,
        String
        > {
    Log.d("BARCODESCANNERLOG", value)
    // ✅ 1. Digital Link should ALWAYS be parsed
    if (value.startsWith("http")) {

        val dlResults = GS1URLParser
            .parseDigitalLink(value)
            .map {
                GS1ParsedResult(
                    ai = it.ai,
                    value = it.value,
                    description = it.description
                )
            }

        val parsing = ConvertToAuthentication
            .convertDynamicPathToGS1(value)

        val splittedData = GS1Utils.splitByAI98(
            parsing.originalWithout98
        )

        return if (splittedData != null) {
            Log.d("BARCODELOGS", "parseBarcodeLikeMultiScan: step 1 called")
            Triple(
                dlResults,
                splittedData.barcodeData,
                splittedData.encryptedText
            )
        } else {
            Log.d("BARCODELOGS", "parseBarcodeLikeMultiScan: step 2 called")
            // ✅ Digital Link WITHOUT AI-98
            Triple(
                dlResults,
                value,
                ""
            )
        }
    }

    // ✅ 2. Normal GS1 flow
    val parsedGS1Results = GS1Parser.parseGS1(value)

    Log.d("BARCODESCANNERLOG", parsedGS1Results.joinToString())
    val parsing = ConvertToAuthentication
        .convertDynamicPathToGS1(value)

    val splittedData = GS1Utils.splitByAI98(
        parsing.originalWithout98
    )

    return if (splittedData != null) {
        Triple(
            parsedGS1Results,
            splittedData.barcodeData,
            splittedData.encryptedText
        )
    } else {
        // ✅ 3. Plain GS1
        Triple(
            parsedGS1Results,
            "",
            ""
        )
    }
}

fun parseBarcodeLikeMultiScanForAuth(value: String, type: String): ParsedAuthBarcode {

//    val splitted = GS1Utils.splitByAI98(value)
    val normalized = value.replace('\u001D', 29.toChar())
    val splitted = GS1Utils.splitByAI98(normalized)

    val parseGS1Results = GS1Parser.parseGS1(value)

    // ✅ Always parse Digital Link if URL
    val parsedURL = GS1URLParser.parseDigitalLink(value)
    val normalizedParsed = parsedURL.map {
        GS1ParsedResult(
            ai = it.ai,
            value = it.value,
            description = it.description
        )
    }

    val parsing = ConvertToAuthentication
        .convertDynamicPathToGS1(value)

    // ---------- DIGITAL LINK ----------
    if (value.startsWith("http")) {

        // 🔐 Auth only if AI-98 exists
        val splitted = GS1Utils.splitByAI98(
            parsing.originalWithout98
        )

        return if (splitted != null) {
            ParsedAuthBarcode(
                parsedResults = normalizedParsed,
                barcodeData = splitted.barcodeData,
                encryptedText = splitted.encryptedText,
                isGeneratedBySystem = true,
                companyId = splitted.companyId,
                type = type,
            )
        } else {
            // ✅ Valid GS1 Digital Link, but NOT system-generated
            ParsedAuthBarcode(
                parsedResults = normalizedParsed,
                barcodeData = value,
                encryptedText = "",
                isGeneratedBySystem = false,
                companyId = "",
                type = type,
            )
        }
    }

    // ---------- NORMAL GS1 ----------
    /*    val splitted = GS1Utils.splitByAI98(
            parsing.originalWithout98
        )*/

    if (splitted != null) {
        return ParsedAuthBarcode(
            parsedResults = parseGS1Results,
            barcodeData = splitted.barcodeData,
            encryptedText = splitted.encryptedText,
            isGeneratedBySystem = true,
            companyId = splitted.companyId,
            type = type,
        )
    }

    // ---------- PLAIN GS1 ----------
    return ParsedAuthBarcode(
        parsedResults = parseGS1Results,
        barcodeData = value,
        encryptedText = "",
        isGeneratedBySystem = false,
        companyId = "",
        type = type,
    )
}

object GS1Parser {

    private const val FNC1 = 29.toChar()

    private val aiDict = mapOf(

        // Logistic / Trade Identification
        "00" to Pair("SSCC", 18),
        "01" to Pair("GTIN", 14),
        "02" to Pair("Contained GTIN", 14),
        "10" to Pair("Batch/Lot Number", null),
        "11" to Pair("Production Date", 6),
        "12" to Pair("Due Date", 6),
        "13" to Pair("Packaging Date", 6),
        "15" to Pair("Best Before Date", 6),
        "16" to Pair("Sell By Date", 6),
        "17" to Pair("Expiration Date", 6),
        "20" to Pair("Internal Product Variant", 2),
        "21" to Pair("Serial Number", null),
        "22" to Pair("Consumer Product Variant", null),
        "30" to Pair("Variable Count", null),
        "37" to Pair("Count of Trade Items", null),

        // Additional Identification
        "235" to Pair("TPX – Third Party GTIN Extension", null),
        "240" to Pair("Additional Product Identification", null),
        "241" to Pair("Customer Part Number", null),
        "242" to Pair("Made-to-Order Variation", null),
        "243" to Pair("Packaging Component Number", null),
        "250" to Pair("Secondary Serial Number", null),
        "251" to Pair("Reference to Source Entity", null),
        "253" to Pair("Global Document Type Identifier (GDTI)", null),
        "254" to Pair("GLN Extension Component", null),
        "255" to Pair("Global Coupon Number (GCN)", null),

        // Shipment / Consignment
        "400" to Pair("Customer PO Number", null),
        "401" to Pair("GINC", null),
        "402" to Pair("GSIN", 17),
        "403" to Pair("Routing Code", null),

        // GLN
        "410" to Pair("Ship To GLN", 13),
        "411" to Pair("Bill To GLN", 13),
        "412" to Pair("Purchased From GLN", 13),
        "413" to Pair("Ship For GLN", 13),
        "414" to Pair("Physical Location GLN", 13),
        "415" to Pair("Invoicing Party GLN", 13),
        "416" to Pair("Production Location GLN", 13),
        "417" to Pair("Party GLN", 13),

        // Country / Postal
        "420" to Pair("Postal Code", null),
        "421" to Pair("Postal Code + ISO Country", null),
        "422" to Pair("Country of Origin", 3),
        "423" to Pair("Country of Initial Processing", null),
        "424" to Pair("Country of Processing", 3),
        "425" to Pair("Country of Disassembly", null),
        "426" to Pair("Full Process Chain Country", 3),
        "427" to Pair("Country Subdivision", null),

        // Temperature
        "4330" to Pair("Max Temp Fahrenheit", 6),
        "4331" to Pair("Max Temp Celsius", 6),
        "4332" to Pair("Min Temp Fahrenheit", 6),
        "4333" to Pair("Min Temp Celsius", 6),

        // Asset Identification
        "8001" to Pair("Roll Product Info", 14),
        "8002" to Pair("Mobile Identifier", null),
        "8003" to Pair("GRAI", null),
        "8004" to Pair("GIAI", null),
        "8005" to Pair("Price Per Unit", 6),
        "8006" to Pair("ITIP Piece", 18),
        "8007" to Pair("IBAN", null),
        "8008" to Pair("Production Date & Time", null),
        "8009" to Pair("Sensor Indicator", null),
        "8010" to Pair("Component Part ID", null),
        "8011" to Pair("Component Serial", null),
        "8012" to Pair("Software Version", null),
        "8013" to Pair("Global Model Number", null),
        "8017" to Pair("GSRN Provider", 18),
        "8018" to Pair("GSRN Recipient", 18),
        "8019" to Pair("Service Relation Instance", null),
        "8020" to Pair("Payment Slip Reference", null),
        "8026" to Pair("ITIP Contained Pieces", 18),
        "8030" to Pair("Digital Signature", null),
        "8200" to Pair("Extended Packaging URL", null),

        // Healthcare
        "7001" to Pair("NATO Stock Number", 13),
        "7002" to Pair("UNECE Meat Classification", null),
        "7003" to Pair("Expiration Date & Time", 10),
        "7004" to Pair("Active Potency", null),
        "7005" to Pair("Catch Area", null),
        "7006" to Pair("First Freeze Date", 6),
        "7007" to Pair("Harvest Date", null),
        "7008" to Pair("Species Code", null),
        "7009" to Pair("Fishing Gear Type", null),
        "7010" to Pair("Production Method", null),
        "7011" to Pair("Test By Date", null),
        "7020" to Pair("Refurbishment Lot ID", null),
        "7021" to Pair("Functional Status", null),
        "7022" to Pair("Revision Status", null),
        "7023" to Pair("GIAI Assembly", null),

        // Company Internal
        "91" to Pair("Company Internal 91", null),
        "92" to Pair("Company Internal 92", null),
        "93" to Pair("Company Internal 93", null),
        "94" to Pair("Company Internal 94", null),
        "95" to Pair("Company Internal 95", null),
        "96" to Pair("Company Internal 96", null),
        "97" to Pair("Company Internal 97", null),
        "98" to Pair("Company Internal 98", null),
        "99" to Pair("Company Internal 99", null)
    )

    // =====================================================
    // PUBLIC ENTRY (same flow as Swift)
    // =====================================================

    fun parseGS1(data: String): List<GS1ParsedResult> {

        val cleaned = preprocess(data)

        return if (cleaned.contains("(")) {
            parseBracketed(cleaned)
        } else {
            parsePlain(cleaned)
        }
    }

    // =====================================================
    // PREPROCESS RAW SCANNER DATA
    // =====================================================

    private fun preprocess(data: String): String {

        var input = data

        // Remove ]C1 ]d2 ]Q3 symbology
        if (input.startsWith("]") && input.length > 3) {
            input = input.substring(3)
        }

        val sb = StringBuilder(input.length)

        input.forEach {
            if (it == FNC1 || it.code >= 32) {
                sb.append(it)
            }
        }

        return sb.toString()
    }

    // =====================================================
    // BRACKETED MODE (same as Swift)
    // =====================================================

    private fun parseBracketed(data: String): List<GS1ParsedResult> {

        val regex = Regex("""\((\d{2,4})\)([^\(]+)""")

        return regex.findAll(data).map {

            val ai = it.groupValues[1]
            val value = it.groupValues[2]

            GS1ParsedResult(
                ai,
                value,
                aiDict[ai]?.first ?: "Unknown AI"
            )
        }.toList()
    }

    // =====================================================
    // PLAIN GS1 MODE — SWIFT COMPATIBLE
    // =====================================================

    /*private fun parsePlain(input: String): List<GS1ParsedResult> {

        val results = mutableListOf<GS1ParsedResult>()

        var index = 0
        val size = input.length
        val hasFNC1 = input.contains(FNC1)

        *//*fun looksLikeAI(pos: Int): String? {

            for (len in listOf(4, 3, 2)) {

                if (pos + len > size) continue

                val candidate = input.substring(pos, pos + len)

                if (aiDict.containsKey(candidate)) return candidate

                val num = candidate.toIntOrNull()
                if (len == 2 && num != null && num in 91..99) return candidate

                if (len == 4) {
                    val prefix3 = candidate.take(3).toIntOrNull()
                    if (prefix3 != null && prefix3 in 310..369) return candidate
                    if (prefix3 != null && prefix3 in 390..395) return candidate
                }
            }

            return null
        }*//*

        fun looksLikeAI(
            input: String,
            pos: Int,
            hasFNC1: Boolean
        ): String? {

            val size = input.length

            // ✅ STRICT RULE — Do not detect AI inside pure EAN/UPC
            val isPureGTIN =
                input.all { it.isDigit() } &&
                        (size == 8 || size == 12 || size == 13 || size == 14)

            if (isPureGTIN && pos == 0) {
                return null
            }

            for (len in listOf(4, 3, 2)) {

                if (pos + len > size) continue

                val candidate = input.substring(pos, pos + len)
                val meta = aiDict[candidate]

                // ------------------------------------------------
                // ✔ Exact AI dictionary match
                // ------------------------------------------------
                if (meta != null) {

                    // ⭐ STRICT: Company internal AIs need GS1 context
                    if (candidate in listOf(
                            "91","92","93","94","95","96","97","98","99"
                        ) && !hasFNC1 && pos == 0
                    ) {
                        return null
                    }

                    return candidate
                }

                // ------------------------------------------------
                // ✔ Measure AIs (310x, 390x etc.)
                // ------------------------------------------------
                if (len == 4) {

                    val prefix3 = candidate.take(3).toIntOrNull()

                    if (prefix3 != null && prefix3 in 310..369) return candidate
                    if (prefix3 != null && prefix3 in 390..395) return candidate
                }
            }

            return null
        }

        while (index < size) {

            // Skip any FNC1
            while (index < size && input[index] == FNC1) index++
            if (index >= size) break

            val ai = looksLikeAI(input, index, hasFNC1)?: run {
                index++
                continue
            }

            index += ai.length

            val meta = aiDict[ai]
            val fixedLen = meta?.second

            // =====================================
            // FIXED LENGTH AI (same as Swift)
            // =====================================
            if (fixedLen != null) {

                if (index + fixedLen > size) break

                val value = input.substring(index, index + fixedLen)

                results.add(
                    GS1ParsedResult(ai, value, meta.first)
                )

                index += fixedLen
            }
            // =====================================
            // VARIABLE LENGTH AI — STRICT BOUNDARY
            // =====================================
            else {

                val start = index

                while (index < size) {

                    if (hasFNC1 && input[index] == FNC1) break

                    // ⭐ STRICT SWIFT RULE
                    val nextAI = looksLikeAI(input, index, hasFNC1)
//                    val nextAI = looksLikeAI(index)
                    if (!hasFNC1 && nextAI != null && index > start) break

                    index++
                }

                val value = input.substring(start, index)

                results.add(
                    GS1ParsedResult(
                        ai,
                        value,
                        meta?.first ?: "AI $ai"
                    )
                )
            }
        }

        return results
    }*/

    private fun parsePlain(inputRaw: String): List<GS1ParsedResult> {

        val results = mutableListOf<GS1ParsedResult>()

        // ✅ Normalize input (important for Android/iOS FNC1 consistency)
        val input = inputRaw.replace('\u001D', FNC1)

        val size = input.length
        val hasFNC1 = input.contains(FNC1)

        // ---------------------------------------------------------
        // ✅ STRICT: If pure GTIN/EAN → do NOT treat as GS1 string
        // ---------------------------------------------------------
        val isPureGTIN =
            input.all { it.isDigit() } &&
                    (size == 8 || size == 12 || size == 13 || size == 14)

        if (isPureGTIN) {
            return emptyList()
        }

        var index = 0

        fun looksLikeAI(pos: Int): String? {

            for (len in listOf(4, 3, 2)) {

                if (pos + len > size) continue

                val candidate = input.substring(pos, pos + len)
                val meta = aiDict[candidate]

                if (meta != null) {

                    // ✅ STRICT: 91–99 only valid if GS1 structure exists
                    if (candidate in listOf(
                            "91","92","93","94","95","96","97","98","99"
                        )
                        && !hasFNC1
                        && pos == 0
                    ) {
                        return null
                    }

                    return candidate
                }

                // Measure AIs (310x, 390x etc.)
                if (len == 4) {

                    val prefix3 = candidate.take(3).toIntOrNull()

                    if (prefix3 != null && prefix3 in 310..369) return candidate
                    if (prefix3 != null && prefix3 in 390..395) return candidate
                }
            }

            return null
        }

        while (index < size) {

            // Skip FNC1 separators
            while (index < size && input[index] == FNC1) index++
            if (index >= size) break

            val ai = looksLikeAI(index) ?: run {
                index++
                continue
            }

            index += ai.length

            val meta = aiDict[ai]
            val fixedLen = meta?.second

            if (fixedLen != null) {

                // -------------------------------
                // ✅ Fixed Length AI
                // -------------------------------
                if (index + fixedLen > size) break

                val value = input.substring(index, index + fixedLen)

                results.add(
                    GS1ParsedResult(ai, value, meta.first)
                )

                index += fixedLen
            } else {

                // -------------------------------
                // ✅ Variable Length AI
                // -------------------------------
                val start = index

                while (index < size) {

                    if (input[index] == FNC1) break

                    val nextAI = looksLikeAI(index)
                    if (!hasFNC1 && nextAI != null && index > start) break

                    index++
                }

                val value = input.substring(start, index)

                results.add(
                    GS1ParsedResult(
                        ai,
                        value,
                        meta?.first ?: "AI $ai"
                    )
                )
            }
        }

        return results
    }
}