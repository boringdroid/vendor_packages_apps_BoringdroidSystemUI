/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.android.launcher3.views.RecyclerViewFastScroller;

import androidx.recyclerview.widget.RecyclerView;


/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public abstract class BaseRecyclerView extends RecyclerView  {

    protected RecyclerViewFastScroller mScrollbar;

    public BaseRecyclerView(Context context) {
        this(context, null);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        bindFastScrollbar();
    }

    public void bindFastScrollbar() {
        ViewGroup parent = (ViewGroup) getParent().getParent();
        mScrollbar = parent.findViewById(R.id.fast_scroller);
        mScrollbar.setRecyclerView(this, parent.findViewById(R.id.fast_scroller_popup));
        onUpdateScrollbar(0);
    }

    public int getScrollBarTop() {
        return getPaddingTop();
    }

    /**
     * Returns the height of the fast scroll bar
     */
    public int getScrollbarTrackHeight() {
        return mScrollbar.getHeight() - getScrollBarTop() - getPaddingBottom();
    }

    /**
     * @return whether fast scrolling is supported in the current state.
     */
    public boolean supportsFastScrolling() {
        return true;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     */
    public abstract String scrollToPositionAtProgress(float touchFraction);

    /**
     * Updates the bounds for the scrollbar.
     * <p>Override in each subclass of this base class.
     */
    public abstract void onUpdateScrollbar(int dy);

    /**
     * <p>Override in each subclass of this base class.
     */
    public void onFastScrollCompleted() {}

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
    }
}