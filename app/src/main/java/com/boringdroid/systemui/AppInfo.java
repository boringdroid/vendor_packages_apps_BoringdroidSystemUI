package com.boringdroid.systemui;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public class AppInfo {
    private String mName;
    private String mPackageName;
    private ComponentName mComponentName;
    private Drawable mIcon;

    public void setName(String name) {
        mName = name;
    }

    public void setComponentName(ComponentName componentName) {
        mComponentName = componentName;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    public String getName() {
        return mName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Drawable getIcon() {
        return mIcon;
    }
}
