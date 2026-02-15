package com.example.scanner_sdk.customview.model

data class ScannedItem(
    val raw: String,
    val isAuthentic: Boolean,
    val parsedMap: List<GS1ParsedResult>
)
