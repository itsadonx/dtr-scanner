package com.dtr.scanner.sync

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    const val ITERATIONS = 12_000
    const val KEY_BITS = 256

    fun verify(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val salt = hexToBytes(saltHex) ?: return false
        val expected = hexToBytes(expectedHashHex) ?: return false
        val actual = pbkdf2(password, salt)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.length % 2 != 0 || clean.isEmpty()) return null
        return try {
            ByteArray(clean.length / 2) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
