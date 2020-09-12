/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.inputconsumers;

import static com.android.launcher3.config.BaseFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.view.KeyEvent;
import android.view.MotionEvent;


import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.Utilities;

/**
 * Input consumer for handling touch on the recents/Launcher activity.
 */
public class OverviewInputConsumer<T extends BaseDraggingActivity>
        implements InputConsumer {

    private final T mActivity;

    private final int[] mLocationOnScreen = new int[2];

    private final boolean mStartingInActivityBounds;

    public OverviewInputConsumer(T activity,
                                 boolean startingInActivityBounds) {
        mActivity = activity;
        mStartingInActivityBounds = startingInActivityBounds;
    }

    @Override
    public int getType() {
        return TYPE_OVERVIEW;
    }

    @Override
    public boolean allowInterceptByParent() {
        return true;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        int flags = ev.getEdgeFlags();
        if (!mStartingInActivityBounds) {
            ev.setEdgeFlags(flags | Utilities.EDGE_NAV_BAR);
        }
        ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
        ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        ev.setEdgeFlags(flags);
    }

    @Override
    public void onKeyEvent(KeyEvent ev) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            mActivity.dispatchKeyEvent(ev);
        }
    }
}

