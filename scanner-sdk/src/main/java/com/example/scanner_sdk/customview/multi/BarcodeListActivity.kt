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

        fun removeCompanyId(text: String): List<String> {
            return when {
                text.contains("(98)") -> text.split("(98)")
                text.contains("(97)") -> text.split("(97)")
                text.contains("/98)") -> text.split("/98")
                text.contains("/97") -> text.split("/97")
                text.contains("/97") -> text.split("/97")
                text.contains("\u001D98") -> text.split("\u001D98")
                text.contains("\u001D97") -> text.split("\u001D97")
//                text.contains("98") -> text.split("98") // Todo handle this type here
//                text.contains("97") -> text.split("97") // Todo handle this type here
                else -> listOf(text)
            }
        }

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
                    val barcodeValues = removeCompanyId(barcodeData[0])

                    val apiResult = authenticateBarcodeSuspend(
                        raw = barcodeData[0],
                        barcode = barcodeValues[0],
                        type = if (barcodeData.size > 1) barcodeData[1] else "",
                        encryptedText = if (barcodeValues.size > 1)
                            removeCompanyId(barcodeValues[1])[0] else "",
                        companyId = companyId,
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
            val array = JSONArray(jsonString)
            if (array.length() == 0) return null

            val obj: JSONObject = array.getJSONObject(0)

            val barcodeData   = obj.optString("barcode_data", "")
            val encryptedText = obj.optString("encrypted_text", "")
            val quality       = obj.optString("quality", "Unknown")

            // gs1_data is a JSON object whose keys are AI numbers
            val gs1Object: JSONObject = obj.optJSONObject("gs1_data") ?: JSONObject()
            val gs1Fields = mutableListOf<Gs1Field>()

            val keys = gs1Object.keys()
            while (keys.hasNext()) {
                val ai = keys.next()
                val fieldObj: JSONObject = gs1Object.optJSONObject(ai) ?: continue
                gs1Fields.add(
                    Gs1Field(
                        ai    = ai,
                        name  = fieldObj.optString("name", ai),
                        value = fieldObj.optString("value", "—")
                    )
                )
            }

            ScanResult(
                raw = barcode,
                type = type,
                barcodeData   = barcodeData,
                gs1Fields     = gs1Fields,
                encryptedText = encryptedText,
                quality       = quality
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

