package im.angry.openeuicc.ui

object PackageNameCleaner {
    fun clean(rawName: String?): String {
        val raw = rawName.orEmpty().trim()
        if (raw.isBlank()) return "eSIM Package"

        val provider = providerName(raw)
        val data = dataLabel(raw)

        return listOf(provider, data)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { cleanRaw(raw) }
    }

    fun cleanWithPrefix(prefix: String, rawName: String?): String =
        "$prefix: ${clean(rawName)}"

    private fun providerName(rawName: String): String {
        val text = rawName.lowercase()

        return when {
            text.contains("orange holiday") ||
                text.contains("orange europe") ||
                text.contains("e-211") ||
                text.contains("e211") -> "Orange Europe"

            text.contains("orange world") ||
                text.contains("global") -> "Orange World"

            text.contains("orange balkans") ||
                text.contains("balkans") ||
                text.contains("europe-(41)") ||
                text.contains("europe（41）") ||
                text.contains("europe-41") -> "Orange Balkans"

            text.contains("vodafone") -> "Vodafone Europe"

            text.contains("turkey") ||
                text.contains("turkiye") ||
                text.contains("türkiye") -> "Roam2World Turkey"

            else -> ""
        }
    }

    private fun dataLabel(rawName: String): String {
        val match = Regex("""(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE).find(rawName)
        return match?.value
            ?.uppercase()
            ?.replace("GB", " GB")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            .orEmpty()
    }

    private fun cleanRaw(rawName: String): String =
        rawName
            .replace("【Esim】", "", ignoreCase = true)
            .replace("【SIMCARD】", "SIM Card", ignoreCase = true)
            .replace("—", " ")
            .replace("–", " ")
            .replace(Regex("""\(valid for .*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
