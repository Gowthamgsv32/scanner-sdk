package com.example.scanner_sdk.customview.multi.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
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

    val txtTitle: TextView
    val previewView: PreviewView
    val multiOverlayView: MultiOverlayView
    val btnGallery: ImageButton
//    val flashButton: ImageButton
//    val zoomSeekBar: SeekBar
/*    val singleScan: LinearLayout
    val multiScan: LinearLayout*/

    init {
        LayoutInflater.from(context).inflate(R.layout.multi_scanner, this, true)

        txtTitle = findViewById(R.id.multiTxtTitle)
        previewView = findViewById(R.id.multiPreviewView)
        multiOverlayView = findViewById(R.id.multiOverlayView)
        btnGallery = findViewById(R.id.multi_btn_gallery)
//        flashButton = findViewById(R.id.btn_flash_toggle)
//        zoomSeekBar = findViewById(R.id.zoom_seekbar)
//        singleScan = findViewById(R.id.nav_single_scan)
//        multiScan = findViewById(R.id.nav_multi_scan)
    }
}