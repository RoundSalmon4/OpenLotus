plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/version.gradle.kts"))

val appVersionMajor = extra["appVersionMajor"] as Int
val appVersionMinor = extra["appVersionMinor"] as Int
val appVersionPatch = extra["appVersionPatch"] as Int
val appVersionCode = extra["appVersionCode"] as Int
val versionOverride = providers.gradleProperty("versionOverride").orNull
val appVersionName = versionOverride ?: "$appVersionMajor.$appVersionMinor.$appVersionPatch"

android {
    namespace = "com.openlotus.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openlotus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildFeatures {
        compose = true
    }

    lint {
        // We build release on demand in CI and do not want lint to trip it up.
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        // Unit tests run on the JVM, so stub the android.util.Log calls.
        unitTests.isReturnDefaultValues = true
    }

    val releaseStore = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (!releaseStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
