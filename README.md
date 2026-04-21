# BoringdroidSystemUI

A PC-class SystemUI for boringdroid. BoringdroidSystemUI is loaded into the
stock AOSP SystemUI process as a
[SystemUI plugin](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/plugin/),
so it inherits SystemUI's permissions, UID and signature without forking
SystemUI itself. The plugin replaces the navigation bar with a desktop
taskbar and layers a notch of desktop-class surfaces on top of it — Start
menu, Action Center, Calendar, Overview.

## Architecture

```
            ┌─────────────────────────────────────────────┐
            │ com.android.systemui (AOSP, uid 1000)       │
            │                                             │
            │  ┌─────────────── SystemUIOverlay ────────┐ │
            │  │   plugin entry point                   │ │
            │  │                                        │ │
            │  │   TaskbarWindow   ─── TYPE_NAVIGATION_BAR
            │  │       └ providedInsets = 72dp         │ │
            │  │   AllAppsWindow   ─── start menu       │ │
            │  │   ActionCenterWindow ─── notif + QS    │ │
            │  │   CalendarClockWindow ─── clock + cal  │ │
            │  │                                        │ │
            │  │   AccessibilityManager.registerSystemAction
            │  │       └ GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS
            │  │       → Meta key opens Start menu      │ │
            │  └────────────────────────────────────────┘ │
            └─────────────────────────────────────────────┘
                              │ broadcast (TOGGLE_OVERVIEW)
                              ▼
            ┌─────────────────────────────────────────────┐
            │ com.boringdroid.systemui (own process)      │
            │                                             │
            │   BoringdroidOverviewService ── bound by    │
            │     SystemUI's OverviewProxyService;        │
            │     owns the OverviewWindow (TYPE_APPLICATION_OVERLAY)
            │                                             │
            │   BoringdroidNotificationMirror ── mirrors  │
            │     notifications into SystemUI via         │
            │     NotificationFeedIpc broadcasts         │
            └─────────────────────────────────────────────┘
```

Two processes are involved:

1. **SystemUI host (`com.android.systemui`, uid 1000).** The plugin runs
   here. Everything that needs to draw on the taskbar/status-bar layer —
   the taskbar itself, Start menu, Action Center, Calendar — is owned by
   `SystemUIOverlay` in this process. It talks to the framework via
   system-signature APIs (WindowManager, AccessibilityManager,
   NotificationListenerService bindings).

2. **Plugin package (`com.boringdroid.systemui`).** Hosts the Overview
   binder (`BoringdroidOverviewService`, the IOverviewProxy that
   SystemUI's `OverviewProxyService` binds to) and the notification
   mirror. Runs in its own process because `OverviewProxyService`
   requires the recents component to live outside SystemUI.

Keep that split in mind when wiring new flows: a broadcast that needs to
reach the overview window must be addressed to the plugin package, not
the SystemUI host.

## Key surfaces

- **Taskbar** (`taskbar/Taskbar.kt`). Compose-first, pinned via
  `TaskbarWindow` as a `TYPE_NAVIGATION_BAR` — chosen so WMS propagates
  the `providedInsets` it carries. Advertises the taskbar height plus
  the `panel_taskbar_gap` as a navigation-bar inset so Launcher3 and
  other full-height apps leave room above it.
- **Start menu** (`AllAppsLayout.kt`, `AllAppsWindow.kt`). Search pill,
  pinned grid, user rail (lock / sign out / power). Toggled by clicking
  the start button, tapping the taskbar search pill, or pressing the
  Meta (Windows) key.
- **Action Center** (`actioncenter/ActionCenterLayout.kt`). Big clock
  header, 3x3 QS tile grid backed by `QsController` / `QsTileStore`, a
  media card, and a `LazyColumn` of notifications fed by
  `NotificationFeed`. `ACTION_CLEAR_ALL` broadcast + in-panel "Clear
  all" button.
- **Calendar panel** (`calendar/CalendarClockLayout.kt`). Clock + month
  grid + agenda. Mutually exclusive with Action Center. Backed by
  `CalendarContract.Instances` in a background loader.
- **Overview** (`overview/OverviewLayout.kt`). macOS-Mission-Control-style
  expo — thumbnails fly from their real window bounds to a common-scale
  grid and back. The window itself is owned by
  `BoringdroidOverviewService` in the plugin process (see above);
  `SystemUIOverlay` triggers it via `ACTION_TOGGLE_OVERVIEW` broadcast.

## Framework dependency: one surviving `frameworks/base` patch

BoringdroidSystemUI is *almost* pure plugin. On AOSP 14 it still depends
on one four-line edit inside the stock framework:

- **File**: `frameworks/base/packages/SystemUI/src/com/android/systemui/navigationbar/NavigationBarController.java`
- **What it does**: early-returns from `createNavigationBar(Display)` when
  `SystemProperties.getBoolean("persist.sys.systemuiplugin.enabled", false)`
  is true (the prop is set by `vendor/boringdroid/boringdroid.mk`).
- **Why it has to exist**: BoringdroidSystemUI renders its own
  `TYPE_NAVIGATION_BAR` taskbar window. Without this guard, AOSP
  *also* creates its own NavigationBar on `displayId=0`, producing
  double-bar visual collisions and inset-accounting bugs.
- **Why it can't be an overlay**: there is no product-overlay or RRO
  mechanism in AOSP 14 that suppresses NavigationBar creation per-display
  from outside the framework. `config_showNavigationBar` is read by WMS
  at `DisplayPolicy` construction time — before product overlays are
  guaranteed to be applied — so flipping it via overlay is unreliable
  for our use case. A `WindowManager`-side suppression hook would itself
  be a forked-AOSP patch, and strictly larger than the current one.
- **Maintainer note**: **do not delete this patch as part of a
  "framework-patch-free" cleanup.** It is intentionally the smallest
  survivable diff. The edit is wrapped in `// region boringdroid` /
  `// endregion` so it's trivial to locate. If a future AOSP release
  adds a per-display NavigationBar suppression hook (or makes
  `config_showNavigationBar` overlay-honoring at the right lifecycle
  point), this patch can be retired — until then, keep it.

## Build

`BoringdroidSystemUI` ships as an AOSP module — the build is Soong. From
the AOSP root:

```shell
source build/envsetup.sh
lunch boringdroid_x86_64-userdebug
m BoringdroidSystemUI BoringdroidSystemUITests
```

`Android.bp` declares both the plugin APK and its instrumentation APK
via the standard `android_app` / `android_test` module types, with
`platform_apis: true` and `certificate: "platform"` so the plugin loads
inside SystemUI's UID on the signed image.

For IDE iteration Android Studio can import the module directly via
Soong's IntelliJ integration (`idegen`); the in-tree Gradle project
has been retired — running the full tree build is the source of truth
for both the plugin APK and its test APK.

## Tests

Instrumentation suite (UiAutomator) lives under `app/src/androidTest`
and builds as `BoringdroidSystemUITests.apk`:

```shell
m BoringdroidSystemUI BoringdroidSystemUITests
bash .claude/scripts/run-boringdroid-tests.sh
```

The script builds the APKs, installs them against a running
`boringdroid_x86_64-userdebug` emulator, restarts SystemUI to pick up
the plugin, drops adb to shell uid (so `cmd notification post` works),
restarts Launcher so it re-reads the taskbar's navigation-bar inset,
and runs every class in `com.boringdroid.systemui.test`.
