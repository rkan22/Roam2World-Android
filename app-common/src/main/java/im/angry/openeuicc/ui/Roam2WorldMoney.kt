package im.angry.openeuicc.ui

internal fun r2wMoney(value: String?, blank: String = "--"): String {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return blank
    if (raw.contains("•")) return raw

    val currencyChars = listOf('$', '€', '£', '₺')
    if (currencyChars.any { raw.contains(it) }) return raw

    val sign = when {
        raw.startsWith("+") -> "+"
        raw.startsWith("-") -> "-"
        else -> ""
    }

    val amount = raw.removePrefix("+").removePrefix("-").trim()
    if (amount.isBlank()) return blank
    if (currencyChars.any { amount.contains(it) }) {
        return if (sign.isBlank()) amount else "$sign $amount"
    }

    // If the backend already returns a currency code like "USD 10" or "10 USD", keep it as-is.
    if (amount.any { it.isLetter() }) return raw

    return if (sign.isBlank()) {
        "$$amount"
    } else {
        "$sign $$amount"
    }
}
