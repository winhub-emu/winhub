package com.winhub.hernandez.store.xbox

import java.io.File
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Private temporary launch views (ported from DroidDeck, GPL-3.0): one directory per launch, removed afterwards. */
internal class XboxLaunchViews {
    private val recovered = HashSet<String>()

    /** A new launch directory under [root] (Bannerlator: inside imagefs, so the Wine session can read the view). */
    @Synchronized fun create(root: File): File {
        val parent = File(root, "xbox-views").canonicalFile
        check(parent == File(root.canonicalFile, "xbox-views")) { "Xbox launch storage contains a redirected path" }
        check(parent.mkdirs() || parent.isDirectory)
        if (parent.path !in recovered) {
            parent.listFiles()?.filter { it.name.startsWith("launch-") }?.forEach(::remove)
            recovered.add(parent.path)
        }
        return Files.createTempDirectory(parent.toPath(), "launch-").toFile()
    }

    companion object {
        fun remove(directory: File) {
            if (!Files.exists(directory.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
