package com.example.scanner_sdk.customview.helper

import org.junit.Assert.assertEquals
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
