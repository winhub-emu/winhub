package com.winhub.hernandez.ui.screens

import android.graphics.Typeface
import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// HelpDialog — a plain, CENTERED Compose dialog for the per-field "?" help. Uses
// the app's standard OutlinedAlertDialog (rounded + outlined, like every other
// dialog) and SCROLLS when the text is long. Replaces the old AppUtils.showHelpBox
// PopupWindow, which anchored to the top-left corner and couldn't scroll.
// ─────────────────────────────────────────────────────────────────────────────

/** Convert a small HTML help string (<b>/<i>/<br>) into a styled AnnotatedString for Compose Text. */
private fun htmlToAnnotated(html: String): AnnotatedString {
    val spanned: Spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    val plain = spanned.toString().trimEnd('\n', ' ')
    return buildAnnotatedString {
        append(plain)
        spanned.getSpans(0, spanned.length, StyleSpan::class.java).forEach { span ->
            val start = spanned.getSpanStart(span).coerceIn(0, plain.length)
            val end = spanned.getSpanEnd(span).coerceIn(0, plain.length)
            if (start >= end) return@forEach
            when (span.style) {
                Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                Typeface.BOLD_ITALIC ->
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
        }
    }
}

/**
 * Same box, but for help text resolved at runtime rather than by resource id — the preset editor
 * looks its strings up by name (box64_env_var_help__*, *_preset_help__*), so it has the text, not
 * an id.
 */
@Composable
internal fun HelpTextDialog(text: String, onDismiss: () -> Unit) {
    val annotated = remember(text) { htmlToAnnotated(text) }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                annotated,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
internal fun HelpDialog(textResId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val annotated = remember(textResId) { htmlToAnnotated(context.getString(textResId)) }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            // Bounded height + scroll so a long description scrolls inside the box.
            Text(
                annotated,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
