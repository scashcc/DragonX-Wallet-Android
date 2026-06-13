pluginManagement {
    repositories {
        gradlePluginPortal()
    }

    plugins {
        id("com.github.ben-manes.versions") version ("0.39.0") apply (false)
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        val isRepoRestrictionEnabled = false

        mavenLocal()
        google()
        mavenCentral()
        maven("https://jitpack.io")

        /*
        // Uncomment to use a snapshot version of the SDK, e.g. when the SDK version ends in -SNAPSHOT
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            if (isRepoRestrictionEnabled) {
                content {
                    includeGroup("cash.z.ecc.android")
                }
            }
        }*/
    }
}

rootProject.name = "ecc-wallet"

includeBuild("build-convention")

include(":app")
include(":qrecycler")
include(":feedback")
include(":mnemonic")
include(":lockbox")

if (extra["IS_SDK_INCLUDED_BUILD"].toString().toBoolean()) {
    // Currently assume the SDK is up one level with a hardcoded directory name
    // If this becomes problematic, `IS_SDK_INCLUDED_BUILD` could be turned into a path
    // instead.
    includeBuild("../zcash-android-sdk") {
        dependencySubstitution {
            substitute(module("cash.z.ecc.android:zcash-android-sdk")).using(project(":sdk-lib"))
        }
    }
}
