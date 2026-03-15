#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATIONPLAYER_MANIFEST="$ROOT_DIR/vendor/stationplayer/Cargo.toml"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs"
NATIVE_LIB_NAME="libuniffi_stationplayer.so"

mkdir -p "$JNI_DIR/arm64-v8a" "$JNI_DIR/x86_64"
rm -f \
  "$JNI_DIR/arm64-v8a/libstationplayer.so" \
  "$JNI_DIR/x86_64/libstationplayer.so" \
  "$JNI_DIR/arm64-v8a/$NATIVE_LIB_NAME" \
  "$JNI_DIR/x86_64/$NATIVE_LIB_NAME"

if ! cargo ndk -h >/dev/null 2>&1; then
  echo "cargo-ndk is required to build Android libraries. Install it or use the provided nix shell." >&2
  exit 1
fi

# The Nix dev shell exports host compiler variables like CC=gcc and CXX=g++.
# Those override cargo-ndk's target-specific NDK toolchain environment and make
# cc-rs compile Android C/asm code with the host compiler.
unset \
  AR \
  AR_FOR_BUILD \
  AS \
  AS_FOR_BUILD \
  CC \
  CC_FOR_BUILD \
  CFLAGS \
  CXX \
  CXX_FOR_BUILD \
  CXXFLAGS \
  LD \
  LD_FOR_BUILD \
  LDFLAGS \
  RANLIB \
  RANLIB_FOR_BUILD \
  STRIP \
  STRIP_FOR_BUILD

cargo ndk \
  --platform 28 \
  --target arm64-v8a \
  --target x86_64 \
  build \
  --manifest-path "$STATIONPLAYER_MANIFEST" \
  --release

cp \
  "$ROOT_DIR/target/aarch64-linux-android/release/libstationplayer.so" \
  "$JNI_DIR/arm64-v8a/$NATIVE_LIB_NAME"
cp \
  "$ROOT_DIR/target/x86_64-linux-android/release/libstationplayer.so" \
  "$JNI_DIR/x86_64/$NATIVE_LIB_NAME"
