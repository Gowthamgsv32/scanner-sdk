package com.example.scanner_sdk.customview.auth

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.FrameMetadata
import com.example.scanner_sdk.customview.single.OverlayView
import com.google.mlkit.vision.barcode.common.Barcode

class AuthOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

//    val txtTitle: TextView
    val previewView: PreviewView
    val overlayView: OverlayView
    val btnGallery: ImageButton
    val flashButton: ImageButton
    val zoomSeekBar: SeekBar
    /*    val singleScan: LinearLayout
        val multiScan: LinearLayout*/

    init {
        LayoutInflater.from(context).inflate(R.layout.single_scanner, this, true)

//        txtTitle = findViewById(R.id.txtTitle)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        btnGallery = findViewById(R.id.btn_gallery)
        flashButton = findViewById(R.id.btn_flash_toggle)
        zoomSeekBar = findViewById(R.id.zoom_seekbar)
//        singleScan = findViewById(R.id.nav_single_scan)
//        multiScan = findViewById(R.id.nav_multi_scan)
    }
}