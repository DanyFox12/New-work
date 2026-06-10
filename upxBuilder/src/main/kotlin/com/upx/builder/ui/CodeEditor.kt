package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upx.builder.editor.Language
import com.upx.builder.editor.SyntaxHighlighter
import com.upx.builder.theme.AppTheme

/**
 * A scrollable code editor with a line-number gutter, live syntax highlighting,
 * auto-indentation, auto-closing brackets, and a completion bar that suggests
 * language keywords and identifiers from the file as you type.
 */
@Composable
fun CodeEditor(
    text: String,
    language: Language,
    theme: AppTheme,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    // External change (tab switch, project open): resync, keep cursor in range.
    if (value.text != text) {
        value = TextFieldValue(text, TextRange(value.selection.start.coerceAtMost(text.length)))
    }

    val scroll = rememberScrollState()
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val highlight = remember(language, theme.id) {
        VisualTransformation { input ->
            TransformedText(
                SyntaxHighlighter.highlight(input.text, language, theme.syntax),
                OffsetMapping.Identity,
            )
        }
    }

    // ----- Code completion -----
    val cursor = value.selection.start
    val prefixStart = wordStart(value.text, cursor)
    val prefix = if (value.selection.collapsed && cursor <= value.text.length) {
        value.text.substring(prefixStart, cursor)
    } else ""
    val suggestions = buildSuggestions(value.text, prefix, language)

    fun accept(suggestion: String) {
        val newText = value.text.substring(0, prefixStart) + suggestion + value.text.substring(cursor)
        val v = TextFieldValue(newText, TextRange(prefixStart + suggestion.length))
        value = v
        onTextChange(v.text)
    }

    Column(modifier = modifier) {
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.gutterBackground)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                suggestions.forEach { s ->
                    val isKeyword = s in language.keywords
                    Text(
                        text = s,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (isKeyword) theme.syntax.keyword else theme.syntax.plain,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.editorBackground)
                            .clickable { accept(s) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth().background(theme.editorBackground).verticalScroll(scroll)) {
            // Line-number gutter
            Column(
                modifier = Modifier
                    .background(theme.gutterBackground)
                    .fillMaxHeight()
                    .width(48.dp)
                    .padding(top = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                for (n in 1..lineCount) {
                    Text(
                        text = n.toString(),
                        style = mono.copy(color = theme.lineNumber, textAlign = TextAlign.End),
                    )
                }
            }

            // Editing surface
            Box(modifier = Modifier.fillMaxSize().padding(start = 8.dp, top = 8.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = { new ->
                        val assisted = applyEditorAssists(value, new)
                        value = assisted
                        onTextChange(assisted.text)
                    },
                    textStyle = mono.copy(color = theme.syntax.plain),
                    cursorBrush = SolidColor(theme.accent),
                    visualTransformation = highlight,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Index where the identifier containing/preceding [cursor] starts. */
private fun wordStart(text: String, cursor: Int): Int {
    var s = cursor.coerceIn(0, text.length)
    while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
    return s
}

private val identifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]{2,}")

/**
 * Completion candidates for the word being typed: language keywords first,
 * then identifiers already present in the file. Shown after 2+ characters.
 */
private fun buildSuggestions(text: String, prefix: String, language: Language): List<String> {
    if (prefix.length < 2) return emptyList()
    val fromKeywords = language.keywords
        .filter { it.startsWith(prefix) && it != prefix }
        .sorted()
    val fromFile = identifierRegex.findAll(text)
        .map { it.value }
        .filter { it.startsWith(prefix) && it != prefix && it !in language.keywords }
        .distinct()
        .sorted()
        .toList()
    return (fromKeywords + fromFile).take(8)
}

/**
 * Smart-typing helpers applied to single-character insertions:
 * - Enter keeps the previous line's indentation (plus one level after `{`, `(` or `:`).
 * - Typing `(`, `[` or `{` inserts the matching closer and leaves the cursor between.
 */
private fun applyEditorAssists(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (!new.selection.collapsed || new.text.length != old.text.length + 1) return new
    val pos = new.selection.start - 1
    if (pos < 0 || pos >= new.text.length) return new

    return when (val ch = new.text[pos]) {
        '\n' -> {
            val lineStart = if (pos == 0) 0 else new.text.lastIndexOf('\n', pos - 1) + 1
            val prevLine = new.text.substring(lineStart, pos)
            val baseIndent = prevLine.takeWhile { it == ' ' || it == '\t' }
            val trimmed = prevLine.trimEnd()
            val extra = if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith(":")) "    " else ""
            val insert = baseIndent + extra
            if (insert.isEmpty()) new
            else TextFieldValue(
                text = new.text.substring(0, pos + 1) + insert + new.text.substring(pos + 1),
                selection = TextRange(pos + 1 + insert.length),
            )
        }
        '(', '[', '{' -> {
            val closer = when (ch) { '(' -> ")"; '[' -> "]"; else -> "}" }
            TextFieldValue(
                text = new.text.substring(0, pos + 1) + closer + new.text.substring(pos + 1),
                selection = TextRange(pos + 1),
            )
        }
        else -> new
    }
}
