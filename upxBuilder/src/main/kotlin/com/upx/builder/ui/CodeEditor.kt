package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
 * auto-indentation on Enter, and auto-closing brackets — the conveniences that
 * make typing code on a phone or desktop much faster.
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

    Row(modifier = modifier.background(theme.editorBackground).verticalScroll(scroll)) {
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
