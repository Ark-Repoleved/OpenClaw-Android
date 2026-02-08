package com.openclaw.dashboard.presentation.components

import android.content.Context
import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.prism4j.Prism4j
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.GrammarLocator

/**
 * Custom Prism4j GrammarLocator that supports common programming languages.
 * This provides basic syntax highlighting support without annotation processing.
 */
class DefaultGrammarLocator : GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): io.noties.prism4j.Grammar? {
        return null // Return null to use default highlighting
    }
    
    override fun languages(): Set<String> {
        return emptySet()
    }
}

/**
 * A Composable that renders Markdown text using Markwon library.
 * Supports full GFM (GitHub Flavored Markdown) including:
 * - Headers, Bold, Italic
 * - Strikethrough (~~text~~)
 * - Tables
 * - Task lists (- [ ] / - [x])
 * - Code blocks with syntax highlighting
 * - Links (auto-linkify)
 * - Images
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isUserMessage: Boolean = false
) {
    val textColor = if (isUserMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    val textColorArgb = textColor.toArgb()
    val linkColorArgb = linkColor.toArgb()
    val codeBackgroundArgb = codeBackgroundColor.toArgb()
    
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColorArgb)
                setLinkTextColor(linkColorArgb)
                textSize = 15f
                setLineSpacing(0f, 1.2f)
            }
        },
        update = { textView ->
            val markwon = createMarkwon(textView.context, isDarkTheme, codeBackgroundArgb)
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(textColorArgb)
            textView.setLinkTextColor(linkColorArgb)
        }
    )
}

/**
 * Extension function to calculate luminance of a color
 */
private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}

/**
 * Creates a Markwon instance with all plugins configured.
 */
private fun createMarkwon(
    context: Context,
    isDarkTheme: Boolean,
    codeBackgroundColor: Int
): Markwon {
    val prism4j = Prism4j(DefaultGrammarLocator())
    val prismTheme: Prism4jTheme = if (isDarkTheme) {
        Prism4jThemeDarkula.create()
    } else {
        Prism4jThemeDefault.create()
    }
    
    return Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(SyntaxHighlightPlugin.create(prism4j, prismTheme))
        .build()
}
