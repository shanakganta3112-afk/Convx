package com.convx.extension.api

/**
 * What an extension can do, mirroring the four kinds the reference player defines.
 *
 * Only [MUSIC] is implemented in this pass; the others exist so a tracker or lyrics
 * extension can be added later without changing the host.
 */
enum class ExtensionType {
    MUSIC,
    TRACKER,
    LYRICS,
    MISC,
}
