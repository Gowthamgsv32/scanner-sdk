package com.example.scanner_sdk.customview

import android.view.View
import android.widget.ImageView
import androidx.camera.core.ImageCapture
import com.example.scanner_sdk.R
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