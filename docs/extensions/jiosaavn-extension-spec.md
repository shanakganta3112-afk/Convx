# Extension feature — reconstruction spec (JioSaavn extension)

Status: implementation spec for the first pass. Written before code, per the repo's
agent rules. Every behavioural claim below names the file it came from. Anything I
could not determine is listed under **Open questions** rather than guessed.

## 1. What is being rebuilt, and from what evidence

Target behaviour: the extension system in **Echo** (`brahmkshatriya/echo`,
`f210820680cd5e7bdcb383e3daab3fa2c06b71e0`), with exactly one extension ported —
the **JioSaavn** extension.

Evidence sources:

| Source | What it establishes |
| --- | --- |
| `echo/common/src/commonMain/kotlin/dev/brahmkshatriya/echo/common/Extension.kt` | The extension type hierarchy: a sealed `Extension<T : ExtensionClient>` with `MusicExtension`, `TrackerExtension`, `LyricsExtension`, `MiscExtension`, each carrying `metadata` + an injectable client. |
| `echo/common/.../models/ExtensionType.kt` | `ExtensionType(feature)` — `MUSIC("music")`, `TRACKER("tracker")`, `LYRICS("lyrics")`, `MISC("misc")`. |
| `echo/common/.../models/Metadata.kt` | The metadata record: `className, path, importType, type, id, name, version, description, author, authorUrl, icon, repoUrl, updateUrl, preservedPackages, isEnabled`. |
| `echo/common/.../clients/*.kt` | The capability interfaces: `ExtensionClient` (base), `SearchFeedClient`, `QuickSearchClient`, `TrackClient`, `AlbumClient`, `ArtistClient`, `PlaylistClient`, `RadioClient`, `LikeClient`, `SaveClient`, `FollowClient`, `HideClient`, `ShareClient`, `PlaylistEditClient`, `TrackerClient`, `LyricsClient`, `LoginClient`, `DownloadClient`. Capability is expressed by interface implementation, not by flags. |
| `echo/app/src/main/java/dev/brahmkshatriya/echo/extensions/repo/ExtensionParser.kt` | How an extension is described at runtime: `PackageManager.getPackageArchiveInfo` → `applicationInfo.metaData` keys `class, id, version, name, description, author, author_url, icon_url, repo_url, update_url, preserved_packages, enabled`; type comes from a `<uses-feature>` whose name starts with `dev.brahmkshatriya.echo.` |
| `echo/app/.../extensions/repo/DexLoader.kt` | An extension is a separate APK/dex file loaded with a class loader, then `clazz.getConstructor().newInstance()`. |
| `echo/app/.../extensions/ExtensionLoader.kt` | Lifecycle: load → enable/disable → hand the client a settings/context bundle via provider interfaces (`MetadataProvider`, `GlobalSettingsProvider`, `MessageFlowProvider`, `NetworkConnectionProvider`, …). |
| `Echo-Saavn-Extension` (`abhishekkchoudharyy/Echo-Saavn-Extension`, `942744566a954f0cffa35ba8c40980db0dd9b383`) | The one extension being ported: which JioSaavn endpoints it calls, and the response field names it parses. |
| `Convx/integrations/src/main/kotlin/com/music/jiosaavn/SaavnService.kt` | Convx already talks to the same private API: `api.php?__call=search.getResults` and `song.getDetails`, DES-encrypted `encrypted_media_url` (key `38346591`), `_48/_96/_160/_320` CDN rungs. |

## 2. What Convx already has (collision analysis)

Two things in Convx look like "extensions" and are **not** this feature:

1. `:spine` — `com.music.spine` (`ModuleManager`, `SpineModule`, `QuickJsExecutor`,
   `EclipseAddon`) plus `ui/screens/settings/ModuleSourceScreen.kt`. This hosts **JS
   modules** and Eclipse HTTP addons, fetched from user-entered source URLs, and is
   consulted at playback time in `utils/YTPlayerUtils.kt` ("SPINE INTERCEPT START").
   It is a different, unrelated plugin format.
2. `com.convx.music.extensions` — a package of Kotlin extension *functions*
   (`StringExt.kt`, `ModifierExt.kt`, …). Pure naming coincidence.

