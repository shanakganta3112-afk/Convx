package com.convx.extension.jiosaavn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ciphertext in these tests was produced independently with Python's `cryptography`
 * (TripleDES with the 8-byte key repeated, ECB, PKCS5) from the plaintext URL, so a
 * passing decrypt here is a real round-trip, not a restatement of the implementation.
 */
class SaavnStreamResolverTest {

    private val encrypted =
        "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyXtO9QwbkAG9KX+Pt+JAcK6pju4wPKmVjH0zsaPtTcGE="
    private val plain = "https://aac.saavncdn.com/000/abcdefghijklmnop_320.mp4"

    @Test fun `decrypts to the plaintext cdn url`() {
        assertEquals(plain, SaavnStreamResolver.decrypt(encrypted))
    }

    @Test fun `reads the bitrate rung out of the filename`() {
        val stream = SaavnStreamResolver.stream(encrypted, supports320 = true)
        requireNotNull(stream)
        assertEquals(plain, stream.url)
        assertEquals(320, stream.bitrateKbps)
        assertEquals("audio/mp4", stream.mimeType)
    }

    @Test fun `blank input yields no stream`() {
        assertNull(SaavnStreamResolver.stream("", supports320 = true))
        assertEquals("", SaavnStreamResolver.decrypt(""))
    }

    @Test fun `undecryptable input yields no stream`() {
        assertNull(SaavnStreamResolver.stream("not-base64-at-all!!", supports320 = true))
    }
}
