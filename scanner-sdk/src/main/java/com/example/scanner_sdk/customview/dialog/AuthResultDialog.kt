package com.example.scanner_sdk.customview.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.GS1ParsedResult

class AuthResultDialog(
    private val raw: String,
    private val message: String,
    private val isError: Boolean,
    private val parsedData: List<GS1ParsedResult>,
    private val onContinue: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_auth_result)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val messageTxt = dialog.findViewById<TextView>(R.id.txt_message)
        val titleTxt = dialog.findViewById<TextView>(R.id.txt_title)
        val txtVerifiedStatus = dialog.findViewById<TextView>(R.id.txt_verified_status)
        val verifyImg = dialog.findViewById<ImageView>(R.id.img_verify)

        val red = ContextCompat.getColor(dialog.context, R.color.red)
        val white = ContextCompat.getColor(dialog.context, R.color.white)
        val green = ContextCompat.getColor(dialog.context, R.color.light_green)
        val black = ContextCompat.getColor(dialog.context, R.color.black)

        messageTxt.text = message

        if (isError) {
            titleTxt.visibility = View.GONE
            verifyImg.visibility = View.GONE
            txtVerifiedStatus.text = "Not Verified"
            txtVerifiedStatus.setBackgroundResource(R.drawable.bg_not_verified_chip)

            txtVerifiedStatus.setBackgroundColor(red)
            txtVerifiedStatus.setTextColor(white)

            messageTxt.setTextColor(red)
            titleTxt.setTextColor(red)

        } else {
            titleTxt.text = "Authentication Successful"
            txtVerifiedStatus.text = "Verified"

            txtVerifiedStatus.setBackgroundResource(R.drawable.bg_verified_chip)
            txtVerifiedStatus.setTextColor(white)

            messageTxt.setTextColor(green)
            titleTxt.setTextColor(green)

            verifyImg.setImageResource(R.drawable.ic_tick)
            verifyImg.setColorFilter(green)
        }

        fun bind(cardId: Int, title: String, value: String) {
            val card = dialog.findViewById<View>(cardId)
            card.findViewById<TextView>(R.id.txtTitle).text = title
            card.findViewById<TextView>(R.id.txtValue).text = value
        }

        bind(
            R.id.cardGTIN,
            parsedData.firstOrNull()?.description ?: "",
            parsedData.firstOrNull()?.value ?: ""
        )
        bind(
            R.id.cardBatch,
            parsedData.getOrNull(1)?.description ?: "",
            parsedData.getOrNull(1)?.value ?: ""
        )
        bind(
            R.id.cardProd,
            parsedData.getOrNull(2)?.description ?: "",
            parsedData.getOrNull(2)?.value ?: ""
        )
        bind(
            R.id.cardExp,
            parsedData.getOrNull(3)?.description ?: "",
            parsedData.getOrNull(3)?.value ?: ""
        )

        dialog.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            dismiss()
            onContinue()
        }

        return dialog
    }
}
