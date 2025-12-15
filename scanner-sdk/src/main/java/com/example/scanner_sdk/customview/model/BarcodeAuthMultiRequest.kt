package com.example.scanner_sdk.customview.model

import com.google.gson.annotations.SerializedName

data class BarcodeAuthMultiRequest(
    @SerializedName("barcode_data")
    val barcodeData: String,
    @SerializedName("encrypted_text")
    val encryptedText: String
)


data class BarcodeAuthMultiResponse(
    @SerializedName("barcode_data")
    val barcodeData: String,
    @SerializedName("encrypted_text")
    val encryptedText: String,
    @SerializedName("quality")
    val quality: String? = null,
    @SerializedName("success")
    val success: Boolean? = null,
    @SerializedName("verified")
    val verified: Boolean? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("product_info")
    val productInfo: ProductInfo? = null,
    @SerializedName("authentication_code")
    val authenticationCode: String? = null,
    @SerializedName("error")
    val error: String? = null
)

data class ProductInfo(
    @SerializedName("name")
    val name: String,
    @SerializedName("brand")
    val brand: String,
    @SerializedName("gtin")
    val gtin: String? = null,
    @SerializedName("batch_number")
    val batchNumber: String? = null,
    @SerializedName("expiry_date")
    val expiryDate: String? = null,
    @SerializedName("serial_number")
    val serialNumber: String? = null
)