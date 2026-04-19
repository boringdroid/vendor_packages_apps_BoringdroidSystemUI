// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.graphics.Region
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.SurfaceControl
import com.android.systemui.shared.recents.IOverviewProxy

/**
 * IOverviewProxy implementation. AIDL methods are `oneway`, invoked on the Binder thread —
 * show/hide post to the service's main looper handler since `WindowManager.addView` must run on the
 * UI thread.
 *
 * `onOverviewShown` / `onOverviewHidden` / `onOverviewToggle` drive the [OverviewWindow]. Other
 * callbacks (system-UI state, nav bar, etc.) remain logging no-ops.
 */
class OverviewProxyImpl(private val window: OverviewWindow, private val mainHandler: Handler) :
    IOverviewProxy.Stub() {

    override fun onActiveNavBarRegionChanges(activeRegion: Region?) {
        if (DEBUG) Log.d(TAG, "onActiveNavBarRegionChanges $activeRegion")
    }

    override fun onInitialize(params: Bundle?) {
        if (DEBUG) Log.d(TAG, "onInitialize $params")
    }

    override fun onOverviewToggle() {
        if (DEBUG) Log.d(TAG, "onOverviewToggle")
        mainHandler.post { window.toggle() }
    }

    override fun onOverviewShown(triggeredFromAltTab: Boolean) {
        if (DEBUG) Log.d(TAG, "onOverviewShown altTab=$triggeredFromAltTab")
        mainHandler.post { window.show() }
    }

    override fun onOverviewHidden(triggeredFromAltTab: Boolean, triggeredFromHomeKey: Boolean) {
        if (DEBUG)
            Log.d(TAG, "onOverviewHidden altTab=$triggeredFromAltTab home=$triggeredFromHomeKey")
        mainHandler.post { window.hide() }
    }

    override fun onAssistantAvailable(available: Boolean, longPressHomeEnabled: Boolean) {}

    override fun onAssistantVisibilityChanged(visibility: Float) {}

    override fun onAssistantOverrideInvoked(invocationType: Int) {}

    override fun onSystemUiStateChanged(stateFlags: Int) {}

    override fun onRotationProposal(rotation: Int, isValid: Boolean) {}

    override fun disable(displayId: Int, state1: Int, state2: Int, animate: Boolean) {}

    override fun onSystemBarAttributesChanged(displayId: Int, behavior: Int) {}

    override fun onNavButtonsDarkIntensityChanged(darkIntensity: Float) {}

    override fun enterStageSplitFromRunningApp(leftOrTop: Boolean) {}

    override fun onNavigationBarSurface(surface: SurfaceControl?) {}

    override fun onTaskbarToggled() {}

    companion object {
        private const val TAG = "BoringdroidOverview"
        private const val DEBUG = true
    }
}
