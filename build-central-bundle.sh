#!/usr/bin/env bash
# =============================================================================
#  Build a Maven Central deployment bundle for manual upload.
#
#  Drops a ready-to-upload zip on your Desktop. Then:
#    1. Go to https://central.sonatype.com/publishing
#    2. Click "Publish Component" → drop the zip into the upload area
#    3. Sonatype validates → review the deployment → click "Publish"
#
#  Re-runnable. Cleans its own staging dir on exit.
# =============================================================================
set -euo pipefail

# ─── Config ────────────────────────────────────────────────────────────────
GROUP_ID="com.rajandube"
ARTIFACT_ID="jelly"
VERSION="0.1.0"

# ─── Derived paths ─────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GROUP_PATH=$(echo "$GROUP_ID" | tr '.' '/')
ARTIFACT_PATH="$GROUP_PATH/$ARTIFACT_ID/$VERSION"
M2_DIR="$HOME/.m2/repository/$ARTIFACT_PATH"
STAGING_DIR="$(mktemp -d -t jelly-bundle)"
OUTPUT_ZIP="$HOME/Desktop/${ARTIFACT_ID}-${VERSION}-central-bundle.zip"

# Always clean the staging dir, even on failure.
trap 'rm -rf "$STAGING_DIR"' EXIT

# ─── Step 1: Build + sign via Gradle ───────────────────────────────────────
echo "→ Building $GROUP_ID:$ARTIFACT_ID:$VERSION + signing artifacts..."
rm -rf "$M2_DIR"
./gradlew ":${ARTIFACT_ID}:publishToMavenLocal" --no-daemon -q

if [ ! -d "$M2_DIR" ]; then
  echo "ERROR: build did not produce $M2_DIR" >&2
  exit 1
fi

# ─── Step 2: Stage in a clean Maven layout ─────────────────────────────────
echo "→ Staging in Maven layout..."
mkdir -p "$STAGING_DIR/$ARTIFACT_PATH"

# Copy only the artifacts + signatures; skip any pre-existing checksums
# (mavenLocal produces partial ones that don't match Central's expectations).
for f in "$M2_DIR"/*; do
  case "$f" in
    *.md5|*.sha1|*.sha256|*.sha512) ;;
    *) cp "$f" "$STAGING_DIR/$ARTIFACT_PATH/" ;;
  esac
done

# ─── Step 3: Generate checksums for every file ─────────────────────────────
# Central requires .md5 and .sha1; .sha256 + .sha512 are recommended.
echo "→ Generating MD5 + SHA-1 + SHA-256 + SHA-512 checksums..."
cd "$STAGING_DIR/$ARTIFACT_PATH"
for f in *; do
  case "$f" in
    *.md5|*.sha1|*.sha256|*.sha512) continue ;;
  esac
  md5 -q "$f"               > "$f.md5"
  shasum -a 1   "$f" | awk '{print $1}' > "$f.sha1"
  shasum -a 256 "$f" | awk '{print $1}' > "$f.sha256"
  shasum -a 512 "$f" | awk '{print $1}' > "$f.sha512"
done

# ─── Step 4: Zip the Maven layout (com/ at zip root) ───────────────────────
echo "→ Building deployment zip..."
cd "$STAGING_DIR"
rm -f "$OUTPUT_ZIP"
zip -rq "$OUTPUT_ZIP" "$GROUP_PATH"

# ─── Done ──────────────────────────────────────────────────────────────────
ZIP_SIZE=$(du -h "$OUTPUT_ZIP" | cut -f1)
FILE_COUNT=$(find "$STAGING_DIR/$ARTIFACT_PATH" -type f | wc -l | tr -d ' ')

echo ""
echo "════════════════════════════════════════════════════════════════════"
echo "  ✓ Bundle ready: $OUTPUT_ZIP"
echo "════════════════════════════════════════════════════════════════════"
echo "  Coordinates: $GROUP_ID:$ARTIFACT_ID:$VERSION"
echo "  Size:        $ZIP_SIZE"
echo "  Files:       $FILE_COUNT (artifact + signature + 4× checksum each)"
echo ""
echo "  Next steps:"
echo "    1. Open https://central.sonatype.com/publishing"
echo "    2. Click 'Publish Component' (top right)"
echo "    3. Drop the zip into the upload area"
echo "    4. Wait for validation → review the deployment"
echo "    5. Click 'Publish' to release to Maven Central"
echo ""
