import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

/*
 * Two consequences of the baseline profile plugin, handled once for all three
 * apps rather than three times.
 *
 * Both are about the build types it synthesises. Recording a profile needs a
 * release build that has not been through R8 — a profile is a list of method
 * names, and names R8 has already rewritten are names ART will never see — so
 * the plugin copies `release` into `nonMinifiedRelease` (and `benchmarkRelease`
 * for timing runs). Nothing below is a choice about how the apps are built;
 * both are repairs to variants none of the three build files know exist.
 *
 * Here rather than in each app because neither has anything to do with what
 * the storefront, the seller phone or the rider app *are*. See
 * baselineprofile/README.md.
 */
subprojects {

    /*
     * The synthesised build types inherit `release`'s signing config, which is
     * null on any checkout without the keystore — and an unsigned APK cannot be
     * installed, so recording a profile would be something only a release
     * machine could do.
     *
     * Neither variant ships. Debug-signing them costs nothing and keeps
     * `generateBaselineProfile` working on the same checkout that can only
     * `assembleDebug`, which is the rule each app's signing block is already
     * written to.
     */
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            buildTypes.configureEach {
                if (name != "release" && name.endsWith("Release")) {
                    signingConfig = signingConfigs.getByName("debug")
                }
            }
        }
    }

    /*
     * A patch over a Kotlin-Gradle-plugin defect that only the synthesised
     * variants trip.
     *
     * KGP hands `nonMinifiedRelease`'s Kotlin source set the inherited
     * `src/release/kotlin` directory as an *unresolved* Gradle Provider rather
     * than a path. Nothing notices until KSP reads the source roots back out,
     * gets Provider.toString() — the literal string `provider(?)` — resolves it
     * against the project directory, and dies on Windows, where `?` cannot be
     * in a filename:
     *
     *     Could not create task ':app:kspNonMinifiedReleaseKotlin'.
     *     > Illegal char <?> at index 44: ...\app\provider(?)
     *
     * `release` itself is unaffected, which is why `assembleRelease` has always
     * been fine and only `generateBaselineProfile` hits this.
     *
     * Substituted rather than dropped: none of the three apps has a
     * `src/release/` today, so discarding the entry would work and would
     * quietly stop compiling that directory on the day somebody adds one.
     * Gradle is content with a source directory that is not there.
     *
     * Removable once KGP resolves the provider before handing it over. The
     * guard is self-clearing — it does nothing when no such entry is found.
     */
    plugins.withId("org.jetbrains.kotlin.android") {
        afterEvaluate {
            extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
                sourceSets.configureEach {
                    val unresolved = kotlin.srcDirs.filter { it.name == "provider(?)" }

                    if (unresolved.isNotEmpty()) {
                        kotlin.setSrcDirs(
                            kotlin.srcDirs - unresolved.toSet() +
                                project.file("src/release/kotlin"),
                        )
                    }
                }
            }
        }
    }
}
