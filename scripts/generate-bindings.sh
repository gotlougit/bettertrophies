#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BINDINGS_MANIFEST="$ROOT_DIR/rust/bindings/Cargo.toml"
UNIFFI_CONFIG="$ROOT_DIR/vendor/stationplayer/uniffi.toml"
UDL_PATH="$ROOT_DIR/vendor/stationplayer/src/stationplayer.udl"
OUTPUT_DIR="${1:-$ROOT_DIR/build/generated/uniffi/java}"

mkdir -p "$OUTPUT_DIR"
rm -rf "$OUTPUT_DIR"/*

cargo run \
  --manifest-path "$BINDINGS_MANIFEST" \
  -- generate \
  "$UDL_PATH" \
  --crate stationplayer \
  --config "$UNIFFI_CONFIG" \
  --language kotlin \
  --no-format \
  --out-dir "$OUTPUT_DIR"
