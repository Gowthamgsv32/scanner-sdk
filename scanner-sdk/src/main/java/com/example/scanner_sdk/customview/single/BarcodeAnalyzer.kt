package com.example.scanner_sdk.customview.single

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.scanner_sdk.customview.model.FrameMetadata
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onResults: (List<Barcode>, FrameMetadata) -> Unit,
    private val useFrontCamera: Boolean = false
) : ImageAnalysis.Analyzer {

    // Configure MLKit for higher accuracy (use only required formats if possible)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                // Restrict formats you actually need for faster + more accurate detection
                Barcode.FORMAT_ALL_FORMATS
                // Add or remove formats depending on your app
            )
//            .enableAllPotentialBarcodes() // Optional: allow MLKit to use all models internally
            .build()
    )

    private var lastSuccessTime = 0L
    private val frameThrottleMs = 300L // minimum time between frames processed fully

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close(); return
        }

        val now = System.currentTimeMillis()
        if (now - lastSuccessTime < frameThrottleMs) {
            // Skip overly frequent frames to avoid analyzer overload
            imageProxy.close()
            return
        }

//        Log.d("BarcodeAnalyzer", "Analyzing frame...")

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        val rotatedW: Int
        val rotatedH: Int
        if (rotation == 90 || rotation == 270) {
            rotatedW = mediaImage.height
            rotatedH = mediaImage.width
        } else {
            rotatedW = mediaImage.width
            rotatedH = mediaImage.height
        }

        val metadata = FrameMetadata(
            width = rotatedW,
            height = rotatedH,
            isFlipped = useFrontCamera,
            rotation = rotation
        )

        // Process the frame with ML Kit
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    lastSuccessTime = now
//                    Log.d("BarcodeAnalyzer", "✅ Detected ${barcodes.size} barcode(s)")
                } else {
                    Log.d("BarcodeAnalyzer", "✅ Detected empty")
                }
                onResults(barcodes, metadata)
            }
            .addOnFailureListener { e ->
                Log.e("BarcodeAnalyzer", "❌ Detection failed: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}