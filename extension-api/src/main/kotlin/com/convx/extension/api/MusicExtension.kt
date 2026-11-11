package com.convx.extension.api

/** An extension plus the client that serves it. */
data class MusicExtension(
    val metadata: ExtensionMetadata,
    val client: ExtensionClient,
) {
    val id: String get() = metadata.id
    val name: String get() = metadata.name
    val type: ExtensionType get() = metadata.type
}
