import groovy.json.JsonSlurper
import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

data class RustlsPlatformVerifierAndroidMetadata(
    val artifactVersion: String,
)

fun Project.readRustlsPlatformVerifierAndroidMetadata(): RustlsPlatformVerifierAndroidMetadata {
    val cargoMetadataProcess = ProcessBuilder(
        "cargo",
        "metadata",
        "--format-version",
        "1",
        "--locked",
        "--offline",
        "--quiet",
        "--manifest-path",
        rootDir.resolve("vendor/stationplayer/Cargo.toml").absolutePath,
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
    val rustlsPlatformVerifierAndroidPackage = packages
        .map { it as Map<*, *> }
        .firstOrNull { it["name"] == "rustls-platform-verifier-android" }
        ?: error("Cargo metadata did not contain rustls-platform-verifier-android")

    return RustlsPlatformVerifierAndroidMetadata(
        artifactVersion = rustlsPlatformVerifierAndroidPackage["version"] as String,
    )
}

val rustlsPlatformVerifierAndroidMetadata = project.readRustlsPlatformVerifierAndroidMetadata()

android {
    namespace = "dev.gotlou.bettertrophies"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.gotlou.bettertrophies"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets.named("main") {
        java.srcDir("src/generated/java")
        jniLibs.srcDir("src/main/jniLibs")
    }
}

val generateRustBindings by tasks.registering(Exec::class) {
    group = "rust"
    description = "Generates UniFFI Kotlin bindings from stationplayer."
    workingDir = rootDir
    commandLine("bash", "./scripts/generate-bindings.sh")
}

val buildRustAndroidLibs by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds Android stationplayer libraries and copies them into jniLibs."
    workingDir = rootDir
    commandLine("bash", "./scripts/build-android-libs.sh")
}

tasks.named("preBuild") {
    dependsOn(generateRustBindings)
    dependsOn(buildRustAndroidLibs)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("rustls:rustls-platform-verifier:${rustlsPlatformVerifierAndroidMetadata.artifactVersion}")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
