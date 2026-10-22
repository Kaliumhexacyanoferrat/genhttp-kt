package org.genhttp.modules.i18n.parsers

import java.util.Locale

/**
 * Parses the value of language headers such as `Accept-Language`.
 */
public object CultureInfoParser {

    private val QualityPattern = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")

    // regions .NET (CLDR) assigns to neutral cultures when creating specific cultures
    private val DefaultRegions: Map<String, String> = mapOf(
        "af" to "ZA", "am" to "ET", "ar" to "SA", "az" to "AZ", "be" to "BY", "bg" to "BG", "bn" to "BD", "bs" to "BA",
        "ca" to "ES", "cs" to "CZ", "cy" to "GB", "da" to "DK", "de" to "DE", "el" to "GR", "en" to "US", "es" to "ES",
        "et" to "EE", "eu" to "ES", "fa" to "IR", "fi" to "FI", "fil" to "PH", "fr" to "FR", "ga" to "IE", "gl" to "ES",
        "gu" to "IN", "he" to "IL", "hi" to "IN", "hr" to "HR", "hu" to "HU", "hy" to "AM", "id" to "ID", "is" to "IS",
        "it" to "IT", "ja" to "JP", "ka" to "GE", "kk" to "KZ", "km" to "KH", "kn" to "IN", "ko" to "KR", "ky" to "KG",
        "lo" to "LA", "lt" to "LT", "lv" to "LV", "mk" to "MK", "ml" to "IN", "mn" to "MN", "mr" to "IN", "ms" to "MY",
        "mt" to "MT", "my" to "MM", "nb" to "NO", "ne" to "NP", "nl" to "NL", "nn" to "NO", "no" to "NO", "pa" to "IN",
        "pl" to "PL", "ps" to "AF", "pt" to "BR", "quz" to "BO", "ro" to "RO", "ru" to "RU", "si" to "LK", "sk" to "SK",
        "sl" to "SI", "sq" to "AL", "sr" to "RS", "sv" to "SE", "sw" to "KE", "ta" to "IN", "te" to "IN", "th" to "TH",
        "tr" to "TR", "uk" to "UA", "ur" to "PK", "uz" to "UZ", "vi" to "VN", "zh" to "CN", "zu" to "ZA"
    )

    private val IsoLanguages: Set<String> by lazy { Locale.getISOLanguages().toSet() }

    private val AvailableLocales: List<Locale> by lazy {
        Locale.getAvailableLocales().filter { it.country.isNotEmpty() && it.variant.isEmpty() && it.script.isEmpty() }
    }

    /**
     * Parses the given language header (e.g. from `Accept-Language`) into a list of locales, sorted by their
     * quality values in descending order. Languages without a region are resolved to their default region
     * (e.g. `en` becomes `en-US`), the same way `CultureInfo.CreateSpecificCulture` does in .NET.
     * If no valid languages are found, an empty list is returned.
     *
     * Specification: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language
     *
     * @param language The language header string to parse
     * @return The locales parsed from the input string
     */
    public fun parseFromLanguage(language: String?): List<Locale> {
        if (language == null || language.isBlank()) {
            return emptyList()
        }

        val text = language.trim()

        if (text.isEmpty()) {
            return emptyList()
        }

        val entries = mutableListOf<Pair<String, Double>>()

        for (rawSegment in text.split(',')) {
            val segment = rawSegment.trim()

            if (segment.isEmpty()) {
                continue
            }

            // segment format: "lang[-region][;q=...]" or just "lang[-region]"
            val semicolonIndex = segment.indexOf(';')

            var qValue = 1.0
            val languagePart: String

            if (semicolonIndex >= 0) {
                languagePart = segment.substring(0, semicolonIndex).trim()

                val afterSemicolon = segment.substring(semicolonIndex + 1).trim()

                // afterSemicolon should look like "q=0.5"
                if (afterSemicolon.startsWith("q=", ignoreCase = true)) {
                    val qText = afterSemicolon.substring(2).trim()

                    qValue = if (QualityPattern.matches(qText)) (qText.toDoubleOrNull() ?: 1.0) else 1.0
                }
            } else {
                languagePart = segment
            }

            if (languagePart.isNotEmpty()) {
                entries.add(languagePart to qValue)
            }
        }

        if (entries.isEmpty()) {
            return emptyList()
        }

        // sort by quality descending (stable)
        val sorted = entries.sortedByDescending { it.second }

        val results = ArrayList<Locale>(sorted.size)

        for ((lang, _) in sorted) {
            if (lang.isBlank()) {
                continue
            }

            val parsed = createSpecificCulture(lang)

            if (parsed != null) {
                results.add(parsed)
            }
        }

        return results
    }

    /**
     * Counterpart of `CultureInfo.CreateSpecificCulture`: returns null for unknown cultures and the invariant
     * culture.
     */
    private fun createSpecificCulture(tag: String): Locale? {
        val locale = Locale.forLanguageTag(tag)

        val language = locale.language

        if (language.isEmpty()) {
            return null
        }

        val known = when (language.length) {
            2 -> IsoLanguages.contains(language)
            3 -> true
            else -> false
        }

        if (!known) {
            return null
        }

        if (locale.country.isNotEmpty() || locale.script.isNotEmpty()) {
            return locale
        }

        val region = DefaultRegions[language] ?: AvailableLocales
            .filter { it.language == language }
            .let { candidates -> (candidates.firstOrNull { it.country.equals(language, ignoreCase = true) } ?: candidates.minByOrNull { it.country })?.country }

        return if (region != null) Locale.Builder().setLocale(locale).setRegion(region).build() else locale
    }

}
