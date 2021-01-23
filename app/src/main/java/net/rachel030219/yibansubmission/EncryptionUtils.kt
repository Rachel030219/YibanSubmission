package net.rachel030219.yibansubmission

import android.util.Base64
import okhttp3.internal.and
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {

    fun encrypt(content: String, key: String): String {
        // process the key
        val sha1sumKey = sha1(key)
        // encrypt the content, then base64 encode it
        val encryptedContent = cipher(Cipher.ENCRYPT_MODE, sha1sumKey.convertToHex().substring(0 until 32)).doFinal(
            content.toByteArray(
                Charsets.UTF_8
            )
        )
        return Base64.encodeToString(encryptedContent, Base64.DEFAULT)
    }

    fun decrypt(encryptedContent: String, key: String): String {
        // process the key
        val sha1sumKey = sha1(key).convertToHex().substring(0 until 32)
        // decode the encrypted content, then decrypt it
        val content = Base64.decode(encryptedContent, Base64.DEFAULT)
        return String(cipher(Cipher.DECRYPT_MODE, sha1sumKey).doFinal(content), Charsets.UTF_8)
    }

    private fun cipher(opMode: Int, secretKey: String): Cipher {
        if(secretKey.length != 32) throw RuntimeException("SecretKey length is not 32 chars")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val sk = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES")
        val iv = IvParameterSpec(secretKey.substring(0, 16).toByteArray(Charsets.UTF_8))
        c.init(opMode, sk, iv)
        return c
    }

    private fun sha1(str: String): ByteArray = MessageDigest.getInstance("SHA-1").let {
        it.update(str.toByteArray(Charsets.UTF_8), 0, str.toByteArray(Charsets.UTF_8).size)
        it.digest()
    }
}


private fun ByteArray.convertToHex(): String {
    val buf = StringBuilder()
    for (b in this) {
        var halfbyte: Int = b.toInt() ushr 4 and 0x0F
        var twoHalfs = 0
        do {
            buf.append(if (halfbyte in 0..9) ('0'.toInt() + halfbyte).toChar() else ('a'.toInt() + (halfbyte - 10)).toChar())
            halfbyte = b and 0x0F
        } while (twoHalfs++ < 1)
    }
    return buf.toString()
}