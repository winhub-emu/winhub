package com.winhub.hernandez.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * [UbisoftDiagScan] and the small pure parts of the Ubisoft report: redaction, registry sections, folder walks and
 * the before/after comparison, copy rules, logcat and `/proc` parsing, and the deep-trace channel rule.
 */
class UbisoftDiagScanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val hive = """
WINE REGISTRY Version 2
;; All keys relative to \\Machine

[Software\\Wow6432Node\\Ubisoft\\Launcher] 1793630773
#time=1dd6ad8e82cadc0
"InstallDir"="C:\\Program Files (x86)\\Ubisoft\\Ubisoft Game Launcher\\"
"UserToken"="abcdef"

[Software\\Wow6432Node\\Valve\\Steam] 1793628533
#time=1dd6ad3b139d090
"InstallPath"="C:\\Steam"

[Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Uplay Install 1875] 1793632643
#time=1dd6add431606e2
"DisplayName"="Assassin's Creed Syndicate"
""".trimIndent()

    // ---- Redaction ------------------------------------------------------------------------------

    @Test
    fun yamlSecretsAreReplacedAndTheRestIsKept() {
        val yaml = "login:\n  username: bob\n  password: hunter2\n  rememberMe: true\noverlay:\n  enabled: false\n" +
            "session:\n  id: abc\n  more:\n    x: 1\nlanguage: en\n"
        val out = UbisoftDiagScan.redactYaml(yaml)
        assertEquals(
            "login:\n  username: <redacted>\n  password: <redacted>\n  rememberMe: true\noverlay:\n  enabled: false\n" +
                "session:\n  <redacted>\n  <redacted>\n    <redacted>\nlanguage: en\n",
            out,
        )
    }

    @Test
    fun logLinesLoseTokensAndKeepEverythingElse() {
        assertEquals("token=<redacted> user=x", UbisoftDiagScan.redactLine("token=abc12345 user=x"))
        assertFalse(UbisoftDiagScan.redactLine("Authorization: Bearer abcdefghijkl").contains("abcdefghijkl"))
        assertFalse(UbisoftDiagScan.redactLine("jwt eyJhbGciOiJI.eyJzdWIiOiIx.c2ln here").contains("eyJzdWIi"))
        assertEquals("loaded 12 files from the cache", UbisoftDiagScan.redactLine("loaded 12 files from the cache"))
        assertEquals("short", UbisoftDiagScan.redactLine("short"))
    }

    @Test
    fun secretNames() {
        for (n in listOf("Password", "AccessToken", "client_secret", "ExchangeCode", "AuthTicket", "Cookie", "SessionId")) {
            assertTrue(n, UbisoftDiagScan.isSecretName(n))
        }
        assertFalse(UbisoftDiagScan.isSecretName("InstallDir"))
    }

    @Test
    fun yamlFieldsThatNameAPersonAreReplacedWithTheirChildren() {
        val yaml = "profile:\n  nickname: Big Bob\n  level: 5\nemail: bob@example.com\nlanguage: en\n"
        assertEquals("profile:\n  <redacted>\n  <redacted>\nemail: <redacted>\nlanguage: en\n", UbisoftDiagScan.redactYaml(yaml))
    }

    @Test
    fun epicLaunchArgumentsLoseTheirValuesWhetherQuotedOrBare() {
        val line = "wine game.exe -epicusername=Bob -epicuserid=abc123 -epicovt=\"tok en\" -AUTH_PASSWORD=pw -AUTH_TYPE=exchangecode " +
            "-epicdeploymentid=d1 -epicsandboxid=s1 -epiclocale=en"
        val out = UbisoftDiagScan.redactLine(line)
        for (secret in listOf("Bob", "abc123", "tok en", "=pw", "exchangecode", "=d1", "=s1")) assertFalse(secret + " in " + out, out.contains(secret))
        assertTrue(out, out.contains("-epicusername=<redacted>"))
        assertTrue(out, out.contains("-epicovt=<redacted>"))
        assertTrue(out, out.contains("-AUTH_TYPE=<redacted>"))
        assertTrue(out, out.contains("-epiclocale=en"))
    }

    @Test
    fun emailsAndGuidsAreMaskedInAnyLine() {
        val out = UbisoftDiagScan.redactLine("contact a.b@example.com id 123e4567-e89b-12d3-a456-426614174000 end")
        assertFalse(out, out.contains("example.com"))
        assertFalse(out, out.contains("426614174000"))
        assertTrue(out, out.endsWith(" end"))
    }

    @Test
    fun jsonFieldsThatNameAPersonKeepTheirQuotesAndLoseTheirValues() {
        val out = UbisoftDiagScan.redactLine("{\"email\": \"bob@x.com\", \"nickname\": \"Big Bob\", \"nameOnPlatform\": \"Bob\", \"level\": 5}")
        assertFalse(out, out.contains("Big"))
        assertFalse(out, out.contains("bob@x.com"))
        assertFalse(out, out.contains("\"Bob\""))
        assertTrue(out, out.contains("\"nickname\": \"<redacted>\""))
        assertTrue(out, out.contains("\"level\": 5"))
    }

    @Test
    fun aLineOverEightKilobytesIsCutBeforeTheRegexesRun() {
        val out = UbisoftDiagScan.redactLine("a".repeat(20_000) + " token=secretvalue1")
        assertTrue(out.length < UbisoftDiagScan.MAX_LINE_CHARS + 64)
        assertTrue(out.endsWith("[line cut at ${UbisoftDiagScan.MAX_LINE_CHARS} chars]"))
        assertFalse(out.contains("secretvalue1"))
    }

    @Test
    fun aCommandLineIsRedactedBeforeItIsCutTo200Characters() {
        val stat = UbisoftDiagScan.ProcStat(1, "wine", 'S', 0, 0, 0, 1, 0, 0)
        // The value would start at character 196, inside the cut, if the cut came first.
        val out = UbisoftDiagScan.formatProc(stat, "ab ".repeat(62) + "-epicovt=abcdefgh")
        assertFalse(out, out.contains("abc"))
        assertTrue(out, out.contains("-epicovt=<re"))
        assertTrue(UbisoftDiagScan.formatProc(stat, "wine -epicovt=abcdefgh").contains("-epicovt=<redacted>"))
    }

    @Test
    fun aTextFileIsRedactedByItsKind() {
        assertEquals("login:\n  username: <redacted>\n", UbisoftDiagScan.redactFile("settings.yaml", "login:\n  username: bob\n"))
        assertEquals("started\ntoken=<redacted>\n", UbisoftDiagScan.redactFile("launcher.log", "started\ntoken=abc12345\n"))
    }

    // ---- Registry -------------------------------------------------------------------------------

    @Test
    fun registrySectionsKeepOnlyUbisoftKeysWithTheirTimestampsAndHideSecrets() {
        val r = UbisoftDiagScan.registrySections(BufferedReader(StringReader(hive)), 100_000)
        assertEquals(
            listOf(
                "Software\\Wow6432Node\\Ubisoft\\Launcher",
                "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Uplay Install 1875",
            ),
            r.keys,
        )
        assertTrue(r.text.contains("] 1793630773"))
        assertTrue(r.text.contains("#time=1dd6ad8e82cadc0"))
        assertTrue(r.text.contains("\"InstallDir\"="))
        assertTrue(r.text.contains("\"UserToken\"=<redacted>"))
        assertFalse(r.text.contains("abcdef"))
        assertFalse(r.text.contains("Valve"))
        assertFalse(r.truncated)
    }

    @Test
    fun missingKeysAreReportedByHasKey() {
        val r = UbisoftDiagScan.registrySections(BufferedReader(StringReader(hive)), 100_000)
        assertTrue(UbisoftDiagScan.hasKey(r.keys, "software\\wow6432node\\ubisoft\\launcher"))
        assertFalse(UbisoftDiagScan.hasKey(r.keys, "Software\\Wow6432Node\\Ubisoft\\Launcher\\Installs\\1875"))
    }

    @Test
    fun registrySectionsStopAtTheSizeLimit() {
        val r = UbisoftDiagScan.registrySections(BufferedReader(StringReader(hive)), 10)
        assertEquals(1, r.keys.size)
        assertTrue(r.truncated)
    }

    @Test
    fun registryValuesLoseEmailAddressesButKeepGuidsAndPaths() {
        val text = "[Software\\\\Ubisoft\\\\Launcher] 1\n\"Mail\"=\"bob@example.com\"\n\"Id\"=\"123e4567-e89b-12d3-a456-426614174000\"\n"
        val r = UbisoftDiagScan.registrySections(BufferedReader(StringReader(text)), 100_000)
        assertFalse(r.text, r.text.contains("example.com"))
        assertTrue(r.text, r.text.contains("426614174000"))
    }

    // ---- Walks and the comparison ---------------------------------------------------------------

    private fun walkAll(root: File): Map<String, UbisoftDiagScan.Entry> {
        val out = LinkedHashMap<String, UbisoftDiagScan.Entry>()
        UbisoftDiagScan.walk(root, 8, 1000, Long.MAX_VALUE) { p, e -> out[p] = e }
        return out
    }

    @Test
    fun aWalkListsEverythingSortedAndAnEntryRoundTrips() {
        val root = tmp.newFolder("prefix")
        File(root, "b.txt").writeText("hello")
        File(root, "dir").mkdirs()
        File(root, "dir/a b.dat").writeBytes(ByteArray(300))
        val all = walkAll(root)
        assertEquals(listOf("b.txt", "dir", "dir/a b.dat"), all.keys.toList())
        assertEquals(5L, all.getValue("b.txt").size)
        assertEquals('d', all.getValue("dir").kind)
        val line = UbisoftDiagScan.formatEntry("dir/a b.dat", all.getValue("dir/a b.dat"))
        val back = UbisoftDiagScan.parseEntryLine(line)
        assertNotNull(back)
        assertEquals("dir/a b.dat", back!!.first)
        assertEquals(300L, back.second.size)
        assertEquals('f', back.second.kind)
        assertEquals(all.getValue("dir/a b.dat").mtime / 1000, back.second.mtime / 1000)
        assertNull(UbisoftDiagScan.parseEntryLine("# a header line"))
    }

    @Test
    fun theDepthAndEntryCapsAreReported() {
        val root = tmp.newFolder("deep")
        File(root, "a/b/c").mkdirs()
        File(root, "a/b/c/f.txt").writeText("x")
        val seen = ArrayList<String>()
        val r = UbisoftDiagScan.walk(root, 2, 1000, Long.MAX_VALUE) { p, _ -> seen += p }
        assertEquals(listOf("a", "a/b"), seen)
        assertEquals(1, r.depthLimited)
        val capped = UbisoftDiagScan.walk(root, 8, 2, Long.MAX_VALUE) { _, _ -> }
        assertEquals(2, capped.entries)
        assertEquals(1, capped.omitted)
        assertTrue(UbisoftDiagScan.describeWalk(r).contains("depth cap"))
    }

    @Test
    fun theComparisonFindsAddedChangedAndRemovedFiles() {
        val root = tmp.newFolder("cmp")
        File(root, "same.txt").writeText("same")
        File(root, "gone.txt").writeText("gone")
        File(root, "grows.txt").writeText("one")
        val before = walkAll(root)

        File(root, "gone.txt").delete()
        File(root, "grows.txt").writeText("one and more")
        File(root, "new.txt").writeText("new")
        val cmp = UbisoftDiagScan.Comparison(before)
        UbisoftDiagScan.walk(root, 8, 1000, Long.MAX_VALUE) { p, e -> cmp.onNow(p, e) }
        cmp.finish(true)

        assertEquals(1, cmp.addedCount)
        assertEquals(1, cmp.changedCount)
        assertEquals(1, cmp.removedCount)
        assertEquals(1, cmp.unchangedCount)
        assertTrue(cmp.added.single().endsWith("new.txt"))
        assertTrue(cmp.removed.single().endsWith("gone.txt"))
        val text = cmp.render("test", true)
        assertTrue(text.contains("added=1 changed=1 removed=1 unchanged=1"))
    }

    @Test
    fun anIncompleteWalkDoesNotClaimRemovals() {
        val cmp = UbisoftDiagScan.Comparison(mapOf("x" to UbisoftDiagScan.Entry(1, 0, 'f', "?", null)))
        cmp.finish(false)
        assertEquals(0, cmp.removedCount)
        assertTrue(cmp.render("t", false).contains("unknown"))
    }

    // ---- Files ----------------------------------------------------------------------------------

    @Test
    fun onlyAllowListedTextFilesAreCopiedAndTheRestIsHashedOnly() {
        val text = UbisoftDiagScan.CopyAction.TEXT_REDACTED
        assertEquals(text, UbisoftDiagScan.copyAction("x/settings.yaml", 100))
        assertEquals(text, UbisoftDiagScan.copyAction("x/logs/launcher_log.txt", 100))
        assertEquals(text, UbisoftDiagScan.copyAction("x/readme.md", 100))
        // Only the file NAME counts, not the folders above it.
        assertEquals(text, UbisoftDiagScan.copyAction("x/user/launcher.log", 100))
        for (other in listOf("x/y/upc.dll", "x/data.bin", "x/config.sqlite", "x/noextension", "x/uplay_install.state", "x/game.pak", "x/stuff.dat")) {
            assertEquals(other, UbisoftDiagScan.CopyAction.HASH_OTHER, UbisoftDiagScan.copyAction(other, 100))
        }
        assertEquals(UbisoftDiagScan.CopyAction.HASH_LARGE, UbisoftDiagScan.copyAction("x/big.log", 3L * 1024 * 1024))
    }

    @Test
    fun aNameThatSuggestsACredentialOrIdentityStoreIsNeverCopiedEvenAsText() {
        for (n in listOf(
            "users.dat", "launcher_session.log", "store.json", "profile.json", "ownership.txt", "my_key.txt", "cert.xml", "cache.log",
            "login.ini", "wallet.cfg", "account.yaml", "Password.txt", "tickets.json", "cookies.txt", "secure_storage.xml", "auth.conf",
        )) {
            assertEquals(n, UbisoftDiagScan.CopyAction.HASH_SENSITIVE, UbisoftDiagScan.copyAction("x/$n", 100))
        }
    }

    @Test
    fun readUpToStopsAtTheLimit() {
        assertEquals("abc", UbisoftDiagScan.readUpTo("abcdef".byteInputStream(), 3))
        assertEquals("ab", UbisoftDiagScan.readUpTo("ab".byteInputStream(), 10))
    }

    @Test
    fun sha256AndTails() {
        val f = tmp.newFile("t.log")
        f.writeText("line one\nline two\nline three\n")
        assertEquals(64, UbisoftDiagScan.sha256(f, 1000).length)
        assertTrue(UbisoftDiagScan.sha256(f, 3).startsWith("(not hashed"))
        val whole = UbisoftDiagScan.readTail(f, 1000)!!
        assertEquals("line one\nline two\nline three\n", whole.text)
        assertFalse(whole.cut)
        val tail = UbisoftDiagScan.readTail(f, 15)!!
        assertTrue(tail.cut)
        assertEquals("line three\n", tail.text)
        val bin = tmp.newFile("b.bin")
        bin.writeBytes(byteArrayOf(1, 0, 2, 0, 3))
        assertTrue(UbisoftDiagScan.readTail(bin, 100)!!.binary)
    }

    // ---- Logcat, /proc, limits ------------------------------------------------------------------

    @Test
    fun logcatLinesAreMatchedByTagOrByWord() {
        val tags = listOf("UbisoftSupport", "BH_REALSTEAM")
        assertTrue(UbisoftDiagScan.logcatLineWanted("11-03 10:00:00.123 I/UbisoftSupport( 1234): hello", tags))
        assertTrue(UbisoftDiagScan.logcatLineWanted("11-03 10:00:00.123 W/BH_REALSTEAM( 1234): x", tags))
        assertTrue(UbisoftDiagScan.logcatLineWanted("11-03 10:00:00.123 D/Other( 1): started Uplay r2 loader", tags))
        assertFalse(UbisoftDiagScan.logcatLineWanted("11-03 10:00:00.123 D/Other( 1): nothing here", tags))
    }

    @Test
    fun procStatIsParsedEvenWithSpacesInTheName() {
        val f = MutableList(22) { "0" }
        f[0] = "S"; f[1] = "1"; f[11] = "111"; f[12] = "222"; f[17] = "5"; f[20] = "123456"; f[21] = "789"
        val s = UbisoftDiagScan.parseProcStat("1234 (wine pre (loader)) " + f.joinToString(" "))
        assertEquals(UbisoftDiagScan.ProcStat(1234, "wine pre (loader)", 'S', 1, 111, 222, 5, 123456, 789), s)
        assertNull(UbisoftDiagScan.parseProcStat("garbage"))
        assertTrue(UbisoftDiagScan.formatProc(s!!, "cmd").contains("threads=5"))
    }

    @Test
    fun statusSummaryKeepsTheUsefulLines() {
        val status = "Name:\twine\nState:\tD (disk sleep)\nThreads:\t12\nVmRSS:\t  3000 kB\nUid:\t1 1 1 1\n"
        val out = UbisoftDiagScan.statusSummary(status)
        assertTrue(out.contains("State=D (disk sleep)"))
        assertTrue(out.contains("Threads=12"))
        assertTrue(out.contains("VmRSS=3000 kB"))
        assertFalse(out.contains("Uid"))
    }

    @Test
    fun textIsCutOnACharacterBoundary() {
        assertEquals("h" to true, UbisoftDiagScan.capUtf8("h\u00e9llo", 2))
        assertEquals("abc" to false, UbisoftDiagScan.capUtf8("abc", 10))
        assertEquals("" to true, UbisoftDiagScan.capUtf8("abc", 0))
    }

    // ---- Deep trace, outcome, chain -------------------------------------------------------------

    @Test
    fun deepTraceAddsOnlyTheMissingChannelsToAPlusList() {
        assertEquals("+timestamp,+loaddll,+reg,+file", UbisoftDiag.withDeepTrace("+timestamp,+loaddll"))
        assertEquals("+reg,+file", UbisoftDiag.withDeepTrace("+reg"))
        assertEquals("+reg,+file", UbisoftDiag.withDeepTrace("+reg,+file"))
        assertNull(UbisoftDiag.withDeepTrace("-all"))
        assertNull(UbisoftDiag.withDeepTrace(""))
        assertNull(UbisoftDiag.withDeepTrace(null))
    }

    @Test
    fun aSwitchIsOnOnlyFromThePreferenceOrTheContainerExtra() {
        assertFalse(UbisoftDiag.switchOn(false, null))
        assertFalse(UbisoftDiag.switchOn(false, ""))
        assertFalse(UbisoftDiag.switchOn(false, "0"))
        assertFalse(UbisoftDiag.switchOn(false, "true"))
        assertTrue(UbisoftDiag.switchOn(true, null))
        assertTrue(UbisoftDiag.switchOn(false, "1"))
        assertTrue(UbisoftDiag.switchOn(false, " 1 "))
    }

    @Test
    fun theDecisionsNoteSaysWhetherTheReportRuns() {
        assertTrue(UbisoftDiag.reportNote(true).startsWith("Ubisoft diagnostics report: ON"))
        val off = UbisoftDiag.reportNote(false)
        assertTrue(off, off.startsWith("Ubisoft diagnostics report: OFF"))
        assertTrue(off, off.contains("container extra ubisoftDiagReport=1"))
        assertTrue(off.endsWith("\n"))
    }
}
