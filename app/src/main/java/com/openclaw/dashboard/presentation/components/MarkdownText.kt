package com.openclaw.dashboard.presentation.components

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * A Composable that renders Markdown text using Markwon library.
 * Supports full GFM (GitHub Flavored Markdown) including:
 * - Headers, Bold, Italic
 * - Strikethrough (~~text~~)
 * - Tables
 * - Task lists (- [ ] / - [x])
 * - Code blocks
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
    
    val textColorArgb = textColor.toArgb()
    val linkColorArgb = linkColor.toArgb()
    
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
            val markwon = createMarkwon(textView.context)
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(textColorArgb)
            textView.setLinkTextColor(linkColorArgb)
        }
    )
}

/**
 * Creates a Markwon instance with all plugins configured.
 */
private fun createMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .build()
}
