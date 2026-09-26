import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle.plugin)
}

secrets {
    // Real keys go in local.properties (gitignored, never committed).
    // Missing keys fall back to the placeholder values in local.defaults.properties.
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

// Release signing credentials live in a properties file OUTSIDE this repo
// entirely (never local.properties, never anything gitignored-but-adjacent) -
// see ~/.android-release-keys/keystore.properties. Overridable via
// VELOCITYSUITES_RELEASE_KEYSTORE_PROPERTIES for a different machine/CI.
// Debug/test/lint tasks must never depend on this file existing - only
// release-signing tasks are ever gated on it (see the release buildType
// block and the afterEvaluate check below).
val releaseKeystorePropertiesPath: String =
    System.getenv("VELOCITYSUITES_RELEASE_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android-release-keys/keystore.properties"
val releaseKeystorePropertiesFile = file(releaseKeystorePropertiesPath)
val hasReleaseSigning = releaseKeystorePropertiesFile.exists()
val releaseKeystoreProperties = Properties().apply {
    if (hasReleaseSigning) {
        FileInputStream(releaseKeystorePropertiesFile).use { load(it) }
    }
}

// Build identification (see ProfileManagementActivity's About section) - best-effort
// only. A build must never fail just because git isn't on PATH or this isn't a git
// checkout at all (e.g. a CI artifact export) - "unknown" is a safe, honest fallback,
// never a build failure. Recomputed on every Gradle configuration pass, so it always
// reflects the tree actually being built, not a stale cached value.
val gitCommitHash: String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0 && output.isNotEmpty()) output else "unknown"
} catch (e: Exception) {
    "unknown"
}
val buildTimestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

android {
    namespace = "com.example.velocitysuites"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.velocitysuites"
        minSdk = 29
        targetSdk = 36
        // Versioning policy (see project docs / commit history): versionCode must
        // monotonically increase for every distributed build, never reset or decrease -
        // it's the only reliable way to tell a stale install from the current one, since
        // Android itself (dumpsys/PackageManager) reports it regardless of what the app's
        // own UI displays. versionName is the human-facing "1.1.0"-style label; bump its
        // patch/minor/major segment per normal semver judgement, versionCode always by
        // exactly 1 regardless of how big the versionName jump is.
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitHash\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTimestamp\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Deliberately no fallback to the debug signingConfig here - if
            // hasReleaseSigning is false, this buildType simply has no
            // signingConfig at all, and assembleRelease/bundleRelease will
            // fail loudly instead (see the afterEvaluate check below),
            // rather than silently shipping a debug-signed or unsigned
            // "release" artifact.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

// Fail the build LOUDLY - not with a silently unsigned/debug-signed
// "release" artifact - when a release-signing-relevant task actually runs
// without release signing configured. Debug/test/lint tasks are completely
// unaffected regardless of whether releaseKeystorePropertiesFile exists.
gradle.taskGraph.whenReady {
    val requestedReleaseSigningTask = allTasks.any { task ->
        task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
    }
    if (requestedReleaseSigningTask && !hasReleaseSigning) {
        throw GradleException(
            "Release signing credentials not found at " +
                "$releaseKeystorePropertiesPath (or \$VELOCITYSUITES_RELEASE_KEYSTORE_PROPERTIES). " +
                "Refusing to build an unsigned or debug-signed release artifact."
        )
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.glide)
    implementation(libs.play.services.maps)
    implementation(libs.work.runtime)
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.55")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}