Native JioSaavn streaming also already exists: `EnableSaavnStreamingKey`,
`SaavnAudioQualityKey`, `SaavnFallbackToYouTubeKey` in `constants/PreferenceKeys.kt`,
consumed in `utils/YTPlayerUtils.kt` (Saavn intercept) and surfaced in
`ui/screens/settings/JioSettings.kt`. The extension must therefore **add browse and
search over the catalogue**, and reuse the existing streaming path rather than
replacing it.

## 3. Licensing finding (why this is a reimplementation, not a port of source)

Echo is licensed under the **Unabandon Public License (UPL)** (`echo/LICENSE.md`),
which incorporates GPLv3 and *additionally* requires that any derivative work's source
be publicly available. Convx is GPL-3.0 (`Convx/LICENSE`). Two consequences:

- Copying Echo source into this repository is a licensing decision, not a technical one.
  It is only safe if this repository is public.
- Therefore this pass **re-expresses the interfaces** in our own package
  (`com.convx.extension.api`) instead of vendoring `dev.brahmkshatriya.echo.common`.
  Method shapes are re-derived from the evidence above; no Echo source, comments or
  naming are copied. The JioSaavn data layer is written fresh against the observed
  HTTP contract.

If the team later decides to vendor Echo's `common` module verbatim, this repository
must be public first, and `NOTICE` must be updated.

## 4. Reconstructed design

### 4.1 Modules

| Module | Kind | Responsibility |
| --- | --- | --- |
| `:extension-api` | Kotlin/JVM library | The contract only: types, models, capability interfaces. No Android, no network. |
| `:extension-jiosaavn` | Kotlin/JVM library | The JioSaavn implementation of that contract. |
| `:app` | Android app | Registry (which extensions exist, which are enabled), mapping into Convx's existing `YTItem` / `AlbumPage` / `ArtistPage` / `PlaylistPage` types, and the wiring points in §4.5. |

Rationale for JVM libraries rather than Android libraries: the contract and the Saavn
client use no Android API, so they stay unit-testable on the JVM. Convx's own
`SaavnService` cannot be reused directly because it depends on `android.util.Base64`
and `android.util.Log`; the extension re-implements the same DES step with
`java.util.Base64`.

### 4.2 Contract (as implemented in `:extension-api`)

```kotlin
enum class ExtensionType { MUSIC, TRACKER, LYRICS, MISC }

data class ExtensionMetadata(
    val id: String, val name: String, val version: String,
    val description: String, val author: String,
    val type: ExtensionType = ExtensionType.MUSIC,
    val authorUrl: String? = null, val iconUrl: String? = null,
    val repoUrl: String? = null, val updateUrl: String? = null,
)

sealed interface ExtensionMediaItem { val id: String; val title: String; val cover: String? }
data class ExtensionTrack(... artists, albumId, albumTitle, durationMs, isExplicit, streamable)  : ExtensionMediaItem
data class ExtensionAlbum(... artists, year, trackCount) : ExtensionMediaItem
data class ExtensionArtist(... ) : ExtensionMediaItem
data class ExtensionPlaylist(... owner, trackCount) : ExtensionMediaItem

data class ExtensionPage<T>(val items: List<T>, val continuation: String? = null)
data class ExtensionSearchResult(tracks, albums, artists, playlists)
data class ExtensionStream(val url: String, val bitrateKbps: Int?, val mimeType: String?)

interface ExtensionClient                                   // marker
interface SearchClient      { search(query, limit): ExtensionSearchResult }
interface AlbumClient       { album(id): ExtensionAlbumPage }
interface ArtistClient      { artist(id): ExtensionArtistPage }
interface PlaylistClient    { playlist(id): ExtensionPlaylistPage }
interface StreamClient      { stream(trackId): ExtensionStream? }

data class MusicExtension(val metadata: ExtensionMetadata, val client: ExtensionClient)
```

Capability is still expressed by interface implementation, as in Echo — the app asks
`client as? AlbumClient` rather than reading a flag. That keeps a second extension
(e.g. a YouTube one) addable without touching the host.

### 4.3 JioSaavn HTTP contract (captured from CI)

> Captured on 2026-11-11 by `SaavnLiveProbeTest`, run from the "CI & Debug Build"
> workflow with `probe_saavn: true` (the sandbox this spec was written in cannot reach
> jiosaavn.com; GitHub runners can). Everything below is response data, not inference.

Captured facts that changed the implementation:

- `search.getResults` → `{results, start, total}`; items carry `id, title, image,
  subtitle, perma_url, language, play_count, explicit_content, more_info{...}`.
