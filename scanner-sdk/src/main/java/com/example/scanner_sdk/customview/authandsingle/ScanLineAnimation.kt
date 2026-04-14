package com.example.scanner_sdk.customview.authandsingle

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.single.OverlayView

class VerificationScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    //    val txtTitle: TextView
    val previewView: PreviewView
    val overlayView: OverlayView
    val btnGallery: ImageButton
    val btnCameraSwitch: ImageButton
    val flashButton: ImageButton
    val cameraSwitch: ImageButton
    val zoomPlus: ImageView
    val zoomMinus: ImageView
    val zoomPercentage: TextView
    val switch: SwitchCompat

    init {
        LayoutInflater.from(context).inflate(R.layout.verification_scanner, this, true)

//        txtTitle = findViewById(R.id.verification_txt_title)
        previewView = findViewById(R.id.verificationPreviewView)
        overlayView = findViewById(R.id.verificationOverlayView)
        btnGallery = findViewById(R.id.verification_btn_gallery)
        btnCameraSwitch = findViewById(R.id.verification_btn_camera_switch)
        flashButton = findViewById(R.id.verification_btn_flash_toggle)
        cameraSwitch = findViewById(R.id.verify_btn_camera_switch)
        zoomPlus = findViewById(R.id.verification_zoom_plus)
        zoomMinus = findViewById(R.id.verification_zoom_minus)
        zoomPercentage = findViewById(R.id.verification_zoom_percentage)
        switch = findViewById(R.id.switch_verify_verificationenticity)

        startScanLineAnimation()
    }

    private fun startScanLineAnimation() {
        val scanLine = findViewById<View>(R.id.scan_line)
        val frame = findViewById<View>(R.id.scanner_frame)

        frame.post {
            val frameHeight = frame.height.toFloat()
            val animator = ObjectAnimator.ofFloat(
                scanLine, "translationY",
                -frameHeight / 2f + 8f,   // top of frame
                frameHeight / 2f - 8f    // bottom of frame
            ).apply {
                duration = 2000L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            animator.start()
        }
    }
}

// Call startScanLineAnimation() from init {} after inflation, e.g.:
//   init {
//       LayoutInflater.from(context).inflate(R.layout.verification_scanner, this, true)
//       ...
//       startScanLineAnimation()
//   }
