package com.convx.extension.jiosaavn

import com.convx.extension.api.ExtensionStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Turns `more_info.encrypted_media_url` into a playable CDN URL.
 *
 * The value is Base64 of a DES/ECB/PKCS5 ciphertext under a fixed 8-byte key; the
 * plaintext is an `aac.saavncdn.com` URL whose filename carries the bitrate rung.
 * Convx already does exactly this in `com.music.jiosaavn.SaavnService`; this copy
 * exists because that one depends on `android.util.Base64` and cannot run off-device.
 */
object SaavnStreamResolver {

    private const val DES_KEY = "38346591"
    private val QUALITY_SUFFIX = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$")

    fun decrypt(encryptedUrl: String): String {
        if (encryptedUrl.isBlank()) return ""
        return runCatching {
            val key = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val bytes = decodeBase64(encryptedUrl)
            String(cipher.doFinal(bytes), Charsets.UTF_8).trim()
        }.getOrDefault("")
    }

    /**
     * @param supports320 whether the catalogue entry advertises a 320kbps rendition;
     * asking for a rung the track does not have yields nothing, so it is only
     * reported when the source says it exists.
     */
    fun stream(encryptedUrl: String, supports320: Boolean = true): ExtensionStream? {
        val decrypted = decrypt(encryptedUrl)
        if (decrypted.isBlank()) return null

        val match = QUALITY_SUFFIX.find(decrypted)
            ?: return ExtensionStream(
                url = decrypted,
                bitrateKbps = if (supports320) 320 else null,
                mimeType = null,
            )

        val mimeType = when (match.groupValues[2]) {
            "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            else -> "audio/mpeg"
        }
        return ExtensionStream(
            url = decrypted,
            bitrateKbps = match.groupValues[1].toIntOrNull(),
            mimeType = mimeType,
        )
    }

    private fun decodeBase64(value: String): ByteArray = runCatching {
        Base64.getDecoder().decode(value)
    }.getOrElse {
        Base64.getMimeDecoder().decode(value)
    }
}
