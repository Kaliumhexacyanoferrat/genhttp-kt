package org.genhttp.testing.acceptance.modules.i18n

import org.genhttp.modules.i18n.parsers.CultureInfoParser

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

import java.util.Locale
import java.util.stream.Stream

import kotlin.test.assertEquals

class LanguageParserTests {

    companion object {

        // region Helper method

        // the regions .NET assigns to neutral cultures in CultureInfo.CreateSpecificCulture
        private val specificCultures = mapOf("en" to "en-US", "de" to "de-DE", "fr" to "fr-FR")

        private fun culture(name: String): Locale = Locale.forLanguageTag(specificCultures[name] ?: name)

        private fun data(input: String?, vararg expectedResult: Locale): Arguments = Arguments.of(input, expectedResult.toList())

        // endregion

        @JvmStatic
        fun testData(): Stream<Arguments> = Stream.of(
            // Empty input
            data(null),
            data(""),
            data(" "),
            data(","),
            data(" ,"),
            data(", "),
            data(" , "),

            // Invalid input
            data("unknown"),
            data("unknown,zw"),

            // Invalid quality
            data("en;q=0_8,de;q=0#9", culture("en"), culture("de")),

            // Trailing/leading whitespaces
            data(" en", culture("en")),
            data("en ", culture("en")),

            // Single language
            data("en", culture("en")),
            data("eN", culture("en")),
            data("EN", culture("en")),
            data("en-US", culture("en-US")),
            data("en-UK", culture("en-UK")),
            data("en-uk", culture("en-uk")),

            // Multiple languages
            data("en,de", culture("en"), culture("de")),
            data("en,de,", culture("en"), culture("de")),
            data(",en,de", culture("en"), culture("de")),
            data("de,en", culture("de"), culture("en")),
            data("en, de", culture("en"), culture("de")),
            data("en, de, fr", culture("en"), culture("de"), culture("fr")),
            data("en,en,de", culture("en"), culture("en"), culture("de")),

            data("de,en-uk,fr", culture("de"), culture("en-UK"), culture("fr")),
            data("de, en-uk,fr", culture("de"), culture("en-UK"), culture("fr")),
            data("de,en-uk ,fr", culture("de"), culture("en-UK"), culture("fr")),

            // Preference order
            data("en;q=0.8,de;q=0.9", culture("de"), culture("en")),
            data("en;q=0.9,de;q=0.8", culture("en"), culture("de")),
            data("en;q=0.9,de;q=0.9", culture("en"), culture("de")),
            data("en;q=0.9,de;q=0.9,fr;q=0.8", culture("en"), culture("de"), culture("fr")),

            // Preference order mixed
            data("en;q=0.9,fr;q=0.8,de", culture("de"), culture("en"), culture("fr")),
            data("en ;q=0.9,fr;q=0.8,de", culture("de"), culture("en"), culture("fr")),
            data("en; q=0.9 ,  fr; q=  0.8,  de", culture("de"), culture("en"), culture("fr")),
            data("en-UK;q=0.9,fr;q=0.8,de", culture("de"), culture("en-uk"), culture("fr")),
            data("en-UK;q=0.9,ww;q=0.8,de", culture("de"), culture("en-uk")),

            // Malformed
            data("en;q=0.9,fr;;q=0.8,de", culture("fr"), culture("de"), culture("en")),
            data("en;q=0.9,frq=0.8,de", culture("de"), culture("en"))
        )

    }

    @ParameterizedTest
    @MethodSource("testData")
    fun testLanguageParser(input: String?, expectedResult: List<Locale>) {
        val actualResult = CultureInfoParser.parseFromLanguage(input)

        assertEquals(expectedResult, actualResult)
    }

}
