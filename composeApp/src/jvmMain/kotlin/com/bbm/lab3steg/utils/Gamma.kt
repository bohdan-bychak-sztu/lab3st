package com.bbm.lab3steg.utils

object AlphabetUtils {
    const val UKRAINIAN = "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя"
    const val ENGLISH = "abcdefghijklmnopqrstuvwxyz"

    fun getAlphabet(language: String) = if (language == "Ukrainian") UKRAINIAN else ENGLISH
}

fun performGammaArithmetic(
    text: String,
    key: String,
    language: String,
    formula: String, // "S = Г + О", "S = Г - О", "S = О - Г"
    isEncrypt: Boolean
): String {
    val alphabet = AlphabetUtils.getAlphabet(language)
    val n = alphabet.length
    val cleanKey = key.lowercase()

    return text.mapIndexed { index, char ->
        val isUpper = char.isUpperCase()
        val lowerChar = char.lowercaseChar()
        val charIdx = alphabet.indexOf(lowerChar)

        if (charIdx == -1) return@mapIndexed char

        val gammaIdx = alphabet.indexOf(cleanKey[index % cleanKey.length])
        if (gammaIdx == -1) return@mapIndexed char

        val resultIdx = when (formula) {
            "S = Г + О" -> if (isEncrypt) (gammaIdx + charIdx) % n else (charIdx - gammaIdx + n) % n
            "S = Г - О" -> if (isEncrypt) (gammaIdx - charIdx + n) % n else (gammaIdx - charIdx + n) % n
            "S = О - Г" -> if (isEncrypt) (charIdx - gammaIdx + n) % n else (charIdx + gammaIdx) % n
            else -> charIdx
        }

        val resultChar = alphabet[resultIdx]
        if (isUpper) resultChar.uppercaseChar() else resultChar
    }.joinToString("")
}