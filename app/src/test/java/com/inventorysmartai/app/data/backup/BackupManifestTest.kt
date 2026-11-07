package com.inventorysmartai.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManifestTest {

    private val sample = BackupManifest(dbVersion = 4, appVersion = "0.5.0", createdAt = 1_760_000_000_000L, productCount = 120, invoiceCount = 35)

    @Test
    fun `a manifest survives serialize then parse`() {
        assertEquals(sample, BackupManifest.parse(sample.serialize()))
    }

    @Test
    fun `text without the essential keys is not a manifest`() {
        assertNull(BackupManifest.parse(""))
        assertNull(BackupManifest.parse("hello world"))
        assertNull(BackupManifest.parse("format=1\ncreatedAt=5\n")) // dbVersion missing
        assertNull(BackupManifest.parse("format=one\ndbVersion=4\ncreatedAt=5\n")) // format is not a number
    }

    @Test
    fun `counts are optional and default to zero`() {
        val parsed = BackupManifest.parse("format=1\ndbVersion=4\ncreatedAt=5\n")
        assertEquals(0, parsed?.productCount)
        assertEquals(0, parsed?.invoiceCount)
    }

    // ------------------------------------------------------------------ restore rules

    @Test
    fun `a matching backup is accepted`() {
        assertEquals(RestoreCheck.Ok, BackupRules.check(sample, hasDatabase = true, currentDbVersion = 4))
    }

    @Test
    fun `a file without a manifest is rejected`() {
        assertTrue(BackupRules.check(null, hasDatabase = true, currentDbVersion = 4) is RestoreCheck.Rejected)
    }

    @Test
    fun `an unknown format is rejected`() {
        assertTrue(BackupRules.check(sample.copy(format = 99), hasDatabase = true, currentDbVersion = 4) is RestoreCheck.Rejected)
    }

    @Test
    fun `a backup without the database is rejected`() {
        assertTrue(BackupRules.check(sample, hasDatabase = false, currentDbVersion = 4) is RestoreCheck.Rejected)
    }

    /** المهم: قاعدة بإصدار مخطط مختلف كانت ستُمسح بصمت عند فتحها (fallbackToDestructiveMigration)، فالرفض أسلم. */
    @Test
    fun `a backup from another schema version is rejected and says both versions`() {
        val result = BackupRules.check(sample.copy(dbVersion = 3), hasDatabase = true, currentDbVersion = 4)
        assertTrue(result is RestoreCheck.Rejected)
        val reason = (result as RestoreCheck.Rejected).reason
        assertTrue(reason.contains("3") && reason.contains("4"))
    }
}
