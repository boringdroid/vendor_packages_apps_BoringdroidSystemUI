/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.views;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;

import android.annotation.TargetApi;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * A viewgroup with utility methods for drag-n-drop and touch interception
 */
public abstract class BaseDragLayer<T extends Context & ActivityContext>
        extends InsettableFrameLayout {

    // Touch is being dispatched through the normal view dispatch system
    private static final int TOUCH_DISPATCHING_VIEW = 1 << 0;
    // Touch is being dispatched through the normal view dispatch system, and started at the
    // system gesture region
    private static final int TOUCH_DISPATCHING_GESTURE = 1 << 1;
    // Touch is being dispatched through a proxy from InputMonitor
    private static final int TOUCH_DISPATCHING_PROXY = 1 << 2;

    @ViewDebug.ExportedProperty(category = "launcher")
    private final RectF mSystemGestureRegion = new RectF();
    private int mTouchDispatchState = 0;

    protected final T mActivity;
    private final MultiValueAlpha mMultiValueAlpha;
    private final WallpaperManager mWallpaperManager;
    private final BroadcastReceiver mWallpaperChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onWallpaperChanged();
        }
    };
    private final String[] mWallpapersWithoutSysuiScrims;

    // All the touch controllers for the view
    protected TouchController[] mControllers;
    // Touch controller which is currently active for the normal view dispatch
    protected TouchController mActiveController;

    private TouchCompleteListener mTouchCompleteListener;

    protected boolean mAllowSysuiScrims = true;

    public BaseDragLayer(Context context, AttributeSet attrs, int alphaChannelCount) {
        super(context, attrs);
        mActivity = (T) ActivityContext.lookupContext(context);
        mMultiValueAlpha = new MultiValueAlpha(this, alphaChannelCount);
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
        mWallpapersWithoutSysuiScrims = getResources().getStringArray(
                R.array.live_wallpapers_remove_sysui_scrims);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        } else if (action == MotionEvent.ACTION_DOWN) {
            mActivity.finishAutoCancelActionMode();
        }
        return findActiveController(ev);
    }

    private TouchController findControllerToHandleTouch(MotionEvent ev) {
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView();
        if (topView != null && topView.onControllerInterceptTouchEvent(ev)) {
            return topView;
        }

        for (TouchController controller : mControllers) {
            if (controller.onControllerInterceptTouchEvent(ev)) {
                return controller;
            }
        }
        return null;
    }

    protected boolean findActiveController(MotionEvent ev) {
        mActiveController = null;
        if ((mTouchDispatchState & (TOUCH_DISPATCHING_GESTURE | TOUCH_DISPATCHING_PROXY)) == 0) {
            // Only look for controllers if we are not dispatching from gesture area and proxy is
            // not active
            mActiveController = findControllerToHandleTouch(ev);
        }
        return mActiveController != null;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        // Shortcuts can appear above folder
        View topView = AbstractFloatingView.getTopOpenViewWithType(
        );
        if (topView != null) {
            if (child == topView) {
                return super.onRequestSendAccessibilityEvent(child, event);
            }
            // Skip propagating onRequestSendAccessibilityEvent for all other children
            // which are not topView
            return false;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenViewWithType(
        );
        if (topView != null) {
            // Only add the top view as a child for accessibility when it is open
            addAccessibleChildToList(topView, childrenForAccessibility);
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    protected void addAccessibleChildToList(View child, ArrayList<View> outList) {
        if (child.isImportantForAccessibility()) {
            outList.add(child);
        } else {
            child.addChildrenForAccessibility(outList);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child instanceof AbstractFloatingView) {
            // Handles the case where the view is removed without being properly closed.
            // This can happen if something goes wrong during a state change/transition.
            AbstractFloatingView floatingView = (AbstractFloatingView) child;
            if (floatingView.isOpen()) {
                postDelayed(() -> floatingView.close(false), getSingleFrameMs(getContext()));
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }

        if (mActiveController != null) {
            return mActiveController.onControllerTouchEvent(ev);
        } else {
            // In case no child view handled the touch event, we may not get onIntercept anymore
            return findActiveController(ev);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_DOWN: {
                float x = ev.getX();
                float y = ev.getY();
                mTouchDispatchState |= TOUCH_DISPATCHING_VIEW;

                if ((y < mSystemGestureRegion.top
                        || x < mSystemGestureRegion.left
                        || x > (getWidth() - mSystemGestureRegion.right)
                        || y > (getHeight() - mSystemGestureRegion.bottom))) {
                    mTouchDispatchState |= TOUCH_DISPATCHING_GESTURE;
                } else {
                    mTouchDispatchState &= ~TOUCH_DISPATCHING_GESTURE;
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                mTouchDispatchState &= ~TOUCH_DISPATCHING_GESTURE;
                mTouchDispatchState &= ~TOUCH_DISPATCHING_VIEW;
                break;
        }
        super.dispatchTouchEvent(ev);

        // We want to get all events so that mTouchDispatchSource is maintained properly
        return true;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        // Consume the unhandled move if a container is open, to avoid switching pages underneath.
        return AbstractFloatingView.getTopOpenView() != null;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View topView = AbstractFloatingView.getTopOpenView();
        if (topView != null) {
            return topView.requestFocus(direction, previouslyFocusedRect);
        } else {
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        }
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        View topView = AbstractFloatingView.getTopOpenView();
        if (topView != null) {
            topView.addFocusables(views, direction);
        } else {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    public interface TouchCompleteListener {
        void onTouchComplete();
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "DragLayer:");
        if (mActiveController != null) {
            writer.println(prefix + "\tactiveController: " + mActiveController);
            mActiveController.dump(prefix + "\t", writer);
        }
        writer.println(prefix + "\tdragLayerAlpha : " + mMultiValueAlpha );
    }

    public static class LayoutParams extends InsettableFrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (flp instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) flp;
                if (lp.customPosition) {
                    child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
                }
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.Q)
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            Insets gestureInsets = insets.getMandatorySystemGestureInsets();
            mSystemGestureRegion.set(gestureInsets.left, gestureInsets.top,
                    gestureInsets.right, gestureInsets.bottom);
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivity.registerReceiver(mWallpaperChangeReceiver,
                new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        onWallpaperChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mActivity.unregisterReceiver(mWallpaperChangeReceiver);
    }

    private void onWallpaperChanged() {
        WallpaperInfo newWallpaperInfo = mWallpaperManager.getWallpaperInfo();
        boolean oldAllowSysuiScrims = mAllowSysuiScrims;
        mAllowSysuiScrims = computeAllowSysuiScrims(newWallpaperInfo);
        if (mAllowSysuiScrims != oldAllowSysuiScrims) {
            // Reapply insets so scrim can be removed or re-added if necessary.
            setInsets(mInsets);
        }
    }

    /**
     * Determines whether we can scrim the status bar and nav bar for the given wallpaper by
     * checking against a list of live wallpapers that we don't show the scrims on.
     */
    private boolean computeAllowSysuiScrims(@Nullable WallpaperInfo newWallpaperInfo) {
        if (newWallpaperInfo == null) {
            // New wallpaper is static, not live. Thus, blacklist isn't applicable.
            return true;
        }
        ComponentName newWallpaper = newWallpaperInfo.getComponent();
        for (String wallpaperWithoutScrim : mWallpapersWithoutSysuiScrims) {
            if (newWallpaper.equals(ComponentName.unflattenFromString(wallpaperWithoutScrim))) {
                // New wallpaper is blacklisted from showing a scrim.
                return false;
            }
        }
        return true;
    }
}
