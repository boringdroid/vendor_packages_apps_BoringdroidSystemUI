/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.android.launcher3.util.TouchController;

/**
 * Base class for a View which shows a floating UI on top of the launcher UI.
 */
public abstract class AbstractFloatingView extends LinearLayout implements TouchController {
    protected boolean mIsOpen;

    public AbstractFloatingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AbstractFloatingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * We need to handle touch events to prevent them from falling through to the workspace below.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    public final void close(boolean animate) {
        animate &= Utilities.areAnimationsEnabled(getContext());
        handleClose(animate);
        mIsOpen = false;
    }

    protected abstract void handleClose(boolean animate);

    public final boolean isOpen() {
        return mIsOpen;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    protected static <T extends AbstractFloatingView> T getOpenView() {
        return null;
    }

    public static AbstractFloatingView getTopOpenView() {
        return getTopOpenViewWithType();
    }

    public static AbstractFloatingView getTopOpenViewWithType() {
        return getOpenView();
    }
}
