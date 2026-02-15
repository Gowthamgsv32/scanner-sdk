package com.example.scanner_sdk.customview.multi.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.multi.MultiOverlayView
import com.example.scanner_sdk.customview.single.OverlayView

class MultiScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    val scanCountTxt: TextView
    val previewView: PreviewView
    val multiOverlayView: MultiOverlayView
    val btnGallery: ImageButton
    val flashButton: ImageButton
    val cameraSwitch: ImageButton
    val zoomPlus: ImageView
    val zoomMinus: ImageView
    val zoomPercentage: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.multi_scanner, this, true)

        previewView = findViewById(R.id.multiPreviewView)
        multiOverlayView = findViewById(R.id.multiOverlayView)
        btnGallery = findViewById(R.id.multi_btn_gallery)
        scanCountTxt = findViewById(R.id.scanCountTxt)
        flashButton = findViewById(R.id.multi_btn_flash_toggle)
        zoomPlus = findViewById(R.id.multiZoomPlus)
        zoomMinus = findViewById(R.id.multiZoomMinus)
        zoomPercentage = findViewById(R.id.multiZoomPercentage)
        cameraSwitch = findViewById(R.id.multi_btn_camera_switch)
    }
}