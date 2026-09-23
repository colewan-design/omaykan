import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.omaykan.seller.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // 28, where :seller is 26 — the floor for capturing a profile, not for
        // using one. See baselineprofile/README.md.
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":seller"

    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            allDevices {
                // The same emulator the storefront's profile is recorded on,
                // and the same name, so all three share one AVD and one
                // download. Why AOSP and API 34: baselineprofile/README.md.
                create<ManagedVirtualDevice>("profileEmulator") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp"

                    // Stated, not defaulted: AGP 9 flips it to arm64-v8a.
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

/*
 * Hand the test the application id of the build it is driving. The variant
 * under test is `nonMinifiedRelease`, which the plugin synthesises, so its id
 * is not something this file can state.
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

    // Never a device that happens to be plugged in: a capture on an unrooted
    // phone degrades rather than failing, and would land in the repository
    // looking exactly like a good one.
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
