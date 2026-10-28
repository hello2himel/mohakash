import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.util.Properties

/**
 * Reads `config/site.json` from the repository root at *configuration time*.
 *
 * The file is the single source of truth for every owner-specific URL used by the
 * Android app (API base URLs, website, repository). Nothing is hard-coded in Kotlin.
 *
 * The lookup walks up from the Gradle root project directory so the build works no
 * matter where the checkout lives.
 */
val siteConfig: Map<String, Any?> = run {
    var dir: File? = rootProject.projectDir
    var found: File? = null
    while (dir != null) {
        val candidate = File(dir, "config/site.json")
        if (candidate.isFile) {
            found = candidate
            break
        }
        dir = dir.parentFile
    }
    checkNotNull(found) { "Could not find config/site.json above ${rootProject.projectDir}" }
    @Suppress("UNCHECKED_CAST")
    (JsonSlurper().parse(found) as Map<String, Any?>)
}

@Suppress("UNCHECKED_CAST")
val androidConfig = (siteConfig["android"] as Map<String, Any?>?) ?: emptyMap()
@Suppress("UNCHECKED_CAST")
val githubConfig = (siteConfig["github"] as Map<String, Any?>?) ?: emptyMap()
@Suppress("UNCHECKED_CAST")
val websiteConfig = (siteConfig["website"] as Map<String, Any?>?) ?: emptyMap()

val apiBaseUrlFallbacks: List<String> =
    (androidConfig["api_base_url_fallbacks"] as List<*>?)?.mapNotNull { it?.toString() }?.filter { it.isNotBlank() }
        ?: emptyList()

require(apiBaseUrlFallbacks.isNotEmpty()) { "config/site.json must define android.api_base_url_fallbacks" }

/** The permanent application ID shown by Play, F-Droid and Android Settings. */
val appId = androidConfig["package"]?.toString() ?: "mohakash.stargzr.space"
/** The Kotlin/Android source namespace. It deliberately does not change with the app ID. */
val namespaceName = androidConfig["namespace"]?.toString() ?: "app.mohakash.apod"
val githubOwner = githubConfig["owner"]?.toString() ?: "hello2himel"
val githubRepo = githubConfig["repo"]?.toString() ?: "mohakash"
val repositoryUrl = "https://github.com/$githubOwner/$githubRepo"
val defaultBranch = githubConfig["default_branch"]?.toString() ?: "main"
val contentRightsUrl = "$repositoryUrl/blob/$defaultBranch/CONTENT_RIGHTS.md"
val thirdPartyNoticesUrl = "$repositoryUrl/blob/$defaultBranch/THIRD_PARTY_NOTICES.md"
val websiteBaseUrl = websiteConfig["base_url"]?.toString() ?: repositoryUrl
val donationUrl = websiteConfig["donation_url"]?.toString()
    ?: "https://hello2himel.netlify.app/donate"
val websiteHost = runCatching { URI(websiteBaseUrl).host }.getOrNull()
    ?.takeIf { it.isNotBlank() }
    ?: error("website.base_url in config/site.json must be an absolute URL, got $websiteBaseUrl")

/**
 * Release version numbers are checked in so F-Droid and every other
 * reproducible builder get exactly the same values. CI may override them for
 * a one-off build without changing the source tree.
 */
val versionPropertiesFile = file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.isFile) {
        versionPropertiesFile.inputStream().use { load(it) }
    }
}
val appVersionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
    ?: versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: 1
val appVersionName = System.getenv("ANDROID_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: versionProperties.getProperty("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "1.0.0"

/**
 * Release signing is supplied either by environment variables (CI) or by the
 * git-ignored `android/keystore.properties` file (local builds). If neither is
 * present, `assembleRelease` still produces an unsigned APK for F-Droid-style
 * builders, but CI refuses to publish it.
 *
 * Never commit a keystore or its passwords. See RELEASING.md.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val signingValue: (String, String) -> String? = { envName, propertyName ->
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
}
val releaseStorePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val releaseSigningConfigured = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

if (!releaseSigningConfigured) {
    logger.lifecycle(
        "Release signing is not configured; release builds will be unsigned. " +
            "See android/keystore.properties.example.",
    )
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = namespaceName
    compileSdk = 36

    // CI restores a stable debug keystore so a freshly downloaded APK can be
    // installed over the previous build. Local builds without the environment
    // variable keep Gradle's default debug signing.
    signingConfigs {
        create("stableDebug") {
            val storePath = System.getenv("ANDROID_DEBUG_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("ANDROID_DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("ANDROID_DEBUG_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("ANDROID_DEBUG_KEY_PASSWORD") ?: "android"
            }
        }
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 36

        // fdroidserver's checkupdates parser only understands literal values
        // in this file. Keep these in sync with version.properties; the
        // appVersionCode/appVersionName assignments below are the actual
        // source of truth for builds and CI overrides.
        versionCode = 24
        versionName = "2026.10.27.17"

        versionCode = appVersionCode
        versionName = appVersionName

        // Used by the App Links intent filters. Keep it in sync with the website domain
        // without duplicating the owner-specific value in the manifest.
        manifestPlaceholders["websiteHost"] = websiteHost

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Comma separated list of candidate API base URLs, tried in order at runtime.
        buildConfigField(
            "String",
            "API_BASE_URL_FALLBACKS",
            "\"${apiBaseUrlFallbacks.joinToString(",")}\"",
        )
        buildConfigField("String", "REPOSITORY_URL", "\"$repositoryUrl\"")
        buildConfigField("String", "CONTENT_RIGHTS_URL", "\"$contentRightsUrl\"")
        buildConfigField("String", "THIRD_PARTY_NOTICES_URL", "\"$thirdPartyNoticesUrl\"")
        buildConfigField("String", "WEBSITE_BASE_URL", "\"$websiteBaseUrl\"")
        buildConfigField("String", "PRIVACY_URL", "\"$websiteBaseUrl/privacy\"")
        buildConfigField("String", "DONATE_URL", "\"$donationUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (!System.getenv("ANDROID_DEBUG_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                null
            }
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
        buildConfig = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.coil.compose)
    implementation(libs.coil.base)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
