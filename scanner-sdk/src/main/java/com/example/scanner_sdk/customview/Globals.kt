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

fun parseGS1Data(data: String): List<GS1ParsedResult> {

    val isBracketed = data.contains("(")

    val aiDict = mapOf(
        "00" to Triple("SSCC", 18, false),
        "01" to Triple("GTIN", 14, false),
        "10" to Triple("Batch/Lot Number", null, true),
        "11" to Triple("Production Date", 6, false),
        "15" to Triple("Best Before Date", 6, false),
        "17" to Triple("Expiration Date", 6, false),
        "21" to Triple("Serial Number", null, true),
        "24" to Triple("Additional Product Identification", null, true),
        "240" to Triple("Additional Product Information", null, true),
        "422" to Triple("Country of Origin", 3, false),
        "98" to Triple("Internal / Encrypted Data", null, true)
    )

    fun shouldIgnoreAI(ai: String): Boolean = ai == "97"

    // =====================================================
    // ✅ 1️⃣ BRACKETED GS1 MODE
    // =====================================================
    if (isBracketed) {

        val regex = Regex("""\((\d{2,4})\)([^\(]+)""")
        val results = mutableListOf<GS1ParsedResult>()

        regex.findAll(data).forEach { match ->
            val ai = match.groupValues[1]
            val value = match.groupValues[2]

            if (shouldIgnoreAI(ai)) return@forEach

            val desc = aiDict[ai]?.first ?: "Unknown AI"

            results.add(
                GS1ParsedResult(
                    ai = ai,
                    value = value,
                    description = desc
                )
            )
        }

        return results
    }

    // =====================================================
    // ✅ 2️⃣ NON-BRACKETED GS1 MODE (FNC1 / PLAIN)
    // =====================================================

    val hasFNC1 = data.any { it.code == 29 }

    val input = data.filter {
        it.code == 29 || it.code >= 32
    }

    val results = mutableListOf<GS1ParsedResult>()
    var index = 0

    fun looksLikeAI(idx: Int): String? {
        for (len in listOf(4, 3, 2)) {
            if (idx + len <= input.length) {
                val ai = input.substring(idx, idx + len)
                if (aiDict.containsKey(ai)) return ai
            }
        }
        return null
    }

    while (index < input.length) {

        val ai = looksLikeAI(index) ?: break
        if (shouldIgnoreAI(ai)) break

        val meta = aiDict[ai] ?: break
        val fixedLen = meta.second
        val description = meta.first

        index += ai.length

        if (fixedLen != null) {
            val value = input.substring(index, index + fixedLen)
            results.add(
                GS1ParsedResult(ai, value, description)
            )
            index += fixedLen
        } else {
            val valueBuilder = StringBuilder()
            while (index < input.length) {

                if (hasFNC1 && input[index].code == 29) break
                if (!hasFNC1 && looksLikeAI(index) != null) break

                valueBuilder.append(input[index])
                index++
            }
            results.add(
                GS1ParsedResult(ai, valueBuilder.toString(), description)
            )
        }

        // Skip FNC1 separator
        if (index < input.length && input[index].code == 29) {
            index++
        }
    }

    return results
}

