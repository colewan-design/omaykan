pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/*
 * The Mapbox Maps SDK's download credential.
 *
 * A **secret** `sk.` token with the `DOWNLOADS:READ` scope, and a different
 * thing entirely from the public `pk.` one the app carries at runtime
 * (OMAYKAN_MAPBOX_TOKEN → BuildConfig.MAPBOX_TOKEN). This one is a build
 * credential: it is the password on a Maven repository, it never reaches the
 * APK, and it is what Gradle needs before it can resolve a single Mapbox
 * artifact.
 *
 * Two places to put it, in this order:
 *
 *   1. `OMAYKAN_MAPBOX_DOWNLOADS_TOKEN` in the environment — matches the rest
 *      of this build, and the right answer for CI.
 *   2. `MAPBOX_DOWNLOADS_TOKEN` in **`~/.gradle/gradle.properties`** — Mapbox's
 *      own documented route, and it survives across shells.
 *
 * Not, under any circumstances, this project's `gradle.properties`: that file
 * is tracked, and a secret token in it is a secret token in the history. The
 * home-directory one is outside the repository and cannot be committed by
 * accident.
 */
val mapboxDownloadsToken: String = run {
    val fromEnvironment =
        providers.environmentVariable("OMAYKAN_MAPBOX_DOWNLOADS_TOKEN").getOrElse("").trim()
    val fromProperties =
        providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").getOrElse("").trim()

    fromEnvironment.ifBlank { fromProperties }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        /*
         * Declared only when there is a token — and since :rider started
         * shipping the Maps SDK rather than only previewing it in a debug
         * screen, not having one is a build failure rather than a smaller
         * build.
         *
         * The check below is there to make that failure legible. Without it
         * the missing repository surfaces as "Could not find
         * com.mapbox.maps:android:11.14.0" with a list of the repositories
         * that were searched, which tells you everything except the one thing
         * you need to know: that this is a credential you have to go and get,
         * not a version that does not exist.
         *
         * Declaring it unconditionally with a blank password is the other
         * obvious fix and is worse — it answers 401, which reads as "your
         * token is wrong" to somebody who has not got one at all.
         *
         * FAIL_ON_PROJECT_REPOS above is why this has to live here rather than
         * in rider/build.gradle.kts — a repository declared in a module would
         * fail the build outright.
         */
        if (mapboxDownloadsToken.isBlank()) {
            logger.warn(
                """
                |
                |  No Mapbox downloads token, so :rider will not resolve.
                |
                |  It needs a secret `sk.` token with the DOWNLOADS:READ scope —
                |  a build credential, and a different thing from the public
                |  `pk.` one the app carries at runtime. Put it in either:
                |
                |    OMAYKAN_MAPBOX_DOWNLOADS_TOKEN  in the environment, or
                |    MAPBOX_DOWNLOADS_TOKEN          in ~/.gradle/gradle.properties
                |
                |  Not in this project's gradle.properties: that file is tracked.
                |
                """.trimMargin(),
            )
        }

        if (mapboxDownloadsToken.isNotBlank()) {
            maven("https://api.mapbox.com/downloads/v2/releases/maven") {
                authentication { create<BasicAuthentication>("basic") }

                credentials {
                    // Literally "mapbox". The token is the password.
                    username = "mapbox"
                    password = mapboxDownloadsToken
                }

                // Nothing but Mapbox is ever asked of this host. Without it
                // Gradle would try a metered, authenticated endpoint for every
                // unrelated artifact that missed the two repositories above.
                content { includeGroupByRegex("""com\.mapbox\..*""") }
            }
        }
    }
}

rootProject.name = "omaykan-storefront"

/*
 * Three apps, one build.
 *
 * `:app` is the shopper's storefront. `:seller` is the merchant's phone — the
 * small, specific thing documentation/mobile-plan.md §11 describes: incoming
 * orders and the handful of decisions a shop owner makes about them while
 * standing away from the till. `:rider` is the third side of the same order:
 * the job board, and the road between the shop and the door.
 *
 * They are separate applications rather than flavours of one because they are
 * three products with three audiences, three listings and three identities on
 * the phone — and because their credentials are genuinely different. `:app`
 * holds a customer token, `:seller` a device token, `:rider` a personal token
 * on its own Laravel guard. One app that switched between them would be one
 * app that could leak one into another's request.
 *
 * They share the version catalog and nothing else. That is deliberate for now:
 * factoring the theme and the network plumbing into a library module is a
 * refactor of a shipped app, and doing it before the newest app has settled
 * would be designing the shared layer from two examples and a guess. See
 * seller/README.md and rider/README.md.
 */
include(":app")
include(":seller")
include(":rider")
