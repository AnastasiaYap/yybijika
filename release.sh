#!/usr/bin/env bash
# Cut a release: bump the version, rebuild the deck, test, sign, tag, publish.
#
# Signing happens here rather than in CI, so the keystore never leaves this
# machine. Phones pick the new build up from GitHub on their next launch.
#
# Usage:
#   ./release.sh 0.2.0 "What changed, one line per bullet"
#   ./release.sh 0.2.0 --notes-file notes.md
#   ./release.sh 0.2.0 "..." --dry-run

set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "usage: ./release.sh <version> [notes] [--dry-run]" >&2
    exit 1
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "version must look like 1.2.3, got '$VERSION'" >&2
    exit 1
fi
shift

NOTES=""
DRY_RUN=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)    DRY_RUN=1; shift ;;
        --notes-file) NOTES="$(cat "$2")"; shift 2 ;;
        *)            NOTES="$1"; shift ;;
    esac
done
NOTES="${NOTES:-Maintenance release.}"

GRADLE="app/build.gradle.kts"
APK="dist/YingyingBijika-${VERSION}-release.apk"
# The sample deck, for anyone who is not the author. A different applicationId,
# so it can never install over the personal one, and a different name, so the
# in-app updater in each build picks its own.
STARTER="dist/yybijika-starter-${VERSION}-release.apk"

# --- checks ---------------------------------------------------------------

if [[ ! -f keystore.properties ]]; then
    echo "keystore.properties missing — an unsigned build cannot install over an existing one" >&2
    exit 1
fi

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    echo "tag v$VERSION already exists" >&2
    exit 1
fi

CURRENT=$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
LOWEST=$(printf '%s\n%s\n' "$CURRENT" "$VERSION" | sort -V | head -1)
if [[ "$VERSION" == "$CURRENT" || "$LOWEST" == "$VERSION" ]]; then
    echo "version must increase: $CURRENT -> $VERSION" >&2
    exit 1
fi

# versionCode is what Android compares when deciding whether an install is an
# upgrade; versionName is only ever shown to a human.
CODE=$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | grep -oE '[0-9]+')
NEXT_CODE=$((CODE + 1))

echo "  $CURRENT -> $VERSION   (versionCode $CODE -> $NEXT_CODE)"
[[ $DRY_RUN -eq 1 ]] && { echo "  dry run, stopping here"; exit 0; }

# --- bump -----------------------------------------------------------------

sed -i '' -E "s/versionCode = [0-9]+/versionCode = $NEXT_CODE/" "$GRADLE"
sed -i '' -E "s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE"

# --- rebuild the deck so the release always ships current content ---------

echo "  rebuilding both decks…"
./.venv/bin/python pipeline/build.py
cp content/content.db app/src/personal/assets/content.db
./.venv/bin/python pipeline/build.py --starter --out content/starter.db
cp content/starter.db app/src/starter/assets/content.db

# --- test and build -------------------------------------------------------

echo "  testing…"
./gradlew testPersonalDebugUnitTest --quiet

echo "  building signed release…"
./gradlew assemblePersonalRelease assembleStarterRelease --quiet

mkdir -p dist
cp app/build/outputs/apk/personal/release/app-personal-release.apk "$APK"
cp app/build/outputs/apk/starter/release/app-starter-release.apk "$STARTER"

BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/*/ | tail -1)
for built in "$APK" "$STARTER"; do
    "${BUILD_TOOLS}apksigner" verify "$built" >/dev/null
    echo "  signed: $built ($(du -h "$built" | cut -f1))"
done

# --- publish --------------------------------------------------------------

git add -A
git commit -q -m "Release v$VERSION

$NOTES"
git tag -a "v$VERSION" -m "v$VERSION"
git push -q origin HEAD --tags

gh release create "v$VERSION" "$APK" "$STARTER" \
    --title "v$VERSION" \
    --notes "$NOTES"

echo
echo "  published v$VERSION"
echo "  phones will offer it on their next launch."
