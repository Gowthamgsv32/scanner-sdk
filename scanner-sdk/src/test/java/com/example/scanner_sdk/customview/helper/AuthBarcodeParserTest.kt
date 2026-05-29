package com.example.scanner_sdk.customview.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthBarcodeParserTest {

    private val FNC1 = '\u001D'

    @Test
    fun fnc1Gs1_matchesIosBracketedPayload() {
        val raw =
            "010890603807001010BATCH00${FNC1}1126050598M4M3BLU4675IQA====${FNC1}9748"
        val result = AuthBarcodeParser.parse(raw, "QR Code")

        assertEquals("(01)08906038070010(10)BATCH00(11)260505", result.barcodeData)
        assertEquals("M4M3BLU4675IQA====", result.encryptedText)
        assertEquals("48", result.companyId)
    }

    @Test
    fun fnc1Gs1_batch002_packagingAndExpiryDates() {
        val raw =
            "010189041033141710BATCH002" +
                "${FNC1}1325040417280505" +
                "${FNC1}98EGI7WN6RCW7VEA====" +
                "${FNC1}9748"
        val result = AuthBarcodeParser.parse(raw, "QR Code")

        assertEquals("EGI7WN6RCW7VEA====", result.encryptedText)
        assertEquals("48", result.companyId)
        assertTrue(result.barcodeData.contains("(01)01890410331417"))
        assertTrue(result.barcodeData.contains("(10)BATCH002"))
        assertTrue(result.barcodeData.contains("(13)250404"))
        assertTrue(result.barcodeData.contains("(17)280505"))
        assertFalse(result.barcodeData.contains("(02)"))
        assertFalse(result.barcodeData.contains("(98)"))
    }

    @Test
    fun flattenedGs1_batch002_matchesIos() {
        val raw =
            "010189041033141710BATCH002132504041728050598EGI7WN6RCW7VEA====9748"
        val split = GS1Utils.splitByAI98(raw)
        requireNotNull(split)
        assertEquals("EGI7WN6RCW7VEA====", split.encryptedText)
        assertEquals("48", split.companyId)

        val result = AuthBarcodeParser.parse(raw, "QR Code")
        assertTrue(result.barcodeData.contains("(10)BATCH002"))
        assertFalse(result.barcodeData.contains("(02)"))
        assertEquals("EGI7WN6RCW7VEA====", result.encryptedText)
    }

    @Test
    fun digitalLink_extractsBaseUrlAndEncryptedText() {
        val raw =
            "https://dl.ratifye.ai/01/08906038070010/22/213445/10/BATCH001/21/234567?98=BLQMYP3CPV4YTA====/97=0"
        val result = AuthBarcodeParser.parse(raw, "QR Code")

        assertEquals(
            "https://dl.ratifye.ai/01/08906038070010/22/213445/10/BATCH001/21/234567",
            result.barcodeData,
        )
        assertEquals("BLQMYP3CPV4YTA====", result.encryptedText)
    }

    @Test
    fun digitalLinkBaseUrl_stripsQueryAndTrailing97() {
        val raw =
            "https://dl.ratifye.ai/01/08906038070010/22/213445/10/BATCH001/21/234567?98=BLQMYP3CPV4YTA====/97=0"
        assertEquals(
            "https://dl.ratifye.ai/01/08906038070010/22/213445/10/BATCH001/21/234567",
            AuthBarcodeParser.digitalLinkBaseUrl(raw),
        )
    }
}
