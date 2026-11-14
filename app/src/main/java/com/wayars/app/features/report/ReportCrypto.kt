package com.wayars.app.features.report

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Encrypts an order-reading report so only the developer can open it: the text is gzipped and
 * sealed with a fresh AES-256-GCM key, and that key is encrypted with the developer's RSA-3072
 * public key (OAEP, SHA-256 / MGF1-SHA-256). The private key never ships with the app.
 *
 * File layout: "WAYR1" | key length (2 bytes, big-endian) | RSA-encrypted AES key | 12-byte IV |
 * AES-GCM ciphertext (with tag). Decrypted by the developer's offline decoder page.
 */
object ReportCrypto {
    private const val PUBLIC_KEY_X509_B64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAo5fOn5RdY5MdKn/+mcNaS9hw4rXtWe2zoGsEJhwNlaMZN6WZKMEZ9DjVxGiJH5hOWZN1VNrxFJgZBNUVxA3fJLRL4vEtaq0xmTT0SVnBqVGb1M3GvZd0/cAZIyuN+pYbb0q2FY6ih8a5HalmBOnnoRWlZCva/Mi65ef2vQVgtlY7+x34slYWCqzkb4nqOsAanWOJDX9x0hSyABbqjkeRyZNGDWg9knek7tBa+f6t3OPoG1RmHUoA6DSBs2ck5Kh+NlrVLvxgwKmYyVFzlfNi56qmWU26XR8WcA1W3wE3HKOobt1l+7kzBd3Cw4FpHmO/nqvF5a/i3QD40DRVEIlR8JvDg3j8tUjYgX2ThRbD4buKttOa7T3ClM1pB8bpmrFpF67Yq2baKkb/LVQlL6PwSP0X6w891hW/+5shhNEJoxSRDleQCGHksdMkOCHyCFpXwFDQEmJXSdQVpMgo498xlVjK4otsItPk9Zm72ffBDlwWXVwXJQjVCbnL/+p/gGDjAgMBAAE="
    private val MAGIC = "WAYR1".toByteArray(Charsets.US_ASCII)

    fun encrypt(plain: ByteArray): ByteArray {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_X509_B64)))
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val body = aes.doFinal(gzip(plain))
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsa.init(
            Cipher.ENCRYPT_MODE, publicKey,
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        )
        val wrappedKey = rsa.doFinal(aesKey.encoded)
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(wrappedKey.size shr 8 and 0xFF); out.write(wrappedKey.size and 0xFF)
        out.write(wrappedKey); out.write(iv); out.write(body)
        return out.toByteArray()
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(data) } }.toByteArray()
}
