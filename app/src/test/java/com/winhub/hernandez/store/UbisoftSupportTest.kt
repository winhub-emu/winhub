package com.winhub.hernandez.store

import com.winhub.hernandez.store.steamscript.InstallScriptExecutor
import com.winhub.hernandez.store.steamscript.InstallScriptModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [UbisoftSupport]: installer names and the depot search (and what it costs a game that is not Ubisoft's), the Epic and
 * Steam setup decisions, the stamp, the launch decisions note, the overlay edit of `settings.yaml`.
 */
class UbisoftSupportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** The depot scans are remembered for a while; the next test must not see this one's folders. */
    @After
    fun forgetTheDepotScans() = UbisoftSupport.forgetScans()

    @Test
    fun overlayOffCreatesTheSectionInAnEmptyFile() {
        assertEquals("overlay:\n  enabled: false\n  warning_enabled: false\n", UbisoftSupport.overlayOff(""))
    }

    @Test
    fun overlayOffAppendsTheSectionAndKeepsTheRest() {
        val out = UbisoftSupport.overlayOff("misc:\n  language: en\n")
        assertEquals("misc:\n  language: en\noverlay:\n  enabled: false\n  warning_enabled: false\n", out)
    }

    @Test
    fun overlayOffFlipsExistingKeysAndKeepsNeighbours() {
        val before = "overlay:\n  enabled: true\n  forceunhookgame: false\n  warning_enabled: true\nmisc:\n  x: 1\n"
        val after = "overlay:\n  enabled: false\n  forceunhookgame: false\n  warning_enabled: false\nmisc:\n  x: 1\n"
        assertEquals(after, UbisoftSupport.overlayOff(before))
    }

    @Test
    fun overlayOffAddsOnlyTheMissingKey() {
        val out = UbisoftSupport.overlayOff("overlay:\n  enabled: false\n\nmisc:\n  x: 1\n")
        assertEquals("overlay:\n  enabled: false\n  warning_enabled: false\n\nmisc:\n  x: 1\n", out)
    }

    @Test
    fun overlayOffIsIdempotentAndKeepsCrLf() {
        val once = UbisoftSupport.overlayOff("overlay:\r\n  enabled: true\r\n")
        assertEquals("overlay:\r\n  enabled: false\r\n  warning_enabled: false\r\n", once)
        assertEquals(once, UbisoftSupport.overlayOff(once))
    }

    @Test
    fun findsAnInstallScriptNamedAfterTheApp() {
        val dir = tmp.newFolder("game")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        assertNull(InstallScriptExecutor.locateScript(dir))
        val script = File(dir, "242050_install.vdf").apply { writeText("\"InstallScript\" { }") }
        assertEquals(script, InstallScriptExecutor.locateScript(dir))
        // The classic name still wins when both are there.
        val classic = File(dir, "installScript.vdf").apply { writeText("\"InstallScript\" { }") }
        assertEquals(classic, InstallScriptExecutor.locateScript(dir))
    }

    @Test
    fun appNamedInstallScriptIsOnlyUsedForUbisoftDepots() {
        val plain = tmp.newFolder("plainScript")
        File(plain, "game.exe").writeText("x")
        File(plain, "242050_install.vdf").writeText("\"InstallScript\" { }")
        assertNull(InstallScriptExecutor.locateScript(plain))

        val loader = tmp.newFolder("loaderScript")
        File(loader, "upc_r1_loader.dll").writeText("x")
        val loaderScript = File(loader, "242050_install.vdf").apply { writeText("\"InstallScript\" { }") }
        assertEquals(loaderScript, InstallScriptExecutor.locateScript(loader))

        val bundled = tmp.newFolder("bundledScript")
        File(bundled, "UbisoftConnectInstaller.exe").writeText("x")
        val bundledScript = File(bundled, "242050_install.vdf").apply { writeText("\"InstallScript\" { }") }
        assertEquals(bundledScript, InstallScriptExecutor.locateScript(bundled))

        // Other names are unaffected by the Ubisoft check.
        val classic = File(plain, "installScript.vdf").apply { writeText("\"InstallScript\" { }") }
        assertEquals(classic, InstallScriptExecutor.locateScript(plain))
    }

    @Test
    fun isInstallerLooksAtTheFileNameOnly() {
        assertTrue(UbisoftSupport.isInstaller("%INSTALLDIR%\\UbisoftConnectInstaller.exe"))
        assertTrue(UbisoftSupport.isInstaller("%INSTALLDIR%/Support/uplayinstaller.exe"))
        assertTrue(UbisoftSupport.isInstaller("UplayInstaller.exe"))
        assertFalse(UbisoftSupport.isInstaller("%INSTALLDIR%\\UplayInstaller\\setup.exe"))
        assertFalse(UbisoftSupport.isInstaller("%INSTALLDIR%/UbisoftConnectInstaller/setup.exe"))
    }

    @Test
    fun installerNamesAreRecognised() {
        for (name in listOf(
            "UbisoftConnectInstaller.exe", "UplayInstaller.exe", "Ubisoft Connect Installer.exe",
            "UbisoftGameLauncherInstaller.exe", "Uplay_Installer.exe", "UplayInstaller_x64.exe",
            "ubisoft-connect-installer.EXE", "UPCInstaller.exe",
        )) assertTrue(name, UbisoftSupport.isInstallerName(name))
        for (name in listOf(
            "UplayLaunch.exe", "UplayService.exe", "upc.exe", "UbisoftGameLauncher.exe", "uplay_r1_loader64.dll",
            "DXSETUP.exe", "vcredist_x86.exe", "UplayInstaller.dll", "UplayInstaller", "UplayInstaller.exe.bak",
            "UplayUninstaller.exe", "UbisoftConnectUninstall.exe", "UpcomingInstaller.exe", "EAappInstaller.exe",
            "UplaySetup.exe", "Setup.exe", "",
        )) assertFalse(name, UbisoftSupport.isInstallerName(name))
    }

    @Test
    fun installerNamesRankConnectFirstThenUbisoftThenUplay() {
        assertEquals(0, UbisoftSupport.installerRank("UbisoftConnectInstaller.exe"))
        assertEquals(1, UbisoftSupport.installerRank("UbisoftGameLauncherInstaller.exe"))
        assertEquals(2, UbisoftSupport.installerRank("UplayInstaller.exe"))
        assertEquals(2, UbisoftSupport.installerRank("UPCInstaller.exe"))
        assertNull(UbisoftSupport.installerRank("upc.exe"))
    }

    @Test
    fun scriptStepsAndDepotSearchShareOneMatcher() {
        for (name in listOf("Ubisoft Connect Installer.exe", "Uplay_Installer.exe", "UplayInstaller_x64.exe", "UplayLaunch.exe", "DXSETUP.exe")) {
            assertEquals(name, UbisoftSupport.isInstallerName(name), UbisoftSupport.isInstaller("%INSTALLDIR%\\Support\\$name"))
            assertEquals(name, UbisoftSupport.isInstallerName(name), UbisoftSupport.isInstaller("\"%INSTALLDIR%/$name\""))
        }
    }

    @Test
    fun theLauncherInstallerIsRunFirst() {
        val redist = InstallScriptModel.RunProcess("Redist", "%INSTALLDIR%\\redist\\vcredist_x86.exe", "/q", null, null, null)
        val other = InstallScriptModel.RunProcess("Other", "%INSTALLDIR%\\other.exe", "", null, null, null)
        val uplay = InstallScriptModel.RunProcess("Uplay", "%INSTALLDIR%\\UplayInstaller.exe", "/S", null, null, null)
        assertEquals(listOf(uplay, redist, other), UbisoftSupport.installerFirst(listOf(redist, uplay, other)))
        assertEquals(listOf(redist, other), UbisoftSupport.installerFirst(listOf(redist, other)))
        // Two installers: Ubisoft Connect's own comes before an older Uplay one.
        val connect = InstallScriptModel.RunProcess("Connect", "%INSTALLDIR%\\UbisoftConnectInstaller.exe", "/S", null, null, null)
        assertEquals(listOf(connect, uplay, redist), UbisoftSupport.installerFirst(listOf(redist, uplay, connect)))
    }

    private val ubisoftScript = "\"InstallScript\" { \"Run Process\" { \"Ubisoft Connect PC Client\" " +
        "{ \"process 1\" \"%INSTALLDIR%/Support/Uplay/UbisoftConnectInstaller.exe\" \"command 1\" \"/S\" } } }"

    @Test
    fun theDepotsInstallerIsAddedOnlyWhenTheScriptRunsNone() {
        val redist = InstallScriptModel.RunProcess("Redist", "%INSTALLDIR%\\redist\\vcredist_x86.exe", "/q", null, null, null)
        val legacy = InstallScriptModel.RunProcess("Uplay", "%INSTALLDIR%\\UplayInstaller.exe", "/S", null, null, null)

        val ubisoft = tmp.newFolder("withBundled")
        File(ubisoft, "UbisoftConnectInstaller.exe").writeText("x")
        assertEquals(
            listOf(redist.process, "%INSTALLDIR%\\UbisoftConnectInstaller.exe"),
            UbisoftSupport.withBundledInstaller(listOf(redist), ubisoft).map { it.process },
        )
        assertEquals(
            listOf(redist.process, legacy.process),
            UbisoftSupport.withBundledInstaller(listOf(redist, legacy), ubisoft).map { it.process },
        )
        // A depot that bundles no Ubisoft installer (EA...) keeps exactly the script's steps.
        val other = tmp.newFolder("withoutBundled")
        File(other, "game.exe").writeText("x")
        assertEquals(listOf(redist.process), UbisoftSupport.withBundledInstaller(listOf(redist), other).map { it.process })
        assertTrue(UbisoftSupport.withBundledInstaller(emptyList(), other).isEmpty())
    }

    // ---- What the depot search costs a game that is not Ubisoft's ---------------------------------

    @Test
    fun aDepotWithoutAUbisoftHintIsSearchedOneFolderDownOnly() {
        val dir = tmp.newFolder("noHint")
        File(dir, "game.exe").writeText("x")
        File(dir, "Support/Uplay").mkdirs()
        File(dir, "Support/Uplay/UplayInstaller.exe").writeText("x")
        UbisoftSupport.forgetScans()
        assertNull(UbisoftSupport.bundledInstaller(dir))
        assertFalse(UbisoftSupport.hasLauncherFiles(dir))
        assertFalse(UbisoftSupport.detect(dir))
        // One folder below the root is still looked at (an Epic build's Uplay folder).
        File(dir, "Uplay").mkdirs()
        val oneDown = File(dir, "Uplay/UplayInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(oneDown, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun aLoaderInTheRootLetsTheSearchGoDeep() {
        val dir = tmp.newFolder("loaderHint")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        File(dir, "Support/Uplay").mkdirs()
        val deep = File(dir, "Support/Uplay/UplayInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(deep, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun anInstallScriptThatNamesUbisoftLetsTheSearchGoDeep() {
        val dir = tmp.newFolder("scriptHint")
        File(dir, "installScript.vdf").writeText(ubisoftScript)
        File(dir, "Support/Uplay").mkdirs()
        val deep = File(dir, "Support/Uplay/UbisoftConnectInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(deep, UbisoftSupport.bundledInstaller(dir))

        // The same layout with a script that says nothing about Ubisoft stays unsearched.
        val quiet = tmp.newFolder("quietScript")
        File(quiet, "installScript.vdf").writeText("\"InstallScript\" { }")
        File(quiet, "Support/Uplay").mkdirs()
        File(quiet, "Support/Uplay/UbisoftConnectInstaller.exe").writeText("x")
        UbisoftSupport.forgetScans()
        assertNull(UbisoftSupport.bundledInstaller(quiet))
    }

    @Test
    fun theLoaderHintIsReadFromTheNamesAlone() {
        val dir = tmp.newFolder("rootNames")
        // A folder with a loader's name is no loader; a file in a sub folder is not in the root.
        File(dir, "uplay_r1_loader.dll").mkdirs()
        File(dir, "Bin").mkdirs()
        File(dir, "Bin/uplay_r1_loader64.dll").writeText("x")
        assertFalse(UbisoftSupport.rootHasLoader(dir, dir.list()!!))
        File(dir, "UPC_R2_LOADER64.dll").writeText("x")
        assertTrue(UbisoftSupport.rootHasLoader(dir, dir.list()!!))
        assertFalse(UbisoftSupport.rootHasLoader(dir, arrayOf("game.exe", "readme.txt")))
    }

    @Test
    fun theSearchStopsAfterTheFolderCap() {
        val dir = tmp.newFolder("chain")
        File(dir, "a/b/c").mkdirs()
        val hit = File(dir, "a/b/c/UplayInstaller.exe").apply { writeText("x") }
        // The root, a, b and c are four listings: a cap of three stops before c is read.
        assertNull(UbisoftSupport.searchInstaller(dir, UbisoftSupport.DEEP_LEVELS, maxDirs = 3))
        assertEquals(hit, UbisoftSupport.searchInstaller(dir, UbisoftSupport.DEEP_LEVELS, maxDirs = 4))
    }

    @Test
    fun theKnownLayoutsStillResolve() {
        // Black Flag / Freedom Cry on Steam: loader in the root, the installer in the root or below.
        val steam = tmp.newFolder("blackFlag")
        File(steam, "uplay_r1_loader64.dll").writeText("x")
        File(steam, "Support/Uplay").mkdirs()
        val steamInstaller = File(steam, "Support/Uplay/UbisoftConnectInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertTrue(UbisoftSupport.hasLauncherFiles(steam))
        assertEquals(steamInstaller, UbisoftSupport.bundledInstaller(steam))
        // Syndicate on Epic: Uplay\UplayInstaller.exe one folder below the root, loader and install record in the root.
        val epic = tmp.newFolder("syndicate")
        File(epic, "uplay_r1_loader64.dll").writeText("x")
        File(epic, "uplay_install.state").writeText("x")
        File(epic, "Uplay").mkdirs()
        val epicInstaller = File(epic, "Uplay/UplayInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertTrue(UbisoftSupport.hasLauncherFiles(epic))
        assertEquals(epicInstaller, UbisoftSupport.bundledInstaller(epic))
    }

    // ---- A Steam depot's setup decision -----------------------------------------------------------

    @Test
    fun aSteamDepotWithALegacyScriptStepGetsTheDownloadEvenWithAModernFileBesideIt() {
        // The name the Steam gate judges is the script step's: that is what the Run Process stage runs first.
        val dir = tmp.newFolder("legacyStep")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        File(dir, "UbisoftConnectInstaller.exe").writeText("x")
        File(dir, "installScript.vdf").writeText(
            "\"InstallScript\" { \"Run Process\" { \"Uplay\" { \"process 1\" \"%INSTALLDIR%/UplayInstaller.exe\" \"command 1\" \"/S\" } } }"
        )
        UbisoftSupport.forgetScans()
        assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.LEGACY_BUNDLED, UbisoftSupport.steamDecisionFor(dir))
    }

    @Test
    fun aSteamDepotIsJudgedByItsFiles() {
        val modern = tmp.newFolder("steamModern")
        File(modern, "UbisoftConnectInstaller.exe").writeText("x")
        val loaderOnly = tmp.newFolder("steamLoaderOnly")
        File(loaderOnly, "uplay_r1_loader.dll").writeText("x")
        val plain = tmp.newFolder("steamPlain")
        File(plain, "game.exe").writeText("x")
        UbisoftSupport.forgetScans()
        assertDecision(UbisoftSupport.EpicSetup.BUNDLED, UbisoftSupport.EpicSetupReason.MODERN_BUNDLED, UbisoftSupport.steamDecisionFor(modern))
        assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.NO_INSTALLER, UbisoftSupport.steamDecisionFor(loaderOnly))
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.NOT_APPLICABLE, UbisoftSupport.steamDecisionFor(plain))
    }

    @Test
    fun findsTheBundledInstallerInTheRootOrBelowIt() {
        val dir = tmp.newFolder("game2")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        assertNull(UbisoftSupport.bundledInstaller(dir))
        val nested = File(dir, "Support/Uplay").apply { mkdirs() }
        val old = File(nested, "UplayInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(old, UbisoftSupport.bundledInstaller(dir))
        val root = File(dir, "UbisoftConnectInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(root, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun theShallowestInstallerWinsOverADeeperOne() {
        val dir = tmp.newFolder("depth")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        File(dir, "A/B/C").mkdirs()
        File(dir, "Support").mkdirs()
        File(dir, "A/B/C/UbisoftConnectInstaller.exe").writeText("x")
        val shallow = File(dir, "Support/UplayInstaller.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(shallow, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun inOneFolderConnectBeatsOtherUbisoftBeatsUplayThenNameOrder() {
        val dir = tmp.newFolder("rank")
        val uplay = File(dir, "UplayInstaller.exe").apply { writeText("x") }
        val uplayUnderscore = File(dir, "Uplay_Installer.exe").apply { writeText("x") }
        val launcher = File(dir, "UbisoftGameLauncherInstaller.exe").apply { writeText("x") }
        val connect = File(dir, "UbisoftConnectInstaller.exe").apply { writeText("x") }
        File(dir, "Redist").mkdirs()
        File(dir, "UplayService.exe").writeText("x")
        File(dir, "Folder Installer Ubisoft.exe").mkdirs()
        UbisoftSupport.forgetScans()
        assertEquals(connect, UbisoftSupport.bundledInstaller(dir))
        connect.delete(); UbisoftSupport.forgetScans()
        assertEquals(launcher, UbisoftSupport.bundledInstaller(dir))
        launcher.delete(); UbisoftSupport.forgetScans()
        // Same rank: by name, "uplay_installer" sorts before "uplayinstaller".
        assertEquals(uplayUnderscore, UbisoftSupport.bundledInstaller(dir))
        uplayUnderscore.delete(); UbisoftSupport.forgetScans()
        assertEquals(uplay, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun theInstallerSearchGoesThreeFoldersDeepAndNoFurther() {
        val dir = tmp.newFolder("limit")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        File(dir, "a/b/c/d").mkdirs()
        File(dir, "a/b/c/d/UplayInstaller.exe").writeText("x")
        UbisoftSupport.forgetScans()
        assertNull(UbisoftSupport.bundledInstaller(dir))
        val ok = File(dir, "a/b/c/Uplay_Installer.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(ok, UbisoftSupport.bundledInstaller(dir))
    }

    @Test
    fun aDepotScanIsReusedUntilItIsForgotten() {
        val dir = tmp.newFolder("scanned")
        assertFalse(UbisoftSupport.hasLauncherFiles(dir))
        File(dir, "uplay_r1_loader.dll").writeText("x")
        // Still the answer from a moment ago: the folders are not walked again for every question.
        assertFalse(UbisoftSupport.hasLauncherFiles(dir))
        UbisoftSupport.forgetScans()
        assertTrue(UbisoftSupport.hasLauncherFiles(dir))
    }

    @Test
    fun detectsUbisoftTitlesFromTheirFiles() {
        val plain = tmp.newFolder("plain")
        File(plain, "game.exe").writeText("x")
        assertFalse(UbisoftSupport.detect(plain))

        val loader = tmp.newFolder("loader")
        File(loader, "uplay_r1_loader.dll").writeText("x")
        assertTrue(UbisoftSupport.detect(loader))

        val bundled = tmp.newFolder("bundled")
        File(bundled, "UbisoftConnectInstaller.exe").writeText("x")
        assertTrue(UbisoftSupport.detect(bundled))
    }

    @Test
    fun fallbackRunProcessUsesTheBundledInstallerSilently() {
        val dir = tmp.newFolder("game3")
        File(dir, "uplay_r1_loader.dll").writeText("x")
        assertTrue(UbisoftSupport.fallbackRunProcesses(dir).isEmpty())
        File(dir, "Support/Uplay").mkdirs()
        File(dir, "Support/Uplay/UplayInstaller.exe").writeText("x")
        UbisoftSupport.forgetScans()
        val rp = UbisoftSupport.fallbackRunProcesses(dir).single()
        assertEquals("%INSTALLDIR%\\Support\\Uplay\\UplayInstaller.exe", rp.process)
        assertEquals("/S", rp.command)
        assertEquals("Ubisoft Connect PC Client", rp.name)
        assertTrue(UbisoftSupport.isInstaller(rp.process))
        assertFalse(UbisoftSupport.isInstaller("%INSTALLDIR%\\__Installer\\Origin\\redist\\internal\\EAappInstaller.exe"))
    }

    @Test
    fun epicLoaderAndInstallRecordNamesCountAsUbisoft() {
        for (name in listOf("uplay_r1_loader64.dll", "uplay_r2_loader.dll", "upc_r2_loader64.dll", "UPLAY_INSTALL.manifest")) {
            val dir = tmp.newFolder("epic_" + name)
            UbisoftSupport.forgetScans()
            assertFalse(name, UbisoftSupport.hasLauncherFiles(dir))
            File(dir, name).writeText("x")
            UbisoftSupport.forgetScans()
            assertTrue(name, UbisoftSupport.hasLauncherFiles(dir))
        }
        val plain = tmp.newFolder("epic_plain")
        File(plain, "Game.exe").writeText("x")
        File(plain, "Engine/Binaries").mkdirs()
        UbisoftSupport.forgetScans()
        assertFalse(UbisoftSupport.hasLauncherFiles(plain))
    }

    @Test
    fun findsTheInstallerAnEpicBuildKeepsInItsUplayFolder() {
        val dir = tmp.newFolder("acs")
        File(dir, "Uplay").mkdirs()
        File(dir, "Uplay/UplayInstaller.exe").writeText("x")
        UbisoftSupport.forgetScans()
        assertEquals(File(dir, "Uplay/UplayInstaller.exe"), UbisoftSupport.bundledInstaller(dir))
        val rp = UbisoftSupport.fallbackRunProcesses(dir).single()
        assertEquals("%INSTALLDIR%\\Uplay\\UplayInstaller.exe", rp.process)
        assertEquals("/S", rp.command)
    }

    @Test
    fun anEpicBuildWithAnOddlyNamedInstallerIsStillFound() {
        val dir = tmp.newFolder("odd")
        File(dir, "Uplay").mkdirs()
        val exe = File(dir, "Uplay/Ubisoft Connect Installer.exe").apply { writeText("x") }
        UbisoftSupport.forgetScans()
        assertEquals(exe, UbisoftSupport.bundledInstaller(dir))
        assertEquals("%INSTALLDIR%\\Uplay\\Ubisoft Connect Installer.exe", UbisoftSupport.fallbackRunProcesses(dir).single().process)
    }

    @Test
    fun stampValueReadsTheShortcutTag() {
        assertEquals(true, UbisoftSupport.stampValue("1"))
        assertEquals(true, UbisoftSupport.stampValue(" 1 "))
        assertEquals(false, UbisoftSupport.stampValue("0"))
        assertNull(UbisoftSupport.stampValue(""))
        assertNull(UbisoftSupport.stampValue(null))
        assertNull(UbisoftSupport.stampValue("yes"))
    }

    @Test
    fun gameFolderUnderTakesTheFolderBelowTheRoot() {
        fun folder(path: String, root: String = "epic_games") = UbisoftSupport.gameFolderUnder(path, root)?.path?.replace('\\', '/')
        assertEquals(
            "/data/user/0/app/files/imagefs/epic_games/Assassins Creed Syndicate",
            folder("/data/user/0/app/files/imagefs/epic_games/Assassins Creed Syndicate/ACS.exe"),
        )
        assertEquals("/x/Epic_Games/G", folder("/x/Epic_Games/G/Binaries/Win64/g.exe"))
        assertEquals("/x/epic_games/G", folder("\\x\\epic_games\\G\\g.exe"))
        assertNull(folder("/x/steam_games/G/g.exe"))
        assertNull(folder("/x/epic_games"))
        assertNull(folder("/x/epic_games/"))
        assertNull(folder("/x/epic_games_old/G/g.exe"))
    }

    @Test
    fun onlyAnUncheckedZeroStampIsLookedAtAgain() {
        assertTrue(UbisoftSupport.stampNeedsRecheck(false, alreadyChecked = false))
        assertFalse(UbisoftSupport.stampNeedsRecheck(false, alreadyChecked = true))
        assertFalse(UbisoftSupport.stampNeedsRecheck(true, alreadyChecked = false))
        assertFalse(UbisoftSupport.stampNeedsRecheck(true, alreadyChecked = true))
        assertFalse(UbisoftSupport.stampNeedsRecheck(null, alreadyChecked = false))
        assertFalse(UbisoftSupport.stampNeedsRecheck(null, alreadyChecked = true))
    }

    @Test
    fun stampForStampsZeroOnlyWhenTheFolderHoldsFiles() {
        assertEquals("1", UbisoftSupport.stampFor(folderHasFiles = true, ubisoft = true))
        assertEquals("1", UbisoftSupport.stampFor(folderHasFiles = false, ubisoft = true))
        assertEquals("0", UbisoftSupport.stampFor(folderHasFiles = true, ubisoft = false))
        assertNull(UbisoftSupport.stampFor(folderHasFiles = false, ubisoft = false))
    }

    @Test
    fun stampForFolderLeavesAnEmptyOrMissingFolderUnset() {
        UbisoftSupport.forgetScans()
        val empty = tmp.newFolder("empty")
        val onlyDirs = tmp.newFolder("onlyDirs")
        File(onlyDirs, "Bin").mkdirs()
        val plain = tmp.newFolder("plain")
        File(plain, "game.exe").writeText("x")
        val ubisoft = tmp.newFolder("ubisoft")
        File(ubisoft, "uplay_r1_loader.dll").writeText("x")
        assertNull(UbisoftSupport.stampForFolder(null))
        assertNull(UbisoftSupport.stampForFolder(File(tmp.root, "missing")))
        assertNull(UbisoftSupport.stampForFolder(empty))
        assertNull(UbisoftSupport.stampForFolder(onlyDirs))
        assertEquals("0", UbisoftSupport.stampForFolder(plain))
        assertEquals("1", UbisoftSupport.stampForFolder(ubisoft))
    }

    @Test
    fun dropStaleStampRemovesOnlyTheZeroLine() {
        val text = "[Desktop Entry]\nName=G\n\n[Extra Data]\nstoreSource=epic\nubisoft=0\nepicEos=1\n"
        assertEquals("[Desktop Entry]\nName=G\n\n[Extra Data]\nstoreSource=epic\nepicEos=1\n", UbisoftSupport.dropStaleStamp(text))
    }

    @Test
    fun dropStaleStampKeepsAOneAndAnythingElse() {
        val one = "[Extra Data]\nstoreSource=epic\nubisoft=1\n"
        assertEquals(one, UbisoftSupport.dropStaleStamp(one))
        val none = "[Extra Data]\nstoreSource=epic\n"
        assertEquals(none, UbisoftSupport.dropStaleStamp(none))
        val similar = "[Extra Data]\nxubisoft=0\nubisoft_note=0\nubisoft=yes\n"
        assertEquals(similar, UbisoftSupport.dropStaleStamp(similar))
        assertEquals("", UbisoftSupport.dropStaleStamp(""))
    }

    @Test
    fun dropStaleStampHandlesCrLfAndAMissingFinalNewline() {
        assertEquals("a=1\r\nb=2", UbisoftSupport.dropStaleStamp("a=1\r\nubisoft=0\r\nb=2"))
        assertEquals("a=1", UbisoftSupport.dropStaleStamp("a=1\nubisoft=0"))
        assertEquals("a=1\n", UbisoftSupport.dropStaleStamp("a=1\nubisoft=0\n"))
    }

    // ---- Epic-like detection ----------------------------------------------------------------------

    private val epicExe = "Z:\\epic_games\\Assassins Creed Syndicate\\ACS.exe"

    @Test
    fun anEpicStoreTagIsEpicWhateverThePath() {
        assertTrue(UbisoftSupport.isEpicLike("epic", epicExe))
        assertTrue(UbisoftSupport.isEpicLike("epic", "C:\\Games\\X\\x.exe"))
        assertTrue(UbisoftSupport.isEpicLike("epic", ""))
    }

    @Test
    fun aCustomOrUntaggedShortcutUnderEpicGamesIsEpicLike() {
        assertTrue(UbisoftSupport.isEpicLike("custom", epicExe))
        assertTrue(UbisoftSupport.isEpicLike("", epicExe))
        assertTrue(UbisoftSupport.isEpicLike("custom", "Z:/epic_games/Assassins Creed Syndicate/ACS.exe"))
        assertTrue(UbisoftSupport.isEpicLike("", "/data/user/0/app/files/imagefs/epic_games/G/Bin/g.exe"))
    }

    @Test
    fun epicLikePathMatchingIgnoresCaseAndNeedsAGameFolder() {
        assertTrue(UbisoftSupport.isEpicLike("custom", "Z:\\EPIC_GAMES\\Assassins Creed Syndicate\\ACS.EXE"))
        assertTrue(UbisoftSupport.isEpicLike("CUSTOM", epicExe))
        assertFalse(UbisoftSupport.isEpicLike("custom", "Z:\\epic_games"))
        assertFalse(UbisoftSupport.isEpicLike("custom", "Z:\\epic_games\\"))
        assertFalse(UbisoftSupport.isEpicLike("custom", "Z:\\epic_games_old\\G\\g.exe"))
    }

    @Test
    fun aCustomOrUntaggedShortcutElsewhereIsNotEpicLike() {
        assertFalse(UbisoftSupport.isEpicLike("custom", "C:\\Games\\Assassins Creed Syndicate\\ACS.exe"))
        assertFalse(UbisoftSupport.isEpicLike("", "C:\\Games\\Assassins Creed Syndicate\\ACS.exe"))
        assertFalse(UbisoftSupport.isEpicLike("", ""))
    }

    @Test
    fun anotherStoresTagIsNeverEpicLikeEvenUnderEpicGames() {
        for (tag in listOf("steam", "gog", "amazon", "xbox", "xbox:abc", "android", "Steam", "ea", "origin")) {
            assertFalse("storeSource=$tag", UbisoftSupport.isEpicLike(tag, epicExe))
        }
    }

    @Test
    fun aSteamInstallFolderIsNotEpicLikeEvenWithEpicGamesInItsPath() {
        assertFalse(UbisoftSupport.isEpicLike("steam", "Z:\\steam_games\\epic_games\\G\\g.exe"))
        assertFalse(UbisoftSupport.isEpicLike("", "Z:\\steam_games\\epic_games\\G\\g.exe"))
        assertFalse(UbisoftSupport.isEpicLike("custom", "Z:\\steam_games\\epic_games\\G\\g.exe"))
        assertFalse(UbisoftSupport.isEpicLike("", "G:\\steamapps\\common\\epic_games\\G\\g.exe"))
        assertFalse(UbisoftSupport.isEpicLike("steam", "Z:\\games\\epic_games\\steamapps\\common\\G\\g.exe"))
    }

    @Test
    fun theEpicLikeReasonNamesTheTagAndThePath() {
        val under = UbisoftSupport.epicLikeReason("custom", epicExe)
        assertTrue(under.first)
        assertTrue(under.second, under.second.contains("custom") && under.second.contains("epic_games") && under.second.contains("Assassins Creed Syndicate"))
        assertEquals("storeSource=epic", UbisoftSupport.epicLikeReason("epic", "").second)
        val elsewhere = UbisoftSupport.epicLikeReason("custom", "C:\\Games\\x.exe")
        assertFalse(elsewhere.first)
        assertEquals("storeSource is custom and the exe path is not under epic_games", elsewhere.second)
        assertEquals("storeSource is empty and the exe path is not under epic_games", UbisoftSupport.epicLikeReason("", "C:\\x.exe").second)
        assertEquals("storeSource='steam' is another store's tag", UbisoftSupport.epicLikeReason("steam", epicExe).second)
    }

    // ---- Launch report route -----------------------------------------------------------------------

    @Test
    fun theReportRouteNeedsAnEpicLikeGameAndEitherTheStampOrARecordedInstall() {
        val skipped = UbisoftInstallRegistry.Result.SKIPPED_NOT_UBISOFT
        assertTrue(UbisoftSupport.epicRouteDecision(true, true, null))
        assertTrue(UbisoftSupport.epicRouteDecision(true, true, skipped))
        assertFalse(UbisoftSupport.epicRouteDecision(true, false, skipped))
        assertFalse(UbisoftSupport.epicRouteDecision(true, null, skipped))
        assertFalse(UbisoftSupport.epicRouteDecision(true, null, null))
        // A wrong 0 stamp cannot hide the report when the install record found a state file or folder.
        assertTrue(UbisoftSupport.epicRouteDecision(true, false, UbisoftInstallRegistry.Result.REGISTERED))
        assertTrue(UbisoftSupport.epicRouteDecision(true, false, UbisoftInstallRegistry.Result.ALREADY_PRESENT))
        assertTrue(UbisoftSupport.epicRouteDecision(true, null, UbisoftInstallRegistry.Result.NO_STATE_FILE))
        // Not an Epic-like game: never.
        assertFalse(UbisoftSupport.epicRouteDecision(false, true, UbisoftInstallRegistry.Result.REGISTERED))
        assertFalse(UbisoftSupport.epicRouteDecision(false, true, null))
    }

    // ---- Launch decisions note ---------------------------------------------------------------------

    @Test
    fun aUbisoftMarkerInThePathIsFoundWithEitherSeparatorAndAnyCase() {
        assertTrue(UbisoftSupport.pathHasUbisoftMarker("Z:\\Games\\X\\uplay_r1_loader.dll"))
        assertTrue(UbisoftSupport.pathHasUbisoftMarker("C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher\\UPC.exe"))
        assertTrue(UbisoftSupport.pathHasUbisoftMarker("/x/Uplay/UplayInstaller.exe"))
        assertFalse(UbisoftSupport.pathHasUbisoftMarker("Z:\\steam_games\\Portal 2\\portal2.exe"))
        assertFalse(UbisoftSupport.pathHasUbisoftMarker("Z:\\games\\upcoming\\upcoming.exe"))
        assertFalse(UbisoftSupport.pathHasUbisoftMarker(""))
    }

    @Test
    fun aLaunchGetsANoteOnlyWhenSomethingUbisoftApplies() {
        // A plain Epic game (stamp 0 or unset), a Steam game without the chain, a custom exe: nothing.
        assertNull(UbisoftSupport.decisionsTrigger(false, false, false, null))
        assertEquals("Epic-like shortcut stamped Ubisoft", UbisoftSupport.decisionsTrigger(true, false, false, null))
        assertEquals("Ubisoft launcher chain armed", UbisoftSupport.decisionsTrigger(false, true, false, null))
        assertEquals("Ubisoft file name in the exe path", UbisoftSupport.decisionsTrigger(false, false, true, null))
        assertEquals("Ubisoft launch report route 'steam'", UbisoftSupport.decisionsTrigger(false, false, false, "steam"))
        assertEquals(
            "Epic-like shortcut stamped Ubisoft, Ubisoft launch report route 'epic'",
            UbisoftSupport.decisionsTrigger(true, false, false, "epic"),
        )
    }

    @Test
    fun theNoReportReasonSaysWhy() {
        assertEquals(
            "storeSource is custom and the exe path is not under epic_games, and no Ubisoft launcher chain in a Steam launch plan",
            UbisoftSupport.noRouteReason("custom", "C:\\Games\\x.exe", "", false, false),
        )
        assertEquals(
            "storeSource is custom and the exe path is under epic_games (game folder 'G'); ubisoft stamp=0 after recheck and the install record was SKIPPED_NOT_UBISOFT",
            UbisoftSupport.noRouteReason("custom", "Z:\\epic_games\\G\\g.exe", "0", false, true),
        )
        assertEquals(
            "storeSource=epic; ubisoft stamp=unset",
            UbisoftSupport.noRouteReason("epic", "Z:\\epic_games\\G\\g.exe", "", false, false),
        )
        assertEquals("the Ubisoft launcher chain is armed but the report was not started", UbisoftSupport.noRouteReason("steam", "", "", true, false))
    }

    private fun facts(route: String?) = UbisoftSupport.LaunchFacts(
        timestamp = "2026-11-03 14:02:11", trigger = "Epic-like shortcut stamped Ubisoft",
        app = "3.1.8 (versionCode 318)", flavor = "standard", debug = false,
        shortcutFile = "/c/desktop/Assassins Creed Syndicate.desktop", name = "Assassins Creed Syndicate", exe = epicExe,
        storeSource = "custom", epicAppName = "", epicSandboxId = "", epicEos = "", ubisoft = "", launchMode = "", steamAppId = "",
        strictEpic = false, epicLike = true, epicLikeWhy = "storeSource is custom and the exe path is under epic_games (game folder 'Assassins Creed Syndicate')",
        installDir = "/imagefs/epic_games/Assassins Creed Syndicate", installRoute = "the game folder below epic_games on the exe path",
        hasLauncherFiles = "true", stateFile = "present, product id 1875 (Assassin's Creed Syndicate)",
        launcherInstalled = "launcherInstalled=true (upc.exe=present)",
        connectBuild = UbisoftSupport.connectBuildText(7178),
        bundledInstaller = UbisoftSupport.bundledInstallerText("/imagefs/epic_games/Assassins Creed Syndicate/Uplay/UplayInstaller.exe", 128714248L),
        setupDecision = "Epic gate: " + UbisoftSupport.epicSetupDecision(false, "UplayInstaller.exe"),
        registration = "REGISTERED: 11 of 11 value(s) written",
        route = route, routeWhy = "not needed",
    )

    @Test
    fun theDecisionsNoteListsEveryFact() {
        val text = UbisoftSupport.launchDecisionsText(facts("epic"))
        for (part in listOf(
            "timestamp: 2026-11-03 14:02:11", "written because: Epic-like shortcut stamped Ubisoft", "app: 3.1.8 (versionCode 318) flavor=standard debug=false",
            "shortcut file: /c/desktop/Assassins Creed Syndicate.desktop",
            "storeSource='custom'  epicAppName=''  epicSandboxId=''  epicEos=''  ubisoft=''  launchMode=''  steamAppId=''",
            "storeSource is exactly epic: false", "): true - storeSource is custom and the exe path is under epic_games",
            "install folder: /imagefs/epic_games/Assassins Creed Syndicate  (found by: the game folder below epic_games on the exe path)",
            "hasLauncherFiles(install folder): true", "uplay_install.state: present, product id 1875",
            "Ubisoft Connect in the prefix: launcherInstalled=true", "install record (UbisoftInstallRegistry.last): REGISTERED: 11 of 11",
            "Ubisoft Connect build (version.txt): 7178\n",
            "bundled installer: /imagefs/epic_games/Assassins Creed Syndicate/Uplay/UplayInstaller.exe (128714248 bytes, current-client name: false)\n",
            "Ubisoft Connect setup decision (read-only, as the launch gate would answer now): Epic gate: DOWNLOAD (LEGACY_BUNDLED)\n",
            "Ubisoft launch report route: epic\n",
        )) assertTrue("missing '$part' in:\n$text", text.contains(part))
        assertFalse(text.contains("not needed"))
    }

    @Test
    fun connectBuildFromReadsAPlainNumber() {
        assertEquals(7178, UbisoftSupport.connectBuildFrom("7178"))
        assertEquals(13393, UbisoftSupport.connectBuildFrom(" 13393\r\n"))
        assertEquals(13393, UbisoftSupport.connectBuildFrom("\uFEFF13393\n"))
    }

    @Test
    fun connectBuildFromRefusesAnythingElse() {
        assertNull(UbisoftSupport.connectBuildFrom(""))
        assertNull(UbisoftSupport.connectBuildFrom("   \n"))
        assertNull(UbisoftSupport.connectBuildFrom("v175"))
        assertNull(UbisoftSupport.connectBuildFrom("7178 13393"))
        assertNull(UbisoftSupport.connectBuildFrom("7178\n13393"))
        assertNull(UbisoftSupport.connectBuildFrom("0"))
        assertNull(UbisoftSupport.connectBuildFrom("-5"))
        assertNull(UbisoftSupport.connectBuildFrom("99999999999999999999"))
        assertNull(UbisoftSupport.connectBuildFrom("1000000"))
    }

    @Test
    fun connectBuildReadsTheFileInThePrefix() {
        val root = tmp.newFolder("c1")
        assertNull(UbisoftSupport.connectBuildAt(root))
        val dir = File(root, ".wine/drive_c/Program Files (x86)/Ubisoft/Ubisoft Game Launcher")
        dir.mkdirs()
        val file = File(dir, "version.txt")
        file.writeText("7178")
        assertEquals(7178, UbisoftSupport.connectBuildAt(root))
        file.writeText("13393\n")
        assertEquals(13393, UbisoftSupport.connectBuildAt(root))
        file.writeText("x".repeat(500))
        assertNull(UbisoftSupport.connectBuildAt(root))
    }

    @Test
    fun onlyTheCurrentClientNameIsModern() {
        assertTrue(UbisoftSupport.isModernInstallerName("UbisoftConnectInstaller.exe"))
        assertTrue(UbisoftSupport.isModernInstallerName("Ubisoft Connect Installer.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("UplayInstaller.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("Uplay_Installer.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("UbisoftGameLauncherInstaller.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("upcinstaller.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("UbisoftConnectUninstaller.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName("DXSETUP.exe"))
        assertFalse(UbisoftSupport.isModernInstallerName(null))
    }

    @Test
    fun theProcessFormOfTheModernCheckJudgesTheFileNameOnly() {
        assertTrue(UbisoftSupport.isModernInstaller("%INSTALLDIR%\\Support\\Uplay\\UbisoftConnectInstaller.exe"))
        assertTrue(UbisoftSupport.isModernInstaller("\"C:\\bl_installscript\\ubisoft_installer\\UbisoftConnectInstaller.exe\""))
        assertFalse(UbisoftSupport.isModernInstaller("%INSTALLDIR%\\Support\\Uplay\\UplayInstaller.exe"))
        assertFalse(UbisoftSupport.isModernInstaller("%INSTALLDIR%\\DirectX\\DXSETUP.exe"))
    }

    @Test
    fun theBestInstallerNameIsTheLowestRankAndTheFirstOfEquals() {
        assertNull(UbisoftSupport.bestInstallerName(emptyList()))
        assertEquals("UbisoftConnectInstaller.exe", UbisoftSupport.bestInstallerName(listOf("UplayInstaller.exe", "UbisoftConnectInstaller.exe")))
        assertEquals("UbisoftGameLauncherInstaller.exe", UbisoftSupport.bestInstallerName(listOf("UbisoftGameLauncherInstaller.exe", "UplayInstaller.exe")))
        assertEquals("Uplay_Installer.exe", UbisoftSupport.bestInstallerName(listOf("Uplay_Installer.exe", "UplayInstaller.exe")))
    }

    private fun epic(installed: Boolean, bundled: String?) = UbisoftSupport.epicSetupDecision(installed, bundled)

    private fun assertDecision(setup: UbisoftSupport.EpicSetup, reason: UbisoftSupport.EpicSetupReason, actual: UbisoftSupport.EpicDecision) =
        assertEquals(UbisoftSupport.EpicDecision(setup, reason), actual)

    @Test
    fun epicConnectMissingWithACurrentBundledInstallerRunsIt() {
        assertDecision(UbisoftSupport.EpicSetup.BUNDLED, UbisoftSupport.EpicSetupReason.MODERN_BUNDLED, epic(false, "UbisoftConnectInstaller.exe"))
    }

    @Test
    fun epicConnectMissingWithALegacyBundledInstallerDownloadsTheCurrentOne() {
        for (name in listOf("UplayInstaller.exe", "Uplay_Installer.exe", "UbisoftGameLauncherInstaller.exe", "upcinstaller_x64.exe")) {
            assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.LEGACY_BUNDLED, epic(false, name))
        }
    }

    @Test
    fun epicConnectMissingWithNoBundledInstallerDownloads() {
        assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.NO_INSTALLER, epic(false, null))
    }

    @Test
    fun epicConnectInstalledNeedsNothingWhateverTheGameBundles() {
        for (name in listOf(null, "UbisoftConnectInstaller.exe", "UplayInstaller.exe")) {
            assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.CURRENT, epic(true, name))
        }
    }

    private fun steam(installed: Boolean, ubisoft: Boolean, installer: String?, launcherFiles: Boolean) =
        UbisoftSupport.steamSetupDecision(installed, ubisoft, installer, launcherFiles)

    @Test
    fun steamConnectInstalledNeedsNothing() {
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.CURRENT, steam(true, true, "UplayInstaller.exe", true))
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.CURRENT, steam(true, false, null, false))
    }

    @Test
    fun steamNonUbisoftGameNeedsNothing() {
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.NOT_APPLICABLE, steam(false, false, null, false))
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.NOT_APPLICABLE, steam(false, false, "UbisoftConnectInstaller.exe", true))
    }

    @Test
    fun steamModernInstallerRunsAsBefore() {
        assertDecision(UbisoftSupport.EpicSetup.BUNDLED, UbisoftSupport.EpicSetupReason.MODERN_BUNDLED, steam(false, true, "UbisoftConnectInstaller.exe", true))
        assertDecision(UbisoftSupport.EpicSetup.BUNDLED, UbisoftSupport.EpicSetupReason.MODERN_BUNDLED, steam(false, true, "UbisoftConnectInstaller.exe", false))
    }

    @Test
    fun steamLegacyInstallerIsNeverRunAndTheCurrentOneIsDownloaded() {
        for (name in listOf("UplayInstaller.exe", "Uplay_Installer.exe", "UbisoftGameLauncherInstaller.exe")) {
            assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.LEGACY_BUNDLED, steam(false, true, name, true))
            assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.LEGACY_BUNDLED, steam(false, true, name, false))
        }
    }

    @Test
    fun steamLauncherFilesWithoutAnyInstallerDownloads() {
        assertDecision(UbisoftSupport.EpicSetup.DOWNLOAD, UbisoftSupport.EpicSetupReason.NO_INSTALLER, steam(false, true, null, true))
    }

    @Test
    fun steamUbisoftScriptWithoutLauncherFilesOrInstallerHasNothingToSetUp() {
        assertDecision(UbisoftSupport.EpicSetup.NONE, UbisoftSupport.EpicSetupReason.NOT_APPLICABLE, steam(false, true, null, false))
    }

    @Test
    fun theBundledInstallerTextSaysSizeAndWhetherItIsCurrent() {
        assertEquals("none", UbisoftSupport.bundledInstallerText(null, 0L))
        assertEquals(
            "/g/UbisoftConnectInstaller.exe (262918936 bytes, current-client name: true)",
            UbisoftSupport.bundledInstallerText("/g/UbisoftConnectInstaller.exe", 262918936L),
        )
    }

    @Test
    fun theSteamChainIsRecognisedFromThePlanEnv() {
        assertTrue(UbisoftSupport.steamChainArmed(mapOf(EaSupport.CHAIN_ENV to UbisoftSupport.CHAIN_VALUE)))
        assertFalse(UbisoftSupport.steamChainArmed(mapOf(EaSupport.CHAIN_ENV to EaSupport.CHAIN_VALUE)))
        assertFalse(UbisoftSupport.steamChainArmed(emptyMap()))
        assertFalse(UbisoftSupport.steamChainArmed(null))
    }

    @Test
    fun anInstallScriptIsFoundByNameWithoutLookingAtTheDepot() {
        val dir = tmp.newFolder("byName")
        assertNull(InstallScriptExecutor.locateScriptByName(dir))
        val app = File(dir, "242050_install.vdf").apply { writeText("x") }
        // The app-named script is returned by name alone; locateScript is the one that asks the depot's files.
        assertEquals(app, InstallScriptExecutor.locateScriptByName(dir))
        assertNull(InstallScriptExecutor.locateScript(dir))
        val classic = File(dir, "installScript.vdf").apply { writeText("x") }
        assertEquals(classic, InstallScriptExecutor.locateScriptByName(dir))
        File(dir, "Folder_installscript.vdf").mkdirs()
        assertEquals(classic, InstallScriptExecutor.locateScript(dir))
    }

    @Test
    fun aScriptNameBecomesOneSafeFileName() {
        assertEquals("Ubisoft_Connect_PC_Client", InstallScriptExecutor.safeFileName("Ubisoft Connect PC Client"))
        assertEquals("a_b_c_d_e", InstallScriptExecutor.safeFileName("a/b\\c:d*e"))
        assertEquals("Need_for_Speed__Payback", InstallScriptExecutor.safeFileName("Need for Speed™ Payback"))
        assertEquals("x_1_y", InstallScriptExecutor.safeFileName("x\"1&y"))
        assertEquals("EA-app_1.0", InstallScriptExecutor.safeFileName("EA-app_1.0"))
        assertEquals("installscript", InstallScriptExecutor.safeFileName(""))
    }

    @Test
    fun aCopyTargetMustStayBelowItsAliasRoot() {
        val root = File(tmp.root, "bl_installscript").apply { mkdirs() }
        assertTrue(InstallScriptExecutor.isUnder(root, File(root, "Game/Support/x.exe")))
        assertFalse(InstallScriptExecutor.isUnder(root, File(root, "Game/../../outside/x.exe")))
        assertFalse(InstallScriptExecutor.isUnder(root, root))
        assertFalse(InstallScriptExecutor.isUnder(root, File(tmp.root, "bl_installscript_other/x.exe")))
    }

    @Test
    fun theSharedSetupCheckOnlyLooksAtUbisoftCandidates() {
        val none = UbisoftSupport.GateKind.NONE
        val epic = UbisoftSupport.GateKind.EPIC
        val steam = UbisoftSupport.GateKind.STEAM
        // gateKind(androidEntry, epicLike, stamp, stampRechecked, steamOrigin)
        assertEquals(none, UbisoftSupport.gateKind(false, false, null, false, false))   // custom / GOG / Xbox / Linux
        assertEquals(none, UbisoftSupport.gateKind(true, true, true, false, true))      // an Android game is never one
        assertEquals(epic, UbisoftSupport.gateKind(false, true, null, false, false))    // Epic, never looked at
        assertEquals(epic, UbisoftSupport.gateKind(false, true, true, false, false))    // Epic stamped Ubisoft
        assertEquals(epic, UbisoftSupport.gateKind(false, true, true, true, false))
        assertEquals(epic, UbisoftSupport.gateKind(false, true, false, false, false))   // "0" not looked at again yet
        assertEquals(none, UbisoftSupport.gateKind(false, true, false, true, false))    // "0", looked at again: skip the disk
        assertEquals(steam, UbisoftSupport.gateKind(false, false, null, false, true))
        assertEquals(epic, UbisoftSupport.gateKind(false, true, null, false, true))     // Epic wins over a Steam-looking path
    }

    @Test
    fun theDecisionsNoteExplainsAMissingReportRoute() {
        val text = UbisoftSupport.launchDecisionsText(facts(null))
        assertTrue(text, text.contains("Ubisoft launch report route: none - not needed\n"))
    }
}
