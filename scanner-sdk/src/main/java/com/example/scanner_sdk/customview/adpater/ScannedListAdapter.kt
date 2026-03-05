package com.example.scanner_sdk.customview.adpater

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.model.ScannedItem

class ScannedListAdapter(
    private val list: List<ScannedItem>,
    private val context: Context,
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
        if (item.raw.startsWith("http", true)) {

            holder.raw.setTextColor(ContextCompat.getColor(context, R.color.blue))

            holder.raw.setOnClickListener {

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.raw))
                context.startActivity(intent)
            }
        }
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
            txtTitle.setTextColor(Color.BLACK)
            txtValue.text = gs1.value
            txtValue.setTextColor(Color.BLACK)

            holder.container.addView(row)
        }

        holder.status.text =
            if (item.isAuthentic) "Authentic Product"
            else "Product Not Authentic"
        holder.status.setTextColor(Color.WHITE)
        holder.status.setBackgroundResource(if (item.isAuthentic) R.drawable.bg_chip_green else R.drawable.bg_chip_red)
    }
}
