package com.winhub.hernandez.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * How far up a floating button at the bottom of a page must sit to clear the floating "B Back" hint (bottom right) while an
 * XMB store screen shows it, else 0. A floating button adds it to its bottom padding so the hint never covers it. Everywhere
 * else it stays 0.dp, so reading it is always safe.
 */
val LocalXmbHintInset = compositionLocalOf { 0.dp }
