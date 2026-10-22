import groovy.json.JsonSlurper
import java.io.File

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

val packageName = androidConfig["package"]?.toString() ?: "app.mohakash.apod"
val githubOwner = githubConfig["owner"]?.toString() ?: "hello2himel"
val githubRepo = githubConfig["repo"]?.toString() ?: "mohakash"
val repositoryUrl = "https://github.com/$githubOwner/$githubRepo"
val contentRightsUrl = "$repositoryUrl/blob/master/CONTENT_RIGHTS.md"
val websiteBaseUrl = websiteConfig["base_url"]?.toString() ?: repositoryUrl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = packageName
    compileSdk = 36

    defaultConfig {
        applicationId = packageName
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Comma separated list of candidate API base URLs, tried in order at runtime.
        buildConfigField(
            "String",
            "API_BASE_URL_FALLBACKS",
            "\"${apiBaseUrlFallbacks.joinToString(",")}\"",
        )
        buildConfigField("String", "REPOSITORY_URL", "\"$repositoryUrl\"")
        buildConfigField("String", "CONTENT_RIGHTS_URL", "\"$contentRightsUrl\"")
        buildConfigField("String", "WEBSITE_BASE_URL", "\"$websiteBaseUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
