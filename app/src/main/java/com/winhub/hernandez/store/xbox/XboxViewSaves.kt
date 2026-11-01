package com.winhub.hernandez.store.xbox

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Keeps what an Xbox game writes from vanishing with its temporary launch view (imagefs/xbox-views, removed at
 * session end). Unreal games write `<Project>/Saved` beside their binaries; in the view that folder would be
 * temporary, so before launch it is linked to the container's `AppData/Local/<Project>/Saved`, where Unreal
 * puts it on a normal Windows install and where the Save Manager's vault finds it by name. Anything else the
 * game creates in the view is moved into its install folder when the session ends.
 */
object XboxViewSaves {
    /** Links each Unreal project's `Saved` into [localAppData]; returns the view's own files at launch time. */
    fun prepare(app: Context, viewRoot: File, localAppData: File): Set<String> {
        viewRoot.listFiles().orEmpty()
            .filter { Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) && File(it, "Binaries").isDirectory }
            .forEach { project ->
                val link = File(project, "Saved")
                if (Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS)) return@forEach
                val target = File(localAppData, "${project.name}/Saved").apply { mkdirs() }
                runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }
                    .onSuccess { XboxDiag.log(app, "saves: ${project.name}/Saved -> container AppData/Local/${project.name}/Saved") }
                    .onFailure { XboxDiag.log(app, "saves: could not link ${project.name}/Saved: ${it.javaClass.simpleName}") }
            }
        return files(viewRoot)
    }

    /** Moves files the game created in the view (not there at launch) into [installRoot] at the same place. */
    fun persist(viewRoot: File, installRoot: File, atLaunch: Set<String>) {
        if (!viewRoot.isDirectory) return
        var moved = 0
        for (rel in files(viewRoot) - atLaunch) {
            runCatching {
                val dest = File(installRoot, rel)
                dest.parentFile?.mkdirs()
                Files.move(File(viewRoot, rel).toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                moved++
            }
        }
        if (moved > 0) XboxDiag.log(null, "saves: kept $moved file(s) the game wrote in its launch view")
    }

    /** Regular files under [root], not following symbolic links (links lead into the install or the container). */
    private fun files(root: File): Set<String> {
        val out = HashSet<String>()
        fun walk(dir: File, prefix: String) {
            for (f in dir.listFiles().orEmpty()) {
                val path = f.toPath()
                when {
                    Files.isSymbolicLink(path) -> Unit
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> walk(f, "$prefix${f.name}/")
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> out += "$prefix${f.name}"
                }
            }
        }
        walk(root, "")
        return out
    }
}
