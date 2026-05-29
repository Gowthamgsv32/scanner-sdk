package com.example.scanner_sdk.customview.helper

/**
 * Digital Link auth trailer extraction — aligned with iOS `ResolverPage` / `GS1URLParser`.
 * Supports `?98=…`, `/97=…`, and parenthetical suffixes `…(98)ENC(97)48`.
 */
internal object GS1DigitalLinkAuth {

    private val trailingParenAuth = Regex("""\(98\)[^?#]*(\(97\)[^?#]*)?$""")
    private val paren98 = Regex("""\(98\)([^()]*)""")
    private val paren97 = Regex("""\(97\)([^()]*)""")
    private val query98 = Regex("""98=([^&/]+)""")
    private val query97 = Regex("""97=([^&/]+)""")
    private val path97 = Regex("""/97=([^/?\u001D]+)""")

    fun stripTrailingAuthParentheticals(url: String): String =
        trailingParenAuth.replace(url, "")

    fun extractAuthFromParentheticals(text: String): Pair<String?, String?> {
        val enc = paren98.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        val co = paren97.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return enc to co
    }

    /** Clean URL for `barcode_data` — no auth trailer in path or query. */
    fun cleanDigitalLinkUrl(raw: String): String {
        var url = raw.substringBefore(GS1ParseSupport.FNC1).trim()
        url = url.substringBefore("?98=").substringBefore("&98=")
        url = stripTrailingAuthParentheticals(url)
        url = path97.replace(url, "").trimEnd('/')
        return url
    }

    fun extractEncryptedText(raw: String): String {
        val (fromParen, _) = extractAuthFromParentheticals(raw)
        if (!fromParen.isNullOrEmpty()) return fromParen
        query98.find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    fun extractCompanyId(raw: String): String {
        val (_, fromParen) = extractAuthFromParentheticals(raw)
        if (!fromParen.isNullOrEmpty()) return fromParen
        query97.find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { if (it.isNotEmpty()) return it }
        path97.find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    /** Raw query after `?` (handles glued `(98)…` that breaks [Uri.getQuery]). */
    fun rawQueryString(url: String): String? {
        val q = url.indexOf('?')
        if (q < 0) return null
        var after = url.substring(q + 1)
        val hash = after.indexOf('#')
        if (hash >= 0) after = after.substring(0, hash)
        after = stripTrailingAuthParentheticals(after)
        return after.trim().takeIf { it.isNotEmpty() }
    }
}
