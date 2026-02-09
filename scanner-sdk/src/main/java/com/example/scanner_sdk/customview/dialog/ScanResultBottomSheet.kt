package com.example.scanner_sdk.customview.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.GS1ParsedResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ScanResultBottomSheet(
    private val rawData: String,
    private val parsedData:  List<GS1ParsedResult>
) : BottomSheetDialogFragment() {

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

        val txtRawData = view.findViewById<TextView>(R.id.txtRawData)
        val txtGTINTitle = view.findViewById<TextView>(R.id.txtGTINTitle)
        val txtGTINValue = view.findViewById<TextView>(R.id.txtGTINValue)
        val txtBatchNoTitle = view.findViewById<TextView>(R.id.txtBatchNoTitle)
        val txtBatchNoValue = view.findViewById<TextView>(R.id.txtBatchNoValue)
        val txtProductionDateTitle = view.findViewById<TextView>(R.id.txtProductionDateTitle)
        val txtProductionDateValue = view.findViewById<TextView>(R.id.txtProductionDateValue)
        val txtExpireDateTitle = view.findViewById<TextView>(R.id.txtExpireDateTitle)
        val txtExpireDateValue = view.findViewById<TextView>(R.id.txtExpireDateValue)

        txtRawData.text = rawData

        txtGTINTitle.text = parsedData.firstOrNull()?.description
        txtGTINValue.text = parsedData.firstOrNull()?.value

        txtBatchNoTitle.text = parsedData.getOrNull(1)?.description
        txtBatchNoValue.text = parsedData.getOrNull(1)?.value

        txtProductionDateTitle.text = parsedData.getOrNull(2)?.description
        txtProductionDateValue.text = parsedData.getOrNull(2)?.value

        txtExpireDateTitle.text = parsedData.getOrNull(3)?.description
        txtExpireDateValue.text = parsedData.getOrNull(3)?.value

        return view
    }
}