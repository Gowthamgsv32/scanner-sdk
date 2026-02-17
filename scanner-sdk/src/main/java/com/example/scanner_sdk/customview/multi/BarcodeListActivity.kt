package com.example.scanner_sdk.customview.multi

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scanner_sdk.R
import com.example.scanner_sdk.customview.adpater.ScannedListAdapter
import com.example.scanner_sdk.customview.model.ParsedAuthBarcode
import com.example.scanner_sdk.customview.model.ScannedItem
import com.example.scanner_sdk.customview.parseBarcodeLikeMultiScanForAuth
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BarcodeListActivity : FragmentActivity() {

    private val authBarCodeList = mutableListOf<ParsedAuthBarcode>()
    private val barCodeResultList = mutableListOf<ScannedItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val rawList = intent.getStringArrayListExtra("BARCODE_LIST") ?: arrayListOf()

        // Show loading
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {

            // Step 1 — Parse
            rawList.forEach {
                authBarCodeList.add(parseBarcodeLikeMultiScanForAuth(it))
            }

            // Step 2 — Call APIs in parallel
            val jobs = authBarCodeList.map { result ->

                async {

                    authenticateBarcodeSuspend(
                        barcode = result.barcodeData,
                        encryptedText = result.encryptedText,
                        companyId = result.companyId
                    ).let { isSuccess ->

                        ScannedItem(
                            raw = result.barcodeData,
                            isAuthentic = isSuccess,
                            parsedMap = result.parsedResults
                        )
                    }
                }
            }

            // ⭐ Wait for ALL API calls to finish
            val finalList = jobs.awaitAll()

            // Step 3 — Update UI
            progressBar.visibility = View.GONE

            recyclerView.adapter = ScannedListAdapter(finalList)
        }
    }

    suspend fun authenticateBarcodeSuspend(
        barcode: String,
        encryptedText: String,
        companyId: String
    ): Boolean {

        return withContext(Dispatchers.IO) {

            try {
                val url = "https://dlhub.8aiku.com/scan/auth-bc"

                val requestBody = listOf(
                    mapOf(
                        "barcode_data" to barcode,
                        "encrypted_text" to encryptedText,
                        "company_id" to companyId
                    )
                )

                val json = Gson().toJson(requestBody)

                val request = Request.Builder()
                    .url(url)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body.string()

                val jsonElement = JsonParser.parseString(responseBody)

                if (jsonElement.isJsonArray) {
                    val quality = jsonElement.asJsonArray
                        .firstOrNull()
                        ?.asJsonObject
                        ?.get("quality")
                        ?.asString

                    return@withContext !quality.equals("Fake", true)
                }

                false

            } catch (e: Exception) {
                false
            }
        }
    }

}

