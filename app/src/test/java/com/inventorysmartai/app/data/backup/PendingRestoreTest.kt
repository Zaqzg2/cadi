package com.inventorysmartai.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingRestoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dbFile by lazy { File(tmp.newFolder("databases"), "inventory_smart.db") }
    private val pendingDir by lazy { File(tmp.root, PendingRestore.DIR_NAME) }

    private fun stage(bytes: ByteArray) {
        pendingDir.mkdirs()
        File(pendingDir, BackupManifest.ENTRY_DATABASE).writeBytes(bytes)
    }

    @Test
    fun `nothing staged leaves the current database alone`() {
        dbFile.writeBytes(byteArrayOf(1, 1, 1))

        assertFalse(PendingRestore.applyIfPresent(dbFile, pendingDir))

        assertArrayEquals(byteArrayOf(1, 1, 1), dbFile.readBytes())
    }

    @Test
    fun `a staged database replaces the current one and its sidecar files`() {
        dbFile.writeBytes(byteArrayOf(1, 1, 1))
        File(dbFile.path + "-wal").writeBytes(byteArrayOf(8))
        File(dbFile.path + "-shm").writeBytes(byteArrayOf(8))
        stage(byteArrayOf(9, 9, 9, 9))

        assertTrue(PendingRestore.applyIfPresent(dbFile, pendingDir))

        assertArrayEquals(byteArrayOf(9, 9, 9, 9), dbFile.readBytes())
        assertFalse(File(dbFile.path + "-wal").exists())
        assertFalse(File(dbFile.path + "-shm").exists())
        assertFalse(File(dbFile.parentFile, "inventory_smart.db.restoring").exists())
        assertFalse(pendingDir.exists())
    }

    @Test
    fun `a restore works on a fresh install with no database yet`() {
        stage(byteArrayOf(4, 2))

        assertTrue(PendingRestore.applyIfPresent(dbFile, pendingDir))

        assertArrayEquals(byteArrayOf(4, 2), dbFile.readBytes())
    }

    @Test
    fun `an empty staged file is a failed attempt - it is cleaned and the database is kept`() {
        dbFile.writeBytes(byteArrayOf(1, 1, 1))
        stage(ByteArray(0))

        assertFalse(PendingRestore.applyIfPresent(dbFile, pendingDir))

        assertArrayEquals(byteArrayOf(1, 1, 1), dbFile.readBytes())
        assertFalse(pendingDir.exists())
    }

    @Test
    fun `applying twice only restores once`() {
        stage(byteArrayOf(3, 3))

        assertTrue(PendingRestore.applyIfPresent(dbFile, pendingDir))
        assertFalse(PendingRestore.applyIfPresent(dbFile, pendingDir))
    }
}
