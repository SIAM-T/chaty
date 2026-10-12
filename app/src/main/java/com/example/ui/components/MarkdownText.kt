package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Formats WhatsApp-style markdown:
 * *bold* -> Bold
 * _italics_ -> Italic
 * ~strike~ -> Strikethrough
 * `code` -> Monospace
 */
fun parseMarkdownToAnnotatedString(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // Bold: *text*
                text[i] == '*' && text.indexOf('*', i + 1) != -1 -> {
                    val end = text.indexOf('*', i + 1)
                    val content = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                    append(content)
                    pop()
                    i = end + 1
                }
                // Italics: _text_
                text[i] == '_' && text.indexOf('_', i + 1) != -1 -> {
                    val end = text.indexOf('_', i + 1)
                    val content = text.substring(i + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                    append(content)
                    pop()
                    i = end + 1
                }
                // Strikethrough: ~text~
                text[i] == '~' && text.indexOf('~', i + 1) != -1 -> {
                    val end = text.indexOf('~', i + 1)
                    val content = text.substring(i + 1, end)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor))
                    append(content)
                    pop()
                    i = end + 1
                }
                // Monospace / Code: `code`
                text[i] == '`' && text.indexOf('`', i + 1) != -1 -> {
                    val end = text.indexOf('`', i + 1)
                    val content = text.substring(i + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = baseColor.copy(alpha = 0.15f),
                            color = baseColor
                        )
                    )
                    append(" $content ")
                    pop()
                    i = end + 1
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
fun FormattedChatMessageText(
    text: String,
    textColor: Color,
    fontSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    val annotated = parseMarkdownToAnnotatedString(text, textColor)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
        lineHeight = 22.sp,
        modifier = modifier
    )
}
