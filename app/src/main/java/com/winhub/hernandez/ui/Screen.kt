package com.winhub.hernandez.ui

sealed class Screen(val route: String, val label: String, val iconName: String) {
    object Containers    : Screen("containers",     "Containers",             "folder")
    object Games         : Screen("games",           "Games",                  "shortcut")
    object Contents      : Screen("contents",       "Contents",               "inventory_2")
    object InputControls : Screen("input_controls", "Input Controls",         "sports_esports")
    object AdrenoTools   : Screen("adreno_tools",   "Adrenotools GPU Drivers","memory")
    object Wrappers      : Screen("wrapper_manager","Manage Wrappers",        "layers")
    object Saves         : Screen("saves",          "Saves",                  "save")
    object SaveManager   : Screen("save_manager",   "Save Manager",           "save")
    object FileManager   : Screen("file_manager",   "File Manager",           "folder_open")
    object Settings      : Screen("settings",       "Settings",               "settings")
    object Appearance    : Screen("appearance",     "Appearance",             "palette")

    // Community feed over GitHub Issues on winhub-emu/social-hub. Own sign-in + own scrolling
    // (see ui/XServerSocialTab.kt), so it is a route like the others rather than a dialog.
    object SocialHub     : Screen("social_hub",     "Social Hub",             "group")

    object Gog    : Screen("gog",    "GOG",          "storefront")
    object Epic   : Screen("epic",   "Epic Games",   "storefront")
    object Amazon : Screen("amazon", "Amazon Games", "storefront")
    object Xbox   : Screen("xbox",   "Xbox",         "storefront")
    object Steam  : Screen("steam",  "Steam",        "storefront")

    object ContainerDetail : Screen("container_detail?id={id}", "Container", "")

    // Couch/TV launcher shown at startup instead of the normal UI when enable_big_picture_mode is on.
    // Registered as a route (see AppNavGraph) but intentionally NOT listed in the drawer.
    object BigPicture : Screen("big_picture", "Big Picture", "sports_esports")

    // DroidDeck front end: full-bleed shell (no top bar) hosting this app's own screens.
    // Registered as a route (see AppNavGraph) but intentionally NOT listed in the drawer.
    object Deck : Screen("deck", "Deck", "sports_esports")

    companion object {
        val drawerItems by lazy {
            // Screen.Wrappers stays registered as a route (the wrapper manager is now reached via the
            // ☁ cloud button in container/game settings) but is intentionally NOT listed in the drawer.
            // Screen.Appearance is registered as a route too but is intentionally NOT listed either:
            // the theme is forced to Amethyst, so the picker has no entry point.
            listOf(Games, Containers, FileManager, Settings, InputControls, Contents, Saves, SocialHub)
        }
        val storeItems by lazy {
            listOf(Gog, Epic, Amazon, Xbox, Steam)
        }
    }
}
