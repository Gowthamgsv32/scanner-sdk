package com.example.scanner_sdk.customview.auth

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

class AuthScannerView @JvmOverloads constructor(
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
        LayoutInflater.from(context).inflate(R.layout.auth_scanner, this, true)

//        txtTitle = findViewById(R.id.auth_txt_title)
        previewView = findViewById(R.id.authPreviewView)
        overlayView = findViewById(R.id.authOverlayView)
        btnGallery = findViewById(R.id.auth_btn_gallery)
        flashButton = findViewById(R.id.auth_btn_flash_toggle)
        zoomSeekBar = findViewById(R.id.auth_zoom_seekbar)
//        singleScan = findViewById(R.id.nav_single_scan)
//        multiScan = findViewById(R.id.nav_multi_scan)
    }
}