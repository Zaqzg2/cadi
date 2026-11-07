package com.inventorysmartai.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = BackupManifest(dbVersion = 4, appVersion = "0.5.0", createdAt = 42L, productCount = 3, invoiceCount = 1)

    private fun file(name: String, bytes: ByteArray): File = File(tmp.root, name).apply { writeBytes(bytes) }

    private fun archive(db: File, wal: File? = null): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, manifest, db, wal)
        return out.toByteArray()
    }

    @Test
    fun `inspect reads the manifest and lists the entries without extracting`() {
        val bytes = archive(file("db", byteArrayOf(1, 2, 3)))

        val info = BackupArchive.inspect(ByteArrayInputStream(bytes))

        assertEquals(manifest, info.manifest)
        assertTrue(info.hasDatabase)
        assertEquals(setOf(BackupManifest.ENTRY_MANIFEST, BackupManifest.ENTRY_DATABASE), info.entryNames)
    }

    @Test
    fun `a non empty wal file travels inside the archive`() {
        val bytes = archive(file("db", byteArrayOf(1)), file("wal", byteArrayOf(9, 9)))
        assertTrue(BackupManifest.ENTRY_WAL in BackupArchive.inspect(ByteArrayInputStream(bytes)).entryNames)
    }

    @Test
    fun `an empty or missing wal file is left out`() {
        val emptyWal = file("wal", ByteArray(0))
        assertFalse(BackupManifest.ENTRY_WAL in BackupArchive.inspect(ByteArrayInputStream(archive(file("db", byteArrayOf(1)), emptyWal))).entryNames)
        assertFalse(BackupManifest.ENTRY_WAL in BackupArchive.inspect(ByteArrayInputStream(archive(file("db2", byteArrayOf(1)), File(tmp.root, "absent")))).entryNames)
    }

    @Test
    fun `extract restores the database and wal byte for byte`() {
        val db = byteArrayOf(5, 4, 3, 2, 1)
        val wal = byteArrayOf(7, 7, 7)
        val bytes = archive(file("db", db), file("wal", wal))
        val target = File(tmp.root, "out")

        val extracted = BackupArchive.extract(ByteArrayInputStream(bytes), target)

        assertEquals(manifest, extracted.manifest)
        assertArrayEquals(db, extracted.database?.readBytes())
        assertArrayEquals(wal, extracted.wal?.readBytes())
    }

    @Test
    fun `extract never writes outside the target folder - names inside the archive are not trusted`() {
        val hostile = ByteArrayOutputStream().also { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("../evil.txt")); zip.write(byteArrayOf(1)); zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_DATABASE)); zip.write(byteArrayOf(2)); zip.closeEntry()
            }
        }.toByteArray()
        val target = File(tmp.root, "inner").apply { mkdirs() }

        val extracted = BackupArchive.extract(ByteArrayInputStream(hostile), target)

        assertNotNull(extracted.database)
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(target, "evil.txt").exists())
        assertEquals(listOf(BackupManifest.ENTRY_DATABASE), target.listFiles()!!.map { it.name })
    }

    @Test
    fun `bytes that are not an archive have no manifest and no database`() {
        val info = BackupArchive.inspect(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
        assertNull(info.manifest)
        assertFalse(info.hasDatabase)
    }
}
