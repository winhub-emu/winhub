package com.winhub.hernandez.ui

/**
 * The Social Hub tab (0 Feed, 1 Configs, 2 Profile) the next opening of the hub should land on: set by the Steam Home's Social
 * page, taken once by whichever hub screen opens (the Deck page or the full-screen XMB page).
 */
internal object SocialHubTarget {
    @Volatile
    private var tab: Int = -1

    fun set(index: Int) { tab = index }

    /** The tab asked for, once; null when none was (the hub then opens on Feed). */
    fun take(): Int? = tab.takeIf { it in 0..2 }.also { tab = -1 }
}
