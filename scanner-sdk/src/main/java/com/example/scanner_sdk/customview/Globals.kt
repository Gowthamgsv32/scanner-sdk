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
    val parsedGS1Results = parseGS1(value)

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

fun parseGS1(data: String): List<GS1ParsedResult> {

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

fun parseBarcodeLikeMultiScanForAuth(value: String): ParsedAuthBarcode {

    val parseGS1Results = parseGS1(value)

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
                companyId = splitted.companyId
            )
        } else {
            // ✅ Valid GS1 Digital Link, but NOT system-generated
            ParsedAuthBarcode(
                parsedResults = normalizedParsed,
                barcodeData = "",
                encryptedText = "",
                isGeneratedBySystem = false,
                companyId = ""
            )
        }
    }

    // ---------- NORMAL GS1 ----------
    val splitted = GS1Utils.splitByAI98(
        parsing.originalWithout98
    )

    if (splitted != null) {
        return ParsedAuthBarcode(
            parsedResults = parseGS1Results,
            barcodeData = splitted.barcodeData,
            encryptedText = splitted.encryptedText,
            isGeneratedBySystem = true,
            companyId = splitted.companyId
        )
    }

    // ---------- PLAIN GS1 ----------
    return ParsedAuthBarcode(
        parsedResults = parseGS1Results,
        barcodeData = "",
        encryptedText = "",
        isGeneratedBySystem = false,
        companyId = ""
    )
}

