package com.boringdroid.systemui

import android.content.ComponentName
import android.graphics.drawable.Drawable

class TaskInfo {
    var id = 0
    private var mBaseActivityComponentName: ComponentName? = null
    private var mRealActivityComponentName: ComponentName? = null
    var packageName: String? = null
    var icon: Drawable? = null
    fun setBaseActivityComponentName(baseActivityComponentName: ComponentName?) {
        mBaseActivityComponentName = baseActivityComponentName
    }

    fun setRealActivityComponentName(realActivityComponentName: ComponentName?) {
        mRealActivityComponentName = realActivityComponentName
    }

    override fun equals(another: Any?): Boolean {
        if (another !is TaskInfo) {
            return false
        }
        // The task id is unique in system.
        return id == another.id
    }

    override fun hashCode(): Int {
        return id
    }

    override fun toString(): String {
        return ("Task id "
                + id
                + ", origin "
                + mBaseActivityComponentName
                + ", real "
                + mRealActivityComponentName
                + ", package "
                + packageName)
    }
}