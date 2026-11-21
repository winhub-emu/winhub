package com.winhub.hernandez.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** The pure parts of [UbisoftInstallerDownload]: what counts as a whole, trustworthy download, and where redirects may go. */
class UbisoftInstallerDownloadTest {

    private val mz = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x90.toByte())
    private val mb = 1024L * 1024L

    @Test
    fun onlyAnMzHeaderLooksLikeAWindowsProgram() {
        assertTrue(UbisoftInstallerDownload.hasMzHeader(mz))
        assertFalse(UbisoftInstallerDownload.hasMzHeader("<html>".toByteArray()))
        assertFalse(UbisoftInstallerDownload.hasMzHeader(byteArrayOf('M'.code.toByte())))
        assertFalse(UbisoftInstallerDownload.hasMzHeader(ByteArray(0)))
    }

    @Test
    fun theSizeMustEqualAKnownContentLength() {
        assertTrue(UbisoftInstallerDownload.sizeMatches(262_918_936L, 262_918_936L))
        assertFalse(UbisoftInstallerDownload.sizeMatches(262_918_936L, 262_918_935L))
        assertFalse(UbisoftInstallerDownload.sizeMatches(-1L, -1L))
        assertFalse(UbisoftInstallerDownload.sizeMatches(0L, 0L))
    }

    @Test
    fun theDiskNeedsRoomForTheFileAndItsUnpacking() {
        assertTrue(UbisoftInstallerDownload.hasEnoughSpace(400 * mb, 251 * mb))
        assertFalse(UbisoftInstallerDownload.hasEnoughSpace(300 * mb, 251 * mb))
    }

    @Test
    fun aKeptFileIsReusedOnlyWhenItIsBigAndAnExecutable() {
        assertTrue(UbisoftInstallerDownload.isPlausibleInstaller(251 * mb, mz))
        assertFalse(UbisoftInstallerDownload.isPlausibleInstaller(1 * mb, mz))
        assertFalse(UbisoftInstallerDownload.isPlausibleInstaller(251 * mb, "<html>".toByteArray()))
        assertFalse(UbisoftInstallerDownload.isPlausibleInstaller(251 * mb, ByteArray(0)))
    }

    @Test
    fun aFullDiskIsRecognisedFromTheErrorText() {
        assertTrue(UbisoftInstallerDownload.isNoSpace(IOException("write failed: ENOSPC (No space left on device)")))
        assertTrue(UbisoftInstallerDownload.isNoSpace(IOException("No space left on device")))
        assertFalse(UbisoftInstallerDownload.isNoSpace(IOException("Connection reset")))
        assertFalse(UbisoftInstallerDownload.isNoSpace(IOException()))
    }

    @Test
    fun redirectsStayOnTheSameHostOverHttps() {
        val from = UbisoftInstallerDownload.INSTALLER_URL
        assertEquals(
            "https://static3.cdn.ubi.com/orbit/other/UbisoftConnectInstaller.exe",
            UbisoftInstallerDownload.redirectTarget(from, "/orbit/other/UbisoftConnectInstaller.exe"),
        )
        assertEquals(
            "https://STATIC3.cdn.ubi.com/x.exe",
            UbisoftInstallerDownload.redirectTarget(from, "https://STATIC3.cdn.ubi.com/x.exe"),
        )
        assertNull(UbisoftInstallerDownload.redirectTarget(from, "https://evil.example.com/x.exe"))
        assertNull(UbisoftInstallerDownload.redirectTarget(from, "http://static3.cdn.ubi.com/x.exe"))
        assertNull(UbisoftInstallerDownload.redirectTarget(from, "ftp://static3.cdn.ubi.com/x.exe"))
        assertNull(UbisoftInstallerDownload.redirectTarget(from, ""))
        assertNull(UbisoftInstallerDownload.redirectTarget(from, null))
    }

    @Test
    fun aRedirectToAnotherPortOrWithCredentialsIsRefused() {
        val from = UbisoftInstallerDownload.INSTALLER_URL
        assertNull(UbisoftInstallerDownload.redirectTarget(from, "https://static3.cdn.ubi.com:8443/x.exe"))
        assertNull(UbisoftInstallerDownload.redirectTarget(from, "https://user:pw@static3.cdn.ubi.com/x.exe"))
        assertEquals(
            "https://static3.cdn.ubi.com:8443/y.exe",
            UbisoftInstallerDownload.redirectTarget("https://static3.cdn.ubi.com:8443/x.exe", "/y.exe"),
        )
    }

    @Test
    fun onlyAnInstallerSizedLengthIsAccepted() {
        assertTrue(UbisoftInstallerDownload.isPlausibleLength(262_918_936L))
        assertTrue(UbisoftInstallerDownload.isPlausibleLength(1024 * mb))
        assertFalse(UbisoftInstallerDownload.isPlausibleLength(1024 * mb + 1))
        assertFalse(UbisoftInstallerDownload.isPlausibleLength(1 * mb))
        assertFalse(UbisoftInstallerDownload.isPlausibleLength(0L))
        assertFalse(UbisoftInstallerDownload.isPlausibleLength(-1L))
    }

    @Test
    fun aTransferHasTwentyFiveMinutes() {
        assertFalse(UbisoftInstallerDownload.pastDeadline(0L))
        assertFalse(UbisoftInstallerDownload.pastDeadline(25L * 60 * 1000))
        assertTrue(UbisoftInstallerDownload.pastDeadline(25L * 60 * 1000 + 1))
    }

    @Test
    fun theSpaceMessageNamesTheFileAndItsMargin() {
        assertEquals(351, UbisoftInstallerDownload.NEEDED_FREE_MB)
        assertTrue(UbisoftInstallerDownload.hasEnoughSpace(UbisoftInstallerDownload.NEEDED_FREE_MB * mb, UbisoftInstallerDownload.APPROX_MB * mb))
    }

    @Test
    fun onlyTheDownloadedInstallerIsDeletedByTheSessionBatch() {
        val path = "C:\\bl_installscript\\ubisoft_installer\\UbisoftConnectInstaller.exe"
        assertTrue(UbisoftInstallerDownload.isDownloadedPath(path))
        assertTrue(UbisoftInstallerDownload.isDownloadedPath("\"c:\\BL_INSTALLSCRIPT\\ubisoft_installer\\ubisoftconnectinstaller.exe\""))
        assertEquals(
            listOf(
                "echo Removing the downloaded installer...",
                "del /f /q \"C:\\bl_installscript\\ubisoft_installer\\*\" >nul 2>&1",
                "rmdir \"C:\\bl_installscript\\ubisoft_installer\" >nul 2>&1",
            ),
            UbisoftInstallerDownload.cleanupBatchLines(path),
        )
        for (other in listOf(
            "C:\\bl_installscript\\Assassins_Creed_IV\\Support\\Uplay\\UbisoftConnectInstaller.exe",
            "C:\\bl_installscript\\ubisoft_installer\\sub\\UbisoftConnectInstaller.exe",
            "C:\\bl_installscript\\ubisoft_installer\\UplayInstaller.exe",
            "Z:\\steam_games\\Game\\UplayInstaller.exe",
            "%INSTALLDIR%\\Support\\Uplay\\UbisoftConnectInstaller.exe",
            "",
        )) {
            assertFalse(other, UbisoftInstallerDownload.isDownloadedPath(other))
            assertTrue(other, UbisoftInstallerDownload.cleanupBatchLines(other).isEmpty())
        }
    }

    @Test
    fun theFixedUrlIsHttpsAndTheWinePathIsUnderTheContainersCDrive() {
        assertTrue(UbisoftInstallerDownload.INSTALLER_URL.startsWith("https://"))
        assertEquals(
            "C:\\bl_installscript\\ubisoft_installer\\UbisoftConnectInstaller.exe",
            UbisoftInstallerDownload.windowsPathOf(File("/data/c/.wine/drive_c/bl_installscript/ubisoft_installer/UbisoftConnectInstaller.exe")),
        )
    }
}
