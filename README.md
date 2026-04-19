# BoringdroidSystemUI

The BoringdroidSystemUI is a pc-style SystemUI implementation that uses 
[SystemUI plugin](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/plugin/)
to hook itself to SystemUI.

It also uses 
[SystemUI SharedLib](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/shared/)
to receive the task changed events from system.

We provide gradle build script to build app with gradle, and develop it with Android Studio. It uses the keystore
generated from AOSP debug key, and it will help to install debug app from Android Studio to Android.

`build.gradle.kts` uses jars of above library to remove system API dependency, and
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
[SystemUI SharedLib's Android.bp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/shared/Android.bp).


The `SystemUISharedLib` is a new all-in-one library.

## Framework dependency: one surviving `frameworks/base` patch

BoringdroidSystemUI is *almost* pure plugin. On AOSP-14 it still depends on
**one** four-line edit inside the stock framework:

- File: `frameworks/base/packages/SystemUI/src/com/android/systemui/navigationbar/NavigationBarController.java`
- What it does: early-returns from `createNavigationBar(Display)` when
  `SystemProperties.getBoolean("persist.sys.systemuiplugin.enabled", false)`
  is true (the prop is set by `vendor/boringdroid/boringdroid.mk`).
- Why it has to exist: BoringdroidSystemUI renders its own
  `TYPE_NAVIGATION_BAR_PANEL` taskbar window. Without this guard, AOSP
  *also* creates its own NavigationBar on `displayId=0`, producing
  double-bar visual collisions and inset-accounting bugs.
- Why it can't be an overlay: there is no product-overlay or RRO mechanism
  in AOSP-14 that suppresses NavigationBar creation per-display from
  outside the framework. `config_showNavigationBar` is read by WMS at
  `DisplayPolicy` construction time — before product overlays are
  guaranteed to be applied — so flipping it via overlay is unreliable
  for our use case. A `WindowManager`-side suppression hook would itself
  be a forked-AOSP patch, and strictly larger than the current one.
- Maintainer note: **do not delete this patch as part of a
  "framework-patch-free" cleanup.** It is intentionally the smallest
  survivable diff. The edit is wrapped in `// region boringdroid` /
  `// endregion` so it's trivial to locate. If a future AOSP release
  adds a per-display NavigationBar suppression hook (or makes
  `config_showNavigationBar` overlay-honoring at the right lifecycle
  point), this patch can be retired — until then, keep it.

Full rationale and the broader "what stays AOSP vs. what we fork"
decision lives in
`docs/superpowers/specs/2026-04-19-systemui-state.md` §3.1.

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
