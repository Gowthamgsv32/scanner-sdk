package com.example.scanner_sdk.customview.model

import android.content.Intent
import com.example.scanner_sdk.customview.ScanMode

/**
 * App-level configuration for the scanner SDK.
 * Scan mode and authenticity verification are controlled only via these props.
 */
data class ScannerConfig(
    val scanMode: ScanMode = ScanMode.SINGLE,
    val verifyAuthenticity: Boolean = false,
) {
    companion object {
        const val EXTRA_SCAN_MODE = "scanner_scan_mode"
        const val EXTRA_VERIFY_AUTHENTICITY = "scanner_verify_authenticity"

        fun fromIntent(intent: Intent): ScannerConfig = ScannerConfig(
            scanMode = runCatching {
                ScanMode.valueOf(
                    intent.getStringExtra(EXTRA_SCAN_MODE) ?: ScanMode.SINGLE.name
                )
            }.getOrDefault(ScanMode.SINGLE),
            verifyAuthenticity = intent.getBooleanExtra(EXTRA_VERIFY_AUTHENTICITY, false),
        )
    }
}
