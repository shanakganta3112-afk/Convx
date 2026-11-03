# Convx → Offline Local Music Player: Architecture Analysis and Conversion Plan

Status: analysis complete, plan proposed. Nothing in the online path has been removed yet.

Repo: `cosmictaserdev-creator/Convx` at commit `55b11a2cc6f31104a05bd684b0adc5accad90c37`
Analysed: 2026-11-03

---

## 1. What this app actually is

Convx is a Kotlin / Jetpack Compose Android music client built on Media3. It is a
YouTube Music front end: catalogue, streaming, discovery and library all come from
`innertube/`, an unofficial InnerTube (YouTube) client. It is a Gradle multi-module
project, ten modules, ~1,300 Kotlin files total.

| Module | .kt files | Responsibility | Fate in an offline player |
| --- | --- | --- | --- |
| `app` | 900 | screens, ViewModels, navigation, playback service, Room DB, DI | stays; online surfaces removed |
| `hig` | 116 | Cupertino-style controls, theme, glass | stays untouched |
| `innertube` | 123 | InnerTube client, models, stream URLs | removed |
| `lyrics` | 64 | lyrics providers and parsing | trimmed to local/embedded |
| `integrations` | 24 | Discord, Last.fm, recognition | removed |
| `spotify` | 21 | Spotify auth + playlist import | removed |
| `canvas` | 13 | animated canvas catalogue | removed |
| `spine` | 11 | third-party streaming add-ons | removed |
| `network-storage` | 9 | SMB / WebDAV mounts | decision needed (see §7) |
| `artwork` | 8 | artwork loading + cache | trimmed (network fetcher removed) |

Toolchain as pinned: Kotlin 2.3.10, Gradle 9.8.2, JDK 21, `compileSdk` 37, `minSdk` 26,
`targetSdk` 36, Media3 1.7.1, Room 2.8.4 (schema v40), Hilt 2.59.1, KSP, Compose.
Product flavours: `abi` × `variant` (`universal`, `arm64`, `arm32`, `x86_64` × `foss`, `gms`).

## 2. The load-bearing discovery

**The offline product already exists inside this repo, behind one boolean.**

`LocalOnlyModeKey` (`app/src/main/kotlin/com/convx/music/constants/PreferenceKeys.kt:166`)
is read as `LocalLocalOnlyMode` (`AppNavItems.kt:18`), provided once at the root
(`AppRootContent.kt:745`), and every major surface already branches on it:

- `Screens.mainScreens(localOnly)` returns `[Home, Songs, Search, Library]` instead of
  `[Home, Search, Radio, Library]` (`ui/screens/Screens.kt:102`).
- `HomeRoute` renders `LocalHomeScreen` instead of the online `HomeScreen`
  (`ui/screens/HomeScreen.kt:283`).
- `LibrarySongsScreen`, `LibraryAlbumsScreen`, `LibraryArtistsScreen`, `LibraryMixScreen`,
  `AppleLibrarySubscreens`, `SearchScreen`, `LocalSearchScreen` all switch to local queries.
- `LibraryViewModels` swaps every `SongFilter` / `AlbumFilter` / `ArtistFilter` to `LOCAL`.
- `AppleLibraryViewModel` and `LocalSearchViewModel` read the same key.
- Settings exposes it as a toggle (`SettingsScreen.kt:271`, `LocalOnlyRow`).

This changes the shape of the job completely. The conversion is **not** "build a local
player" — it is "make the existing local player the only player, and remove the online
half". That is a deletion and de-risking project, not a greenfield build, and it means
the UI the owner wants preserved survives by construction.

## 3. The playback seam

This is the single most important seam in the codebase, and it is already clean.

- Local songs are stored with the MediaStore content URI **as the primary key**:
  `songId = contentUri` = `content://media/external/audio/media/<id>`
  (`utils/LocalAudioScanner.kt:143`). There is no separate `localPath` column; the id
  *is* the playable URI.
