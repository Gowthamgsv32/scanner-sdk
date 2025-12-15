package com.example.scanner_sdk.customview

import android.content.Context

class ApiRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "ApiRepository"

        @Volatile
        private var INSTANCE: ApiRepository? = null

        fun getInstance(context: Context): ApiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /*    private val apiServiceManager = ApiServiceManager.getInstance(context)

        // API Services
        private val authApiService: AuthApiService by lazy {
            apiServiceManager.createService(AuthApiService::class.java)
        }

        suspend fun authenticateBarcodeMulti(barcodeList: List<BarcodeAuthMultiRequest>): Result<List<BarcodeAuthMultiResponse>> {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Authenticating ${barcodeList.size} barcodes")
                    val response = authApiService.authenticateBarcodeMulti(barcodeList)
    *//*
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        Log.d(TAG, "Barcode authentication successful")
                        Result.success(result)
                    } else {
                        Log.w(TAG, "Empty response from barcode authentication")
                        Result.failure(Exception("Empty response"))
                    }
                } else {
                    Log.w(TAG, "Barcode authentication failed: ${response.code()}")
                    Result.failure(Exception("Authentication failed: ${response.code()}"))
                }*//*
            } catch (e: Exception) {
                Log.e(TAG, "Barcode authentication error: ${e.message}", e)
                Result.failure(e)
            }
        }*/
}