- `search.getAlbumResults` → `results`; items carry `id, title, image, year,
  more_info{artistMap, song_count, text, query}` — `song_count` is inside `more_info`,
  not at the root.
- `search.getArtistResults` → `results`; items carry `id, name, image, role, perma_url`.
- `search.getPlaylistResults` → `results`; items carry `id, title, image, numsongs,
  more_info{firstname, lastname, song_count, ...}`.
- **Song detail is `song.getDetails` with `pids=<id>` and `ctx=android`.** The
  `webapi.get type=song` form answers `{"results": []}` for a search-result id, so it
  cannot be used for streaming. The response is keyed by song id (`{<id>: {...}}`).
- `webapi.get type=album` / `type=playlist` return the object at the root with the track
  list in `list`; `webapi.get type=artist` returns `artistId, name, image, topSongs,
  topAlbums, ...` at the root.
- **Detail payloads carry an empty `id` (and, in this API version, an empty `title`).**
  Verified: `raw id=""` for the requested album. Parsers therefore accept the requested
  id as a fallback instead of rejecting the payload.

### 4.3a Calls used

Base: `https://www.jiosaavn.com/api.php`, GET, every call is `?__call=<name>` plus
query parameters. Common parameters added by the client (`BaseApi.kt` in the Saavn
extension): `_format=json`, `_marker=0`, `api_version=4`, `ctx=web6dot0`.

| Purpose | `__call` | Parameters |
| --- | --- | --- |
| Track search | `search.getResults` | `q`, `p` (page), `n` (limit) |
| Album search | `search.getAlbumResults` | `q`, `p`, `n` |
| Artist search | `search.getArtistResults` | `q`, `p`, `n` |
| Playlist search | `search.getPlaylistResults` | `q`, `p`, `n` |
| Track detail | `webapi.get` | `type=song`, `token=<id>` |
| Album detail | `webapi.get` | `type=album`, `token=<id>` |
| Artist detail | `webapi.get` | `type=artist`, `token=<id>`, `n_song`, `n_album`, `p`, `sub_type`, `category`, `sort_order` |
| Playlist detail | `webapi.get` | `type=playlist`, `token=<id>`, `p`, `n` |
| Artist more songs | `artist.getArtistMoreSong` | `artistId`, `page`, `category`, `sort_order` |
| Artist more albums | `artist.getArtistMoreAlbum` | `artistId`, `page`, `category`, `sort_order` |
| Album recommendations | `reco.getAlbumReco` | `albumid` |
| Playlist recommendations | `reco.getPlaylistReco` | `listid` |

Response shapes used (field names as parsed by the Saavn extension's
`TrackParser` / `AlbumParser` / `ArtistParser` / `PlaylistParser`, and matching
`SaavnService.RawSongItem` in Convx):

- `search.getResults` → `{"results": [song...]}`
- `webapi.get type=song` → `{"songs": [song...]}`
- `song` → `id, title, image, subtitle, perma_url, language, play_count,
  explicit_content, more_info { album_id, album, album_url, encrypted_media_url,
  duration, release_date, has_lyrics, "320kbps", label, artistMap { primary_artists
  [{id, name}], featured_artists [], artists [] } }`
- `webapi.get type=album` → album object with `id, title, image, year, song_count,
  more_info.artistMap`, songs in `songs` / `list`
- `webapi.get type=artist` → artist object with `id, name, image, bio`-ish fields plus
  `topSongs` / `topAlbums`
- `webapi.get type=playlist` → playlist object with `id, title, image, list, songs`,
  `more_info { firstname, lastname, ... }`

Streaming: the CDN URL is **not** a plain field. `more_info.encrypted_media_url` is
Base64 DES-ECB (key `38346591`, PKCS5 padding); the plaintext is an `aac.saavncdn.com`
URL ending in `_48|_96|_160|_320.(mp4|aac|mp3)`. A track advertises 320 availability
in `more_info["320kbps"] == "true"`. All of this is already implemented, and battle-
tested, in Convx's `SaavnService` — the extension mirrors it rather than inventing a
new resolution rule.

### 4.4 Identity scheme

Extension items must not collide with YouTube ids, because Convx keys the Room
library, the queue and `DownloadUtil` by id. Every id coming from an extension is
namespaced:

```
saavn:track:<saavnSongId>
saavn:album:<saavnAlbumId>
saavn:artist:<saavnArtistId>
saavn:playlist:<saavnPlaylistId>
```