- `MusicService.createMediaSourceFactory()` (`playback/MusicService.kt:3717-3750`) wraps
  every media item in `ResolvingMediaSource`, and its first branch is:

  ```kotlin
  if (mediaId.startsWith("content://") || mediaId.startsWith("file://") ||
      mediaId.startsWith("/storage/")) return@ResolvingMediaSource progressive.createMediaSource(mediaItem)
  ```

  So **local playback never touches the network resolver**. Everything after that branch
  (`fetchResolvedStream` → `YTPlayerUtils.playerResponseForPlayback` → InnerTube →
  signature deciphering → PoToken → SABR) is the online path.

- `YTPlayerUtils.videoStream()` (`utils/YTPlayerUtils.kt:176`) is the separate video
  path used by Canvas artwork and artist background video.

Consequence: enforcing "offline only" at playback is a small, surgical change at that one
`if` — make the non-local branch fail fast. No other playback code has to move.

## 4. Complete inventory of online-only surfaces

Each of these either disappears or loses its network data source.

**Navigation destinations** (`ui/screens/NavigationBuilder.kt`)

| Route | Screen | Lines |
| --- | --- | --- |
| `explore` | Charts / new releases / moods | 158 |
| `radio`, `radio_station/{seedId}` | RadioScreen | 162, 167 |
| `browse/{browseId}` | BrowseScreen (YT browse) | 223 |
| `search/{query}` | OnlineSearchResult | 238 |
| `artist/{artistId}` + 3 children | online artist pages | 290-328 |
| `online_playlist/{playlistId}` | YT playlists | 347 |
| `youtube_browse/{browseId}` | YouTubeBrowseScreen | 448 |
| `network_browse/{serverId}` | SMB/WebDAV browser | 582 |
| `song_insight/{songId}` | online insight | 600 |
| `ai_dj_set` | AI set builder | 623 |
| `settings/modules/{moduleId}` | spine add-ons | 655 |
| `settings/integrations/listen_together` | Listen Together | 673 |
| `listen_together_from_topbar` | Listen Together | 189 |

Local equivalents already exist and stay: `local_playlist/`, `local_music`,
`local_folder/`, `auto_playlist/`, `cache_playlist/`, `top_playlist/`.

**Screens and feature packages that exist only to talk to the network**

| Path | Lines | Why it goes |
| --- | --- | --- |
| `ui/screens/ExploreScreen.kt` | 295 | YT charts/moods feed |
| `ui/screens/ChartsScreen.kt` | 449 | YT charts |
| `ui/screens/MoodAndGenresScreen.kt` | 271 | YT mood feed |
| `ui/screens/NewReleaseScreen.kt` | 244 | YT new releases |
| `ui/screens/RadioScreen.kt` | 385 | YT radio |
| `ui/screens/YouTubeBrowseScreen.kt` | 319 | YT browse |
| `ui/screens/BrowseScreen.kt` | 195 | YT browse |
| `ui/screens/ListenTogetherScreen.kt` | 1,462 | needs a server |
| `ui/screens/LoginScreen.kt` | 272 | Google account |
| `ui/screens/AccountScreen.kt` | — | Google account |
| `ai/` (3 files) | — | OpenRouter/Mistral calls |
| `jam/` (4 files) | — | live jam sync |
| `listentogether/` (9 files) | — | server protocol |
| `recognition/` (4 files) | — | Shazam/Vibra fingerprinting |
| `utils/DiscordRPC.kt` | 117 | Discord presence |
| `utils/ScrobbleManager.kt` | 144 | Last.fm / ListenBrainz |
| `utils/Wikipedia.kt` | 98 | online artist bio |
| `utils/NetworkArtworkFetcher.kt` | 38 | remote artwork |
| `utils/BotDetectionMitigator.kt`, `cipher/`, `potoken/`, `sabr/` | — | YT anti-bot |
| `core/updater/` (5 files) | — | network self-update |
| `artistvideo/`, `convxcanvas/`, `canvas/` | — | online video artwork |

