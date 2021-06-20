# BoringdroidSystemUI

The BoringdroidSystemUI is a pc-style SystemUI implementation that uses 
[SystemUI plugin](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/plugin/)
to hook itself to SystemUI.

It also uses 
[SystemUI SharedLib](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/shared/)
to receive the task changed events from system.

We provide gradle build script to build app with gradle, and develop it with Android Studio. It uses the keystore
generated from AOSP debug key, and it will help to install debug app from Android Studio to Android.

Both `Android.bp` and `build.gradle` use jars of above library to remove system API dependency, and
built this project directly and separately. The jars are built from system, so we should update them
when we upgrade AOSP.

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

## JDK Requirement

The google java format used by [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
needs `JDK 11`, so you should setup `JDK 11` to build and run `spotlessCheck` and `spotlessApply`.

## Release

The `BoringdroidSystemUI` is released with apk, and you can use the following commands build apk:

```shell script
./gradlew build
```

And copy the `app/build/outputs/apk/release/app-release-unsigned.apk` as the released apk to the 
release repository.