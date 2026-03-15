import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

data class RustlsPlatformVerifierAndroidMetadata(
    val mavenRepositoryUrl: String,
)

fun readRustlsPlatformVerifierAndroidMetadata(rootDir: File): RustlsPlatformVerifierAndroidMetadata {
    val cargoMetadataProcess = ProcessBuilder(
        "cargo",
        "metadata",
        "--format-version",
        "1",
        "--locked",
        "--offline",
        "--quiet",
        "--manifest-path",
        File(rootDir, "vendor/stationplayer/Cargo.toml").absolutePath,
        "--filter-platform",
        "aarch64-linux-android",
    )
        .directory(rootDir)
        .start()
    val cargoMetadataOutput =
        cargoMetadataProcess.inputStream.bufferedReader().use { it.readText() }
    val cargoMetadataError =
        cargoMetadataProcess.errorStream.bufferedReader().use { it.readText() }
    check(cargoMetadataProcess.waitFor() == 0) {
        buildString {
            append("Failed to read Cargo metadata for rustls-platform-verifier-android.")
            if (cargoMetadataError.isNotBlank()) {
                append('\n')
                append(cargoMetadataError)
            }
            if (cargoMetadataOutput.isNotBlank()) {
                append('\n')
                append(cargoMetadataOutput)
            }
        }
    }

    val cargoMetadata = JsonSlurper().parseText(cargoMetadataOutput) as Map<*, *>
    val packages = cargoMetadata["packages"] as List<*>
    val androidPackage = packages
        .map { it as Map<*, *> }
        .firstOrNull { it["name"] == "rustls-platform-verifier-android" }
        ?: error("Cargo metadata did not contain rustls-platform-verifier-android")
    val manifestPath = androidPackage["manifest_path"] as String
    val mavenRepositoryUrl = File(manifestPath).parentFile.resolve("maven").toURI().toString()

    return RustlsPlatformVerifierAndroidMetadata(
        mavenRepositoryUrl = mavenRepositoryUrl,
    )
}

val rustlsPlatformVerifierAndroidMetadata =
    readRustlsPlatformVerifierAndroidMetadata(settingsDir)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(rustlsPlatformVerifierAndroidMetadata.mavenRepositoryUrl)
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }
}

rootProject.name = "BetterTrophies"
include(":app")