**Preferences that become meaningless** — roughly 40 keys in `constants/PreferenceKeys.kt`
covering audio quality, data saver, proxy, PoToken, login, Discord, Last.fm, ListenBrainz,
Listen Together, AI, recognition, network storage. The settings screens that expose them
(`ui/screens/settings/`) need pruning in the same commit that removes the feature, or the
toggles become dead switches.

**Manifest** — `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO` (recognition),
`REQUEST_INSTALL_PACKAGES` (self-update), `CHANGE_WIFI_STATE` (network storage) all become
removable. Removing `INTERNET` is the honest end state for an offline player and is a
strong, checkable acceptance criterion.

## 5. What must be built, not just deleted

Three things genuinely do not exist yet:

1. **A library-permission and first-run flow for local music.** Today the app can start
   with no local library and the local screens simply show nothing. An offline player has
   to ask for `READ_MEDIA_AUDIO`, scan, and show progress. `LocalAudioScanner` (281 lines)
   exists and is callable; the onboarding path does not.
2. **Scan lifecycle.** Re-scan on app start, on storage change, and from a manual action,
   with a visible "N tracks found" result. `ScanResult` already returns
   `totalFound / newSongs / skippedExisting`.
3. **Embedded-artwork and tag coverage.** Without the network, artwork must come from
   MediaStore album art (`content://media/external/audio/albumart`) and embedded tags.
   `utils/LocalAudioProperties.kt` and `ExternalTagEditor.kt` exist; the artwork pipeline
   is still wired to `artwork/`'s network loader and needs a local-only implementation.

## 6. Phased plan

Each phase is independently reviewable and leaves the app in a working state.

**Phase 1 — Offline by default (surgical, ~6 files).**
Force `LocalOnlyModeKey` to true everywhere it is read, drop the Settings toggle so it
cannot be switched off, stop the nav bar ever offering Explore, and make the playback
resolver refuse any non-local media id. The online screens remain in the tree but are
unreachable. This is the phase that can be shipped without touching a single screen body.

**Phase 2 — Delete the discovery surfaces.**
Remove the Explore/Charts/Moods/NewRelease/Radio/YouTubeBrowse/Browse routes and screens,
plus every `navigate("artist/…")` and `navigate("online_playlist/…")` call site. Delete
the routes and the call sites in the same commit — a route removed without its callers is
a guaranteed `IllegalArgumentException` at runtime.

**Phase 3 — Remove the online modules.**
Drop `innertube`, `spine`, `canvas`, `spotify`, `integrations` from `settings.gradle.kts`
and the dependency block; delete `ai/`, `jam/`, `listentogether/`, `recognition/`,
`artistvideo/`, `utils/cipher`, `utils/potoken`, `utils/sabr`, `utils/tidal`. Prune the
~40 dead preferences and their settings rows. Remove `INTERNET` from the manifest.

**Phase 4 — Lyrics and artwork go local.**
Keep embedded/LRC lyrics and MediaStore artwork; delete the online lyrics providers and
the network artwork fetcher.

**Phase 5 — Make it a good local player.**
Permission onboarding, scan-on-start with progress, folder picker and exclusions (already
present, needs surfacing), local search, playlist import from M3U, and a "rescan library"
action. This is the only phase that adds user-visible behaviour rather than removing it.

## 7. Decisions taken

Answered by the owner on 2026-11-03:

1. **SMB/WebDAV** — strip it. On-device only.
2. **Lyrics** — keep the online providers, behind a toggle.
3. **Deletion depth** — hard-delete the online modules.

**One conflict, resolved in favour of lyrics.** Removing `INTERNET` and keeping online
lyrics cannot both hold: an online provider needs the permission. The manifest keeps
`INTERNET` for that reason. If lyrics ever go local-only, deleting that one line from
`AndroidManifest.xml` completes the offline claim.

