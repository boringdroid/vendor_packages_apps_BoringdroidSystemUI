package com.boringdroid.systemui

import android.content.ComponentName
import android.graphics.drawable.Drawable

class TaskInfo {
    var id = 0
    private var baseActivityComponentName: ComponentName? = null
    private var realActivityComponentName: ComponentName? = null
    var packageName: String? = null
    var icon: Drawable? = null

    fun setBaseActivityComponentName(baseActivityComponentName: ComponentName?) {
        this.baseActivityComponentName = baseActivityComponentName
    }

    fun setRealActivityComponentName(realActivityComponentName: ComponentName?) {
        this.realActivityComponentName = realActivityComponentName
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TaskInfo) {
            return false
        }
        // The task id is unique in system.
        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    override fun toString(): String {
        return (
            "Task id " +
                id +
                ", origin " +
                baseActivityComponentName +
                ", real " +
                realActivityComponentName +
                ", package " +
                packageName
        )
    }
}
