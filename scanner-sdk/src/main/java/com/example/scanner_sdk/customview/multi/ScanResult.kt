package com.example.scanner_sdk.customview.multi

data class ScanResult(
    val type: String = "",
    val raw: String = "",
    val barcodeData: String,
    val gs1Fields: List<Gs1Field>,   // dynamic – ordered as received from JSON
    val encryptedText: String,
    val quality: String              // "Real" | "Fake" | …
)

data class Gs1Field(
    val ai: String,       // key from JSON object, e.g. "01", "10", "21"
    val name: String,     // human-readable label from "name" field
    val value: String     // actual value from "value" field
)
