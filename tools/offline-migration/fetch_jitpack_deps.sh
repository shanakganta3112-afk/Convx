#!/usr/bin/env bash
# Fetch the four JitPack-only dependencies into a local Maven repo you can upload.
#
# Why this exists: the build environment cannot reach jitpack.io (the proxy refuses
# it), and these four artifacts resolve from nowhere else. Run this on any machine
# with normal internet, then hand back the .zip it produces.
#
#   ./fetch_jitpack_deps.sh            # writes jitpack-local-repo/ and .zip
#   ./fetch_jitpack_deps.sh --zip-only
#
# Versions are read from gradle/libs.versions.toml, so this script cannot drift from
# the build. Run it from the repository root (or pass the root as $1).

set -euo pipefail

ROOT="${1:-.}"
[[ "$ROOT" == --zip-only ]] && ROOT="."
cd "$ROOT"

[[ -f gradle/libs.versions.toml ]] || {
  echo "error: run this from the Convx repository root (no gradle/libs.versions.toml here)" >&2
  exit 1
}

ver() { grep -E "^$1 *= *\"" gradle/libs.versions.toml | head -1 | sed -E 's/.*"([^"]+)".*/\1/'; }

UCROP=$(ver ucrop)
NEWPIPE=$(ver newpipeextractor)
SMOOTH=$(ver smoothCorner)
SF=$(ver jetpackSfSymbols)

echo "ucrop=$UCROP newpipe=$NEWPIPE smoothCorner=$SMOOTH sfSymbols=$SF"

OUT="jitpack-local-repo"
rm -rf "$OUT"
mkdir -p "$OUT"

# JitPack URL layout mirrors the Maven group, but a dotted group with capitals
# (com.github.cosmictaserdev-creator.Jetpack_SF_Symbols) keeps its case in the path.
fetch() {
  local group="$1" artifact="$2" version="$3" ext="$4"
  local path="${group//.//}"
  local dir="$OUT/$path/$artifact/$version"
  local base="https://jitpack.io/$path/$artifact/$version/$artifact-$version"
  mkdir -p "$dir"

  for suffix in "$ext" pom; do
    if [[ -s "$dir/$artifact-$version.$suffix" ]]; then continue; fi
    echo "  GET $artifact-$version.$suffix"
    if ! curl -fsSL --retry 3 --retry-delay 2 -o "$dir/$artifact-$version.$suffix" "$base.$suffix"; then
      echo "  !! failed: $base.$suffix" >&2
      rm -f "$dir/$artifact-$version.$suffix"
      FAILED=1
    fi
  done
}

FAILED=0
fetch "com.github.yalantis"                                "ucrop"                             "$UCROP"   aar
fetch "com.github.TeamNewPipe"                             "NewPipeExtractor"                  "$NEWPIPE" jar
fetch "com.github.racra"                                   "smooth-corner-rect-android-compose" "$SMOOTH"  aar
fetch "com.github.cosmictaserdev-creator.Jetpack_SF_Symbols" "sfsymbols"                       "$SF"      aar

# The ucrop AAR ships no transitive deps of its own; nothing to rewrite in the POMs.
count=$(find "$OUT" -type f | wc -l | tr -d ' ')
echo "collected $count files under $OUT/"

ZIP="jitpack-local-repo.zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  zip -qr "$ZIP" "$OUT"
else
  echo "  (no zip binary; using python)" >&2
  python3 -c "import shutil,sys; shutil.make_archive('jitpack-local-repo','zip','.', '$OUT')"
fi
echo "wrote $ZIP ($(du -h "$ZIP" | cut -f1))"

if [[ "${FAILED:-0}" != "0" ]]; then
  echo "warning: at least one artifact failed to download; the build will still miss it" >&2
  exit 2
fi
echo "OK — upload $ZIP"
