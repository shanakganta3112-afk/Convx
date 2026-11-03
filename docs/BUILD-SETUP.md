# Getting Convx to build — step by step

Two independent goals, don't conflate them:

- **A build** — so the code can be compiled and verified. Needs the four JitPack
  dependencies to resolve. Pick Path A or Path B.
- **A handover** — so the finished branch reaches your repo as a PR. Needs GitHub write
  access. Optional; it does not block building or verifying.

Everything below has been checked against the repository as it stands at commit `e682a34`.

---

## Path A — allowlist `jitpack.io` (recommended)

One entry, permanent, nothing to upload.

1. Find who owns the workspace's sandbox egress policy. In the sandbox this is a
   read-only ConfigMap mounted at `/etc/egress/allowlist.txt` (400 lines, root-owned,
   symlinked from a ConfigMap) — it is provisioned upstream, not editable from inside the
   sandbox. That upstream policy is what needs the entry.
2. Add this host:

   ```
   jitpack.io
   ```

   If the policy supports wildcards, `*.jitpack.io` is safer. `maven.aliyun.com` was the
   only other blocked host and is already removed from the build, so nothing else is
   needed.
3. Tell me it's done. I verify with a request to the exact artifact URL and report the
   status code — `200`/`302` means open, `502` means still refused.
4. I install the toolchain and run the first build. Nothing further from you.

Why this is the better path: it survives dependency version bumps, new dependencies, and
every future build. Path B has to be redone whenever `gradle/libs.versions.toml` changes.

---

## Path B — vendor the four artifacts (no policy change)

Use this if the egress policy is locked down or slow to change. Needs a machine with
normal internet for about two minutes.

1. Get the repo onto that machine — any clone works, it does not have to be the branch:

   ```bash
   git clone https://github.com/cosmictaserdev-creator/Convx.git
   cd Convx
   ```

2. Run the fetch script:

   ```bash
   bash tools/offline-migration/fetch_jitpack_deps.sh
   ```

   It reads the versions straight out of `gradle/libs.versions.toml` (currently
   `ucrop=2.2.11`, `NewPipeExtractor=v0.25.2`, `smooth-corner=v1.0.0`,
   `sfsymbols=1.1.1`) so it cannot drift from the build. It needs `curl`, and `zip` or
   `python3`.

3. It writes `jitpack-local-repo.zip` in the repo root and prints the file count and size.
   Expect a handful of files and a few MB. If it exits with status 2, at least one
   artifact failed — paste the output and I'll adjust the URL layout.

4. Upload `jitpack-local-repo.zip` in the chat.

5. I unzip it into the working tree, add it to `settings.gradle.kts` as a local Maven
   repository, and **remove** the `https://jitpack.io` repository line so Gradle never
   probes the blocked host:

   ```kotlin
   repositories {
       google()
       mavenCentral()
       maven { setUrl(rootProject.layout.projectDirectory.dir("jitpack-local-repo").asFile.toURI()) }
       // jitpack removed: artifacts are served from jitpack-local-repo above
   }
   ```

   `settings.gradle.kts` is the right file because the build uses
   `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so all dependency resolution happens there.

6. Build.

**When you'd have to redo it:** any change to the four version numbers in
`libs.versions.toml`. Note that `NewPipeExtractor` disappears with the `innertube` module
in phase 3, so the permanent set is only three artifacts — SF Symbols, smooth-corner and
ucrop.

---

## Path C — build it yourself, if you'd rather not wait

If you have Android Studio, you can verify the branch yourself with no involvement from me.
This is the same environment CI uses:

1. JDK 21 (Temurin).
2. Android SDK Platform 37 and matching build-tools (any 37.x).
3. NDK `27.0.12077973` — pinned in `app/build.gradle.kts`, so an older NDK will fail.
4. A debug keystore at `~/.android/debug.keystore` (the debug build type signs with it).
   Android Studio creates one on first build; from a terminal:

   ```bash
   mkdir -p ~/.android
   keytool -genkey -v -keystore ~/.android/debug.keystore -storepass android \
     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```

5. Then:

   ```bash
   ./gradlew :app:assembleUniversalFossDebug
   ```

   `jitpack.io` is reachable from a normal machine, so no vendoring is needed.

**Things you do *not* need to set up**, so don't spend time on them:

- **FFTW.** The CI workflow runs `.github/actions/setup-fftw`, which `cd`s into
  `app/src/main/cpp/vibrafp/third_party/` — a directory that no longer exists in this
  commit. The only native module is `app/src/main/cpp/analyzer/`, and its `CMakeLists.txt`
  does not reference FFTW or `vibrafp`. That CI step is stale; ignore it.
- **`LASTFM_API_KEY` / `LASTFM_SECRET`.** They default to empty strings in
  `buildConfigField`, so the build succeeds without them.
- **Release signing.** Only `keystore/release.keystore` plus `STORE_PASSWORD` /
  `KEY_ALIAS` / `KEY_PASSWORD` for a release build. A debug build needs none of it.

---

## GitHub write access — only needed for the handover

Currently `git push` is 403 and the GitHub API returns `Resource not accessible by
integration`. The App is already authorised, so this is installation scope, not login.
Either one fixes it:

**Option 1 — widen the App's installation**

1. On GitHub, go to Settings → Applications → the connected GitHub App → Configure.
2. Under Repository access, make sure `cosmictaserdev-creator/Convx` is selected (not
   "only select repositories" with it missing).
3. Under Permissions, set **Contents: Read and write**.
4. Approve the change if GitHub asks.

**Option 2 — add the account as a collaborator**

1. In the repo: Settings → Collaborators and teams → Add people.
2. Add `shanakganta3112-afk` with the **Write** role.
3. Accept the invitation from that account.

I verify by pushing the branch; a successful push followed by an open PR confirms it.

---

## What I do the moment either path lands

In this order, so each step's failure is unambiguous:

1. JDK 21 from Adoptium into the sandbox, verify `java -version`.
2. Android command-line tools, then `sdkmanager "platforms;android-37"`, the latest
   `"build-tools;37.*"` (let sdkmanager pick the patch level), and
   `"ndk;27.0.12077973"`; accept licences; export `ANDROID_HOME` and `ANDROID_NDK_HOME`.
3. Generate `~/.android/debug.keystore`.
4. `./gradlew :app:assembleUniversalFossDebug` on the current branch head — this compiles
   what is already committed and tells me whether phase 1 and the module removal are
   sound.
5. Read the compiler output, fix, rebuild until green.
6. Only then resume the conversion (phase 2 routes, then `network-storage`), building after
   each step so a break is always attributable to one change.

Expected on the first run: warnings for now-unused streaming members
(`fetchResolvedStream`, `adaptive`, `ResolvedStream`, `playerCache`) — that is the
streaming path waiting to be deleted in phase 3, not a defect.

## Current state of the branch

```
deef608  feat(offline): make the on-device library the only playback source
33cc4b9  refactor(offline): drop the canvas and integrations modules
e682a34  build: drop the aliyun mirror and add the JitPack fetch script
```

3 commits, 55 files, +904 / −4089. Nothing in it has been compiled yet.
