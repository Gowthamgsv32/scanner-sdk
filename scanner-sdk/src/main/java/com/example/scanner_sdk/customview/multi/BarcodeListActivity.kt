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
import com.example.scanner_sdk.customview.getBarcodeTypeName
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
import org.json.JSONArray
import org.json.JSONObject

class BarcodeListActivity : FragmentActivity() {

//    private var authBarCodeList = listOf<String>()
    private val barCodeResultList = mutableListOf<ScannedItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val rawList = intent.getStringArrayListExtra("BARCODE_LIST") ?: arrayListOf()
        val companyId = intent.getStringExtra("COMPANY_ID") ?: ""
        val userId = intent.getStringExtra("USER_ID") ?: ""

        // Show loading
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {

            // Step 1 — Parse
            rawList.forEach {

//                authBarCodeList = it.split("~~~~~")
/*                if (barcodeList.size > 1) {
                    authBarCodeList.add(parseBarcodeLikeMultiScanForAuth(barcodeList[0], barcodeList[1]))
                }*/
            }


            // Step 2 — Call APIs in parallel
            val jobs = rawList.map { result ->
                async(Dispatchers.IO) {

                    val barcodeData = result.split("~~~~~")
                    val rawValue = barcodeData[0]
                    val barcodeType = if (barcodeData.size > 1) barcodeData[1] else ""
                    val parsed = parseBarcodeLikeMultiScanForAuth(rawValue, barcodeType)
                    val authCompanyId = parsed.companyId.ifEmpty { companyId }

                    val apiResult = authenticateBarcodeSuspend(
                        raw = rawValue,
                        barcode = parsed.barcodeData,
                        type = barcodeType,
                        encryptedText = parsed.encryptedText,
                        companyId = authCompanyId,
                        userId = userId,
                    )

                    // ✅ fallback if API fails
                    apiResult ?: run {
                        val data = parseBarcodeLikeMultiScanForAuth(
                            barcodeData[0],
                            if (barcodeData.size > 1) barcodeData[1] else ""
                        )

                        ScanResult(
                            raw = barcodeData[0],
                            type = if (barcodeData.size > 1) barcodeData[1] else "",
                            barcodeData = barcodeData[0],
                            gs1Fields = data.parsedResults.map {
                                Gs1Field(
                                    ai = it.ai,
                                    name = it.description,
                                    value = it.value
                                )
                            },
                            encryptedText = "",
                            quality = "Fake"
                        )
                    }
                }
            }

            // ⭐ Wait for ALL API calls to finish
            val finalList = jobs.awaitAll()

            // Step 3 — Update UI
            progressBar.visibility = View.GONE

            recyclerView.adapter = ScannedListAdapter(list = finalList, context = this@BarcodeListActivity)
        }
    }
    fun parseScanResponse(jsonString: String, barcode: String, type: String): ScanResult? {
        return try {
            val item = com.example.scanner_sdk.customview.helper.AuthBcResponseParser.parse(jsonString)
                ?: return null
            ScanResult(
                raw = barcode,
                type = type,
                barcodeData = item.barcodeData,
                gs1Fields = item.gs1Results.map { r ->
                    Gs1Field(ai = r.ai, name = r.description, value = r.value)
                },
                encryptedText = item.encryptedText,
                quality = item.quality,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun authenticateBarcodeSuspend(
        raw: String,
        barcode: String,
        type: String,
        encryptedText: String,
        companyId: String,
        userId: String,
    ): ScanResult? {
        return withContext(Dispatchers.IO) {

            try {
                val url = "https://dlhub.8aiku.com/scan/auth-bc"

                val requestBody = listOf(
                    mapOf(
                        "barcode_data" to barcode,
                        "encrypted_text" to encryptedText,
                        "company_id" to companyId,
                        "user_id" to userId,
                    )
                )

                val json = Gson().toJson(requestBody)

                val request = Request.Builder()
                    .url(url)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body.string()

                if (responseBody.isEmpty()) {
                    null
                } else {
                    parseScanResponse(
                        jsonString = responseBody,
                        barcode = if (raw.contains("http:") || raw.contains("https:")) raw else barcode,
                        type = type
                    )
                }
/*                val jsonElement = JsonParser.parseString(responseBody)

                if (jsonElement.isJsonArray) {
                    val quality = jsonElement.asJsonArray
                        .firstOrNull()
                        ?.asJsonObject
                        ?.get("quality")
                        ?.asString

                    return@withContext !quality.equals("Fake", true)
                }*/

            } catch (e: Exception) {
                null
            }
        }
    }

}

