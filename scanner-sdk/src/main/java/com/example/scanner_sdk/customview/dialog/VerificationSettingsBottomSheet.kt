package com.example.scanner_sdk.customview.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.ScanMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VerificationSettingsBottomSheet : BottomSheetDialogFragment() {

    var onScanModeChanged: ((ScanMode) -> Unit)? = null
    var onVerifyAuthenticityChanged: ((Boolean) -> Unit)? = null
    var onDismissCallback: (() -> Unit)? = null

    private var currentScanMode: ScanMode = ScanMode.SINGLE
    private var isVerifyEnabled: Boolean = true

    companion object {
        fun newInstance(
            scanMode: ScanMode = ScanMode.SINGLE,
            isVerifyEnabled: Boolean = true
        ): VerificationSettingsBottomSheet {
            return VerificationSettingsBottomSheet().apply {
                this.currentScanMode = scanMode
                this.isVerifyEnabled = isVerifyEnabled
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottomsheet_verification_settings, container, false)

        // ── Views ──────────────────────────────────────────────────────────────
        val cardSingleScan = view.findViewById<FrameLayout>(R.id.card_single_scan)
        val cardMultiScan = view.findViewById<FrameLayout>(R.id.card_multi_scan)
        val icSingleScanCheck = view.findViewById<ImageView>(R.id.ic_single_scan_check)
        val icMultiScanCheck = view.findViewById<ImageView>(R.id.ic_multi_scan_check)
        val txtVerifyStatus = view.findViewById<TextView>(R.id.txt_verify_status)
        val authStatusDot = view.findViewById<View>(R.id.auth_status_dot)
        val txtAuthStatus = view.findViewById<TextView>(R.id.txt_auth_status)
        val switchVerify = view.findViewById<SwitchCompat>(R.id.switch_verify_authenticity)
        val btnClose = view.findViewById<ImageButton>(R.id.btn_close_bottom_sheet)

        // ── Initial State ──────────────────────────────────────────────────────
        updateScanModeUI(
            cardSingleScan,
            cardMultiScan,
            icSingleScanCheck,
            icMultiScanCheck,
            currentScanMode
        )
        switchVerify.isChecked = isVerifyEnabled
        updateVerifyStatus(txtVerifyStatus, authStatusDot, txtAuthStatus, isVerifyEnabled)

        // ── Scan Mode Cards ────────────────────────────────────────────────────
        cardSingleScan.setOnClickListener {
            if (currentScanMode != ScanMode.SINGLE) {
                currentScanMode = ScanMode.SINGLE
                updateScanModeUI(
                    cardSingleScan,
                    cardMultiScan,
                    icSingleScanCheck,
                    icMultiScanCheck,
                    currentScanMode
                )
                onScanModeChanged?.invoke(ScanMode.SINGLE)
            }
        }

        cardMultiScan.setOnClickListener {
            if (currentScanMode != ScanMode.MULTI) {
                currentScanMode = ScanMode.MULTI
                updateScanModeUI(
                    cardSingleScan,
                    cardMultiScan,
                    icSingleScanCheck,
                    icMultiScanCheck,
                    currentScanMode
                )
                onScanModeChanged?.invoke(ScanMode.MULTI)
            }
        }

        // ── Verify Switch ──────────────────────────────────────────────────────
        switchVerify.setOnCheckedChangeListener { _, isChecked ->
            isVerifyEnabled = isChecked
            updateVerifyStatus(txtVerifyStatus, authStatusDot, txtAuthStatus, isChecked)
            onVerifyAuthenticityChanged?.invoke(isChecked)
        }

        // ── Close Button ───────────────────────────────────────────────────────
        btnClose.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun updateScanModeUI(
        cardSingle: FrameLayout,
        cardMulti: FrameLayout,
        singleCheckIcon: ImageView,
        multiCheckIcon: ImageView,
        mode: ScanMode
    ) {
        when (mode) {
            ScanMode.SINGLE -> {
                cardSingle.setBackgroundResource(R.drawable.scan_mode_selected_bg)
                cardMulti.setBackgroundResource(R.drawable.scan_mode_unselected_bg)
                singleCheckIcon.visibility = View.VISIBLE
                multiCheckIcon.visibility = View.GONE
                cardSingle.findViewById<TextView>(R.id.txt_single_scan_title)
                    ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_accent))
                cardMulti.findViewById<TextView>(R.id.txt_multi_scan_title)
                    ?.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }

            ScanMode.MULTI -> {
                cardSingle.setBackgroundResource(R.drawable.scan_mode_unselected_bg)
                cardMulti.setBackgroundResource(R.drawable.scan_mode_selected_bg)
                singleCheckIcon.visibility = View.GONE
                multiCheckIcon.visibility = View.VISIBLE
                cardSingle.findViewById<TextView>(R.id.txt_single_scan_title)
                    ?.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                cardMulti.findViewById<TextView>(R.id.txt_multi_scan_title)
                    ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_accent))
            }

            else -> {}
        }
    }

    private fun updateVerifyStatus(
        txtStatus: TextView,
        dot: View,
        txtAuth: TextView,
        enabled: Boolean
    ) {
        if (enabled) {
            txtStatus.text = "Enabled — barcodes will be authenticated"
            dot.setBackgroundResource(R.drawable.green_dot_bg)
            txtAuth.text = "Authentication active"
        } else {
            txtStatus.text = "Disabled — authenticity not checked"
            dot.setBackgroundResource(R.drawable.grey_dot_bg)
            txtAuth.text = "Authentication inactive"
        }
    }
}