val aiMetadata: Map<String, Triple<String, Int?, Boolean>> = mapOf(
    "00" to Triple("SSCC", 18, false),
    "01" to Triple("GTIN", 14, false),
    "02" to Triple("GTIN of contained trade items", 14, false),
    "10" to Triple("Batch/Lot Number", null, true),
    "11" to Triple("Production Date", 6, false),
    "12" to Triple("Due Date", 6, false),
    "13" to Triple("Packaging Date", 6, false),
    "15" to Triple("Best Before Date", 6, false),
    "17" to Triple("Expiration Date", 6, false),
    "20" to Triple("Product Variant", 2, false),
    "21" to Triple("Serial Number", null, true),
    "22" to Triple("Secondary Data (health)", null, true),
    "23" to Triple("Lot Number 2", null, true),
    "240" to Triple("Additional Product ID", null, true),
    "241" to Triple("Customer Part Number", null, true),
    "242" to Triple("Made-to-Order Variation Number", null, true),
    "243" to Triple("Packaging Component Number", null, true),
    "250" to Triple("Secondary Serial Number", null, true),
    "251" to Triple("Reference to Source Entity", null, true),
    "253" to Triple("Global Document Type Identifier", null, true),
    "254" to Triple("GLN Extension Component", null, true),
    "255" to Triple("Global Coupon Number", null, true),
    "30" to Triple("Variable Count", null, true),
    "37" to Triple("Count of trade items", null, true),
    "310" to Triple("Net weight (kg)", 6, false),
    "311" to Triple("Length (m)", 6, false),
    "312" to Triple("Width/Diameter (m)", 6, false),
    "313" to Triple("Depth/Thickness (m)", 6, false),
    "314" to Triple("Area (m²)", 6, false),
    "315" to Triple("Net volume (L)", 6, false),
    "316" to Triple("Net volume (m³)", 6, false),
    "320" to Triple("Net weight (lb)", 6, false),
    "321" to Triple("Length (in)", 6, false),
    "322" to Triple("Length (ft)", 6, false),
    "323" to Triple("Length (yd)", 6, false),
    "390" to Triple("Amount payable (local)", null, true),
    "391" to Triple("Amount payable (with ISO code)", null, true),
    "392" to Triple("Amount payable per item (local)", null, true),
    "393" to Triple("Amount payable per item (with ISO code)", null, true),
    "400" to Triple("Customer Purchase Order Number", null, true),
    "401" to Triple("Consignment Number", null, true),
    "402" to Triple("Bill of Lading Number", 17, false),
    "403" to Triple("Routing Code", null, true),
    "410" to Triple("Ship To Location (GLN)", 13, false),
    "411" to Triple("Bill To Location (GLN)", 13, false),
    "412" to Triple("Purchase From Location (GLN)", 13, false),
    "413" to Triple("Ship For Location (GLN)", 13, false),
    "414" to Triple("Physical Location (GLN)", 13, false),
    "420" to Triple("Ship To Postal Code", null, true),
    "421" to Triple("Ship To Postal Code + Country", null, true),
    "422" to Triple("Country of Origin", 3, false),
    "423" to Triple("Country of Processing", null, true),
    "424" to Triple("Country of Processing", 3, false),
    "425" to Triple("Country of Disassembly", 3, false),
    "426" to Triple("Country of Full Process Chain", 3, false),
    "7001" to Triple("NATO Stock Number (NSN)", 13, false),
    "7002" to Triple("Meat Cut Classification", null, true),
    "7003" to Triple("Expiration Date & Time", 10, false),
    "7004" to Triple("Active Potency", null, true),
    "8001" to Triple("Roll product details", 14, false),
    "8002" to Triple("Mobile phone identifier", null, true),
    "8003" to Triple("Returnable Asset ID", null, true),
    "8004" to Triple("Individual Asset ID", null, true),
    "8005" to Triple("Price per Unit of Measure", 6, false),
    "8006" to Triple("Component Item", 18, false),
    "8007" to Triple("International Bank Account Number", null, true),
    "8008" to Triple("Date/Time of Production", null, true),
    "8012" to Triple("Software Version", null, true),
    "8013" to Triple("Global Model Number (GMN)", null, true),
    "8017" to Triple("GSRN – Provider", null, true),
    "8018" to Triple("GSRN – Recipient", null, true),
    "8019" to Triple("Service Relation Instance Number", null, true),
    "8020" to Triple("Payment Slip Reference Number", null, true),
    "8100" to Triple("Coupon Extended Code NS+Offer", 6, false),
    "8101" to Triple("Coupon Extended Code NS+Offer+End", 10, false),
    "8102" to Triple("Coupon Extended Code NS padded", 2, false),
    "8110" to Triple("Coupon Code ID (NA)", null, true),
    "8111" to Triple("Coupon Loyalty Points", 4, false),
    "8112" to Triple("Paperless Coupon Code ID (NA)", null, true),
    "8200" to Triple("Extended Packaging URL", null, true),
    "90" to Triple("Internal Data (mutually agreed)", null, true),
    "91" to Triple("Company Internal", null, true),
    "92" to Triple("Company Internal", null, true),
    "93" to Triple("Company Internal", null, true),
    "94" to Triple("Company Internal", null, true),
    "95" to Triple("Company Internal", null, true),
    "96" to Triple("Company Internal", null, true),
    "97" to Triple("Company Internal", null, true),
    "98" to Triple("Authentication", null, true),
    "99" to Triple("Company Internal", null, true)
)

