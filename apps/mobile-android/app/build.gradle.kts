import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

/*
 * Release signing lives in an untracked keystore.properties beside this file,
 * or wherever KEYSTORE_PROPERTIES points. Absent, the release variant simply
 * goes unsigned rather than failing the build: a debug-only checkout must
 * still be able to run `assembleDebug` and the whole test suite.
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
    namespace = "com.omaykan.storefront"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omaykan.storefront"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /*
         * The shop the front page is the front page *of*.
         *
         * The web landing page is pinned to one tenant at build time
         * (VITE_POS_ORGANIZATION_SLUG / VITE_POS_STORE_CODE in
         * apps/web/.env.production) and its shelves are that shop's catalog.
         * The app mirrors it, from the same values, so the two front pages
         * cannot drift apart by accident. The shop directory underneath is
         * what reaches every other shop.
         */
        buildConfigField(
            "String",
            "DEFAULT_ORG_SLUG",
            "\"${providers.environmentVariable("OMAYKAN_ORG_SLUG").getOrElse("sm")}\"",
        )
        buildConfigField(
            "String",
            "DEFAULT_STORE_CODE",
            "\"${providers.environmentVariable("OMAYKAN_STORE_CODE").getOrElse("main")}\"",
        )

        /*
         * The Android OAuth client id for Sign in with Google.
         *
         * Its own client, separate from the storefront's Web one, because the
         * Google console keys an Android client on the package name and the
         * signing certificate — which is also why debug and release need two of
         * them: `applicationIdSuffix = ".debug"` and the debug keystore make the
         * debug build a different app as far as Google is concerned. Both ids
         * must be listed on the backend (GOOGLE_ANDROID_CLIENT_ID and
         * GOOGLE_EXTRA_CLIENT_IDS) or the token is refused as "issued for a
         * different app".
         *
         * Empty is a supported state, not a broken one: the sign-in button
         * simply does not appear, and guest checkout — which is the whole
         * point of §5 fact 3 — is untouched. There is no client secret to
         * leak here; the app is a public client and PKCE is what protects the
         * exchange. See documentation/google-sign-in.md.
         */
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_CLIENT_ID",
            "\"${buildSecret("OMAYKAN_GOOGLE_ANDROID_CLIENT_ID")}\"",
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
            // Debug talks to the live API by default, so a debug build is
            // useful on any device without a backend running next to it.
            // Overridable per-machine: put OMAYKAN_API_BASE_URL in your
            // environment to aim at something else — http://10.0.2.2:8000 for
            // `php artisan serve` as the emulator sees the host loopback, or a
            // laptop's LAN address for a physical phone. Cleartext to either
            // needs a matching entry in network_security_config.xml.
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
            /*
             * Its own Google client, and this override is the whole reason the
             * debug build could never sign anybody in.
             *
             * `applicationIdSuffix = ".debug"` plus the debug keystore make this
             * a different app as far as Google is concerned, so it needs the
             * second Android client — which documentation/google-sign-in.md has
             * always said to set as OMAYKAN_GOOGLE_ANDROID_CLIENT_ID_DEBUG, and
             * which nothing here ever read. The release id was compiled into
             * both builds, and Google refused the debug one's token as issued
             * for a different app.
             *
             * Falls back to the release id when unset, which is the old
             * behaviour and is right for a debug build of a release-signed app.
             */
            buildConfigField(
                "String",
                "GOOGLE_OAUTH_CLIENT_ID",
                "\"${
                    buildSecret(
                        "OMAYKAN_GOOGLE_ANDROID_CLIENT_ID_DEBUG",
                        default = buildSecret("OMAYKAN_GOOGLE_ANDROID_CLIENT_ID"),
                    )
                }\"",
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

/*
 * Where the recorded profile lands, and why it is committed.
 *
 * `saveInSrc` writes it into src/main/generated/baselineProfiles/ as tracked
 * source rather than leaving it in build/. That is the whole point: a release
 * built on a machine with no emulator — CI, or a laptop — still ships the
 * profile, because the profile is a file in the repository and not something
 * the release build goes and earns. `automaticGenerationDuringBuild` stays off
 * for the same reason; booting an emulator inside `assembleRelease` would turn
 * a two-minute build into a twenty-minute one.
 *
 * Regenerating it is therefore a deliberate act. See baselineprofile/README.md.
 */
baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

/*
 * Bytecode level 17, not 21.
 *
 * Not a toolchain: Hilt's generated-source compilation (hiltJavaCompileDebug)
 * runs on the Gradle JVM and ignores a Java toolchain, so pinning 21 there only
 * fails as "invalid source release: 21" on any machine whose JAVA_HOME is 17.
 * 17 is the floor AGP 8.13 needs anyway and builds identically on either JDK.
 * Android desugars regardless, so nothing is given up by not asking for 21.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room's generated schemas are checked in: a migration written against a schema
// nobody recorded is a migration nobody can test.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


/*
 * Firebase, for order notifications — only when app/google-services.json is
 * present. Each developer downloads their own from the Firebase console, and a
 * checkout without one still builds and runs; it just never registers for
 * push (see PushRegistrar).
 *
 * A variant whose package the file does not list is skipped rather than
 * failing the build. The debug build installs as `.debug`, which is a separate
 * app in the Firebase console: until it is added there, debug builds run
 * without push instead of refusing to build.
 */
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)

    @Suppress("UNCHECKED_CAST")
    val registeredPackages = ((JsonSlurper().parse(googleServicesJson) as Map<String, Any?>)["client"] as List<Map<String, Any?>>)
        .mapNotNull { client ->
            ((client["client_info"] as? Map<String, Any?>)
                ?.get("android_client_info") as? Map<String, Any?>)
                ?.get("package_name") as? String
        }
        .toSet()

    androidComponents.onVariants { variant ->
        if (variant.applicationId.get() !in registeredPackages) {
            val task = "process${variant.name.replaceFirstChar { it.uppercase() }}GoogleServices"
            tasks.matching { it.name == task }.configureEach { enabled = false }
            logger.warn(
                "google-services.json has no client for ${variant.applicationId.get()}; " +
                    "the ${variant.name} build will run without push notifications.",
            )
        }
    }
}

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

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging)

    /*
     * The runtime half of the baseline profile. Without it the profile sits
     * in the APK unread: this is the code that hands it to ART on first run,
     * on installs that did not come from Play.
     */
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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
