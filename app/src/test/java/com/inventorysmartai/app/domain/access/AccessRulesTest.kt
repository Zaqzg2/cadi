package com.inventorysmartai.app.domain.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessRulesTest {

    private fun user(id: String, role: AppRole, name: String = id) = UserProfile(id, name, role, pinSalt = "s", pinHash = "h")

    // ------------------------------------------------------------------ PIN and names

    @Test
    fun `a pin is 4 to 8 digits only`() {
        assertTrue(AccessRules.isValidPin("1234"))
        assertTrue(AccessRules.isValidPin("12345678"))
        assertFalse(AccessRules.isValidPin("123"))
        assertFalse(AccessRules.isValidPin("123456789"))
        assertFalse(AccessRules.isValidPin("12a4"))
        assertFalse(AccessRules.isValidPin(""))
        assertNull(AccessRules.pinError("0000"))
        assertNotNull(AccessRules.pinError("12"))
    }

    @Test
    fun `a name must be present and unique ignoring case and padding`() {
        val users = listOf(user("1", AppRole.OWNER, "Ahmed"))
        assertNotNull(AccessRules.nameError("   ", users))
        assertNotNull(AccessRules.nameError("ahmed ", users))
        assertNull(AccessRules.nameError("Sara", users))
    }

    @Test
    fun `editing a user may keep their own name`() {
        val users = listOf(user("1", AppRole.OWNER, "Ahmed"))
        assertNull(AccessRules.nameError("Ahmed", users, ignoreId = "1"))
    }

    // ------------------------------------------------------------------ removing and demoting

    @Test
    fun `the last owner cannot be removed or demoted`() {
        val users = listOf(user("1", AppRole.OWNER), user("2", AppRole.SALES))
        assertNotNull(AccessRules.removeError(users, targetId = "1", currentUserId = "2"))
        assertNotNull(AccessRules.roleChangeError(users, targetId = "1", newRole = AppRole.SALES))
    }

    @Test
    fun `with two owners one of them can go`() {
        val users = listOf(user("1", AppRole.OWNER), user("2", AppRole.OWNER))
        assertNull(AccessRules.removeError(users, targetId = "1", currentUserId = "2"))
        assertNull(AccessRules.roleChangeError(users, targetId = "1", newRole = AppRole.VIEWER))
    }

    @Test
    fun `you cannot delete the user you are signed in as`() {
        val users = listOf(user("1", AppRole.OWNER), user("2", AppRole.OWNER))
        assertNotNull(AccessRules.removeError(users, targetId = "1", currentUserId = "1"))
    }

    @Test
    fun `a non owner can be removed and an unknown user is reported`() {
        val users = listOf(user("1", AppRole.OWNER), user("2", AppRole.SALES))
        assertNull(AccessRules.removeError(users, targetId = "2", currentUserId = "1"))
        assertNotNull(AccessRules.removeError(users, targetId = "zzz", currentUserId = "1"))
    }

    // ------------------------------------------------------------------ roles

    @Test
    fun `the owner holds every permission`() {
        assertEquals(Permission.entries.toSet(), AppRole.OWNER.permissions)
    }

    @Test
    fun `only the owner manages users, settings and backups`() {
        AppRole.entries.filter { it != AppRole.OWNER }.forEach { role ->
            assertFalse(Permission.MANAGE_USERS in role.permissions)
            assertFalse(Permission.MANAGE_SETTINGS in role.permissions)
            assertFalse(Permission.MANAGE_BACKUP in role.permissions)
        }
    }

    @Test
    fun `the viewer can look but not change anything`() {
        val viewer = AppRole.VIEWER.permissions
        assertTrue(Permission.VIEW_STOCK in viewer)
        assertFalse(viewer.any { it.name.startsWith("MANAGE_") })
    }

    // ------------------------------------------------------------------ state

    private val owner = user("o", AppRole.OWNER)
    private val sales = user("s", AppRole.SALES)

    @Test
    fun `access control that is off lets everything through`() {
        val state = AccessState(loaded = true, enabled = false)
        assertFalse(state.locked)
        assertTrue(state.canOpen("settings/backup"))
        assertTrue(state.can(Permission.MANAGE_USERS))
    }

    @Test
    fun `enabled with nobody signed in is locked and opens nothing restricted`() {
        val state = AccessState(loaded = true, enabled = true, profiles = listOf(owner, sales), currentUserId = null)
        assertTrue(state.locked)
        assertFalse(state.canOpen("sales"))
        assertFalse(state.can(Permission.VIEW_STOCK))
    }

    @Test
    fun `a signed in user opens what the role allows and nothing else`() {
        val state = AccessState(loaded = true, enabled = true, profiles = listOf(owner, sales), currentUserId = "s")
        assertFalse(state.locked)
        assertTrue(state.canOpen("sales"))
        assertTrue(state.canOpen("home"))
        assertFalse(state.canOpen("settings"))
        assertFalse(state.canOpen("settings/backup"))
    }

    @Test
    fun `a user who was deleted while signed in is locked out`() {
        val state = AccessState(loaded = true, enabled = true, profiles = listOf(owner), currentUserId = "ghost")
        assertTrue(state.locked)
    }

    @Test
    fun `a route without a rule stays open so a new screen never breaks`() {
        val state = AccessState(loaded = true, enabled = true, profiles = listOf(owner, sales), currentUserId = "s")
        assertTrue(state.canOpen("some/new/screen"))
    }
}
