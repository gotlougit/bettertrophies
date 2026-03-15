# BetterTrophies

Android app scaffold for browsing PlayStation profile, recent titles, trophy titles, and trophies from an NPSSO token.

This project vendors the upstream `stationplayer` crate and consumes its UniFFI interface directly from the Android app.

Sources used:

- `stationplayer` vendored from `git.sr.ht/~gotlou/stationplayer` at commit `2c8ce1341a137ec70f52aab80f2243f323e36ad4`
- `https://sal.dev/android/intro-rust-android-uniffi/`

## Layout

- `Cargo.toml`: cargo workspace root for the binding generator helper
- `vendor/stationplayer/`: vendored PlayStation API crate plus its UniFFI surface
- `rust/bindings/`: tiny helper crate that runs `uniffi-bindgen`
- `app/`: Jetpack Compose Android client
- `scripts/`: binding generation and Android native library build scripts

## Build flow

1. Enter the Nix dev shell:

```bash
nix develop
```

2. Generate Kotlin bindings from the Rust bridge:

```bash
./scripts/generate-bindings.sh
```

3. Build Android native libraries and copy them into `app/src/main/jniLibs`:

```bash
./scripts/build-android-libs.sh
```

4. Build the Android app:

```bash
gradle :app:assembleDebug
```

The scripts expect `ANDROID_HOME` or `ANDROID_SDK_ROOT` to be set, which the included `flake.nix` already does inside `nix develop`.

The Nix shell uses `rust-overlay` so `cargo-ndk` has the Android Rust stdlibs required for `aarch64-linux-android` and `x86_64-linux-android`.
