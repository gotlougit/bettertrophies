# BetterTrophies

BetterTrophies is an Android app for viewing PlayStation profile data, trophy progress, and cloud captures.
It is meant to be a partial replacement for the official PlayStation app, focusing more on
keeping access to trophies and game captures rather than purchases etc.

## Features

- Load profile and trophy summary data.
- Browse trophy titles and drill into per-title trophies.
- Browse PlayStation cloud captures grouped by game.
- Cache dashboard/trophy/capture metadata locally for faster offline-first reloads.
- Cache capture assets locally, with save-to-gallery and share support.

## Roadmap

- Adding advanced trophy + game stats (eg. playtime, platform etc.)
- Export data
- Console storage management
- Chat support

## Build

1. Enter the Nix development shell:

```bash
nix develop
```

2. Build the debug APK (preBuild runs binding/native steps):

```bash
gradle :app:assembleDebug
```

3. Install on device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Upstream Dependency Metadata

- Vendored `stationplayer` source: `git.sr.ht/~gotlou/stationplayer`

## Acknowledgements

Some of the agent skills are taken directly from https://github.com/android/skills
