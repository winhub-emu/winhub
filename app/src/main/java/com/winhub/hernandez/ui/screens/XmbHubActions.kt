package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.preference.PreferenceManager
import com.winhub.hernandez.container.Shortcut

/** What only Home can do for a game: its own launch-method, settings, pin, container and delete flows. */
internal class HubXmbOps(
    val launchMethod: () -> Unit,
    val addToHome: () -> Unit,
    val containerProperties: () -> Unit,
    val openSettings: () -> Unit,
    val delete: () -> Unit,
)

/**
 * The XMB options column for a Home game. Saves and export live under Settings > General. Launch method, Add to Home Screen, Container properties and
 * Delete run Home's own flows.
 */
internal fun hubXmbActions(
    context: Context,
    shortcut: Shortcut,
    game: HubGame,
    host: XmbGameHost,
    ops: HubXmbOps,
): List<XmbAction> = buildList {
    add(XmbAction("Launch method", Icons.Filled.Layers) { ops.launchMethod() })
    if (GameSettingsStyle.isClassic(PreferenceManager.getDefaultSharedPreferences(context))) {
        add(XmbAction("Settings", Icons.Filled.Settings, "Display, graphics, controller…") { ops.openSettings() })
    } else {
        add(XmbAction("Settings", Icons.Filled.Settings, "Display, graphics, controller…", menu = { xmbSettingsMenu(it, shortcut, host) }))
    }
    add(XmbAction("Game Doctor", Icons.Filled.Healing, "Why it won't run, and what to try", menu = { xmbGameDoctorMenu(it, shortcut) }))
    add(XmbAction("Game Details", Icons.Filled.Edit, "Name, genres, year, description", menu = { xmbGameDetailsMenu(it, shortcut) }))
    if (isSteamOriginShortcut(shortcut)) {
        add(XmbAction("Cloud Saves", Icons.Filled.CloudSync, "Steam Cloud sync — opens its own screen") { launchSaveManager(context, steamAppIdOf(shortcut)) })
    }
    add(XmbAction("Search covers", Icons.Filled.Search, menu = { xmbScrapeCoverMenu(it, shortcut) }))
    add(XmbAction("Clone to container", Icons.Filled.ContentCopy, menu = { xmbCloneMenu(it, shortcut, host) }))
    add(XmbAction("Copy to Drive C", Icons.Filled.DriveFileMove, menu = { xmbCopyToDriveCMenu(it, shortcut) }))
    add(XmbAction("Change executable", Icons.Filled.SwapHoriz, menu = { xmbChangeExeMenu(it, shortcut) }))
    add(XmbAction("View logs", Icons.Filled.Description, menu = { xmbLogsMenu(it, shortcut) }))
    add(XmbAction("Add to Home Screen", Icons.Filled.Home) { ops.addToHome() })
    add(XmbAction("Properties", Icons.Filled.Info, "Times played, playtime", menu = { xmbPropertiesMenu(it, shortcut) }))
    add(XmbAction("Container properties", Icons.Filled.Folder) { ops.containerProperties() })
    add(XmbAction("Delete", Icons.Filled.Delete, "Asks to confirm first", danger = true, menu = { hubDeleteMenu(it, game, ops) }))
}

private fun hubDeleteMenu(xmb: XmbScope, game: HubGame, ops: HubXmbOps): XmbMenu =
    XmbMenu("Delete game?", Icons.Filled.Delete, initialKey = "cancel") {
        listOf(
            XmbRow.Info("msg", "Delete “${game.name}”?", Icons.Filled.Info),
            XmbRow.Action("delete", "Delete", Icons.Filled.Delete, danger = true) {
                ops.delete()
                xmb.popToRoot()
            },
            XmbRow.Action("cancel", "Cancel", Icons.Filled.Close) { xmb.pop() },
        )
    }