`ExtensionIds` (`app/src/main/kotlin/com/convx/music/extensions/ExtensionIds.kt`) owns
the prefix, the builders and the parsers; nothing else hardcodes `"saavn:"`.

### 4.5 Wiring points in Convx (file:line at time of writing)

| Surface | File | Change |
| --- | --- | --- |
| Search | `viewmodels/OnlineSearchViewModel.kt` `load()` | After the YouTube result is mapped, append extension hits as `YTItem`s. |
| Album detail | `viewmodels/AlbumViewModel.kt` `refresh()` (`YouTube.album(albumId)`) | If the id is an extension id, build the page from the extension instead. Same downstream `database.transaction { insert(it) }` path (`db/DatabaseDao.kt:1712 fun insert(albumPage: AlbumPage)`). |
| Artist detail | `viewmodels/ArtistViewModel.kt` (`PageCaches.artistPages.get { YouTube.artist(...) }`) | Same substitution. |
| Playlist detail | `viewmodels/OnlinePlaylistViewModel.kt` (`YouTube.playlist(playlistId)`) | Same substitution. |
| Streaming | `utils/YTPlayerUtils.kt` `playerResponseForPlayback()` | New early branch: an extension id resolves through `StreamClient` and returns `PlaybackData(..., isSaavnStream = true)`. Placed next to the existing "SPINE INTERCEPT" block, which is the precedent for provider intercepts in this function. |
| Settings | `ui/screens/settings/ExtensionsSettingsScreen.kt`, `SettingsHubs.kt:146`, `SearchableSettings.kt:109`, `ui/screens/NavigationBuilder.kt:561` | New page listing installed extensions with an enable switch, following the `JioSettings` page structure and the `SettingsLink` / `SearchableSetting` / `sharedComposable` registration patterns. |
| State | `constants/PreferenceKeys.kt` | `ExtensionEnabledKey` = boolean, default `false` (off until the user turns it on, matching `EnableSaavnStreamingKey`). |

### 4.6 Failure modes and state

- Extension disabled → registry returns nothing; search results are YouTube-only;
  `saavn:` ids already in the library resolve to "unavailable" rather than crashing.
- Network failure or non-200 → the client throws `ExtensionException`; call sites log
  via `reportException` (Convx's existing helper) and fall through to the YouTube path
  where one exists.
- Missing `encrypted_media_url` or failed DES decrypt → `StreamClient` returns `null`,
  playback reports an error; no silent fallback to a different provider for an
  extension-owned id.
- Malformed JSON (the private API is undocumented and can change) → parsers are
  defensive: missing fields default, unparseable item is dropped, not thrown.

## 5. Open questions / not determined

1. **Detail payload display text.** Album/artist/playlist detail responses return an
   empty `id` and empty `title` at the root, while the track list parses fine
   (`album: tracks=1`) and artist/playlist lists come back empty (`topTracks=0`,
   `tracks=0`). The display text presumably lives in `header_desc` / `subtitle` /
   `more_info.text`, and the artist lists may need different `n_song`/`n_album`/`p`
   values. Needs one more probe iteration; search and streaming are unaffected.
2. **Pagination.** `search.getResults` returns `start` and `total` alongside `results`,
   so paging is feasible; the observed clients mix 1-based (search `p`) and 0-based
   (`webapi.get p`) semantics. Not resolved, and nothing paginates in this pass.
3. **`320kbps` is per track** and frequently `"false"`; the resolver reports the rung it
   actually finds in the decrypted filename instead of asking for a rung that may not
   exist. Confirmed working: a probe track resolved to a 96kbps `audio/mp4` CDN URL.
4. **Rate limiting / geo-blocking.** The probe ran from a GitHub runner, not from India.
   Behaviour on a real Indian connection is untested.
5. **Does a `saavn:` id survive the download path?** `DownloadUtil` resolves through
   `YTPlayerUtils.playerResponseForPlayback`, so it inherits the new branch, but
   `DownloadUtil` also caches formats keyed by `mediaId`. Untested.
6. **Album/artist rows in the local library.** Extension items are inserted into the same
   Room tables as YouTube items; whether entity constraints accept the synthetic ids has
   not been exercised on a device.
7. **Runtime behaviour generally.** The branch compiles, its tests pass and the live API
   paths are exercised, but nothing has run in the app on a device.
