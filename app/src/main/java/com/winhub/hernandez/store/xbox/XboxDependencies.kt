package com.winhub.hernandez.store.xbox

import android.content.Context
import com.winhub.hernandez.components.ComponentCatalog
import com.winhub.hernandez.components.ComponentInstaller
import com.winhub.hernandez.container.Container
import java.io.File

/**
 * What an Xbox PC game declares it needs: MicrosoftGame.config's DesktopRegistration/DependencyList
 * `<KnownDependency Name="…"/>` entries, which the Microsoft Store installs with the game. Each known
 * one maps to a component of the Components catalog; [ensure] installs the missing ones into the
 * container before an Xbox launch, without opening Wine (offline layout only).
 */
object XboxDependencies {
    private val COMPONENTS = mapOf("VC14" to "vcredist2022")
    // Also needed in an arm64ec container: a launcher there looks for the arm64 runtime (the x86/x64 packages above don't provide it).
    private val ARM64EC_COMPONENTS = mapOf("VC14" to "vcredist2022_arm64")
    private val KNOWN = Regex("""<KnownDependency\s+Name="([A-Za-z0-9_.-]{1,40})"""")
    private const val INSTALLS_PREFS = "component_installs"

    @JvmStatic fun configFile(folder: File): File? =
        folder.listFiles().orEmpty().firstOrNull { it.isFile && it.name.equals("MicrosoftGame.config", ignoreCase = true) }

    /** The game's known dependencies (as named in the config) with their components; unknown ones are left out. */
    @JvmStatic fun components(gameFolder: File, arm64ec: Boolean = false): List<Pair<String, String>> = runCatching {
        // The name's case varies between games (MicrosoftGame.Config in Cyberpunk 2077).
        val config = configFile(gameFolder)?.takeIf { it.length() < 1L shl 20 } ?: return emptyList()
        KNOWN.findAll(config.readText()).flatMap { m ->
            val dependency = m.groupValues[1]
            listOfNotNull(COMPONENTS[dependency], ARM64EC_COMPONENTS[dependency].takeIf { arm64ec }).map { dependency to it }
        }.distinct().toList()
    }.getOrDefault(emptyList())

    /**
     * Installs the components [productId]'s game needs that this container does not have yet. Never throws
     * and never blocks the launch: a dependency that can't be installed is logged and skipped.
     */
    @JvmStatic fun ensure(context: Context, container: Container, productId: String, arm64ec: Boolean, onStage: (String) -> Unit) {
        val game = XboxInstallState.get(context, productId) ?: return
        val wanted = components(File(game.folder), arm64ec)
        if (wanted.isEmpty()) return
        val prefs = context.getSharedPreferences(INSTALLS_PREFS, Context.MODE_PRIVATE)
        val key = "c${container.id}"
        val missing = wanted.filter { (_, name) -> name !in prefs.getStringSet(key, emptySet()).orEmpty() }
        if (missing.isEmpty()) return
        try {
            if (ComponentCatalog.byName.isEmpty()) ComponentCatalog.load()
            for ((dependency, name) in missing) {
                val component = ComponentCatalog.byName[name]
                if (component == null || !ComponentInstaller.isInstallable(component)) {
                    XboxDiag.log(context, "dependency $dependency: $name not installable here, skipped")
                    continue
                }
                val arch = if (name == ARM64EC_COMPONENTS[dependency]) " (arm64)" else ""
                onStage("Installing $dependency$arch for this game…")
                val error = ComponentInstaller.install(context, container, component) {}
                if (error == null) {
                    prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() + name).apply()
                    XboxDiag.log(context, "dependency $dependency: installed $name into container ${container.id}")
                } else XboxDiag.log(context, "dependency $dependency: $name failed: ${error.take(160)}")
            }
        } catch (e: Exception) {
            XboxDiag.log(context, "dependencies failed: ${e.javaClass.simpleName}")
        }
    }
}
