buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${cash.z.ecc.android.Deps.kotlinVersion}")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:4.7.5")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:${cash.z.ecc.android.Deps.navigationVersion}")
    }
}

plugins {
    id("com.github.ben-manes.versions")
    id("zcash.ktlint-conventions")
}

defaultTasks("clean", "installZcashmainnetRelease")

tasks {
//    named<Delete>("clean") {
//        rootProject.buildDir.deleteRecursively()
//    }

    withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
        gradleReleaseChannel = "current"

        resolutionStrategy {
            componentSelection {
                all {
                    if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
                        reject("Unstable")
                    }
                }
            }
        }
    }
}

val unstableKeywords = listOf("alpha", "beta", "rc", "m", "ea", "build")

fun isNonStable(version: String): Boolean {
    val versionLowerCase = version.toLowerCase()

    return unstableKeywords.any { versionLowerCase.contains(it) }
}