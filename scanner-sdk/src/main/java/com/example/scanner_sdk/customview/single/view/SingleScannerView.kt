package com.example.scanner_sdk.customview.single.view

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

class SingleScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

//    val txtTitle: TextView
    val previewView: PreviewView
    val overlayView: OverlayView
    val btnGallery: ImageButton
    val flashButton: ImageButton
    val cameraSwitch: ImageButton
    val zoomPlus: ImageView
    val zoomMinus: ImageView
    val zoomPercentage: TextView
/*    val singleScan: LinearLayout
    val multiScan: LinearLayout*/

    init {
        LayoutInflater.from(context).inflate(R.layout.single_scanner, this, true)

//        txtTitle = findViewById(R.id.txtTitle)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        btnGallery = findViewById(R.id.btn_gallery)
        flashButton = findViewById(R.id.btn_flash_toggle)
        cameraSwitch = findViewById(R.id.btn_camera_switch)
        zoomPlus = findViewById(R.id.zoomPlus)
        zoomMinus = findViewById(R.id.zoomMinus)
        zoomPercentage = findViewById(R.id.zoomPercentage)
    }
}