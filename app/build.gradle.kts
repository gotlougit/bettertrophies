import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
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
val generatedBindingsDir = layout.buildDirectory.dir("generated/uniffi/java")

abstract class GenerateRustBindingsTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "rust"
        description = "Generates UniFFI Kotlin bindings from stationplayer."
    }

    @TaskAction
    fun generate() {
        execOperations.exec {
            workingDir = project.rootDir
            commandLine("bash", "./scripts/generate-bindings.sh", outputDir.get().asFile.absolutePath)
        }
    }
}

android {
    namespace = "dev.gotlou.bettertrophies"
    compileSdk = 36
    buildToolsVersion = "37.0.0"

    signingConfigs {
        create("release") {
            val keystoreDir = File(System.getProperty("user.home"), ".keystore")
            if (keystoreDir.resolve("release.jks").exists()) {
                storeFile = keystoreDir.resolve("release.jks")
                storePassword = keystoreDir.resolve("store_password").readText().trim()
                keyAlias = keystoreDir.resolve("key_alias").readText().trim()
                keyPassword = keystoreDir.resolve("key_password").readText().trim()
            }
        }
    }

    defaultConfig {
        applicationId = "dev.gotlou.bettertrophies"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["release"]
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // UniFFI output is regenerated during preBuild; linting it produces
        // non-actionable SDK warnings in generated code rather than app code.
        checkGeneratedSources = false
        lintConfig = file("lint.xml")
    }

    sourceSets.named("main") {
        jniLibs.directories.add("src/main/jniLibs")
    }
}

kotlin {
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_3
    }
}

val generateRustBindings by tasks.registering(GenerateRustBindingsTask::class) {
    outputDir.set(generatedBindingsDir)
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateRustBindings,
            GenerateRustBindingsTask::outputDir,
        )
    }
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
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation3:navigation3-runtime:1.1.0")
    implementation("androidx.navigation3:navigation3-ui:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("rustls:rustls-platform-verifier:${rustlsPlatformVerifierAndroidMetadata.artifactVersion}")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
