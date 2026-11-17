package com.lifttracker.app

import com.lifttracker.app.backup.BackupCrypto
import com.lifttracker.app.backup.BackupKind
import com.lifttracker.app.backup.BackupManifest
import com.lifttracker.app.backup.WrongPasswordException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class BackupCryptoTest {
    private fun temp() = File.createTempFile("backup", ".bin").apply { deleteOnExit() }

    @Test
    fun encryptedBackupsRoundTripAcrossChunkBoundaries() {
        for (size in listOf(0, 17, 1 shl 20, (1 shl 20) + 5, 3 * (1 shl 20))) {
            val plain = temp().apply { writeBytes(Random(size).nextBytes(size)) }
            val enc = temp()
            val back = temp()
            BackupCrypto.encrypt(plain, enc, "s3cret-pw".toCharArray())
            assertTrue(BackupCrypto.isEncrypted(enc))
            assertFalse(BackupCrypto.isEncrypted(plain))
            BackupCrypto.decrypt(enc, back, "s3cret-pw".toCharArray())
            assertArrayEquals("size $size", plain.readBytes(), back.readBytes())
        }
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val plain = temp().apply { writeText("workouts") }
        val enc = temp()
        BackupCrypto.encrypt(plain, enc, "right".toCharArray())
        BackupCrypto.decrypt(enc, temp(), "wrong".toCharArray())
    }

    @Test
    fun manifestRoundTrips() {
        val m = BackupManifest(BackupKind.DATA, "2026-11-16T12:00:00", "Pixel", "1.0.0", linkedMapOf("workouts" to 221, "routines" to 8))
        assertEquals(m, BackupManifest.fromJson(m.toJson()))
        assertEquals("221 workouts, 8 routines", m.summary())
    }
}

class RemoteBackupTest {
    @org.junit.Test
    fun webDavListingKeepsFilesAndSkipsFolders() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/dav/files/me/LiftTracker/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/dav/files/me/LiftTracker/LiftTracker-auto-data-20261116-0310.ltbackup</d:href>
                <d:propstat><d:prop><d:getcontentlength>2048</d:getcontentlength><d:getlastmodified>Mon, 16 Nov 2026 03:10:00 GMT</d:getlastmodified><d:resourcetype/></d:prop></d:propstat></d:response>
              <D:response xmlns:D="DAV:"><D:href>/dav/files/me/LiftTracker/my%20notes.txt</D:href></D:response>
            </d:multistatus>"""
        val files = com.lifttracker.app.backup.WebDav.parseListing(xml)
        org.junit.Assert.assertEquals(listOf("LiftTracker-auto-data-20261116-0310.ltbackup", "my notes.txt"), files.map { it.name })
        org.junit.Assert.assertEquals(2048L, files[0].sizeBytes)
    }

    @org.junit.Test
    fun pruningDropsOnlyOldAutomaticBackupsOfTheSameKind() {
        val names = listOf(
            "LiftTracker-auto-data-20261114-0300.zip", "LiftTracker-auto-data-20261116-0300.ltbackup",
            "LiftTracker-auto-data-20261115-0300.zip", "LiftTracker-auto-config-20261101-0300.ltbackup",
            "LiftTracker-data-20260101-1200.zip", "holiday.jpg",
        )
        val gone = com.lifttracker.app.backup.AutoBackup.toPrune(names, com.lifttracker.app.backup.BackupKind.DATA, keep = 2)
        org.junit.Assert.assertEquals(listOf("LiftTracker-auto-data-20261114-0300.zip"), gone)
    }

    @org.junit.Test
    fun webDavAddressesAreChecked() {
        org.junit.Assert.assertNull(com.lifttracker.app.backup.WebDavConfig.urlProblem("https://nas.local/dav/Lift"))
        org.junit.Assert.assertNotNull(com.lifttracker.app.backup.WebDavConfig.urlProblem("nas.local/dav"))
        org.junit.Assert.assertEquals("https://x/y/", com.lifttracker.app.backup.WebDavConfig(" https://x/y// ", "", "").folderUrl)
    }
}
