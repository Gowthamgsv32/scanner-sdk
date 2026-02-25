package com.example.scanner_sdk.customview.adpater

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.ScannedItem
import kotlin.math.log

class ScannedListAdapter(
    private val list: List<ScannedItem>
) : RecyclerView.Adapter<ScannedListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val raw = view.findViewById<TextView>(R.id.txtRawValue)
        val status = view.findViewById<TextView>(R.id.txtAuthStatus)
        val type = view.findViewById<TextView>(R.id.txtBarcodeType)
        val container = view.findViewById<LinearLayout>(R.id.containerParsedValues)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_barcode, parent, false)
        return VH(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = list[position]

        holder.raw.text = item.raw
        holder.type.text = item.type

        // ⭐ IMPORTANT — clear old rows (RecyclerView reuse fix)
        holder.container.removeAllViews()

        val inflater = LayoutInflater.from(holder.itemView.context)

        // ⭐ Add dynamic GS1 rows
        item.parsedMap.forEach { gs1 ->

            if (gs1.ai == "97" || gs1.ai == "98") return@forEach

            val row = inflater.inflate(
                R.layout.item_gs1_row,
                holder.container,
                false
            )

            val txtTitle = row.findViewById<TextView>(R.id.gs1TxtTitle)
            val txtValue = row.findViewById<TextView>(R.id.gs1txtValue)

            txtTitle.text = gs1.description
            txtTitle.setTextColor(Color.WHITE)
            txtValue.text = gs1.value
            txtValue.setTextColor(Color.WHITE)

            holder.container.addView(row)
        }

        holder.status.text =
            if (item.isAuthentic) "Authentic Product"
            else "Fake Product"
        holder.status.setTextColor(Color.WHITE)
        holder.status.setBackgroundResource(if (item.isAuthentic) R.drawable.bg_chip_green else R.drawable.bg_chip_red)
    }
}
