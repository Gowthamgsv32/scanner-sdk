package com.example.scanner_sdk.customview.single

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.scanner_sdk.customview.BarcodeDataProcessor
import com.example.scanner_sdk.customview.ConvertToAuthentication
import com.example.scanner_sdk.customview.ScanMode
import com.example.scanner_sdk.customview.auth.AuthScannerView
import com.example.scanner_sdk.customview.authandsingle.CommonScannerView
import com.example.scanner_sdk.customview.authandsingle.VerificationScannerView
import com.example.scanner_sdk.customview.dialog.AuthResultDialog
import com.example.scanner_sdk.customview.dialog.ScanResultBottomSheet
import com.example.scanner_sdk.customview.getBarcodeTypeName
import com.example.scanner_sdk.customview.log
import com.example.scanner_sdk.customview.model.BarcodeAuthMultiRequest
import com.example.scanner_sdk.customview.model.FrameMetadata
import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.example.scanner_sdk.customview.model.ScannerConfig
import com.example.scanner_sdk.customview.multi.BarcodeListActivity
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.parseBarcodeLikeMultiScan
import com.example.scanner_sdk.customview.helper.AuthBarcodeParser
import com.example.scanner_sdk.customview.helper.AuthBcResponseParser
import com.example.scanner_sdk.customview.parseBarcodeLikeMultiScanForAuth
import com.example.scanner_sdk.customview.single.view.SingleScannerView
import com.example.scanner_sdk.customview.toggleFlash
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.Executors

class ScannerController(
    private val config: ScannerConfig = ScannerConfig(),
    private val singleScannerView: SingleScannerView? = null,
    private val multiScanner: MultiScannerView? = null,
    private val authScannerView: AuthScannerView? = null,
    private val verificationScanner: VerificationScannerView? = null,
    private val commonScannerView: CommonScannerView? = null,
    private val lifecycleOwner: LifecycleOwner,
    private val fragmentManager: FragmentManager,
    private val openGallery: () -> Unit,
    private val error: (Pair<String, String>) -> Unit,
    private val result: (Pair<String, JSONArray?>) -> Unit,
) {
    var shouldResumeScanning: Boolean = true
    private var lastLogTime = System.currentTimeMillis()
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null
    private var lastValue: String? = null
    private var isFlashEnabled = false
    private val barCodeList = arrayListOf<Pair<String, String>>()
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentZoomRatio = 1f
    private var isScanningPaused = false
    private var barcodeAnalyzer: BarcodeAnalyzer? = null
    private var minZoom = 1f
    private var maxZoom = 5f
    private val ZOOM_STEP = 0.5f

    fun startSingleScanner(context: Context) {
        start(context)
    }

    fun startCommonScanner(context: Context, userId: String, companyId: String) {
        startCommonView(context, userId, companyId)
    }

    fun startAuthScanner(context: Context, userId: String, companyId: String) {
        startAuth(context = context, userId = userId, companyId = companyId)
    }

    fun startVerifyScanner(context: Context, userId: String, companyId: String) {
        applyScannerModeFromConfig(
            context = context,
            userId = userId,
            companyId = companyId,
            verificationScannerView = verificationScanner,
            multiScannerView = multiScanner,
        )
    }

    fun startMultiScanner(context: Context, userId: String, companyId: String) {
        startCamera(context, userId, companyId, multiScanner)
    }

    /**
     * Call this after the user picks an image from the gallery.
     * Detects barcodes and routes to the correct handler.
     */
    fun processGalleryImage(
        context: Context,
        uri: Uri,
        userId: String = "0",
        companyId: String = "0",
    ) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            error(Pair("GALLERY_ERROR", "Failed to load image: ${e.message}"))
            return
        }

        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty()) {
                    error(Pair("GALLERY_ERROR", "No barcode found in the selected image"))
                    return@addOnSuccessListener
                }

                // Route to the correct handler based on which view is active
                when {
                    authScannerView != null -> {
                        isScanningPaused = true   // pause live camera while processing
                        handleAuthScan(
                            barcodes = barcodes,
                            userId = userId,
                            companyId = companyId,
                            onScanned = result,
                            onError = error,
                        )
                    }

                    commonScannerView != null -> {
                        if (config.verifyAuthenticity) {
                            handleAuthScan(
                                barcodes = barcodes,
                                userId = userId,
                                companyId = companyId,
                                onScanned = result,
                                onError = error,
                            )
                        } else {
                            handleSingleScan(barcodes)
                        }
                    }

                    else -> {
                        // singleScannerView or fallback
                        handleSingleScan(barcodes)
                    }
                }
            }
            .addOnFailureListener { e ->
                error(Pair("GALLERY_ERROR", "Barcode detection failed: ${e.message}"))
            }
            .addOnCompleteListener {
                scanner.close()
            }
    }

    private fun start(context: Context) {

        camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
            minZoom = zoomState.minZoomRatio
            maxZoom = zoomState.maxZoomRatio
            currentZoomRatio = zoomState.zoomRatio
            updateZoomUI()
        }

        singleScannerView?.previewView?.visibility = View.VISIBLE

        // For torch mode (continuous flash for preview)
        singleScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, singleScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }
        singleScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }

        singleScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK

            // Reset flash when switching camera
            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, singleScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = singleScannerView.previewView.surfaceProvider
                    }

                imageAnalysis?.let { analysis ->
                    bindCamera(context, preview, analysis)
                }
            }
        }

        singleScannerView?.zoomPlus?.setOnClickListener {
            increaseZoom()
            updateZoomUI()
        }

        singleScannerView?.zoomMinus?.setOnClickListener {
            decreaseZoom()
            updateZoomUI()
        }

        singleScannerView?.overlayView?.visibility = View.GONE
