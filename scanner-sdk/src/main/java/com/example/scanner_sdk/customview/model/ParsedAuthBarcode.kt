package com.example.scanner_sdk.customview.model

data class ParsedAuthBarcode(
    val parsedResults: List<GS1ParsedResult>,
    val barcodeData: String,
    val encryptedText: String,
    val isGeneratedBySystem: Boolean,
    val companyId: String
)

