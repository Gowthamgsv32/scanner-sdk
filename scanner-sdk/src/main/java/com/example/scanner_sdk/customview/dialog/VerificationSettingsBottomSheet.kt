package com.example.scanner_sdk.customview.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.ScanMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VerificationSettingsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_SCAN_MODE  = "scan_mode"
        private const val ARG_VERIFY     = "is_verify_enabled"

        fun newInstance(
            scanMode: ScanMode,
            isVerifyEnabled: Boolean,
        ) = VerificationSettingsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_SCAN_MODE, scanMode.name)
                putBoolean(ARG_VERIFY, isVerifyEnabled)
            }
        }
    }

    // ── Callbacks ────────────────────────────────────────────────────────────────
    /** Called immediately when the user taps a mode card (sheet stays open). */
    var onScanModeChanged: ((ScanMode) -> Unit)? = null

    /** Called when the verify-authenticity switch is toggled. */
    var onVerifyAuthenticityChanged: ((Boolean) -> Unit)? = null

    /** Called when the sheet is fully dismissed. */
    var onDismissCallback: (() -> Unit)? = null

    // ── State ────────────────────────────────────────────────────────────────────
    private var currentMode: ScanMode = ScanMode.SINGLE
    private var isVerifyEnabled: Boolean = true

    // ── Views ────────────────────────────────────────────────────────────────────
    private lateinit var cardSingle: LinearLayout
    private lateinit var cardMulti: LinearLayout
    private lateinit var checkSingle: ImageView
    private lateinit var titleSingle: TextView
    private lateinit var titleMulti: TextView
    private lateinit var switchVerify: SwitchCompat
    private lateinit var txtVerifyStatus: TextView
    private lateinit var authStatusDot: View
    private lateinit var txtAuthStatus: TextView
    private lateinit var btnClose: ImageButton

    // ── Lifecycle ────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentMode     = ScanMode.valueOf(arguments?.getString(ARG_SCAN_MODE) ?: ScanMode.SINGLE.name)
        isVerifyEnabled = arguments?.getBoolean(ARG_VERIFY, true) ?: true
    }
    override fun getTheme(): Int = R.style.TransparentBottomSheet
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_verification_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand sheet fully on open
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state     = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        bindViews(view)
        renderMode(currentMode)
        renderVerify(isVerifyEnabled)
        setupListeners()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }

    // ── Bind ─────────────────────────────────────────────────────────────────────
    private fun bindViews(v: View) {
        cardSingle      = v.findViewById(R.id.card_single_scan)
        cardMulti       = v.findViewById(R.id.card_multi_scan)
        checkSingle     = v.findViewById(R.id.ic_single_scan_check)
        titleSingle     = v.findViewById(R.id.txt_single_scan_title)
        titleMulti      = v.findViewById(R.id.txt_multi_scan_title)
        switchVerify    = v.findViewById(R.id.switch_verify_verificationenticity)
        txtVerifyStatus = v.findViewById(R.id.txt_verify_status)
        authStatusDot   = v.findViewById(R.id.auth_status_dot)
        txtAuthStatus   = v.findViewById(R.id.txt_auth_status)
        btnClose        = v.findViewById(R.id.btn_close_bottom_sheet)
    }

    private fun setupListeners() {
        cardSingle.setOnClickListener {
            if (currentMode != ScanMode.SINGLE) {
                currentMode = ScanMode.SINGLE
                renderMode(ScanMode.SINGLE)
                onScanModeChanged?.invoke(ScanMode.SINGLE)
            }
        }

        cardMulti.setOnClickListener {
            if (currentMode != ScanMode.MULTI) {
                currentMode = ScanMode.MULTI
                renderMode(ScanMode.MULTI)
                onScanModeChanged?.invoke(ScanMode.MULTI)
            }
        }

        switchVerify.setOnCheckedChangeListener { _, checked ->
            isVerifyEnabled = checked
            renderVerify(checked)
            onVerifyAuthenticityChanged?.invoke(checked)
        }

        btnClose.setOnClickListener { dismiss() }
    }

    // ── Render helpers ───────────────────────────────────────────────────────────
    private fun renderMode(mode: ScanMode) {
        val blue  = requireContext().getColor(R.color.blue_accent)
        val white = requireContext().getColor(android.R.color.white)

        when (mode) {
            ScanMode.SINGLE -> {
                cardSingle.setBackgroundResource(R.drawable.scan_mode_selected_bg)
                cardMulti.setBackgroundResource(R.drawable.scan_mode_unselected_bg)
                checkSingle.visibility = View.VISIBLE
                titleSingle.setTextColor(blue)
                titleMulti.setTextColor(white)
            }
            ScanMode.MULTI -> {
                cardSingle.setBackgroundResource(R.drawable.scan_mode_unselected_bg)
                cardMulti.setBackgroundResource(R.drawable.scan_mode_selected_bg)
                checkSingle.visibility = View.GONE
                titleSingle.setTextColor(white)
                titleMulti.setTextColor(blue)
            }
            else -> {}
        }
    }

    private fun renderVerify(enabled: Boolean) {
        switchVerify.isChecked = enabled
        if (enabled) {
            txtVerifyStatus.text = "Enabled — barcodes will be authenticated"
            authStatusDot.setBackgroundResource(R.drawable.green_dot_bg)
            txtAuthStatus.text = "Authentication active"
        } else {
            txtVerifyStatus.text = "Disabled — authenticity not checked"
            authStatusDot.setBackgroundResource(R.drawable.grey_dot_bg)
            txtAuthStatus.text = "Authentication inactive"
        }
    }
}

/*
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
}*/
