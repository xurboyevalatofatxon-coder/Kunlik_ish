package uz.dailygoals.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinCredential(val hash: String, val salt: String, val iterations: Int = 210_000)
object PinHasher {
    private fun validate(pin: CharArray) = ensure(pin.size == 4 && pin.all { it in '0'..'9' }, ErrorCode.INVALID_PIN)
    fun create(pin: CharArray): PinCredential {
        validate(pin)
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return PinCredential(derive(pin, salt, 210_000).toHex(), salt.toHex())
    }
    fun verify(pin: CharArray, credential: PinCredential): Boolean {
        if (pin.size != 4 || pin.any { it !in '0'..'9' }) return false
        if (credential.iterations !in 100_000..1_000_000 || credential.salt.length != 64 || credential.hash.length != 64) return false
        return try { MessageDigest.isEqual(derive(pin, credential.salt.fromHex(), credential.iterations), credential.hash.fromHex()) }
        catch (_: IllegalArgumentException) { false }
    }
    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
