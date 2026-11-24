package com.example

import com.example.security.KeystoreCryptoManager
import com.example.security.SecretKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.random.Random

class KeystoreCryptoManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemoryAesKeyProvider : SecretKeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getOrCreateKey(): SecretKey = key
    }

    @Test
    fun `chunked AES-256-GCM encryption and decryption round-trip matches byte for byte`() {
        val crypto = KeystoreCryptoManager(InMemoryAesKeyProvider())
        // 150 KB spans 3 chunks (64 KB + 64 KB + 22 KB)
        val originalBytes = Random(42).nextBytes(150 * 1024 + 137)

        val sourceFile = tempFolder.newFile("sample.m4a").apply { writeBytes(originalBytes) }
        val encryptedFile = tempFolder.newFile("sample.m4a.enc")
        val decryptedFile = tempFolder.newFile("sample_dec.m4a")

        val encryptedPlaintextBytes = crypto.encryptFile(sourceFile, encryptedFile)
        assertEquals(originalBytes.size.toLong(), encryptedPlaintextBytes)
        assertTrue(encryptedFile.length() > originalBytes.size.toLong())

        val decryptedCount = crypto.decryptToFile(encryptedFile, decryptedFile)
        assertEquals(originalBytes.size.toLong(), decryptedCount)
        assertArrayEquals(originalBytes, decryptedFile.readBytes())

        val streamedOutput = ByteArrayOutputStream()
        var lastCallbackBytes = 0L
        val streamedCount = crypto.decryptToStream(encryptedFile, streamedOutput) { written ->
            lastCallbackBytes = written
        }
        assertEquals(originalBytes.size.toLong(), streamedCount)
        assertEquals(originalBytes.size.toLong(), lastCallbackBytes)
        assertArrayEquals(originalBytes, streamedOutput.toByteArray())
    }
}
