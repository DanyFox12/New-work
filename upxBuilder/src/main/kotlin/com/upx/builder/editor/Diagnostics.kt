package com.upx.builder.editor

/** Severity of a code problem found by the analyzer. */
enum class DiagnosticSeverity { ERROR, WARNING, INFO }

/** A single problem detected in the active file. Lines are 1-based. */
data class Diagnostic(
    val line: Int,
    val severity: DiagnosticSeverity,
    val message: String,
)

/**
 * Bridge to the C++ analyzer (libupxanalyzer.so, built with the NDK on Android).
 * On platforms where the native library is not bundled — e.g. the desktop
 * edition — [available] is false and [DiagnosticsEngine] uses its pure-Kotlin
 * fallback, which performs the same checks.
 */
object NativeAnalyzer {
    val available: Boolean = try {
        System.loadLibrary("upxanalyzer")
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Analyzes [code] and returns one problem per line of output, formatted
     * "LINE|SEVERITY|MESSAGE" where SEVERITY is E, W or I.
     */
    external fun analyze(code: String, lineComment: String): String
}

/**
 * Finds problems in source code: unbalanced brackets, unterminated strings,
 * overly long lines and TODO/FIXME markers. Fast single-pass scanning — it runs
 * on every edit, so it must stay linear in the file size.
 */
object DiagnosticsEngine {

    fun analyze(code: String, language: Language): List<Diagnostic> {
        if (language == Language.PLAIN || code.isBlank()) return emptyList()
        if (NativeAnalyzer.available) {
            runCatching { return parse(NativeAnalyzer.analyze(code, language.lineComment)) }
        }
        return analyzeInKotlin(code, language)
    }

    private fun parse(raw: String): List<Diagnostic> =
        raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                Diagnostic(
                    line = parts[0].toIntOrNull() ?: 1,
                    severity = when (parts[1]) {
                        "E" -> DiagnosticSeverity.ERROR
                        "W" -> DiagnosticSeverity.WARNING
                        else -> DiagnosticSeverity.INFO
                    },
                    message = parts[2],
                )
            }
            .toList()

    /** Pure-Kotlin analyzer mirroring the checks in analyzer.cpp. */
    internal fun analyzeInKotlin(code: String, language: Language): List<Diagnostic> {
        val problems = mutableListOf<Diagnostic>()
        val cStyle = language.lineComment == "//"
        val openers = ArrayDeque<Pair<Char, Int>>() // bracket char to line

        var line = 1
        var i = 0
        val n = code.length
        var lineLength = 0

        fun newline() {
            if (lineLength > 150) {
                problems.add(Diagnostic(line, DiagnosticSeverity.WARNING, "Line is very long ($lineLength chars) — consider breaking it up."))
            }
            line++
            lineLength = 0
        }

        while (i < n) {
            val c = code[i]
            lineLength++
            when {
                c == '\n' -> { lineLength--; newline(); i++ }

                // Line comments — scan for TODO/FIXME, then skip to end of line.
                (cStyle && c == '/' && i + 1 < n && code[i + 1] == '/') || (!cStyle && c == '#') -> {
                    val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                    val comment = code.substring(i, end)
                    if (comment.contains("TODO")) problems.add(Diagnostic(line, DiagnosticSeverity.INFO, "TODO found: ${comment.trim().take(80)}"))
                    if (comment.contains("FIXME")) problems.add(Diagnostic(line, DiagnosticSeverity.WARNING, "FIXME found: ${comment.trim().take(80)}"))
                    lineLength += end - i - 1
                    i = end
                }

                // Block comments (C-like only)
                cStyle && c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val close = code.indexOf("*/", i + 2)
                    if (close == -1) {
                        problems.add(Diagnostic(line, DiagnosticSeverity.ERROR, "Unterminated block comment ('/*' without '*/')."))
                        i = n
                    } else {
                        for (j in i until close + 2) if (code[j] == '\n') { newline() }
                        i = close + 2
                    }
                }

                // Python triple-quoted strings — skip wholesale.
                !cStyle && (c == '"' || c == '\'') && i + 2 < n && code[i + 1] == c && code[i + 2] == c -> {
                    val triple = "$c$c$c"
                    val close = code.indexOf(triple, i + 3)
                    if (close == -1) {
                        problems.add(Diagnostic(line, DiagnosticSeverity.ERROR, "Unterminated triple-quoted string."))
                        i = n
                    } else {
                        for (j in i until close + 3) if (code[j] == '\n') { newline() }
                        i = close + 3
                    }
                }

                // String / char literals
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    var terminated = false
                    while (j < n) {
                        when (code[j]) {
                            '\\' -> j += 2
                            c -> { terminated = true; j++; break }
                            '\n' -> break
                            else -> j++
                        }
                    }
                    if (!terminated) {
                        problems.add(Diagnostic(line, DiagnosticSeverity.ERROR, "Unterminated string literal."))
                    }
                    lineLength += (minOf(j, n) - i - 1)
                    i = minOf(j, n)
                }

                c == '(' || c == '[' || c == '{' -> { openers.addLast(c to line); i++ }

                c == ')' || c == ']' || c == '}' -> {
                    val expectedOpener = when (c) { ')' -> '('; ']' -> '['; else -> '{' }
                    val top = openers.lastOrNull()
                    if (top == null) {
                        problems.add(Diagnostic(line, DiagnosticSeverity.ERROR, "Unmatched closing '$c'."))
                    } else if (top.first != expectedOpener) {
                        problems.add(Diagnostic(line, DiagnosticSeverity.ERROR, "Mismatched bracket: expected closing for '${top.first}' (opened at line ${top.second}) but found '$c'."))
                        openers.removeLast()
                    } else {
                        openers.removeLast()
                    }
                    i++
                }

                else -> i++
            }
        }
        if (lineLength > 150) {
            problems.add(Diagnostic(line, DiagnosticSeverity.WARNING, "Line is very long ($lineLength chars) — consider breaking it up."))
        }
        openers.forEach { (ch, ln) ->
            problems.add(Diagnostic(ln, DiagnosticSeverity.ERROR, "Unclosed '$ch' — no matching closing bracket found."))
        }
        return problems.sortedBy { it.line }
    }
}
