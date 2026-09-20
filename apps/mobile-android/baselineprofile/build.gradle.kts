import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.omaykan.storefront.baselineprofile"
    compileSdk = 36

    defaultConfig {
        /*
         * 28, where `:app` is 26.
         *
         * This is the floor for capturing a profile at all — the ART tooling
         * the run leans on does not exist below P. It is a floor on the
         * machine that *records* the profile, not on the phones that get to
         * use it: the recorded list is just names, and every install from 26
         * up reads it.
         */
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The storefront is what gets driven, and what the profile is written back
    // into. :seller and :rider would each need their own module — a profile is
    // a list of methods in one APK and cannot be shared.
    targetProjectPath = ":app"

    /*
     * Required by macrobenchmark, and the reason this is `com.android.test`
     * rather than an androidTest source set in `:app`: the test process must
     * be a separate process from the app it measures, or it would be measuring
     * a JVM it is itself warming up.
     */
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            allDevices {
                /*
                 * An emulator Gradle downloads and throws away, so generating
                 * a profile needs no phone plugged in and works the same on
                 * CI as on a laptop.
                 *
                 * `aosp`, not `google`: the run has to be root to read ART's
                 * profile back out, and the Play-flavoured images are not
                 * rootable. API 34 rather than the newest — it is the image
                 * with the most road behind it for this, and the profile is
                 * not version-specific enough for the difference to matter.
                 */
                create<ManagedVirtualDevice>("profileEmulator") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp"

                    /*
                     * Stated rather than left to the default, which AGP 9
                     * changes from x86_64 to arm64-v8a. Silence is not a
                     * choice here: on this build the flip would either start
                     * translating every test through NDK translation or stop
                     * the device running at all, depending on the machine.
                     */
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

/*
 * Hand the test the application id of the build it is driving.
 *
 * The variant under test is `nonMinifiedRelease`, which the plugin
 * synthesises, so its id is not something this file can state — it is
 * whatever `:app` resolved. Reading it off the built APK keeps the one fact
 * in one place; StorefrontProfile falls back to the release id if the
 * argument is ever absent.
 */
androidComponents {
    onVariants { variant ->
        val artifacts = variant.artifacts.getBuiltArtifactsLoader()

        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifacts.load(it)?.applicationId ?: "" },
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    managedDevices += "profileEmulator"

    /*
     * Never a device that happens to be plugged in.
     *
     * A profile recorded on somebody's unrooted daily phone is silently a
     * worse profile — the capture degrades rather than failing — and it would
     * land in the repository looking exactly like a good one. The emulator
     * above is the only thing that records, so what gets committed is the same
     * artefact whoever ran it.
     */
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
