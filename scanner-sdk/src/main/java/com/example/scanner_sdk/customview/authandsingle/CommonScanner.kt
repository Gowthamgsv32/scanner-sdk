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
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.auth.AuthScannerView
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.single.OverlayView
import com.example.scanner_sdk.customview.single.view.SingleScannerView

class CommonScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    //    val txtTitle: TextView
    val singleScannerView: SingleScannerView
    val multiScannerView: MultiScannerView
//    val authScannerView: AuthScannerView
    val verificationScannerView: VerificationScannerView
    val btnGallery: ImageButton
    val flashButton: ImageButton
    val cameraSwitch: ImageButton

    init {
        LayoutInflater.from(context).inflate(R.layout.common_scanner_view, this, true)

//        txtTitle = findViewById(R.id.verification_txt_title)
        singleScannerView = findViewById(R.id.singleScannerView)
        multiScannerView = findViewById(R.id.multiScannerView)
        verificationScannerView = findViewById(R.id.verifyScannerView)
        btnGallery = findViewById(R.id.common_view_btn_gallery)
        flashButton = findViewById(R.id.common_view_btn_flash_toggle)
        cameraSwitch = findViewById(R.id.common_view_btn_camera_switch)
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