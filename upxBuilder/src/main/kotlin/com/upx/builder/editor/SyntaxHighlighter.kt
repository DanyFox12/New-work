package com.upx.builder.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.upx.builder.theme.SyntaxPalette

/**
 * A lightweight, single-pass tokenizer that colours source code for display.
 *
 * It is deliberately not a full parser: it recognises line/block comments,
 * string and char literals, numbers, annotations, keywords and call sites well
 * enough to give readable, Android-Studio-style highlighting without the cost of
 * a real grammar. Anything it does not recognise falls back to the plain colour.
 */
object SyntaxHighlighter {

    fun highlight(code: String, language: Language, palette: SyntaxPalette): AnnotatedString {
        if (language == Language.PLAIN || code.isEmpty()) {
            return AnnotatedString(code, spanStyle = SpanStyle(color = palette.plain))
        }
        return buildAnnotatedString {
            var i = 0
            val n = code.length
            while (i < n) {
                val c = code[i]
                when {
                    // Line comment
                    c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                        val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                        withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Hash comment (yaml/pubspec-ish, harmless for code)
                    c == '#' -> {
                        val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                        withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Block comment
                    c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                        val close = code.indexOf("*/", i + 2)
                        val end = if (close == -1) n else close + 2
                        withStyle(SpanStyle(color = palette.comment)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // String / char literal
                    c == '"' || c == '\'' -> {
                        val end = scanString(code, i, c)
                        withStyle(SpanStyle(color = palette.string)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Annotation / decorator
                    c == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_') -> {
                        var j = i + 1
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        withStyle(SpanStyle(color = palette.annotation)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Number
                    c.isDigit() -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == 'x' ||
                                    code[j] == '_')) j++
                        withStyle(SpanStyle(color = palette.number)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Identifier or keyword
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        val word = code.substring(i, j)
                        val nextNonSpace = code.getOrNull(skipSpaces(code, j))
                        val style = when {
                            word in language.keywords -> SpanStyle(color = palette.keyword)
                            // Looks like a Type (UpperCamelCase)
                            word.first().isUpperCase() -> SpanStyle(color = palette.type)
                            // Looks like a function call
                            nextNonSpace == '(' -> SpanStyle(color = palette.function)
                            else -> SpanStyle(color = palette.plain)
                        }
                        withStyle(style) { append(word) }
                        i = j
                    }
                    else -> {
                        withStyle(SpanStyle(color = palette.plain)) { append(c) }
                        i++
                    }
                }
            }
        }
    }

    private fun scanString(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        val n = code.length
        while (i < n) {
            when (code[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                '\n' -> return i // unterminated on this line
                else -> i++
            }
        }
        return n
    }

    private fun skipSpaces(code: String, from: Int): Int {
        var i = from
        while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
        return i
    }
}