//        singleScannerView?.txtTitle?.text = "Single Scanner"

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            barcodeAnalyzer = BarcodeAnalyzer(
                onResults = { barcodes, meta ->

                    if (!shouldResumeScanning) return@BarcodeAnalyzer

                    /*Todo: Enable if boundary box needed*/
//                    singleScannerView?.overlayView?.setResults(barcodes, meta)
                    handleSingleScan(barcodes)
                },
            )

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
            preview.surfaceProvider = singleScannerView?.previewView?.surfaceProvider

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }

            cameraProvider?.unbindAll()

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(context))
    }

    private fun increaseZoom() {
        val newZoom = (currentZoomRatio + ZOOM_STEP).coerceAtMost(maxZoom)
        applyZoom(newZoom)
    }

    private fun decreaseZoom() {
        val newZoom = (currentZoomRatio - ZOOM_STEP).coerceAtLeast(minZoom)
        applyZoom(newZoom)
    }

    private fun applyZoom(zoom: Float) {
        currentZoomRatio = zoom
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    private fun updateZoomUI() {
        val label = "%.1fx".format(currentZoomRatio)
        singleScannerView?.zoomPercentage?.text = label
        singleScannerView?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        singleScannerView?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun updateAuthZoomUI() {
        val label = "%.1fx".format(currentZoomRatio)
        authScannerView?.zoomPercentage?.text = label
        authScannerView?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        authScannerView?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun updateMultiZoomUI(view: MultiScannerView?) {
        val label = "%.1fx".format(currentZoomRatio)
        view?.zoomPercentage?.text = label
        view?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        view?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun updateZoomButtons() {
        singleScannerView?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        singleScannerView?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun updateAuthZoomButtons() {
        authScannerView?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        authScannerView?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun updateMultiZoomButtons(view: MultiScannerView?) {
        view?.zoomPlus?.isEnabled = currentZoomRatio < maxZoom
        view?.zoomMinus?.isEnabled = currentZoomRatio > minZoom
    }

    private fun startAuth(context: Context, userId: String, companyId: String) {

        authScannerView?.previewView?.visibility = View.VISIBLE
        updateAuthZoomUI()
        // For torch mode (continuous flash for preview)
        authScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, authScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }
        authScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }
        authScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK

            // Reset flash when switching camera
            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, authScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = authScannerView.previewView.surfaceProvider
                    }

                imageAnalysis?.let { analysis ->
                    bindCamera(context, preview, analysis)
                }
            }
        }

        authScannerView?.zoomPlus?.setOnClickListener {
            increaseZoom()
            updateAuthZoomUI()
        }

        authScannerView?.zoomMinus?.setOnClickListener {
            decreaseZoom()
            updateAuthZoomUI()
        }
        authScannerView?.overlayView?.visibility = View.GONE
//        authScannerView?.txtTitle?.text = "Authendication Scanner"

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            barcodeAnalyzer = BarcodeAnalyzer(
                onResults = { barcodes, meta ->
                    if (isScanningPaused) return@BarcodeAnalyzer

                    /*Todo: Enable if boundary box needed*/
//                    authScannerView?.overlayView?.setResults(barcodes, meta)

                    handleAuthScan(
                        barcodes = barcodes,
                        userId = userId,
                        companyId = companyId,
                        onScanned = result,
                        onError = error
                    )
                },
            )

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
            preview.surfaceProvider = authScannerView?.previewView?.surfaceProvider

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }

            cameraProvider?.unbindAll()

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(context))
    }

    private fun startCommonView(context: Context, userId: String, companyId: String) {
        // ── Flash ─────────────────────────────────────────────────────────────────
        commonScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, commonScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }

        // ── Gallery ───────────────────────────────────────────────────────────────
        commonScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }

        // ── Camera switch ─────────────────────────────────────────────────────────
        commonScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, commonScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = commonScannerView.singleScannerView.previewView.surfaceProvider
                    }
                imageAnalysis?.let { analysis -> bindCamera(context, preview, analysis) }
            }
        }

        applyScannerModeFromConfig(
            context = context,
            userId = userId,
            companyId = companyId,
            verificationScannerView = commonScannerView?.verificationScannerView,
            multiScannerView = commonScannerView?.multiScannerView,
        )
    }

    private fun applyScannerModeFromConfig(
        context: Context,
        userId: String,
        companyId: String,
        verificationScannerView: VerificationScannerView?,
        multiScannerView: MultiScannerView?,
    ) {
        when (config.scanMode) {
            ScanMode.MULTI -> {
                verificationScannerView?.visibility = View.GONE
                multiScannerView?.visibility = View.VISIBLE
                startCamera(context, userId, companyId, multiScannerView)
            }
            ScanMode.SINGLE -> {
                multiScannerView?.visibility = View.GONE
                verificationScannerView?.visibility = View.VISIBLE
                verificationScannerView?.previewView?.visibility = View.VISIBLE
                startVerifyView(context, userId, companyId, verificationScannerView, multiScannerView)
            }
            else -> {}
        }
    }

    private fun startVerifyView(
        context: Context,
        userId: String,
        companyId: String,
        verificationScannerView: VerificationScannerView?,
        multiScannerView: MultiScannerView?
    ) {
        verificationScannerView?.previewView?.visibility = View.VISIBLE

        // ── Flash ─────────────────────────────────────────────────────────────────
        verificationScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, verificationScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }

        // ── Gallery ───────────────────────────────────────────────────────────────
        verificationScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }

        // ── Camera switch ─────────────────────────────────────────────────────────
        verificationScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, verificationScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = verificationScannerView.previewView.surfaceProvider
                    }
                imageAnalysis?.let { analysis -> bindCamera(context, preview, analysis) }
            }
        }

        // ── Zoom ──────────────────────────────────────────────────────────────────
        verificationScannerView?.zoomPlus?.setOnClickListener  { increaseZoom() }
        verificationScannerView?.zoomMinus?.setOnClickListener { decreaseZoom() }

        verificationScannerView?.overlayView?.visibility = View.GONE

        // ── Barcode count tap → BarcodeListActivity ───────────────────────────────
        verificationScannerView?.scanCountTxt?.setOnClickListener {
            if (barCodeList.isEmpty()) return@setOnClickListener
            val intent = Intent(verificationScannerView.context, BarcodeListActivity::class.java)
            intent.putStringArrayListExtra(
                "BARCODE_LIST",
                ArrayList(barCodeList.map { "${it.first}~~~~~${it.second}" })
            )
            intent.putExtra("COMPANY_ID", companyId)
            intent.putExtra("USER_ID", userId)
            verificationScannerView.context.startActivity(intent)
        }

        // ── Camera provider ───────────────────────────────────────────────────────
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            barcodeAnalyzer = BarcodeAnalyzer(
                onResults = { barcodes, meta ->
                    if (!shouldResumeScanning) return@BarcodeAnalyzer

                    if (config.scanMode == ScanMode.MULTI) {
                        // Multi-mode is handled by startCamera(); nothing to do here
                        // (this analyzer only runs while in verify/single view)
                    } else {
                        if (config.verifyAuthenticity) {
                            handleAuthScan(
                                barcodes   = barcodes,
                                userId     = userId,
                                companyId  = companyId,
                                onScanned  = result,
                                onError    = error,
                            )
                        } else {
                            handleSingleScan(barcodes)
                        }
                    }
                },
            )

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
            preview.surfaceProvider = verificationScannerView?.previewView?.surfaceProvider

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }

            cameraProvider?.unbindAll()

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(context))
    }
    /*private fun startVerifyView(context: Context, userId: String, companyId: String) {
        verificationScannerView?.previewView?.visibility = View.VISIBLE

        verificationScannerView?.more?.setOnClickListener {
            val sheet = VerificationSettingsBottomSheet.newInstance(
                scanMode = currentScanMode,
                isVerifyEnabled = isVerifyEnabled,
            )
            sheet.onScanModeChanged = { mode ->
                currentScanMode = mode
                verificationScannerView.scanCountTxt.visibility =
                    if (mode == ScanMode.MULTI) View.VISIBLE else View.GONE
            }
            sheet.onVerifyAuthenticityChanged = { enabled ->
                isVerifyEnabled = enabled
            }
            sheet.onDismissCallback = {
                // resume scanning etc.
            }
            sheet.show(fragmentManager, "VerificationSettings")
        }

        // For torch mode (continuous flash for preview)
        verificationScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, verificationScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }
        verificationScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }
        verificationScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK

            // Reset flash when switching camera
            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, verificationScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = verificationScannerView.previewView.surfaceProvider
                    }

                imageAnalysis?.let { analysis ->
                    bindCamera(context, preview, analysis)
                }
            }
        }

        verificationScannerView?.zoomPlus?.setOnClickListener {
            increaseZoom()
        }

        verificationScannerView?.zoomMinus?.setOnClickListener {
            decreaseZoom()
        }

        verificationScannerView?.overlayView?.visibility = View.GONE

        verificationScannerView?.scanCountTxt?.setOnClickListener {

            if (barCodeList.isEmpty()) return@setOnClickListener

            val context = verificationScannerView.context

            val intent = Intent(context, BarcodeListActivity::class.java)
            intent.putStringArrayListExtra(
                "BARCODE_LIST",
                ArrayList(barCodeList.map { "${it.first}~~~~~${it.second}" })
            )
            intent.putExtra("COMPANY_ID", companyId)
            intent.putExtra("USER_ID", userId)

            context.startActivity(intent)
        }

//        verificationScannerView?.txtTitle?.text = "Authendication Scanner"

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            barcodeAnalyzer = BarcodeAnalyzer(
                onResults = { barcodes, meta ->
                    if (!shouldResumeScanning) return@BarcodeAnalyzer

                    *//*Todo: Enable if boundary box needed*//*
//                    verificationScannerView?.overlayView?.setResults(barcodes, meta)

                    if (currentScanMode == ScanMode.MULTI) {
                        onBarcodes(barcodes, meta)
                        Log.d("BarcodeAnalyzer", "tempValue")
                        barcodes.forEach { codes ->
                            if (!barCodeList.any { it.first == codes.rawValue } && !codes.rawValue.isNullOrBlank()) {
                                barCodeList.add(
                                    Pair(
                                        codes.rawValue.toString(),
                                        getBarcodeTypeName(codes.format)
                                    )
                                )
                            }
                        }
                        if (barCodeList.isNotEmpty()) {
//                                    binding.layoutCount.visibility = android.view.View.VISIBLE
                        }
                        verificationScannerView?.scanCountTxt?.text =
                            "Barcode Count : ${barCodeList.size}"
                    } else {
                        if (isVerifyEnabled) {
                            handleAuthScan(
                                barcodes = barcodes,
                                userId = userId,
                                companyId = companyId,
                                onScanned = result,
                                onError = error,
                            )
                        } else {
                            handleSingleScan(barcodes)
                        }
                    }
                },
            )

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
            preview.surfaceProvider = verificationScannerView?.previewView?.surfaceProvider

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }

            cameraProvider?.unbindAll()

            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(context))
    }*/

    @OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera(context: Context, userId: String, companyId: String, multiScannerView: MultiScannerView?) {
        multiScannerView?.previewView?.visibility = View.VISIBLE
        multiScannerView?.multiOverlayView?.visibility = View.VISIBLE

        multiScannerView?.scanCountTxt?.setOnClickListener {

            if (barCodeList.isEmpty()) return@setOnClickListener

            val context = multiScannerView.context

            val intent = Intent(context, BarcodeListActivity::class.java)
            intent.putStringArrayListExtra(
                "BARCODE_LIST",
                ArrayList(barCodeList.map { "${it.first}~~~~~${it.second}" })
            )
            intent.putExtra("COMPANY_ID", companyId)
            intent.putExtra("USER_ID", userId)

            context.startActivity(intent)
        }


        if (multiScannerView != null) {
            Log.d("multiScannerView", "startCamera: multiScannerView not null")
        } else {
            Log.d("multiScannerView", "startCamera: multiScannerView null")
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val targetSize = Size(1920, 1080)

            val preview = Preview.Builder()
                .setTargetResolution(targetSize)
                .build()
                .also {
                    it.surfaceProvider = multiScannerView?.previewView?.surfaceProvider
                }

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(targetSize) // or 1920x1080
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(
                        executor,
                        BarcodeAnalyzer(
                            onResults = { barcodes, meta ->
                                onBarcodes(barcodes, meta, multiScannerView)
                                Log.d("BarcodeAnalyzer", "tempValue")
                                barcodes.forEach { codes ->
                                    if (!barCodeList.any { it.first == codes.rawValue } && !codes.rawValue.isNullOrBlank()) {
                                        barCodeList.add(
                                            Pair(
                                                codes.rawValue.toString(),
                                                getBarcodeTypeName(codes.format)
                                            )
                                        )
                                    }
                                }
                                if (barCodeList.isNotEmpty()) {
//                                    binding.layoutCount.visibility = android.view.View.VISIBLE
                                }
                                multiScannerView?.scanCountTxt?.text =
                                    "Barcode Count : ${barCodeList.size}"
                            },
                            useFrontCamera = false
                        )
                    )
                }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                camera?.let { camInfo ->
                    camInfo.cameraControl.setLinearZoom(0f) // reset zoom
                    camInfo.cameraControl.enableTorch(false) // optional
                    camInfo.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(
                            multiScannerView!!.previewView.meteringPointFactory.createPoint(
                                multiScannerView.previewView.width / 2f,
                                multiScannerView.previewView.height / 2f
                            )
                        ).build()
                    )

                    // Enable continuous focus if device supports it
                    val camera2Info = Camera2CameraInfo.from(camInfo.cameraInfo)
                    val afModes =
                        camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    if (afModes?.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
                        camInfo.cameraControl.enableTorch(false)
                    }
                }

            } catch (e: Exception) {
                error("Camera binding failed: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(context))

        updateMultiZoomUI(multiScannerView)

        multiScannerView?.zoomPlus?.setOnClickListener {
            increaseZoom()
            updateMultiZoomUI(multiScannerView)
        }

        multiScannerView?.zoomMinus?.setOnClickListener {
            decreaseZoom()
            updateMultiZoomUI(multiScannerView)
        }

        multiScannerView?.flashButton?.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            toggleFlash(isFlashEnabled, multiScannerView.flashButton)
            camera?.cameraControl?.enableTorch(isFlashEnabled)
        }
        multiScannerView?.btnGallery?.setOnClickListener {
            openGallery()
        }

        multiScannerView?.cameraSwitch?.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK

            // Reset flash when switching camera
            isFlashEnabled = false
            camera?.cameraControl?.enableTorch(false)
            toggleFlash(false, multiScannerView.flashButton)

            cameraProvider?.let {
                val preview = Preview.Builder()
                    .setTargetResolution(Size(1920, 1080))
                    .build().also {
                        it.surfaceProvider = multiScannerView.previewView.surfaceProvider
                    }

                imageAnalysis?.let { analysis ->
                    bindCamera(context, preview, analysis)
                }
            }
        }
    }

    private fun handleSingleScan(barcodes: List<Barcode>) {
        if (barcodes.isEmpty()) return

        val firstBarcode = barcodes.firstOrNull() ?: return
        val raw = firstBarcode.rawValue ?: return

        shouldResumeScanning = false

        val (parsed, barcode, encrypted) = parseBarcodeLikeMultiScan(raw)
        val type = getBarcodeTypeName(firstBarcode.format)
        val gson = Gson()

        val json = gson.toJson(parsed)
        result(Pair(raw, JSONArray(json)))
 //       showScanResultBottomSheet(raw = raw, parsedMap = parsed, type = type)
    }

    private fun bindCamera(
        context: Context,
        preview: Preview,
        imageAnalysis: ImageAnalysis
    ) {
        cameraProvider?.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        camera = cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }

    private fun handleAuthScan(
        barcodes: List<Barcode>,
        userId: String,
        companyId: String,
        onScanned: (Pair<String, JSONArray?>) -> Unit,
        onError: (Pair<String, String>) -> Unit
    ) {
        val barcodes = barcodes.firstOrNull() ?: return
        val raw = barcodes.rawValue ?: return

        shouldResumeScanning = false
//        imageAnalysis?.clearAnalyzer()

        val type = getBarcodeTypeName(barcodes.format)
        val result = parseBarcodeLikeMultiScanForAuth(raw, type)

        val barcode = result.barcodeData
        val encryptedText = result.encryptedText
        val authCompanyId = result.companyId.ifEmpty { companyId }

        Log.d("BARCODESCANNERLOG", raw)
        Log.d("BARCODESCANNERLOG", barcode)
        Log.d("BARCODESCANNERLOG", encryptedText)

        lifecycleOwner.lifecycleScope.launch {
            authenticateBarcode(
                barcode = barcode,
                encryptedText = encryptedText,
                companyId = authCompanyId,
                userId = userId,
                onError = { responseBody ->
                    val enriched = AuthBarcodeParser.withAuthResponse(result, responseBody)
                    onError(Pair(raw, Gson().toJson(enriched)))
                },
                onSuccess = { responseBody ->
                    val enriched = AuthBarcodeParser.withAuthResponse(result, responseBody)
                    val quality = AuthBcResponseParser.parse(responseBody)?.quality ?: "Real"
                    onScanned(Pair(raw, authScanResultArray(enriched, quality)))
                },
            )
        }

//        handleAuthenticationResult(raw, barcodes.format)
    }

    fun stop() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        camera = null
    }

    suspend fun authenticateBarcode(
        barcode: String,
        encryptedText: String,
        companyId: String,
        userId: String,
        onError: (String) -> Unit,
        onSuccess: (String) -> Unit,
    ) {
        val url = "https://dlhub.8aiku.com/scan/auth-bc"

        try {
            val requestBody = listOf(
                mapOf(
                    "barcode_data" to barcode,
                    "encrypted_text" to encryptedText,
                    "company_id" to companyId,
                    "user_id" to userId,
                )
            )

            log(requestBody.toString())

            val json = Gson().toJson(requestBody)

            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val client = OkHttpClient()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            val responseBody = response.body.string()

            log("responseBody" + responseBody.toString())

            if (responseBody.isEmpty()) {
                onError(responseBody)
                return
            }

            if (!response.isSuccessful) {
                onError(responseBody)
                return
            }

            onSuccess(responseBody)
            /*            val jsonElement = JsonParser.parseString(responseBody)

                        log("jsonElement" + jsonElement)

                        if (jsonElement.isJsonArray) {
                            val array = jsonElement.asJsonArray
                            val first = array.firstOrNull()?.asJsonObject

                            val quality = first?.get("quality")?.asString

                            if (quality != null) {
                                if (quality.equals("Fake", ignoreCase = true)) {
                                    onSuccess(array)
                                } else {
                                    onSuccess(array)
                                }
                            } else {
                                onSuccess(array)
                            }

                        } else if (jsonElement.isJsonPrimitive) {
                            onError(jsonElement.asString)
                        }
                        else {
                            onError("⚠️ Unexpected response format")
                        }*/

        } catch (e: Exception) {
            onError("❌ Product fake or not authentic")
        } finally {
            /*Todo*/
        }
    }

    private fun authScanResultArray(
        parsed: com.example.scanner_sdk.customview.model.ParsedAuthBarcode,
        quality: String,
    ): JSONArray {
        val item = AuthBcResponseParser.AuthBcItem(
            barcodeData = parsed.barcodeData,
            encryptedText = parsed.encryptedText,
            quality = quality,
            gs1Results = parsed.parsedResults,
        )
        return JSONArray(AuthBcResponseParser.toResultArray(item).toString())
    }

    private fun showAuthScanResult(
        raw: String,
        message: String,
        isError: Boolean,
        parsedMap: List<GS1ParsedResult>
    ) {
        AuthResultDialog(
            raw = raw,
            message = message,
            parsedData = parsedMap,
            isError = isError,
        ) {
            isScanningPaused = false
            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }
        }.show(fragmentManager, "AuthDialog")

    }

    private fun showScanResultBottomSheet(
        raw: String,
        type: String,
        parsedMap: List<GS1ParsedResult>
    ) {
        val bottomSheet = ScanResultBottomSheet(rawData = raw, parsedData = parsedMap, type = type)

        bottomSheet.show(fragmentManager, "ScanResultBottomSheet")

        bottomSheet.onDismissCallback = {
            shouldResumeScanning = true
            barcodeAnalyzer?.let { imageAnalysis?.setAnalyzer(executor, it) }
        }
    }

    private fun onBarcodes(barcodes: List<Barcode>, meta: FrameMetadata, view: MultiScannerView?) {
//        if (analyzerStopped) return
        val now = System.currentTimeMillis()
        if (now - lastLogTime > 1000) {
            view?.multiOverlayView?.setResults(barcodes, meta)

            handleMultiple(barcodes)
            lastLogTime = now
        }
    }

    private fun handleMultiple(barcodes: List<Barcode>) {
        var newCount = 0

        for (b in barcodes) {
            val v = b.rawValue ?: continue
            /*if (seenValues.add(v)) */newCount++
        }
        if (newCount > 0) {
//            onScanned(newCount.toString())
        }
    }

    private fun handleAuthenticationResult(result: String, format: Int) {
//        requireActivity().runOnUiThread {
        // Immediately freeze scanning when processing starts
//            isScanning = false
        val barcodeType = getBarcodeTypeName(format)

        // Step 1: Use ConvertToAuthentication to process the scanned result
        val authResult = ConvertToAuthentication.convertDynamicPathToGS1(result)

        // Step 2: Use BarcodeDataProcessor to extract values and encrypt
        val processedResult = BarcodeDataProcessor.processConvertedResult(authResult)

        // Step 3: Send to dlhub.8aiku.com API for authentication
        try {
            // Create the request object as expected by the API
            val requestList = listOf(
                BarcodeAuthMultiRequest(
                    barcodeData = processedResult.barcodeData,
                    encryptedText = processedResult.encryptedText
                )
            )

            // Log the raw JSON that will be sent
            val gson = com.google.gson.Gson()
            val jsonPayload = gson.toJson(requestList)

            // Log cURL equivalent for comparison
            android.util.Log.d("AuthenticationFragment", "=== CURL EQUIVALENT ===")
            android.util.Log.d(
                "AuthenticationFragment",
                "curl --location 'https://dlhub.8aiku.com/dmai/auth-multbc-ai' \\"
            )
            android.util.Log.d(
                "AuthenticationFragment",
                "--header 'Content-Type: application/json' \\"
            )
            android.util.Log.d("AuthenticationFragment", "--data '$jsonPayload'")
            android.util.Log.d("AuthenticationFragment", "========================")

            // Log individual request details
            requestList.forEachIndexed { index, request ->
                android.util.Log.d("AuthenticationFragment", "Request[$index]:")
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - barcodeData: '${request.barcodeData}'"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - encryptedText: '${request.encryptedText}'"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - barcodeData length: ${request.barcodeData.length}"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - encryptedText length: ${request.encryptedText.length}"
                )

                // Compare with expected values from your cURL
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - Expected barcodeData: 'httpsSakkshcom0195203454189156229767610JAHAH12821HAHAH192811250718'"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - Expected encryptedText: 'vZOyDiK4CHPA='"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - barcodeData matches expected: ${request.barcodeData == "httpsSakkshcom0195203454189156229767610JAHAH12821HAHAH192811250718"}"
                )
                android.util.Log.d(
                    "AuthenticationFragment",
                    "  - encryptedText matches expected: ${request.encryptedText == "vZOyDiK4CHPA="}"
                )
            }
            android.util.Log.d("AuthenticationFragment", "========================")

            // Show loader before API call
