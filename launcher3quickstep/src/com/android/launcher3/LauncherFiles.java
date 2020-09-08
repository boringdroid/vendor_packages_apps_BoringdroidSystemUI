package com.android.launcher3;

/**
 * Central list of files the Launcher writes to the application data directory.
 *
 * To add a new Launcher file, create a String constant referring to the filename.
 */
public class LauncherFiles {

    public static final String SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs";
    // This preference file is not backed up to cloud.
    public static final String DEVICE_PREFERENCES_KEY = "com.android.launcher3.device.prefs";

    public static final String APP_ICONS_DB = "app_icons.db";

}
