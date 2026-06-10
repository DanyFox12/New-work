package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upx.builder.editor.Language
import com.upx.builder.editor.SyntaxHighlighter
import com.upx.builder.theme.AppTheme

/**
 * A scrollable code editor with a line-number gutter and live syntax
 * highlighting. Editing is real: text flows back through [onTextChange].
 */
@Composable
fun CodeEditor(
    text: String,
    language: Language,
    theme: AppTheme,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
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
                .width(56.dp)
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
                value = text,
                onValueChange = onTextChange,
                textStyle = mono.copy(color = theme.syntax.plain),
                cursorBrush = SolidColor(theme.accent),
                visualTransformation = highlight,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
