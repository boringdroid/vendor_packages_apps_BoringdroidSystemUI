package com.android.launcher3;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

public class IconProvider implements ResourceBasedOverride {

    public static MainThreadInitializedObject<IconProvider> INSTANCE =
            forOverride(IconProvider.class, R.string.icon_provider_class);

    public IconProvider() { }

    public String getSystemStateForPackage(String systemState) {
        return systemState;
    }

    public Drawable getIcon(LauncherActivityInfo info, int iconDpi) {
        return info.getIcon(iconDpi);
    }
}