fun parseBarcodeLikeMultiScanForAuth(value: String, type: String): ParsedAuthBarcode {

    val splitted = GS1Utils.splitByAI98(value)

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

fun parseGS1Temp(data: String): List<GS1ParsedResult> {

    val isBracketed = data.contains("(")

    val aiDict = mapOf(
        "00" to Triple("SSCC", 18, false),
        "01" to Triple("GTIN", 14, false),
        "02" to Triple("Contained GTIN", 14, false),
        "10" to Triple("Batch/Lot Number", null, true),
        "11" to Triple("Production Date", 6, false),
        "12" to Triple("Due Date", 6, false),
        "13" to Triple("Packaging Date", 6, false),
        "15" to Triple("Best Before Date", 6, false),
        "16" to Triple("Sell By Date", 6, false),
        "17" to Triple("Expiration Date", 6, false),
        "20" to Triple("Internal Product Variant", 2, false),
        "21" to Triple("Serial Number", null, true),
        "22" to Triple("Consumer Product Variant", null, true),
        "30" to Triple("Variable Count", null, true),
        "37" to Triple("Count of Trade Items", null, true),

        "410" to Triple("Ship To GLN", 13, false),
        "411" to Triple("Bill To GLN", 13, false),
        "412" to Triple("Purchased From GLN", 13, false),
        "413" to Triple("Ship For GLN", 13, false),
        "414" to Triple("Physical Location GLN", 13, false),
        "415" to Triple("Invoicing Party GLN", 13, false),
        "416" to Triple("Production Location GLN", 13, false),
        "417" to Triple("Party GLN", 13, false),

        "420" to Triple("Postal Code", null, true),
        "421" to Triple("Postal Code + ISO Country", null, true),

        "8004" to Triple("GIAI", null, true),
        "8012" to Triple("Software Version", null, true),

        "91" to Triple("Company Internal 91", null, true),
        "92" to Triple("Company Internal 92", null, true),
        "93" to Triple("Company Internal 93", null, true),
        "94" to Triple("Company Internal 94", null, true),
        "95" to Triple("Company Internal 95", null, true),
        "96" to Triple("Company Internal 96", null, true),
        "97" to Triple("Company Internal 97", null, true),
        "98" to Triple("Company Internal 98", null, true),
        "99" to Triple("Company Internal 99", null, true),
    )

    fun shouldIgnoreAI(ai: String) = ai == "97"

    // =====================================================
    // ✅ 1️⃣ BRACKETED MODE
    // =====================================================
    if (isBracketed) {

        val regex = Regex("""\((\d{2,4})\)([^\(]+)""")

        val results = mutableListOf<GS1ParsedResult>()

        regex.findAll(data).forEach { match ->

            val ai = match.groupValues[1]
            val value = match.groupValues[2]

            if (shouldIgnoreAI(ai)) return@forEach

            val desc = aiDict[ai]?.first ?: "Unknown AI"

            results.add(GS1ParsedResult(ai, value, desc))
        }

        return results
    }

    // =====================================================
    // ✅ 2️⃣ NON-BRACKETED MODE
    // =====================================================

    val hasFNC1 = data.any { it.code == 29 }

    val input = buildString {
        data.forEach {
            if (it.code == 29 || it.code >= 32) append(it)
        }
    }

    val results = mutableListOf<GS1ParsedResult>()
    var index = 0

    fun looksLikeAI(idx: Int): String? {

        val measurementPrefixes = setOf(
            "310","311","312","313","314","315","316",
            "320","321","322","323","324","325","326",
            "327","328","329",
            "330","331","332","333","334","335","336",
            "337",
            "340","341","342","343","344","345","346",
            "347","348","349",
            "350","351","352","353","354","355","356",
            "357",
            "360","361","362","363","364","365","366",
            "367","368","369"
        )

        val monetaryPrefixes = setOf("390","391","392","393","394","395")

        for (len in listOf(4,3,2)) {

            if (idx + len > input.length) continue

            val candidate = input.substring(idx, idx + len)

            if (aiDict.containsKey(candidate)) return candidate

            if (len == 2 && candidate.toIntOrNull() in 91..99) {
                return candidate
            }

            if (len == 4) {

                val prefix3 = candidate.take(3)
                val lastDigit = candidate.last()

                if (lastDigit.isDigit()) {

                    if (measurementPrefixes.contains(prefix3)) {
                        return candidate
                    }

                    if (monetaryPrefixes.contains(prefix3)) {
                        return candidate
                    }

                    if (prefix3 == "703" || prefix3 == "723") {
                        return candidate
                    }
                }
            }
        }

        return null
    }
// ⭐ VERY IMPORTANT FIX
    while (index < input.length && input[index].code == 29) {
        index++
    }
    while (index < input.length) {

        val ai = looksLikeAI(index) ?: break
        if (shouldIgnoreAI(ai)) break

        index += ai.length

        val prefix3 = ai.take(3).toIntOrNull()

        // ==========================================
        // Measurement AIs
        // ==========================================
        if (ai.length == 4 && prefix3 != null && prefix3 in 310..369) {

            if (index + 6 > input.length) break

            val value = input.substring(index, index + 6)
            results.add(GS1ParsedResult(ai, value, "Measurement"))

            index += 6
            continue
        }

        // ==========================================
        // Monetary AIs
        // ==========================================
        if (ai.length == 4 && prefix3 != null && prefix3 in 390..395) {

            val sb = StringBuilder()

            while (index < input.length) {

                val ch = input[index]

                if (hasFNC1 && ch.code == 29) break
                if (!hasFNC1 && looksLikeAI(index) != null) break

                sb.append(ch)
                index++
            }

            results.add(GS1ParsedResult(ai, sb.toString(), "Monetary Amount"))
            continue
        }

        // ==========================================
        // Normal dictionary AIs
        // ==========================================
        val meta = aiDict[ai]

        if (meta != null) {

            val name = meta.first
            val fixedLen = meta.second

            if (fixedLen != null) {

                if (index + fixedLen > input.length) break

                val value = input.substring(index, index + fixedLen)
                results.add(GS1ParsedResult(ai, value, name))

                index += fixedLen

            } else {

                val sb = StringBuilder()

                while (index < input.length) {

                    val ch = input[index]

                    if (hasFNC1 && ch.code == 29) break
                    if (!hasFNC1 && looksLikeAI(index) != null) break

                    sb.append(ch)
                    index++
                }

                results.add(GS1ParsedResult(ai, sb.toString(), name))
            }
        }

        // Skip FNC1
        if (index < input.length && input[index].code == 29) {
            index++
        }
    }

    return results
}

object GS1ParserTest {

    private const val FNC1 = 29.toChar()

    // =====================================================
    // AI DICTIONARY (YOUR ORIGINAL STYLE)
    // =====================================================

    private val aiDict = mapOf(
        "00" to Triple("SSCC", 18, false),
        "01" to Triple("GTIN", 14, false),
        "02" to Triple("Contained GTIN", 14, false), // ⭐ IMPORTANT ADD
        "10" to Triple("Batch/Lot Number", null, true),
        "11" to Triple("Production Date", 6, false),
        "15" to Triple("Best Before Date", 6, false),
        "17" to Triple("Expiration Date", 6, false),
        "20" to Triple("Internal Product Variant", 2, false),
        "21" to Triple("Serial Number", null, true),
        "30" to Triple("Variable Count", null, true),
        "37" to Triple("Count of Trade Items", null, true),
        "410" to Triple("Ship To GLN", 13, false),
        "411" to Triple("Bill To GLN", 13, false),
        "414" to Triple("Physical Location GLN", 13, false),
        "420" to Triple("Postal Code", null, true),
        "421" to Triple("Postal Code + ISO Country", null, true),
        "91" to Triple("Company Internal 91", null, true),
        "92" to Triple("Company Internal 92", null, true),
        "93" to Triple("Company Internal 93", null, true),
        "94" to Triple("Company Internal 94", null, true),
        "95" to Triple("Company Internal 95", null, true),
        "96" to Triple("Company Internal 96", null, true),
        "97" to Triple("Company Internal 97", null, true),
        "98" to Triple("Company Internal 98", null, true),
        "99" to Triple("Company Internal 99", null, true)
    )

    private val measurementPrefixes = (310..369).map { it.toString() }.toSet()
    private val monetaryPrefixes = setOf("390","391","392","393","394","395")

    private fun shouldIgnoreAI(ai: String) = ai == "97"

    // =====================================================
    // PUBLIC ENTRY
    // =====================================================

    fun parseGS1(rawData: String): List<GS1ParsedResult> {

        if (rawData.isEmpty()) return emptyList()

        val cleaned = preprocess(rawData)

        return if (cleaned.contains("(")) {
            parseBracketed(cleaned)
        } else {
            parsePlain(cleaned)
        }
    }

    // =====================================================
    // 🔥 INDUSTRIAL PREPROCESS (MAJOR FIXES)
    // =====================================================

    private fun preprocess(data: String): String {

        var input = data

        // ✅ Remove Symbology Identifier (]C1 ]d2 ]Q3 etc)
        if (input.startsWith("]") && input.length > 3) {
            input = input.substring(3)
        }

        // ✅ Remove weird leading control chars except FNC1
        val sb = StringBuilder()

        input.forEach {
            if (it == FNC1 || it.code >= 32) {
                sb.append(it)
            }
        }

        return sb.toString()
    }

    // =====================================================
    // BRACKETED MODE (UNCHANGED)
    // =====================================================

    private fun parseBracketed(data: String): List<GS1ParsedResult> {

        val regex = Regex("""\((\d{2,4})\)([^\(]+)""")

        val results = mutableListOf<GS1ParsedResult>()

        regex.findAll(data).forEach {

            val ai = it.groupValues[1]
            val value = it.groupValues[2]

            if (shouldIgnoreAI(ai)) return@forEach

            val desc = aiDict[ai]?.first ?: "Unknown AI"

            results.add(GS1ParsedResult(ai, value, desc))
        }

        return results
    }

    // =====================================================
    // 🔥 FULLY HARDENED PLAIN MODE
    // =====================================================

    private fun parsePlain(input: String): List<GS1ParsedResult> {

        val results = mutableListOf<GS1ParsedResult>()

        var index = 0
        val hasFNC1 = input.contains(FNC1)

        fun looksLikeAI(idx: Int): String? {

            // ⭐ Skip FNC1 BEFORE detecting AI
            var i = idx
            while (i < input.length && input[i] == FNC1) i++

            for (len in listOf(4,3,2)) {

                if (i + len > input.length) continue

                val candidate = input.substring(i, i + len)

                if (aiDict.containsKey(candidate)) return candidate

                val num = candidate.toIntOrNull()

                if (len == 2 && num != null && num in 91..99) {
                    return candidate
                }

                if (len == 4) {

                    val prefix3 = candidate.take(3)
                    val last = candidate.last()

                    if (!last.isDigit()) continue

                    if (measurementPrefixes.contains(prefix3)) return candidate
                    if (monetaryPrefixes.contains(prefix3)) return candidate
                    if (prefix3 == "703" || prefix3 == "723") return candidate
                }
            }

            return null
        }

        while (index < input.length) {

            // ⭐ Skip ANY FNC1 anywhere
            while (index < input.length && input[index] == FNC1) {
                index++
            }

            if (index >= input.length) break

            val ai = looksLikeAI(index) ?: run {
                // ⭐ NEW: Skip unknown characters instead of breaking
                index++
                continue
            }

            if (shouldIgnoreAI(ai)) {
                index += ai.length
                continue
            }

            index += ai.length

            val prefix3 = ai.take(3).toIntOrNull()

            // ===============================
            // Measurement AI
            // ===============================
            if (ai.length == 4 && prefix3 != null && prefix3 in 310..369) {

                if (index + 6 > input.length) break

                val value = input.substring(index, index + 6)
                results.add(GS1ParsedResult(ai, value, "Measurement"))

                index += 6
                continue
            }

            // ===============================
            // Monetary AI
            // ===============================
            if (ai.length == 4 && prefix3 != null && prefix3 in 390..395) {

                val sb = StringBuilder()

                while (index < input.length) {

                    val ch = input[index]

                    if (hasFNC1 && ch == FNC1) break
                    if (!hasFNC1 && looksLikeAI(index) != null) break

                    sb.append(ch)
                    index++
                }

                results.add(GS1ParsedResult(ai, sb.toString(), "Monetary Amount"))
                continue
            }

            // ===============================
            // Normal AIs
            // ===============================
            val meta = aiDict[ai]

            if (meta != null) {

                val name = meta.first
                val fixedLen = meta.second

                if (fixedLen != null) {

                    if (index + fixedLen > input.length) break

                    val value = input.substring(index, index + fixedLen)

                    results.add(GS1ParsedResult(ai, value, name))

                    index += fixedLen

                } else {

                    val sb = StringBuilder()

                    while (index < input.length) {

                        val ch = input[index]

                        if (hasFNC1 && ch == FNC1) break
                        if (!hasFNC1 && looksLikeAI(index) != null) break

                        sb.append(ch)
                        index++
                    }

                    results.add(GS1ParsedResult(ai, sb.toString(), name))
                }
            }
        }

        return results
    }
}

object GS1ParserV2 {

    private const val FNC1 = 29.toChar()

    private val aiDict = mapOf(
        "00" to Pair("SSCC",18),
        "01" to Pair("GTIN",14),
        "02" to Pair("Contained GTIN",14),
        "10" to Pair("Batch/Lot Number",null),
        "11" to Pair("Production Date",6),
        "15" to Pair("Best Before Date",6),
        "17" to Pair("Expiration Date",6),
        "20" to Pair("Internal Product Variant",2),
        "21" to Pair("Serial Number",null),
        "30" to Pair("Variable Count",null),
        "37" to Pair("Count of Trade Items",null),
        "410" to Pair("Ship To GLN",13),
        "411" to Pair("Bill To GLN",13),
        "414" to Pair("Physical Location GLN",13),
        "420" to Pair("Postal Code",null),
        "421" to Pair("Postal Code + ISO Country",null)
    )

    private val measurementRange = 310..369
    private val monetaryRange = 390..395

    fun parse(raw: String): List<GS1ParsedResult> {

        if (raw.isEmpty()) return emptyList()

        val input = preprocess(raw).toCharArray()

        val results = ArrayList<GS1ParsedResult>(6)

        var i = 0
        val size = input.size

        while (i < size) {

            // ⭐ skip any FNC1 quickly
            while (i < size && input[i] == FNC1) i++
            if (i >= size) break

            val aiStart = i

            val ai = detectAI(input, i, size) ?: run {
                i++
                continue
            }

            i += ai.length

            val meta = aiDict[ai]
            val prefix3 = ai.take(3).toIntOrNull()

            // ======================================
            // Measurement AI 310n–369n
            // ======================================
            if (ai.length == 4 && prefix3 != null && prefix3 in measurementRange) {

                if (i + 6 > size) break

                val value = String(input, i, 6)
                results.add(GS1ParsedResult(ai, value, "Measurement"))

                i += 6
                continue
            }

            // ======================================
            // Monetary AI 390n–395n
            // ======================================
            if (ai.length == 4 && prefix3 != null && prefix3 in monetaryRange) {

                val start = i

                while (i < size && input[i] != FNC1) {
                    i++
                }

                val value = String(input, start, i - start)
                results.add(GS1ParsedResult(ai, value, "Monetary Amount"))

                continue
            }

            // ======================================
            // NORMAL AI
            // ======================================
            if (meta != null) {

                val (desc, fixedLen) = meta

                if (fixedLen != null) {

                    if (i + fixedLen > size) break

                    val value = String(input, i, fixedLen)
                    results.add(GS1ParsedResult(ai, value, desc))

                    i += fixedLen

                } else {

                    val start = i

                    while (i < size && input[i] != FNC1) {
                        // stop if next AI detected
                        if (detectAI(input, i, size) != null) break
                        i++
                    }

                    val value = String(input, start, i - start)
                    results.add(GS1ParsedResult(ai, value, desc))
                }
            }
        }

        return results
    }

    // =====================================================
    // 🔥 SUPER FAST AI DETECTOR (NO substring)
    // =====================================================

    private fun detectAI(chars: CharArray, idx: Int, size: Int): String? {

        fun read(len: Int): String? {
            if (idx + len > size) return null
            for (k in 0 until len) {
                if (!chars[idx + k].isDigit()) return null
            }
            return String(chars, idx, len)
        }

        // try 4-digit AI
        read(4)?.let { ai ->
            val prefix3 = ai.substring(0,3).toIntOrNull()
            if (aiDict.containsKey(ai)) return ai
            if (prefix3 != null && (prefix3 in measurementRange || prefix3 in monetaryRange)) return ai
        }

        // try 3-digit
        read(3)?.let {
            if (aiDict.containsKey(it)) return it
        }

        // try 2-digit
        read(2)?.let {
            if (aiDict.containsKey(it)) return it
            val num = it.toIntOrNull()
            if (num != null && num in 91..99) return it
        }

        return null
    }

    // =====================================================
    // PREPROCESS RAW SCANNER DATA
    // =====================================================

    private fun preprocess(data: String): String {

        var input = data

        // remove ]C1 ]d2 etc
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
}

object GS1ParserV3 {

    private const val FNC1 = 29.toChar()

    private val aiDict = mapOf(
        "00" to Pair("SSCC",18),
        "01" to Pair("GTIN",14),
        "02" to Pair("Contained GTIN",14),
        "10" to Pair("Batch/Lot Number",null),
        "11" to Pair("Production Date",6),
        "15" to Pair("Best Before Date",6),
        "17" to Pair("Expiration Date",6),
        "20" to Pair("Internal Product Variant",2),
        "21" to Pair("Serial Number",null),
        "30" to Pair("Variable Count",null),
        "37" to Pair("Count of Trade Items",null),
        "410" to Pair("Ship To GLN",13),
        "411" to Pair("Bill To GLN",13),
        "414" to Pair("Physical Location GLN",13),
        "420" to Pair("Postal Code",null),
        "421" to Pair("Postal Code + ISO Country",null)
    )

    private val measurementRange = 310..369
    private val monetaryRange = 390..395

    // =====================================================
    // PUBLIC ENTRY
    // =====================================================

    fun parse(raw: String): List<GS1ParsedResult> {

        if (raw.isEmpty()) return emptyList()

        val input = preprocess(raw).toCharArray()
        val results = ArrayList<GS1ParsedResult>(8)

        var i = 0
        val size = input.size

        while (i < size) {

            // Skip any FNC1 safely
            while (i < size && input[i] == FNC1) i++
            if (i >= size) break

            val ai = detectAI(input, i, size)

            // ⭐ STREAM SAFE: never break
            if (ai == null) {
                i++
                continue
            }

            val aiStart = i
            i += ai.length

            val prefix3 = ai.take(3).toIntOrNull()
            val meta = aiDict[ai]

            // ======================================
            // Measurement AI
            // ======================================
            if (ai.length == 4 && prefix3 != null && prefix3 in measurementRange) {

                if (i + 6 <= size) {
                    val value = String(input, i, 6)
                    results.add(GS1ParsedResult(ai, value, "Measurement"))
                    i += 6
                }

                continue
            }

            // ======================================
            // Monetary AI
            // ======================================
            if (ai.length == 4 && prefix3 != null && prefix3 in monetaryRange) {

                val start = i

                while (i < size && input[i] != FNC1) i++

                val value = String(input, start, i - start)
                results.add(GS1ParsedResult(ai, value, "Monetary Amount"))
                continue
            }

            // ======================================
            // NORMAL AI
            // ======================================
            if (meta != null) {

                val (desc, fixedLen) = meta

                if (fixedLen != null) {

                    if (i + fixedLen <= size) {

                        val value = String(input, i, fixedLen)
                        results.add(GS1ParsedResult(ai, value, desc))

                        i += fixedLen
                    } else {
                        // incomplete value — skip safely
                        i = aiStart + 1
                    }

                } else {

                    val start = i

                    while (i < size) {

                        if (input[i] == FNC1) break

                        // detect next AI safely
                        val nextAI = detectAI(input, i, size)
                        if (nextAI != null) break

                        i++
                    }

                    val value = String(input, start, i - start)
                    results.add(GS1ParsedResult(ai, value, desc))
                }
            } else {
                // unknown AI → continue scanning
                i = aiStart + 1
            }
        }

        return results
    }

    // =====================================================
    // SUPER FAST AI DETECTOR (STREAM SAFE)
    // =====================================================

    private fun detectAI(chars: CharArray, idx: Int, size: Int): String? {

        fun read(len: Int): String? {
            if (idx + len > size) return null
            for (k in 0 until len) {
                if (!chars[idx + k].isDigit()) return null
            }
            return String(chars, idx, len)
        }

        read(4)?.let { ai ->
            val prefix3 = ai.substring(0,3).toIntOrNull()
            if (aiDict.containsKey(ai)) return ai
            if (prefix3 != null && (prefix3 in measurementRange || prefix3 in monetaryRange)) return ai
        }

        read(3)?.let {
            if (aiDict.containsKey(it)) return it
        }

        read(2)?.let {
            if (aiDict.containsKey(it)) return it
            val num = it.toIntOrNull()
            if (num != null && num in 91..99) return it
        }

        return null
    }

    // =====================================================
    // PREPROCESS RAW SCANNER DATA
    // =====================================================

    private fun preprocess(data: String): String {

        var input = data

        // Remove symbology identifier ]C1 ]d2 ]Q3
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
}

object GS1Parser {

    private const val FNC1 = 29.toChar()

    private val aiDict = mapOf(

        // Logistic / Trade Identification
        "00" to Pair("SSCC",18),
        "01" to Pair("GTIN",14),
        "02" to Pair("Contained GTIN",14),
        "10" to Pair("Batch/Lot Number",null),
        "11" to Pair("Production Date",6),
        "12" to Pair("Due Date",6),
        "13" to Pair("Packaging Date",6),
        "15" to Pair("Best Before Date",6),
        "16" to Pair("Sell By Date",6),
        "17" to Pair("Expiration Date",6),
        "20" to Pair("Internal Product Variant",2),
        "21" to Pair("Serial Number",null),
        "22" to Pair("Consumer Product Variant",null),
        "30" to Pair("Variable Count",null),
        "37" to Pair("Count of Trade Items",null),

        // Additional Identification
        "235" to Pair("TPX – Third Party GTIN Extension",null),
        "240" to Pair("Additional Product Identification",null),
        "241" to Pair("Customer Part Number",null),
        "242" to Pair("Made-to-Order Variation",null),
        "243" to Pair("Packaging Component Number",null),
        "250" to Pair("Secondary Serial Number",null),
        "251" to Pair("Reference to Source Entity",null),
        "253" to Pair("Global Document Type Identifier (GDTI)",null),
        "254" to Pair("GLN Extension Component",null),
        "255" to Pair("Global Coupon Number (GCN)",null),

        // Shipment / Consignment
        "400" to Pair("Customer PO Number",null),
        "401" to Pair("GINC",null),
        "402" to Pair("GSIN",17),
        "403" to Pair("Routing Code",null),

        // GLN
        "410" to Pair("Ship To GLN",13),
        "411" to Pair("Bill To GLN",13),
        "412" to Pair("Purchased From GLN",13),
        "413" to Pair("Ship For GLN",13),
        "414" to Pair("Physical Location GLN",13),
        "415" to Pair("Invoicing Party GLN",13),
        "416" to Pair("Production Location GLN",13),
        "417" to Pair("Party GLN",13),

        // Country / Postal
        "420" to Pair("Postal Code",null),
        "421" to Pair("Postal Code + ISO Country",null),
        "422" to Pair("Country of Origin",3),
        "423" to Pair("Country of Initial Processing",null),
        "424" to Pair("Country of Processing",3),
        "425" to Pair("Country of Disassembly",null),
        "426" to Pair("Full Process Chain Country",3),
        "427" to Pair("Country Subdivision",null),

        // Temperature
        "4330" to Pair("Max Temp Fahrenheit",6),
        "4331" to Pair("Max Temp Celsius",6),
        "4332" to Pair("Min Temp Fahrenheit",6),
        "4333" to Pair("Min Temp Celsius",6),

        // Asset Identification
        "8001" to Pair("Roll Product Info",14),
        "8002" to Pair("Mobile Identifier",null),
        "8003" to Pair("GRAI",null),
        "8004" to Pair("GIAI",null),
        "8005" to Pair("Price Per Unit",6),
        "8006" to Pair("ITIP Piece",18),
        "8007" to Pair("IBAN",null),
        "8008" to Pair("Production Date & Time",null),
        "8009" to Pair("Sensor Indicator",null),
        "8010" to Pair("Component Part ID",null),
        "8011" to Pair("Component Serial",null),
        "8012" to Pair("Software Version",null),
        "8013" to Pair("Global Model Number",null),
        "8017" to Pair("GSRN Provider",18),
        "8018" to Pair("GSRN Recipient",18),
        "8019" to Pair("Service Relation Instance",null),
        "8020" to Pair("Payment Slip Reference",null),
        "8026" to Pair("ITIP Contained Pieces",18),
        "8030" to Pair("Digital Signature",null),
        "8200" to Pair("Extended Packaging URL",null),

        // Healthcare
        "7001" to Pair("NATO Stock Number",13),
        "7002" to Pair("UNECE Meat Classification",null),
        "7003" to Pair("Expiration Date & Time",10),
        "7004" to Pair("Active Potency",null),
        "7005" to Pair("Catch Area",null),
        "7006" to Pair("First Freeze Date",6),
        "7007" to Pair("Harvest Date",null),
        "7008" to Pair("Species Code",null),
        "7009" to Pair("Fishing Gear Type",null),
        "7010" to Pair("Production Method",null),
        "7011" to Pair("Test By Date",null),
        "7020" to Pair("Refurbishment Lot ID",null),
        "7021" to Pair("Functional Status",null),
        "7022" to Pair("Revision Status",null),
        "7023" to Pair("GIAI Assembly",null),

        // Company Internal
        "91" to Pair("Company Internal 91",null),
        "92" to Pair("Company Internal 92",null),
        "93" to Pair("Company Internal 93",null),
        "94" to Pair("Company Internal 94",null),
        "95" to Pair("Company Internal 95",null),
        "96" to Pair("Company Internal 96",null),
        "97" to Pair("Company Internal 97",null),
        "98" to Pair("Company Internal 98",null),
        "99" to Pair("Company Internal 99",null)
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

    private fun parsePlain(input: String): List<GS1ParsedResult> {

        val results = mutableListOf<GS1ParsedResult>()

        var index = 0
        val size = input.length
        val hasFNC1 = input.contains(FNC1)

        fun looksLikeAI(pos: Int): String? {

            for (len in listOf(4,3,2)) {

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
        }

        while (index < size) {

            // Skip any FNC1
            while (index < size && input[index] == FNC1) index++
            if (index >= size) break

            val ai = looksLikeAI(index) ?: run {
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

object GS1AutoParser {

    private const val FNC1 = 29.toChar()

    // =====================================================
    // PUBLIC ENTRY
    // =====================================================

    fun parse(raw: String): List<GS1ParsedResult> {

        if (raw.isEmpty()) return emptyList()

        val cleaned = preprocess(raw)

        return if (cleaned.contains("(")) {
            parseBracketed(cleaned)
        } else {
            parsePlain(cleaned)
        }
    }

    // =====================================================
    // PREPROCESSING
    // =====================================================

    private fun preprocess(data: String): String {

        var input = data

        // Remove symbology identifier ]C1 ]d2 ]Q3 etc
        if (input.startsWith("]") && input.length > 3) {
            input = input.substring(3)
        }

        val sb = StringBuilder()

        input.forEach {
            if (it == FNC1 || it.code >= 32) {
                sb.append(it)
            }
        }

        return sb.toString()
    }

    // =====================================================
    // BRACKETED MODE (AUTO)
    // =====================================================

    private fun parseBracketed(data: String): List<GS1ParsedResult> {

        val regex = Regex("""\((\d{2,4})\)([^\(]+)""")
        val results = mutableListOf<GS1ParsedResult>()

        regex.findAll(data).forEach {

            val ai = it.groupValues[1]
            val value = it.groupValues[2]

            results.add(
                GS1ParsedResult(
                    ai = ai,
                    value = value,
                    description = autoDescription(ai)
                )
            )
        }

        return results
    }

    // =====================================================
    // AUTO AI DETECTOR
    // =====================================================

    private fun detectAI(input: String, index: Int): String? {

        // Try longest first (GS1 rule)
        for (len in listOf(4,3,2)) {

            if (index + len > input.length) continue

            val ai = input.substring(index, index + len)

            if (!ai.all { it.isDigit() }) continue

            val prefix3 = ai.take(3).toIntOrNull()

            // Measurement 310n–369n
            if (len == 4 && prefix3 != null && prefix3 in 310..369) return ai

            // Monetary 390n–395n
            if (len == 4 && prefix3 != null && prefix3 in 390..395) return ai

            // General GS1 AI rule:
            // valid if starts with 0–9 and length 2–4
            if (len >= 2) return ai
        }

        return null
    }

    // =====================================================
    // FIXED LENGTH RULES (GS1 SPEC)
    // =====================================================

    private fun fixedLength(ai: String): Int? {

        return when (ai) {

            "00" -> 18
            "01","02" -> 14
            "11","12","13","15","16","17" -> 6
            "20" -> 2
            "410","411","412","413","414","415","416","417" -> 13
            "422","424","426" -> 3
            "7001" -> 13

            else -> {

                val prefix3 = ai.take(3).toIntOrNull()

                if (ai.length == 4 && prefix3 != null && prefix3 in 310..369) {
                    6
                } else null
            }
        }
    }

    // =====================================================
    // MAIN PLAIN PARSER
    // =====================================================

    private fun parsePlain(input: String): List<GS1ParsedResult> {

        val results = mutableListOf<GS1ParsedResult>()

        var index = 0
        val hasFNC1 = input.contains(FNC1)

        while (index < input.length) {

            // Skip any FNC1
            while (index < input.length && input[index] == FNC1) {
                index++
            }

            if (index >= input.length) break

            val ai = detectAI(input, index) ?: break

            index += ai.length

            val fixedLen = fixedLength(ai)

            val value: String

            if (fixedLen != null) {

                if (index + fixedLen > input.length) break

                value = input.substring(index, index + fixedLen)
                index += fixedLen

            } else {

                val sb = StringBuilder()

                while (index < input.length) {

                    val ch = input[index]

                    if (hasFNC1 && ch == FNC1) break
                    if (!hasFNC1 && detectAI(input, index) != null) break

                    sb.append(ch)
                    index++
                }

                value = sb.toString()
            }

            results.add(
                GS1ParsedResult(
                    ai = ai,
                    value = value,
                    description = autoDescription(ai)
                )
            )
        }

        return results
    }

    // =====================================================
    // AUTO DESCRIPTION (OPTIONAL)
    // =====================================================

    private fun autoDescription(ai: String): String {

        val prefix3 = ai.take(3).toIntOrNull()

        return when {

            ai == "01" -> "GTIN"
            ai == "02" -> "Contained GTIN"
            ai == "10" -> "Batch/Lot"
            ai == "17" -> "Expiration Date"
            ai == "21" -> "Serial Number"
            ai == "37" -> "Count"

            ai.length == 4 && prefix3 != null && prefix3 in 310..369 ->
                "Measurement"

            ai.length == 4 && prefix3 != null && prefix3 in 390..395 ->
                "Monetary Amount"

            else -> "AI $ai"
        }
    }
}