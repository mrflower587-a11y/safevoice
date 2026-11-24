package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class SaltedHash(
    val hashBase64: String,
    val saltBase64: String
)

class PinSecurityManager {

    private val secureRandom = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }
    }

    fun generateBackupRecoveryCode(): String {
        val raw = CharArray(RECOVERY_CODE_CHARS) {
            RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)]
        }
        return try {
            "${String(raw, 0, 4)}-${String(raw, 4, 4)}-${String(raw, 8, 4)}"
        } finally {
            Arrays.fill(raw, '\u0000')
        }
    }

    fun normalizeRecoveryCode(code: String): String {
        return code.trim()
            .replace("-", "")
            .replace(" ", "")
            .uppercase()
    }

    fun hashSecret(secret: CharArray): SaltedHash {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(secret, salt)
        return SaltedHash(
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            saltBase64 = Base64.getEncoder().encodeToString(salt)
        )
    }

    fun hashPin(pin: String): SaltedHash {
        val chars = pin.toCharArray()
        return try {
            hashSecret(chars)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun hashRecoveryCode(code: String): SaltedHash {
        val normalized = normalizeRecoveryCode(code)
        val chars = normalized.toCharArray()
        return try {
            hashSecret(chars)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun verifyPin(pin: String, storedHashBase64: String?, storedSaltBase64: String?): Boolean {
        if (storedHashBase64.isNullOrBlank() || storedSaltBase64.isNullOrBlank()) return false
        val chars = pin.toCharArray()
        return try {
            verifySecret(chars, storedHashBase64, storedSaltBase64)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    fun verifyRecoveryCode(
        inputCode: String,
        storedHashBase64: String?,
        storedSaltBase64: String?
    ): Boolean {
        if (storedHashBase64.isNullOrBlank() || storedSaltBase64.isNullOrBlank()) return false
        val normalized = normalizeRecoveryCode(inputCode)
        if (normalized.length != RECOVERY_CODE_CHARS) return false
        val chars = normalized.toCharArray()
        return try {
            verifySecret(chars, storedHashBase64, storedSaltBase64)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    private fun verifySecret(
        secretChars: CharArray,
        storedHashBase64: String,
        storedSaltBase64: String
    ): Boolean {
        return try {
            val salt = Base64.getDecoder().decode(storedSaltBase64)
            val expectedHash = Base64.getDecoder().decode(storedHashBase64)
            val candidateHash = pbkdf2(secretChars, salt)
            MessageDigest.isEqual(candidateHash, expectedHash)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun pbkdf2(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun computeLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < 3 -> 0L
            failedAttempts == 3 -> 5_000L
            failedAttempts == 4 -> 15_000L
            failedAttempts == 5 -> 30_000L
            else -> 60_000L
        }
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6
        const val PBKDF2_ITERATIONS = 10_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val RECOVERY_CODE_CHARS = 12
        private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
