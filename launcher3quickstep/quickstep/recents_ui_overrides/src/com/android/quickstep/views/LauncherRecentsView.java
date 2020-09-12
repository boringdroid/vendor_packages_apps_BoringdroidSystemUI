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
package com.android.quickstep.views;

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherStateManager.StateListener;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.LayoutUtils;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.RecentsExtraCard;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<BaseActivity> implements StateListener {

    private final TransformParams mTransformParams = new TransformParams();

    private RecentsExtraCard mRecentsExtraCardPlugin;
    private RecentsExtraViewContainer mRecentsExtraViewContainer;
    private PluginListener<RecentsExtraCard> mRecentsExtraCardPluginListener =
            new PluginListener<RecentsExtraCard>() {
        @Override
        public void onPluginConnected(RecentsExtraCard recentsExtraCard, Context context) {
            createRecentsExtraCard();
            mRecentsExtraCardPlugin = recentsExtraCard;
            mRecentsExtraCardPlugin.setupView(context, mRecentsExtraViewContainer, mActivity);
        }

        @Override
        public void onPluginDisconnected(RecentsExtraCard plugin) {
            removeView(mRecentsExtraViewContainer);
            mRecentsExtraCardPlugin = null;
            mRecentsExtraViewContainer = null;
        }
    };

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setContentAlpha(0);
    }

    @Override
    public void startHome() {
        // TODO recheck it
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    @Override
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView tv,
            ClipAnimationHelper helper) {
        AnimatorSet anim = super.createAdjacentPageAnimForTaskLaunch(tv, helper);

        if (!SysUINavigationMode.getMode(mActivity).hasGestures) {
            return anim;
        }

        return anim;
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(getContext(), dp, outRect);
    }

    @Override
    protected void onTaskLaunchAnimationUpdate(float progress, TaskView tv) {
    }

    @Override
    protected void onTaskLaunched(boolean success) {
        super.onTaskLaunched(success);
    }

    @Override
    public boolean shouldUseMultiWindowTaskSizeStrategy() {
        return mActivity.isInMultiWindowMode();
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        super.setOverviewStateEnabled(enabled);
    }

    @Override
    protected boolean shouldStealTouchFromSiblingsBelow(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // TODO recheck it
            return true;
        }
        return super.shouldStealTouchFromSiblingsBelow(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext())
                .addPluginListener(mRecentsExtraCardPluginListener, RecentsExtraCard.class);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(
                mRecentsExtraCardPluginListener);
    }

    @Override
    protected int computeMinScrollX() {
        if (canComputeScrollX() && !mIsRtl) {
            return computeScrollX();
        }
        return super.computeMinScrollX();
    }

    @Override
    protected int computeMaxScrollX() {
        if (canComputeScrollX() && mIsRtl) {
            return computeScrollX();
        }
        return super.computeMaxScrollX();
    }

    private boolean canComputeScrollX() {
        return mRecentsExtraCardPlugin != null && getTaskViewCount() > 0
                && !mDisallowScrollToClearAll;
    }

    private int computeScrollX() {
        int scrollIndex = getTaskViewStartIndex() - 1;
        while (scrollIndex >= 0 && getChildAt(scrollIndex) instanceof RecentsExtraViewContainer
                && ((RecentsExtraViewContainer) getChildAt(scrollIndex)).isScrollable()) {
            scrollIndex--;
        }
        return getScrollForPage(scrollIndex + 1);
    }

    private void createRecentsExtraCard() {
        mRecentsExtraViewContainer = new RecentsExtraViewContainer(getContext());
        FrameLayout.LayoutParams helpCardParams =
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
        mRecentsExtraViewContainer.setLayoutParams(helpCardParams);
        mRecentsExtraViewContainer.setScrollable(true);
        addView(mRecentsExtraViewContainer, 0);
    }

    @Override
    public void resetTaskVisuals() {
        super.resetTaskVisuals();
        if (mRecentsExtraViewContainer != null) {
            mRecentsExtraViewContainer.setAlpha(mContentAlpha);
        }
    }
}
