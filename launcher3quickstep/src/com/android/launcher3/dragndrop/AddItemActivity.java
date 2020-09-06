/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.annotation.TargetApi;
import android.content.pm.LauncherApps.PinItemRequest;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompatVO;
import com.android.launcher3.views.BaseDragLayer;

@TargetApi(Build.VERSION_CODES.O)
public class AddItemActivity extends BaseActivity implements OnLongClickListener, OnTouchListener {
    private final PointF mLastTouchPos = new PointF();

    private PinItemRequest mRequest;
    private LauncherAppState mApp;
    private InvariantDeviceProfile mIdp;

    private boolean mFinishOnPause = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequest = LauncherAppsCompatVO.getPinItemRequest(getIntent());
        if (mRequest == null) {
            finish();
            return;
        }

        mApp = LauncherAppState.getInstance(this);
        mIdp = mApp.getInvariantDeviceProfile();

        // Use the application context to get the device profile, as in multiwindow-mode, the
        // confirmation activity might be rotated.
        mDeviceProfile = mIdp.getDeviceProfile(getApplicationContext());

        setContentView(R.layout.add_item_confirmation_activity);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        mLastTouchPos.set(motionEvent.getX(), motionEvent.getY());
        return false;
    }

    @Override
    public boolean onLongClick(View view) {
        // TODO recheck it
        mFinishOnPause = true;
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFinishOnPause) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public BaseDragLayer getDragLayer() {
        throw new UnsupportedOperationException();
    }
}
