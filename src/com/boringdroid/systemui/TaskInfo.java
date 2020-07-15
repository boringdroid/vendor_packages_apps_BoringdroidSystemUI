package com.boringdroid.systemui;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public class TaskInfo {
    private int mId;
    private ComponentName mBaseActivityComponentName;
    private ComponentName mRealActivityComponentName;
    private String mPackageName;
    private Drawable mIcon;

    public int getId() {
        return mId;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setBaseActivityComponentName(ComponentName baseActivityComponentName) {
        mBaseActivityComponentName = baseActivityComponentName;
    }

    public void setRealActivityComponentName(ComponentName realActivityComponentName) {
        mRealActivityComponentName = realActivityComponentName;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public void setId(int id) {
        mId = id;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof TaskInfo)) {
            return false;
        }
        TaskInfo task = (TaskInfo) another;
        // The task id is unique in system.
        return mId == task.mId;
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public String toString() {
        return "Task id " + mId + ", origin " + mBaseActivityComponentName
                + ", real " + mRealActivityComponentName + ", package " + mPackageName;
    }
}
