package com.winhub.hernandez.store.xbox

import java.io.File
import org.xmlpull.v1.XmlPullParser

/** MicrosoftGame.config reader (ported from DroidDeck, GPL-3.0): identity, related products and the PC launch executable. */
internal object XboxManifest {
    data class Info(val storeId: String, val name: String, val publisher: String, val version: String,
        val dependency: String, val related: List<String>, val executables: List<String>)

    fun read(folder: File): Info {
        val manifests = folder.listFiles().orEmpty().filter { it.name.equals("MicrosoftGame.config", true) && it.isFile }
        require(manifests.size == 1) { "Missing or ambiguous MicrosoftGame.config." }
        val manifest = manifests.single()
        require(manifest.length() <= 1_048_576)
        val parser = android.util.Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        val identities = ArrayList<List<String>>()
        val storeIds = ArrayList<String>()
        val dependencies = ArrayList<String>()
        val related = ArrayList<String>()
        val entries = ArrayList<String>()
        fun attribute(name: String) = parser.getAttributeValue(null, name).orEmpty()
        var root = false
        var elements = 0
        manifest.inputStream().use { input ->
            parser.setInput(input, null)
            while (true) {
                when (parser.nextToken()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.DOCDECL -> error("Xbox game manifests cannot contain a DTD.")
                    XmlPullParser.START_TAG -> {
                        require(++elements <= 10_000 && parser.depth <= 64) { "Xbox game manifest is too complex." }
                        if (parser.depth == 1) { require(!root && parser.name == "Game"); root = true }
                        when (parser.name) {
                            "Identity" -> identities.add(listOf(attribute("Name"), attribute("Publisher"), attribute("Version")))
                            "StoreId" -> storeIds.add(parser.nextText().trim())
                            "MainPackageDependency" -> dependencies.add(attribute("Name"))
                            "RelatedProduct" -> related.add(parser.nextText().trim())
                            "Executable" -> {
                                val path = attribute("Name").replace('\\', '/')
                                if (path.endsWith(".exe", true) && attribute("TargetDeviceFamily") in listOf("", "PC", "Windows.Desktop") &&
                                    !attribute("IsDevOnly").equals("true", true)) entries.add(path)
                            }
                        }
                    }
                }
            }
        }
        require(root && identities.size <= 1 && storeIds.size <= 1 && dependencies.size <= 1) { "Invalid Xbox game manifest." }
        val ids = related.distinct()
        require(ids.size <= 128 && ids.all { it.matches(Regex("[A-Z0-9]{12}")) })
        val identity = identities.singleOrNull()
        return Info(storeIds.singleOrNull().orEmpty(), identity?.get(0).orEmpty(), identity?.get(1).orEmpty(), identity?.get(2).orEmpty(),
            dependencies.singleOrNull().orEmpty(), ids, entries)
    }

    fun executable(folder: File): String {
        for (path in read(folder).executables) {
            require(!path.startsWith('/') && !path.contains(':') && path.split('/').none { it == ".." || it.isEmpty() })
            var file = folder
            for (part in path.split('/')) {
                val matches = file.listFiles().orEmpty().filter { it.name.equals(part, true) }
                require(matches.size == 1) { "Missing or ambiguous Xbox launch path." }
                file = matches.single()
            }
            require(file.canonicalPath.startsWith(folder.canonicalPath + File.separator))
            if (file.isFile) return file.relativeTo(folder).invariantSeparatorsPath
        }
        error("The Xbox package has no supported PC launch executable.")
    }
}
