#!/usr/bin/env bash
set -euo pipefail

# Extract PlayStation App metadata from the latest Android APK.
#
# Requires: uv, jadx (both available in the Nix dev shell).
#
# Usage:
#   ./extract.sh [OUTPUT_JSON]
#
#   OUTPUT_JSON defaults to metadata/app_constants.json.

OUTPUT="${1:-metadata/app_constants.json}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "[extract] downloading APK ..."
uv tool run gplaydl auth
uv tool run gplaydl download com.scee.psxandroid --no-extras

# Move APK into the temp work dir so jadx output stays contained.
APK_FILE="$(ls com.scee.psxandroid-*.apk 2>/dev/null || true)"
if [ -z "$APK_FILE" ]; then
    echo "ERROR: APK download failed – no .apk file found." >&2
    exit 1
fi
mv "$APK_FILE" "$WORK_DIR/app.apk"

echo "[extract] decompiling with jadx ..."
# This usually fails with some errors because jadx is not perfect
# we can ignore these, most of our interest is in native libs
jadx -d "$WORK_DIR/decompiled" "$WORK_DIR/app.apk" || true

echo "[extract] running credential extraction ..."
cp "$SCRIPT_DIR/extract_credentials.py" "$WORK_DIR/decompiled/"
(
    cd "$WORK_DIR/decompiled"
    uv run --with cryptography extract_credentials.py --json > "$WORK_DIR/extracted.json"
)

mkdir -p "$(dirname "$OUTPUT")"
mv "$WORK_DIR/extracted.json" "$OUTPUT"
echo "[extract] done → $OUTPUT"
