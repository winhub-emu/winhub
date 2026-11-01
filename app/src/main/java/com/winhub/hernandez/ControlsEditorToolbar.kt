package com.winhub.hernandez

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R

private val ToolbarBackground = Color(0xCC1A1A2E)
private val ToolbarButtonTint = Color.White.copy(alpha = 0.86f)
private val ToolbarShape = RoundedCornerShape(16.dp)
private val ToolbarButtonShape = RoundedCornerShape(10.dp)

@Composable
fun ControlsEditorToolbar(
    profileName: String,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onBackgroundClick: () -> Unit,
) {
    Surface(
        color = ToolbarBackground,
        contentColor = Color.White,
        shape = ToolbarShape,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 88.dp, max = 132.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.profile),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profileName,
                    color = colorResource(R.color.colorAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ToolbarDivider()
            ToolbarIconButton(iconRes = R.drawable.icon_add, contentDescription = stringResource(R.string.add), onClick = onAddClick)
            ToolbarIconButton(iconRes = R.drawable.icon_remove, contentDescription = stringResource(R.string.remove), onClick = onRemoveClick)
            ToolbarIconButton(iconRes = R.drawable.icon_settings, contentDescription = stringResource(R.string.settings), onClick = onSettingsClick)
            ToolbarIconButton(iconRes = R.drawable.icon_group, contentDescription = stringResource(R.string.groups), onClick = onGroupsClick)
            ToolbarIconButton(iconRes = R.drawable.icon_background_image, contentDescription = stringResource(R.string.set_background_image), onClick = onBackgroundClick)
        }
    }
}

@Composable
fun InGameControlsEditorToolbar(
    profileName: String,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onDoneClick: () -> Unit,
) {
    Surface(
        color = ToolbarBackground,
        contentColor = Color.White,
        shape = ToolbarShape,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 88.dp, max = 132.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.editing_in_game),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = profileName,
                    color = colorResource(R.color.colorAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ToolbarDivider()
            ToolbarIconButton(iconRes = R.drawable.icon_add, contentDescription = stringResource(R.string.add), onClick = onAddClick)
            ToolbarIconButton(iconRes = R.drawable.icon_remove, contentDescription = stringResource(R.string.remove), onClick = onRemoveClick)
            ToolbarIconButton(iconRes = R.drawable.icon_settings, contentDescription = stringResource(R.string.settings), onClick = onSettingsClick)
            ToolbarIconButton(iconRes = R.drawable.icon_group, contentDescription = stringResource(R.string.groups), onClick = onGroupsClick)
            ToolbarDivider()
            ToolbarIconButton(iconRes = R.drawable.icon_save, contentDescription = stringResource(R.string.save_and_close), onClick = onDoneClick)
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

@Composable
private fun ToolbarIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(ToolbarButtonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ToolbarButtonTint,
            modifier = Modifier.size(22.dp),
        )
    }
}
