package com.example.scanner_sdk.customview.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ScanResultBottomSheet(
    private val rawData: String,
    private val type: String,
    private val parsedData:  List<GS1ParsedResult>
) : BottomSheetDialogFragment() {
    var onDismissCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.bottomsheet_scan_result,
            container,
            false
        )

        view.findViewById<TextView>(R.id.txtRawData).text = rawData
        view.findViewById<TextView>(R.id.btnType).text = type

        val container = view.findViewById<LinearLayout>(R.id.containerParsedValues)

        parsedData.forEach { item ->

            if (item.ai == "97" || item.ai == "98") {
                // nothing
            } else {
                val row = layoutInflater.inflate(
                    R.layout.item_gs1_row,
                    container,
                    false
                )

                val txtTitle = row.findViewById<TextView>(R.id.gs1TxtTitle)
                val txtValue = row.findViewById<TextView>(R.id.gs1txtValue)

                txtTitle.text = item.description
                txtValue.text = item.value

                container.addView(row)
            }

        }
        return view
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
}