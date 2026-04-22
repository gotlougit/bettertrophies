{
  description = "Android and Rust development environment";

  inputs = {
    andy = {
      url = "github:maan2003/andy/prebuilt";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs/stable";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      android-nixpkgs,
      rust-overlay,
      andy,
    }:
    let
      supportedSystems = [
        "x86_64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      overlays = [ (import rust-overlay) ];
    in
    {
      packages = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system overlays;
            config.allowUnfree = true;
          };
        in
        {
          android-sdk = android-nixpkgs.sdk.${system} (
            sdkPkgs:
            with sdkPkgs;
            [
              build-tools-37-0-0
              cmdline-tools-latest
              platform-tools
              platforms-android-36
              emulator
            ]
            ++ pkgs.lib.optionals pkgs.stdenv.isLinux [
              ndk-26-1-10909125
            ]
          );
        }
      );

      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system overlays;
            config.allowUnfree = true;
          };
          gitCommitWrapper = pkgs.writeShellScriptBin "git-commit" ''
            if [ "$#" -eq 0 ]; then
              printf 'usage: git-commit <message>\n' >&2
              exit 1
            fi

            exec git commit -m "llm: $*"
          '';
          mkShell = pkgs.mkShell.override {
            stdenv = if pkgs.stdenv.isLinux then pkgs.stdenvAdapters.useMoldLinker pkgs.stdenv else pkgs.stdenv;
          };
          android-sdk = self.packages.${system}.android-sdk;
          rustToolchain = pkgs.rust-bin.stable.latest.default.override {
            targets = [
              "aarch64-linux-android"
              "x86_64-linux-android"
            ];
            extensions = [ "rust-src" ];
          };
        in
        {
          default = mkShell {
            name = "android-dev";

            ANDROID_HOME = "${android-sdk}/share/android-sdk";
            ANDROID_SDK_ROOT = "${android-sdk}/share/android-sdk";
            JAVA_HOME = pkgs.jdk17.home;
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${android-sdk}/share/android-sdk/build-tools/37.0.0/aapt2";

            shellHook = ''
              export CARGO_HOME="$(realpath ./.localcargo)"
              export _ZO_DATA_DIR="$(realpath ./.localzoxide)"
              export ANDY_PACKAGE="dev.gotlou.bettertrophies"
              export ANDY_PUT_RCE_ON_MY_PHONE="1"
            '';

            packages = [
              gitCommitWrapper
              android-sdk
              pkgs.cargo-ndk
              pkgs.gradle
              pkgs.jdk17
              pkgs.kotlin
              pkgs.pkg-config
              pkgs.openssl.dev
              pkgs.sqlite.dev
              rustToolchain
              pkgs.rust-analyzer
              pkgs.tree-sitter
              andy.packages.${system}.default
            ]
            ++ pkgs.lib.optionals pkgs.stdenv.isLinux [
              pkgs.gdb
            ]
            ++ pkgs.lib.optionals (pkgs.stdenv.isLinux && pkgs.stdenv.isx86_64) [
              pkgs.androidStudioPackages.stable
              pkgs.cargo-rr
            ];
          };
        }
      );
    };
}
