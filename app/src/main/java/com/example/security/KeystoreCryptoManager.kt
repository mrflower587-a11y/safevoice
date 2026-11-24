package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}

class AndroidKeyStoreKeyProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : SecretKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "safevoice_aes_gcm_master_key"
    }
}

class KeystoreCryptoManager(
    private val keyProvider: SecretKeyProvider = AndroidKeyStoreKeyProvider()
) {
    /**
     * Encrypts [sourceFile] into [destFile] using 64 KB chunks.
     * Frame layout per chunk: [4-byte ciphertext+tag length][12-byte GCM IV][ciphertext+tag].
     * Returns total plaintext byte count.
     */
    fun encryptFile(sourceFile: File, destFile: File): Long {
        val secretKey = keyProvider.getOrCreateKey()
        destFile.parentFile?.mkdirs()
        val tempDest = File(destFile.parentFile, "${destFile.name}.tmp")
        var totalPlaintextBytes = 0L

        try {
            BufferedInputStream(FileInputStream(sourceFile), CHUNK_SIZE_BYTES).use { input ->
                DataOutputStream(BufferedOutputStream(FileOutputStream(tempDest), CHUNK_SIZE_BYTES)).use { output ->
                    val buffer = ByteArray(CHUNK_SIZE_BYTES)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead <= 0) break
                        totalPlaintextBytes += bytesRead

                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                        val iv = cipher.iv
                        require(iv.size == GCM_IV_LENGTH_BYTES) {
                            "Unexpected GCM IV length: ${iv.size}"
                        }
                        val encryptedChunk = cipher.doFinal(buffer, 0, bytesRead)

                        output.writeInt(encryptedChunk.size)
                        output.write(iv)
                        output.write(encryptedChunk)
                    }
                    output.flush()
                }
            }

            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempDest.renameTo(destFile)) {
                tempDest.copyTo(destFile, overwrite = true)
                tempDest.delete()
            }
            return totalPlaintextBytes
        } catch (t: Throwable) {
            tempDest.delete()
            throw t
        }
    }

    /**
     * Decrypts [encryptedFile] to [destFile] by streaming 64 KB chunks.
     */
    fun decryptToFile(encryptedFile: File, destFile: File): Long {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { fos ->
            BufferedOutputStream(fos, CHUNK_SIZE_BYTES).use { bos ->
                val written = decryptToStream(encryptedFile, bos)
                bos.flush()
                return written
            }
        }
    }

    /**
     * Streams decrypted plaintext chunks directly into [outputStream] without loading
     * the entire recording into memory.
     */
    fun decryptToStream(
        encryptedFile: File,
        outputStream: OutputStream,
        onBytesWritten: ((Long) -> Unit)? = null
    ): Long {
        val secretKey = keyProvider.getOrCreateKey()
        var totalDecryptedBytes = 0L

        DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), CHUNK_SIZE_BYTES)).use { input ->
            while (true) {
                val chunkLength = try {
                    input.readInt()
                } catch (_: EOFException) {
                    break
                }
                require(chunkLength in 1..MAX_CIPHER_CHUNK_BYTES) {
                    "Corrupt encrypted chunk length: $chunkLength"
                }

                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                input.readFully(iv)

                val encryptedBytes = ByteArray(chunkLength)
                input.readFully(encryptedBytes)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val plaintext = cipher.doFinal(encryptedBytes)

                outputStream.write(plaintext)
                totalDecryptedBytes += plaintext.size
                onBytesWritten?.invoke(totalDecryptedBytes)
            }
            outputStream.flush()
        }
        return totalDecryptedBytes
    }

    companion object {
        const val CHUNK_SIZE_BYTES = 64 * 1024
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MAX_CIPHER_CHUNK_BYTES = CHUNK_SIZE_BYTES + 1024
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
