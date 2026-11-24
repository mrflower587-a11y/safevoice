package com.example

import com.example.security.PinSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityManagerTest {

    private val manager = PinSecurityManager()

    @Test
    fun `pin hashing uses unique salts and verifies accurately`() {
        val hash1 = manager.hashPin("4829")
        val hash2 = manager.hashPin("4829")

        assertNotEquals(hash1.saltBase64, hash2.saltBase64)
        assertNotEquals(hash1.hashBase64, hash2.hashBase64)

        assertTrue(manager.verifyPin("4829", hash1.hashBase64, hash1.saltBase64))
        assertTrue(manager.verifyPin("4829", hash2.hashBase64, hash2.saltBase64))
        assertFalse(manager.verifyPin("1234", hash1.hashBase64, hash1.saltBase64))
    }

    @Test
    fun `recovery code generation format and normalized verification`() {
        val code = manager.generateBackupRecoveryCode()
        assertEquals(14, code.length) // XXXX-XXXX-XXXX
        assertEquals('-', code[4])
        assertEquals('-', code[9])

        val hashed = manager.hashRecoveryCode(code)
        val lowercaseWithoutDashes = code.replace("-", "").lowercase()

        assertTrue(manager.verifyRecoveryCode(code, hashed.hashBase64, hashed.saltBase64))
        assertTrue(manager.verifyRecoveryCode("  $lowercaseWithoutDashes  ", hashed.hashBase64, hashed.saltBase64))
        assertFalse(manager.verifyRecoveryCode("AAAA-BBBB-CCCC", hashed.hashBase64, hashed.saltBase64))
    }

    @Test
    fun `progressive lockout durations follow escalation schedule`() {
        assertEquals(0L, manager.computeLockoutDurationMs(0))
        assertEquals(0L, manager.computeLockoutDurationMs(1))
        assertEquals(0L, manager.computeLockoutDurationMs(2))
        assertEquals(5_000L, manager.computeLockoutDurationMs(3))
        assertEquals(15_000L, manager.computeLockoutDurationMs(4))
        assertEquals(30_000L, manager.computeLockoutDurationMs(5))
        assertEquals(60_000L, manager.computeLockoutDurationMs(6))
        assertEquals(60_000L, manager.computeLockoutDurationMs(10))
    }
}
