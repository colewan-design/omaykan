import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/*
 * Signed with the same keystore mechanism as :app — an untracked
 * keystore.properties at the root of this Gradle build, or wherever
 * KEYSTORE_PROPERTIES points. Absent, release goes unsigned rather than
 * failing: a debug-only checkout must still be able to build and test.
 *
 * The *key* inside it may well want to be a different one from the
 * storefront's, since these publish as two separate apps. That is a release
 * decision, not a build one, so it is left to whoever fills the file in.
 */
val keystoreProperties = Properties().apply {
    val path = providers.environmentVariable("KEYSTORE_PROPERTIES").orNull
    val file = if (path != null) File(path) else rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/*
 * A build-time value that is not in the repository: the Mapbox runtime token,
 * the Google client ids, an API base URL for a laptop on the same LAN.
 *
 * The environment first, because that is what CI sets and what a one-off
 * `FOO=bar ./gradlew ...` expects to win. Then `~/.gradle/gradle.properties`,
 * which is the half that was missing: an environment variable lives exactly as
 * long as the shell that exported it, so every one of these had to be
 * remembered and re-exported before every build, and a forgotten one is not an
 * error — it is a silently degraded APK. Blank stays a supported build; the
 * point is only that it should be a decision rather than an accident.
 *
 * Home directory, never this project's `gradle.properties`: that file is
 * tracked, and a token in it is a token in the history.
 */
fun buildSecret(name: String, default: String = ""): String {
    val fromEnvironment = providers.environmentVariable(name).getOrElse("").trim()
    val fromProperties = providers.gradleProperty(name).getOrElse("").trim()

    return fromEnvironment.ifBlank { fromProperties }.ifBlank { default }
}

android {
    namespace = "com.omaykan.seller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omaykan.seller"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /*
         * The Android OAuth client id for Sign in with Google.
         *
         * Its own client, and not the one :app uses: the Google console keys an
         * Android client on the package name and the signing certificate, and
         * this is a different package. That also means debug and release need
         * two of them — `applicationIdSuffix = ".debug"` and the debug keystore
         * make the debug build a different app as far as Google is concerned.
         * Every id in use must be listed on the backend
         * (GOOGLE_ANDROID_CLIENT_ID and GOOGLE_EXTRA_CLIENT_IDS) or the token
         * is refused as "issued for a different app".
         *
         * Empty is a supported state, not a broken one: the Google button
         * simply does not appear, and a username and password reach every
         * account that has one. There is no client secret to leak here; the app
         * is a public client and PKCE is what protects the exchange. See
         * documentation/google-sign-in.md.
         */
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_CLIENT_ID",
            "\"${providers.environmentVariable("OMAYKAN_SELLER_GOOGLE_ANDROID_CLIENT_ID").getOrElse("")}\"",
        )
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = File(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Same rule as :app — debug talks to the live API unless
            // OMAYKAN_API_BASE_URL says otherwise (http://10.0.2.2:8000 is
            // `php artisan serve` as the emulator sees the host loopback).
            // Cleartext to either needs network_security_config.xml.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${buildSecret("OMAYKAN_API_BASE_URL", default = "https://omaykan.com")}\"",
            )
            buildConfigField(
                "String",
                "MAPBOX_TOKEN",
                "\"${buildSecret("OMAYKAN_MAPBOX_TOKEN")}\"",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://omaykan.com\"")
            buildConfigField(
                "String",
                "MAPBOX_TOKEN",
                "\"${buildSecret("OMAYKAN_MAPBOX_TOKEN")}\"",
            )
            signingConfig = signingConfigs.findByName("release")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// 17 rather than 21, for the same reason :app pins it: Hilt's generated-source
// javac runs on the Gradle JVM and ignores a toolchain, so 21 fails on any
// machine whose JAVA_HOME is 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/*
 * No Room, and no Coil.
 *
 * Not an omission. This app holds nothing worth a database — an order list is
 * the server's answer to "what is happening right now", and a cached one is a
 * lie a merchant would act on. And it shows no product photos: the seller
 * already knows what they sell, and the screen's job is names, counts and
 * money.
 */
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Custom Tabs, for Sign in with Google. The consent page opens in the
    // phone's own browser rather than a WebView or a Play Services SDK — see
    // core/auth/GoogleAuthFlow.kt.
    implementation(libs.androidx.browser)

    // Coil, for exactly one image: the static delivery map in the rider sheet.
    // No other screen in this app shows a picture — see core/map/StaticMap.kt
    // for why that map is a fetched PNG rather than an embedded map SDK.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
