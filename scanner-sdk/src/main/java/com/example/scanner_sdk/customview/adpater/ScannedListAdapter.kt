package com.example.scanner_sdk.customview.adpater

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val gtinTitle = view.findViewById<TextView>(R.id.txt_result_title)
        val gtin = view.findViewById<TextView>(R.id.txtGtin)
        val batch = view.findViewById<TextView>(R.id.txtBatch)
        val prod = view.findViewById<TextView>(R.id.txtProduction)
        val exp = view.findViewById<TextView>(R.id.txtExpiry)
        val status = view.findViewById<TextView>(R.id.txtAuthStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_barcode, parent, false)
        return VH(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = list[position]

        Log.d("SCANNERLOGTEMP", "onBindViewHolder: ${item.parsedMap.joinToString()}")
        holder.raw.text = item.raw
        holder.gtinTitle.text = item.parsedMap.getOrNull(0)?.description ?: ""
        holder.gtin.text = item.parsedMap.getOrNull(0)?.value ?: ""
        holder.batch.text = item.parsedMap.getOrNull(1)?.value ?: ""
        holder.prod.text = item.parsedMap.getOrNull(2)?.value ?: ""
        holder.exp.text = item.parsedMap.getOrNull(3)?.value ?: ""

        holder.status.text =
            if (item.isAuthentic) "Authentic Product"
            else "Fake Product"
    }
}
