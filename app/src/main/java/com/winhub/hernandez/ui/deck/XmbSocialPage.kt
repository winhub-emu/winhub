package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.LocalSocialWide
import com.winhub.hernandez.ui.SocialConfigsContent
import com.winhub.hernandez.ui.SocialContent
import com.winhub.hernandez.ui.SocialProfileContent
import com.winhub.hernandez.ui.screens.DeckBackLayer
import com.winhub.hernandez.ui.screens.LocalDeckBack

/**
 * The Social tab of the XMB Home's tabs page: the Social Hub's own Feed, Configs and Profile shown right here, under centred
 * sub-tabs, not as a way into another screen. The panes lay themselves out for a wide screen (see [LocalSocialWide]). The page is made
 * of ordinary focusable controls, so it is a passthrough page (see [XmbTabsPageFocus.passthrough]): Down from the pill bar lands on the
 * sub-tabs, Left / Right on them change the part and show it at once, and B closes an open post, then goes up to the pill bar.
 */
@Composable
internal fun XmbSocialPage(focus: XmbTabsPageFocus) {
    // The hub's screens ask the shell's back layer to close an open post; here that ask comes to this page instead.
    val back = remember { DeckBackLayer() }
    XmbTabsPagePassthrough(focus) { back.onBack?.invoke() == true }
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(
        stringResource(R.string.social_hub_subtab_feed),
        stringResource(R.string.social_hub_subtab_configs),
        stringResource(R.string.social_hub_subtab_profile),
    )
    CompositionLocalProvider(LocalDeckBack provides back, LocalSocialWide provides true) {
        Column(Modifier.fillMaxSize().padding(focus.contentPadding)) {
            XmbSocialSubTabs(labels, sub) { sub = it }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (sub) {
                    0 -> SocialContent(showTitle = false)
                    1 -> SocialConfigsContent()
                    else -> SocialProfileContent(onGoFeed = { sub = 0 })
                }
            }
        }
    }
}

/**
 * Feed / Configs / Profile as sub-tabs: bold labels centred under the pill bar, the chosen one marked by an accent underline. Left / Right
 * move between them and show the part at once; the cursor is the thin light outline, as on the pill bar.
 */
@Composable
private fun XmbSocialSubTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val src = remember { MutableInteractionSource() }
            val focused by src.collectIsFocusedAsState()
            val shape = deckShape(12.dp)
            Column(
                Modifier
                    .height(48.dp)
                    .defaultMinSize(minWidth = 88.dp)
                    .clip(shape)
                    .then(if (focused) Modifier.border(1.5.dp, XMB_FOCUS_OUTLINE, shape) else Modifier)
                    .onFocusChanged { if (it.isFocused && !on) onSelect(i) }
                    .clickable(interactionSource = src, indication = null) { onSelect(i) }
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    label.uppercase(),
                    color = if (on) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(if (on) accent else Color.Transparent))
            }
        }
    }
}
