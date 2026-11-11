package com.convx.extension.api

/**
 * Describes an extension to the host.
 *
 * @property id stable identifier, also used to persist the enabled flag
 * @property name display name
 * @property version implementation version
 * @property description one line shown under the name in settings
 * @property author who wrote it
 * @property type what kind of extension this is
 * @property authorUrl optional link
 * @property iconUrl optional square icon
 * @property repoUrl optional source link
 * @property updateUrl optional release feed
 */
data class ExtensionMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val type: ExtensionType = ExtensionType.MUSIC,
    val authorUrl: String? = null,
    val iconUrl: String? = null,
    val repoUrl: String? = null,
    val updateUrl: String? = null,
)
