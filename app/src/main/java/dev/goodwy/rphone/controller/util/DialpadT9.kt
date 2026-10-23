package dev.goodwy.rphone.controller.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private typealias CharMap = HashMap<Char, Int>

/**
 * A helper class to map chars of various alphabets to T9 numbers.
 *
 * - `letters`      → all characters for matching (charMap), including extended variants
 * - `displayLetters`→ short version shown on dialpad keys
 *
 * Latin (English) is always available ( initialised in the init block ).
 * Additional languages are loaded from `assets/t9_languages.json` via [init].
 */
object DialpadT9 {

    const val LOCALE_EN = "en"

    @Serializable
    private data class T9LanguageData(
        val lang: String,
        val letters: List<String>,
        val display: List<String>? = null
    )

    private class T9Language {
        val letters = Array(8) { "" }          // full set — used for charMap (matching)
        val displayLetters = Array(8) { "" }   // short set — used for display on keys
        val charMap = CharMap()
    }

    private val languages = HashMap<String, T9Language>()

    var Initialized = false

    init {
        initLatinChars()
    }

    /* ── Latin (always available) ──────────────────────────────────────── */

    private fun initLatinChars() {
        val lang = T9Language()

        // Standard ITU E.161 / ISO 9995-8 layout
        val standard = arrayOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
        for ((i, letters) in standard.withIndex()) {
            val digit = i + 2
            lang.letters[i] = letters
            lang.displayLetters[i] = letters
            for (c in letters) lang.charMap[c] = digit
        }

        // Digits 0-9 → map to themselves (needed so T9Matcher can handle digits in names)
        for (c in '0'..'9') lang.charMap[c] = c.digitToInt()

        // Extended Latin characters (Vietnamese, Polish, Czech, Slovak, Turkish, etc.)
        val extended = mapOf(
            2 to "ÀÁÂÃÄÅÆÇĄĆĀĂẢẠẦẤẬẨẪẰẮẶẲẴČ",
            3 to "ÈÉÊËĘĖĒĐĎẼẺẸỀẾỆỂỄĚ",
            4 to "ÌÍÎÏĮĪĞĦĨỈỊ",
            5 to "ŁĹĽĿ",
            6 to "ÑÒÓÔÕÖØŃŇŌỎỌỒỐỘỔỖỜỚỢỞỠ",
            7 to "ŚŠŞßŘṢȘ",
            8 to "ÙÚÛÜŪȚŤŢŨỦỤỪỨỰỬỮŮ",
            9 to "ŹŻŽÝŸŶỸỶỴŔ"
        )
        for ((digit, chars) in extended) {
            for (c in chars) lang.charMap[c] = digit
        }

        // Hebrew final letters mapping (for T9 search only)
        // We associate the final letters with the same numbers as their standard forms
        val hebrewFinals = mapOf(
            'ך' to 5,
            'ם' to 4,
            'ן' to 4,
            'ף' to 9,
            'ץ' to 8
        )
        for ((c, digit) in hebrewFinals) {
            lang.charMap[c] = digit
        }

        languages[LOCALE_EN] = lang
    }

    /* ── Load extra languages from JSON ────────────────────────────────── */

    fun init(context: Context) {
        if (Initialized) return
        try {
            val jsonText = context.assets.open("t9_languages.json")
                .bufferedReader().use { it.readText() }
            readFromJson(jsonText)
        } catch (_: Exception) {
            Initialized = true   // don't retry on every call
        }
    }

    fun readFromJson(jsonText: String) {
        val json = Json { ignoreUnknownKeys = true }
        val languagesData = json.decodeFromString<List<T9LanguageData>>(jsonText)

        for (data in languagesData) {
            if (languages[data.lang] != null) continue

            val language = T9Language()

            data.letters.forEachIndexed { i, letters ->
                if (i >= 8) return@forEachIndexed
                language.letters[i] = letters
                for (c in letters) {
                    if (c.isLetter()) {
                        language.charMap[c] = i + 2
                    }
                }
            }

            val displayLetters = data.display ?: data.letters
            displayLetters.forEachIndexed { i, letters ->
                if (i >= 8) return@forEachIndexed
                language.displayLetters[i] = letters
            }

            languages[data.lang] = language
        }

        Initialized = true
    }

    /* ── Public API ────────────────────────────────────────────────────── */

    /** Returns the short display letters for a dialpad key (e.g. "АБВГ" for key 2, lang "ru"). */
    fun getLettersForNumber(number: Int, language: String): String {
        val lang = languages[language]
        return if (lang == null || number < 2 || number > 9) ""
        else lang.displayLetters[number - 2]
    }

    /** Converts a whole string to its T9 digit sequence. */
    fun convertLettersToNumbers(letters: String, language: String): String {
        val lang = languages[language]
        val langEn = languages[LOCALE_EN]
        val sb = StringBuilder(letters.length)
        for (c in letters) {
            val upper = c.uppercaseChar()
            sb.append(
                when {
                    lang?.charMap?.containsKey(upper) == true -> lang.charMap[upper]!!
                    langEn?.charMap?.containsKey(upper) == true -> langEn.charMap[upper]!!
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** Maps a single character to its T9 digit, or null if unmapped. */
    fun charToT9(c: Char, language: String): Char? {
        val lang = languages[language]
        val langEn = languages[LOCALE_EN]
        val upper = c.uppercaseChar()
        val number = when {
            lang?.charMap?.containsKey(upper) == true -> lang.charMap[upper]
            langEn?.charMap?.containsKey(upper) == true -> langEn.charMap[upper]
            else -> null
        }
        return number?.digitToChar()
    }

    /** All secondary (non-English) languages that have been loaded. */
    fun getSupportedSecondaryLanguages(): List<String> =
        languages.keys.filter { it != LOCALE_EN }.sorted()

    /**
     * Returns the best-matching language code for the given locale,
     * falling back to English.  Handles the "iw" → "he" alias.
     */
    fun getCurrentLanguage(locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val lang = locale.language
        val normalized = if (lang == "iw") "he" else lang
        return if (languages.containsKey(normalized)) normalized else LOCALE_EN
    }
}
