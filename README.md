# BoringdroidSystemUI

The BoringdroidSystemUI is a pc-style SystemUI implementation that uses 
[SystemUI plugin](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/plugin/)
to hook itself to SystemUI.

It also uses 
[SystemUI SharedLib](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/shared/)
to receive the task changed events from system.

We provide gradle build script to build app with gradle, and develop it with Android Studio. It uses the keystore
generated from AOSP debug key, and it will help to install debug app from Android Studio to Android.

`build.gradle` use jars of above library to remove system API dependency, and
built this project directly and separately. The jars are built from system, so we should update them
when we upgrade AOSP.

## Update system library dependencies for new AOSP version

Execute the following commands to build library files:

```shell
source build/envsetup.sh
lunch boringdroid_x86_64-userdebug
m SystemUISharedLib
```

Copy `out/target/product/boringdroid_x86_64/obj/JAVA_LIBRARIES/SystemUISharedLib_intermediates/javalib.jar`
to replace `SystemUIPluginLib.jar`. And then updating `src/main/SystemUISharedRes` based on
[SystemUI SharedLib's Android.bp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/shared/Android.bp).


The `SystemUISharedLib` is a new all-in-one library.

## Test

### Instrumentation tests

This project has some instrumentation tests, and you should use the below command to check
tests before you push changes to the repository for reviewing:

```shell
./gradlew connectedAndroidTest
```

### Unit tests

This project also has some unit tests, you should use the below command to check tests before
you push changes to the repository for reviewing:

```shell
./gradlew test
```

## Spotless

This project uses [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) to
format source code, and you can use the below command to check and format source code before
you push changes to the repository for reviewing:

```shell
./gradlew spotlessCheck
./gradlew spotlessApply
```

If you encounter an error when use `./gradlew spotlessApply`, you should fix format errors
manually, because the Spotless based formatter can't fix all errors.

## Release

The `BoringdroidSystemUI` is released with apk, and you can use the following commands build apk:

```shell script
./gradlew build
```

And copy the `app/build/outputs/apk/release/app-release-unsigned.apk` as the released apk to the 
release repository.

Also, we can download latest build APK from GitHub Action's artifacts.
