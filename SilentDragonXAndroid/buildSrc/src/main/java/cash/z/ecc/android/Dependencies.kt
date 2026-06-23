package cash.z.ecc.android

object Deps {
    // For use in the top-level build.gradle which gives an error when provided
    // `Deps.Kotlin.version` directly
    const val kotlinVersion =       "1.7.20"
    const val navigationVersion =   "2.5.2"

    const val compileSdkVersion =   33  // bumped 31->33 so the app can use Jetpack Compose Material3 1.0.x (CI already installs platforms;android-33; SDK module already compiles against 33). targetSdk stays 30.
    const val minSdkVersion =       21
    const val targetSdkVersion =    30
    const val versionName =         "1.6.11"
    const val versionCode =         1_06_11_800  // last digits are alpha(0XX) beta(2XX) rc(4XX) release(8XX). Ex: 1_08_04_401 is an release candidate build of version 1.8.4 and 1_08_04_800 would be the final release.
    const val packageName =         "dragonx.android"


    object AndroidX {
        const val ANNOTATION =              "androidx.annotation:annotation:1.3.0-alpha01"
        const val APPCOMPAT =               "androidx.appcompat:appcompat:1.4.0-alpha02"
        const val BIOMETRICS =              "androidx.biometric:biometric:1.2.0-alpha03"
        const val CONSTRAINT_LAYOUT =       "androidx.constraintlayout:constraintlayout:2.1.0-beta02"
        const val CORE_KTX =                "androidx.core:core-ktx:1.6.0"
        const val FRAGMENT_KTX =            "androidx.fragment:fragment-ktx:1.3.6"
        const val LEGACY =                  "androidx.legacy:legacy-support-v4:1.0.0"
        const val MULTIDEX =                "androidx.multidex:multidex:2.0.1"
        const val PAGING =                  "androidx.paging:paging-runtime-ktx:2.1.2"
        const val RECYCLER =                "androidx.recyclerview:recyclerview:1.2.1"

        object Lifecycle :       Version("2.4.0-alpha02") {
            val LIFECYCLE_RUNTIME_KTX =     "androidx.lifecycle:lifecycle-runtime-ktx:$version"
        }
        object Navigation :     Version(navigationVersion) {
            val FRAGMENT_KTX =              "androidx.navigation:navigation-fragment-ktx:$version"
            val UI_KTX =                    "androidx.navigation:navigation-ui-ktx:$version"
        }
        object Room :           Version("2.3.0") {
            val ROOM_COMPILER =             "androidx.room:room-compiler:$version"
            val ROOM_KTX =                  "androidx.room:room-ktx:$version"
        }
    }

    object Google {
        // solves error: Duplicate class com.google.common.util.concurrent.ListenableFuture found in modules jetified-guava-26.0-android.jar (com.google.guava:guava:26.0-android) and listenablefuture-1.0.jar (com.google.guava:listenablefuture:1.0)
        // per this recommendation from Chris Povirk, given guava's decision to split ListenableFuture away from Guava: https://groups.google.com/d/msg/guava-discuss/GghaKwusjcY/bCIAKfzOEwAJ
        const val GUAVA =                   "com.google.guava:guava:27.0.1-android"
        const val MATERIAL =                "com.google.android.material:material:1.4.0-rc01"
    }
    object Grpc :               Version("1.37.0") {
        val ANDROID =                       "io.grpc:grpc-android:$version"
        val OKHTTP =                        "io.grpc:grpc-okhttp:$version"
        val PROTOBUG =                      "io.grpc:grpc-protobuf-lite:$version"
        val STUB =                          "io.grpc:grpc-stub:$version"
    }
    object Analytics { // for dogfooding/crash-reporting/feedback only on internal team builds
        val BUGSNAG =                       "com.bugsnag:bugsnag-android:5.9.4"
        val MIXPANEL =                      "com.mixpanel.android:mixpanel-android:5.6.3"
    }
    object JavaX {
        const val INJECT =                   "javax.inject:javax.inject:1"
        const val JAVA_ANNOTATION =          "javax.annotation:javax.annotation-api:1.3.2"
    }
    object Kotlin :             Version(kotlinVersion) {
        val STDLIB =                         "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$version"
        val REFLECT =                        "org.jetbrains.kotlin:kotlin-reflect:$version"
        object Coroutines :     Version("1.6.4") {
            val ANDROID =                    "org.jetbrains.kotlinx:kotlinx-coroutines-android:$version"
            val CORE =                       "org.jetbrains.kotlinx:kotlinx-coroutines-core:$version"
            val TEST =                       "org.jetbrains.kotlinx:kotlinx-coroutines-test:$version"
        }
    }
    object Zcash {
        const val ANDROID_WALLET_PLUGINS =   "com.github.zcash:zcash-android-wallet-plugins:1.0.0"
        const val KOTLIN_BIP39 =             "cash.z.ecc.android:kotlin-bip39:1.0.4"

        /*  SDK uses mavenLocal build with DRGX customizations for now
            Run the following from Android SDK path to publish SDK locally
            ./gradlew clean
            ./gradlew build
            ./gradlew build publishToMavenLocal
         */
        const val SDK =                      "hush.android:hush-android-sdk:1.9.1-beta01-SNAPSHOT"
    }
    object Misc {
        const val LOTTIE =                   "com.airbnb.android:lottie:3.7.0"
        const val CHIPS =                    "com.github.gmale:chips-input-layout:2.3.4"
        object Plugins {
            const val SECURE_STORAGE =       "com.github.gmale:secure-storage-android:0.0.3"//"de.adorsys.android:securestoragelibrary:1.2.2"
            const val QR_SCANNER =           "com.google.zxing:core:3.4.1"
        }
    }

    object Test {
        const val JUNIT =                    "junit:junit:4.13.2"
        const val MOKITO =                   "org.mockito:mockito-android:3.12.4"
        const val MOKITO_KOTLIN =            "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
        object Android {
            const val CORE =                 "androidx.test:core:1.4.0"
            const val RULES =                "androidx.test:rules:1.4.0"
            const val JUNIT =                "androidx.test.ext:junit:1.1.3"
            const val FRAGMENT =             "androidx.fragment:fragment-testing:1.4.0-alpha08"
            const val ESPRESSO =             "androidx.test.espresso:espresso-core:3.4.0"
            const val ESPRESSO_INTENTS =     "androidx.test.espresso:espresso-intents:3.4.0"
            const val NAVIGATION =           "androidx.navigation:navigation-testing:2.3.0-alpha01"
        }
    }
}

open class Version(@JvmField val version: String)

