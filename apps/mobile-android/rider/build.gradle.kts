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
 * Signed with the same keystore mechanism as :app and :seller — an untracked
 * keystore.properties at the root of this Gradle build, or wherever
 * KEYSTORE_PROPERTIES points. Absent, release goes unsigned rather than
 * failing: a debug-only checkout must still be able to build and test.
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
    namespace = "com.omaykan.rider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omaykan.rider"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // Same rule as the other two — debug talks to the live API unless
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
            /*
             * The same public `pk.*` token :seller uses, and the same rule: a
             * separate token for the mobile builds, scoped to styles, static
             * images and directions, so it can be rotated without touching the
             * website. Blank is a supported build — every job card falls back
             * to the text it showed before there was a map.
             */
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

// 17 rather than 21, for the same reason the other two pin it: Hilt's
// generated-source javac runs on the Gradle JVM and ignores a toolchain, so 21
// fails on any machine whose JAVA_HOME is 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/*
 * No Room, no Coil, and no Play Services.
 *
 * **No Room**, for the same reason :seller has none: a job board is the
 * server's answer to "what is available right now", and a cached one sends a
 * rider to a shop for an order somebody else took twenty minutes ago.
 *
 * **Coil, for two kinds of picture.** It arrived for one — the static route map
 * on a job card — at a point when this app displayed no photographs at all: the
 * registration screen *uploads* two documents and reads them back through the
 * platform's photo picker, which hands over a thumbnail of its own.
 *
 * As of 2026-09 a rider can put a photograph of themselves on their profile, so
 * Coil now also draws faces — in the canopy, on the account screen, and nowhere
 * else. See `core/designsystem/RiderAvatar.kt`, which falls back to the
 * initial-letter disc the app has always drawn: most riders will never upload
 * one, and that is a supported state rather than a gap.
 *
 * **No Play Services — but there is a Maps SDK now.** These were one decision
 * and they have come apart, which is worth being explicit about because the
 * comment here used to read "no Play Services, and so no Maps SDK".
 *
 * Mapbox is not Google: it needs no Play Services, no Google account on the
 * device, and works on the grey-market phones this whole app is shaped around.
 * So the reason to avoid *Google* Maps never applied to it, and the navigation
 * screen renders on-device now.
 *
 * What has not changed is MapHandoff. Turn-by-turn with voice, offline tiles
 * and live traffic is the rider's own navigation app's job, and this app still
 * hands the trip to it by intent rather than competing. What the embedded map
 * is for is the screen a rider glances at with the phone on the handlebars —
 * where they are on the road, and which way the next turn is.
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

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.security.crypto)

    // Coil, for exactly one image: the static route map on a job card. This app
    // shows no other picture — see core/map/StaticMap.kt for why that map is a
    // fetched PNG and not an embedded map SDK.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging)

    /*
     * The Maps SDK, and it ships now.
     *
     * This was `debugImplementation` for as long as the only caller was the
     * debug-source gallery. The navigation screen renders a real map on the
     * device now (JobRouteMap), so it is in src/main and the release APK
     * carries it.
     *
     * ## What that costs, stated plainly
     *
     * Roughly 25 MB of native renderer across the four ABIs, against a release
     * APK that was 1.7 MB. It is the single largest thing in this app by an
     * order of magnitude, and it buys the one thing a server-rendered picture
     * cannot do at any price: rotate under the rider, tilt, and follow.
     *
     * It also makes the secret `sk.` downloads token **mandatory** to build at
     * all — see settings.gradle.kts, which used to let a checkout without one
     * build fine and now cannot. That is the part that costs somebody else
     * time rather than bytes, so the failure there is spelled out rather than
     * left as an unresolved-dependency error.
     */
    implementation("com.mapbox.maps:android:11.14.0")

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