**Deletion depth is bounded by the toolchain, not by preference.** 111 of the 817 files
under `app/src/main` import `com.music.innertube` directly (232 call sites), and `artist/`
alone has 24 `navigate()` call sites. Removing `innertube` therefore rewrites the app's
data layer, not just its screens. With no JDK, no Android SDK 37 and `jitpack.io`
unreachable here, a blind mass deletion would hand back a tree that does not build and
cannot be checked. The work is sequenced so every step is verifiable on a real toolchain;
`docs/OFFLINE_REMOVAL_MANIFEST.md` carries the per-target reference counts that gate each
step.

## 8. Risks

- **No compile here.** `jetpackSfSymbols` — the SF Symbols icon set the whole Cupertino UI
  is drawn with — resolves only from `jitpack.io`, which this environment cannot reach;
  there is also no JDK and no Android SDK 37. So nothing in this repo can be compiled or
  unit-tested here. Every phase must be verified with `./gradlew :app:assembleUniversalFossDebug`
  on the owner's machine before merge. This is the biggest constraint on the project and
  the reason the plan is phased rather than one large diff.
- **Route/caller mismatch** is the dominant crash class in Phase 2. Grep every route string
  before deleting it.
- **Room schema.** `SongEntity.isLocal` and the content-URI primary key already carry
  everything needed, so no migration is required for Phases 1-4. Phase 5 additions must
  add a new schema version (current head is 40).
- **`isLocal` is not the same as "local-only mode".** Downloaded YouTube songs are stored
  with `isDownloaded = true` and a real file, but keep a YouTube id. Under an offline
  player those files are still playable (the download path writes real audio), but they
  are not MediaStore rows. Decide whether to import them on first run or drop them.

## 9. Phase 1 as built

Committed on branch `feat/offline-local-player` (10 files, +67 / -94):

- `constants/PreferenceKeys.kt` — `const val LocalOnlyMode = true` is now the single
  source of truth; the preference key is kept but no longer read anywhere.
- `AppNavItems.kt` — tab set is always `Home / Songs / Search / Library`; Explore is gone
  from both bars; both bars now carry the same items.
- `ui/screens/Screens.kt` — `mainScreens()` has no mode parameter and no online branch.
- `ui/screens/HomeScreen.kt` — `HomeRoute` always renders `LocalHomeScreen`; the online
  feed's view model is never constructed.
- `ui/screens/settings/SettingsScreen.kt` — the local-only switch and its row are deleted;
  there is no online mode left to switch to.
- `ui/screens/settings/AppearanceAdvancedSettings.kt` — optional tab list no longer offers
  Explore.
- `viewmodels/LibraryViewModels.kt`, `viewmodels/LocalSearchViewModel.kt`,
  `ui/screens/library/apple/AppleLibraryViewModel.kt` — every library and search filter
  reads the constant instead of the preference, so no stored value can restore the online
  library.
- `playback/MusicService.kt` — the media source resolver now serves only file URIs and
  audio already in the download cache; anything else throws instead of resolving a stream
  URL. Downloads that were never exported keep playing.

Design note: the constant is deliberately used inside the existing conditionals rather
than deleting the branches outright. `grep -rn "LocalOnlyMode" app/src/main` returns the
exact list of online branches still to remove, which is what Phase 2 and 3 work from.

## 10. Verification strategy

Because the sandbox cannot build, each phase ships with:

- a grep-level invariant check (no remaining reference to a removed symbol),
- a `git diff --stat` review,
- the exact command the owner runs to prove it,
- unit tests written to run under `./gradlew :app:testUniversalFossDebugUnitTest`
  (70 existing test files; the new ones follow their conventions).

The honest statement of reach: analysis and code preparation are done here; compile and
device verification happen on the owner's toolchain.
