# SilentDragonXAndroid
A sample Android wallet using the [DragonX Android SDK](https://git.hush.is/dragonx/dragonx-android-wallet-sdk) which is based on the [Zcash Android SDK](https://github.com/zcash/zcash-android-wallet-sdk).

### Motivation
[Dogfooding](https://en.wikipedia.org/wiki/Eating_your_own_dog_food) - _transitive verb_ -  is the practice of an organization using its own product. This app was created to help us learn. 


# Disclaimers
There are some known areas for improvement:

- This app is mainly intended for learning and improving the related libraries that it uses. There may be bugs.
- Traffic analysis, like in other cryptocurrency wallets, can leak some privacy of the user.
- The wallet requires a trust in the server to display accurate transaction information. 

See the upstream Zcash [Wallet App Threat Model](https://zcash.readthedocs.io/en/latest/rtd_pages/wallet_threat_model.html)
for more information about the security and privacy limitations of the wallet.

If you'd like to help us test, reach out on [Telegram](https://hush.is/tg) or [Matrix](https://hush.is/matrix) and let us know! We're always happy to get feedback!

# Description
This a sample wallet for the following set of features:
- z2z transactions with encrypted memos
- reply-to formatted memos

# Prerequisites
- [The app code](https://git.hush.is/dragonx/SilentDragonXAndroid)
- [The SDK code](https://git.hush.is/dragonx/dragonx-android-wallet-sdk)
- [Android Studio](https://developer.android.com/studio/index.html) last tested with Android Studio Dolphin | 2021.3.1 Patch 1 or [adb](https://www.xda-developers.com/what-is-adb/)
- An Android device or emulator. Android 10 or higher is recommended.

# Building the App
1. Clone the SDK: 
```git clone https://git.hush.is/dragonx/dragonx-android-wallet-sdk.git```
2. Add any new checkpoints to the SDK - Checkpoints are located in ```dragonx-android-wallet-sdk\sdk-lib\src\main\assets\co.electriccoin.zcash\checkpoint\mainnet``` You may use the [sda_checkpoints.pl](https://git.hush.is/hush/hush3/src/branch/dev/contrib/sda_checkpoints.pl) script and modify the start and end block heights accordingly. 

    By default, this script will generate many checkpoints in a single file IE: ```./contrib/sda_checkpoints.pl > newcheckpoints.json```. You may then copy/paste to individual .json files named as the block height the checkpoint is for or modify this script to save individual .json files for each block height. 
3. Compile and publish the SDK locally as the app's code currently relies on mavenLocal for the SDK.
    ```
    ./gradlew clean
    ./gradlew build
    ./gradlew build publishToMavenLocal
    ```
4. Clone the app repo: 
```git clone https://git.hush.is/dragonx/SilentDragonXAndroid```
5. Open the app in Android Studio and press play to install and run on connected device. It should just work.™
6. If you want to build APKs instead of installing on a device or emulator, select Build > Build Bundle(s) / APK(s) > Build APK(s)

## Install from Android Studio
1. [Install Android Studio](https://developer.android.com/studio/install) and setup an emulator
    1a. If using a device, be sure to [put it in developer mode](https://developer.android.com/studio/debug/dev-options) to enable side-loading apps
2. `Import` the SilentDragonXAndroid folder.  
    It will be recognized as an Android project.
3. Press play (once it is done opening and indexing)

## OR Install from the command line
To build from the command line, [setup ADB](https://www.xda-developers.com/install-adb-windows-macos-linux/) and connect your device. Then simply run this and it will both build and install the app:
```bash
cd /path/to/SilentDragonXAndroid
./gradlew
```
Note: The lack of an explicit Gradle task is not a typo. A default task is configured via [build.gradle.kts](build.gradle.kts).

Tip: On macOS and Linux, Gradle is invoked with `./gradlew`.  On Windows, Gradle is invoked with `gradlew`.


# Included builds
These notes are included from upstream and for reference only if looking to create an included build. To simplify implementation of SDK features in conjunction with changes to the app, a Gradle [Included Build](https://docs.gradle.org/current/userguide/composite_builds.html) can be configured.

1. Check out the SDK to a directory path of `../dragonx-android-wallet-sdk` relative to the root of this app's repo.  For example:

        parent/
            SilentDragonXAndroid/
            dragonx-android-wallet-sdk/

1. Verify that the `dragonx-android-wallet-sdk` builds correctly on its own
1. Build `SilentDragonXAndroid`, setting the Gradle property `IS_SDK_INCLUDED_BUILD=true`

There are some limitations of included builds:
1. Properties from `SilentDragonXAndroid` will override those set in `dragonx-android-wallet-sdk` with the same name
1. Modules in each project cannot share the same name.  For this reason, build-conventions have different names in each repo (`dragonx-android-wallet-sdk/build-conventions` vs `other-android-wallet/build-convention`)
1. Kotlin and KSP versions will need to be coordinated between the two projects, because KSP is tightly coupled to the Kotlin version

# Contributing

Contributions are very much welcomed! Contact us on [Telegram](https://hush.is/tg) or [Matrix](https://hush.is/matrix) .

# Reporting an issue

Contact us on [Telegram](https://hush.is/tg) or [Matrix](https://hush.is/matrix)

# Copyright

Copyright 2016-2023 The Hush developers

# License

GPLv3