//                showLoader()

            /*                val apiResult = apiRepository.authenticateBarcodeMulti(requestList)

                            apiResult.onSuccess { authResponseList ->
                                android.util.Log.d("AuthenticationFragment", "=== API RESPONSE SUCCESS ===")
                                android.util.Log.d("AuthenticationFragment", "Response List Size: ${authResponseList.size}")

                                authResponseList.forEachIndexed { index, response ->
                                    android.util.Log.d("AuthenticationFragment", "Response[$index]:")
                                    android.util.Log.d("AuthenticationFragment", "  - barcodeData: '${response.barcodeData}'")
                                    android.util.Log.d("AuthenticationFragment", "  - encryptedText: '${response.encryptedText}'")
                                    android.util.Log.d("AuthenticationFragment", "  - quality: '${response.quality ?: "N/A"}'")
                                    android.util.Log.d("AuthenticationFragment", "  - verified: ${response.verified}")
                                    android.util.Log.d("AuthenticationFragment", "  - message: '${response.message}'")
                                    android.util.Log.d("AuthenticationFragment", "  - error: '${response.error}'")
                                    android.util.Log.d("AuthenticationFragment", "  - authenticationCode: '${response.authenticationCode ?: "N/A"}'")

                                    // Log product info if available
                                    response.productInfo?.let { productInfo ->
                                        android.util.Log.d("AuthenticationFragment", "  - productInfo:")
                                        android.util.Log.d("AuthenticationFragment", "    * name: '${productInfo.name}'")
                                        android.util.Log.d("AuthenticationFragment", "    * brand: '${productInfo.brand}'")
                                        android.util.Log.d("AuthenticationFragment", "    * gtin: '${productInfo.gtin ?: "N/A"}'")
                                        android.util.Log.d("AuthenticationFragment", "    * batchNumber: '${productInfo.batchNumber ?: "N/A"}'")
                                        android.util.Log.d("AuthenticationFragment", "    * expiryDate: '${productInfo.expiryDate ?: "N/A"}'")
                                        android.util.Log.d("AuthenticationFragment", "    * serialNumber: '${productInfo.serialNumber ?: "N/A"}'")
                                    } ?: android.util.Log.d("AuthenticationFragment", "  - productInfo: null")
                                }
                                android.util.Log.d("AuthenticationFragment", "==============================")

                                // Process the first response from the list
                                val authResponse = authResponseList.firstOrNull()
                                if (authResponse != null) {
                                    android.util.Log.d("AuthenticationFragment", "Processing first response:")
                                    android.util.Log.d("AuthenticationFragment", "  - Quality: ${authResponse.quality}")
                                    android.util.Log.d("AuthenticationFragment", "  - Verified: ${authResponse.verified}")

                                    // Hide loader before showing result
                                    hideLoader()

                                    // Show authentication result popup (scanning remains frozen until popup is closed)
                                    showAuthenticationResultPopup(authResponse.quality)

                                } else {
                                    android.util.Log.w("AuthenticationFragment", "Empty response list from API")
                                    requireActivity().runOnUiThread {
                                        hideLoader()
                                        Toast.makeText(requireContext(),
                                            "⚠️ Empty response from authentication service",
                                            Toast.LENGTH_LONG).show()
                                        // Resume scanning on error
                                        isScanning = true
                                    }
                                }
                            }.onFailure { error ->
                                android.util.Log.e("AuthenticationFragment", "=== API RESPONSE FAILED ===")
                                android.util.Log.e("AuthenticationFragment", "Error Type: ${error.javaClass.simpleName}")
                                android.util.Log.e("AuthenticationFragment", "Error Message: ${error.message}")
                                android.util.Log.e("AuthenticationFragment", "Error Cause: ${error.cause?.message}")
                                android.util.Log.e("AuthenticationFragment", "Stack trace: ${error.stackTraceToString()}")
                                android.util.Log.e("AuthenticationFragment", "==============================")

                                // Hide loader on error
                                hideLoader()

                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(),
                                        "🌐 Network Error:\n${error.message}",
                                        Toast.LENGTH_LONG).show()
                                    // Resume scanning on error
                                    isScanning = true
                                }
                            }*/
        } catch (e: Exception) {
            android.util.Log.e("AuthenticationFragment", "=== AUTHENTICATION EXCEPTION ===")
            android.util.Log.e(
                "AuthenticationFragment",
                "Exception Type: ${e.javaClass.simpleName}"
            )
            android.util.Log.e("AuthenticationFragment", "Exception Message: ${e.message}")
            android.util.Log.e("AuthenticationFragment", "Exception Cause: ${e.cause?.message}")
            android.util.Log.e("AuthenticationFragment", "Stack trace: ${e.stackTraceToString()}")
            android.util.Log.e("AuthenticationFragment", "==================================")

            // Hide loader on exception
//            onScanned("⚠️ Unexpected Error:\n${e.message}")
            /*                hideLoader()

                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(),
                                    "⚠️ Unexpected Error:\n${e.message}",
                                    Toast.LENGTH_LONG).show()
                                // Resume scanning on error
                                isScanning = true
                            }*/
        }

        // Note: No automatic resume here - scanning will only resume when popup is closed
    }
//    }

}