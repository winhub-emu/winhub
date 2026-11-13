package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.SocialConfigsContent
import com.winhub.hernandez.ui.SocialContent
import com.winhub.hernandez.ui.SocialProfileContent
import com.winhub.hernandez.ui.screens.DeckBackLayer
import com.winhub.hernandez.ui.screens.DeckStretchyTabs
import com.winhub.hernandez.ui.screens.LocalDeckBack

/**
 * The Social tab of the XMB Home's tabs page: the Social Hub's own Feed, Configs and Profile shown right here, under a small tab
 * strip, not as a way into another screen. The page is made of ordinary focusable controls, so it is a passthrough page (see
 * [XmbTabsPageFocus.passthrough]): Down from the pill bar lands on the strip, Left / Right on it change the part and show it at once,
 * and B closes an open post, then goes up to the pill bar.
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
    CompositionLocalProvider(LocalDeckBack provides back) {
        Column(Modifier.fillMaxSize().padding(focus.contentPadding)) {
            DeckStretchyTabs(
                labels = labels,
                selected = sub,
                onSelect = { sub = it },
                focusable = true,
                selectOnFocus = true,
                fillWidth = false,
                height = 34.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
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
