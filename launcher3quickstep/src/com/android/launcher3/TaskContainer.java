/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.OverScroller;

import java.util.ArrayList;

/**
 * An abstraction of the original Workspace which supports browsing through a
 * sequential list of "pages"
 */
public abstract class TaskContainer extends ViewGroup {
    public static final int MAX_TASK_COUNT = 8;

    private static final String TAG = "PagedView";
    private static final boolean DEBUG = false;

    protected static final int INVALID_TASK_VIEW_INDEX = -1;

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_SNAP_VELOCITY = 1500;
    private static final int MIN_FLING_VELOCITY = 250;

    private boolean mFreeScroll = false;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    protected boolean mFirstLayout = true;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mCurrentTaskViewIndex;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected int mNextTaskViewIndex = INVALID_TASK_VIEW_INDEX;
    protected OverScroller mScroller;
    private VelocityTracker mVelocityTracker;
    protected int mTaskViewSpacing = 0;

    private float mLastMotionX;
    private float mLastMotionXRemainder;

    private boolean mIsBeingDragged;

    protected int mTouchSlop;
    private int mMaximumVelocity;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    protected float mSpringOverScrollX;

    protected int mUnboundedScrollX;

    protected final Rect mInsets = new Rect();
    protected boolean mIsRtl;

    // Similar to the platform implementation of isLayoutValid();
    protected boolean mIsLayoutValid;

    private int[] mTmpIntPair = new int[2];

    public TaskContainer(Context context) {
        this(context, null);
    }

    public TaskContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setHapticFeedbackEnabled(false);
        mIsRtl = Utilities.isRtl(getResources());
        init();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void init() {
        mScroller = new OverScroller(getContext());
        setDefaultInterpolator();

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        float density = getResources().getDisplayMetrics().density;
        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * density);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * density);

        setDefaultFocusHighlightEnabled(false);
    }

    protected void setDefaultInterpolator() {
        mScroller.setInterpolator(Interpolators.SCROLL);
    }

    /**
     * Returns the index of page to be shown immediately afterwards.
     */
    public int getNextTaskViewIndex() {
        return (mNextTaskViewIndex != INVALID_TASK_VIEW_INDEX)
                ? mNextTaskViewIndex : mCurrentTaskViewIndex;
    }

    public int getPageCount() {
        return Math.min(MAX_TASK_COUNT, getChildCount());
    }

    public View getPageAt(int index) {
        return getChildAt(index);
    }

    private void abortScrollerAnimation() {
        mScroller.abortAnimation();
    }

    protected int getUnboundedScrollX() {
        return mUnboundedScrollX;
    }

    @Override
    public void scrollTo(int x, int y) {
    }

    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getUnboundedScrollX() != mScroller.getCurrPos()
                    || getScrollX() != mScroller.getCurrPos()) {
                scrollTo(mScroller.getCurrPos(), 0);
            }
            invalidate();
            return true;
        } else if (mNextTaskViewIndex != INVALID_TASK_VIEW_INDEX) {
            mNextTaskViewIndex = INVALID_TASK_VIEW_INDEX;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    public int getExpectedWidth() {
        return getMeasuredWidth();
    }

    public int getNormalChildWidth() {
        return getExpectedWidth() - getPaddingLeft() - getPaddingRight()
                - mInsets.left - mInsets.right;
    }

    @Override
    public void requestLayout() {
        mIsLayoutValid = false;
        super.requestLayout();
    }

    @Override
    public void forceLayout() {
        mIsLayoutValid = false;
        super.forceLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // We measure the dimensions of the PagedView to be larger than the pages so that when we
        // zoom out (and scale down), the view is still contained in the parent
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        if (DEBUG) Log.d(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize);

        int column = getColumn(getPageCount());
        int taskViewTotalWidth = widthSize - mInsets.left - mInsets.right;
        int singleTaskViewWidth = column == 0 ? taskViewTotalWidth
                : (taskViewTotalWidth - (column + 1) * mTaskViewSpacing) / column;
        Log.d(TAG, "PagedView.onMeasure(): column " + column
                + ", task view width " + singleTaskViewWidth);
        int myWidthSpec = MeasureSpec.makeMeasureSpec(singleTaskViewWidth, MeasureSpec.EXACTLY);

        int row = getRow(getPageCount());
        int taskViewTotalHeight = (heightSize - mInsets.top - mInsets.bottom);
        // TODO add method to set task view vertical spacing
        int singleTaskViewHeight = row == 0 ? taskViewTotalHeight
                : (taskViewTotalHeight - (row + 1) * mTaskViewSpacing) / row;
        Log.d(TAG, "PagedView.onMeasure(): row " + row
                + ", task view height " + singleTaskViewHeight);
        int myHeightSpec = MeasureSpec.makeMeasureSpec(singleTaskViewHeight, MeasureSpec.EXACTLY);

        // measureChildren takes accounts for content padding, we only need to care about extra
        // space due to insets.
        measureChildren(myWidthSpec, myHeightSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mIsLayoutValid = true;
        final int childCount = getChildCount();

        if (childCount == 0) {
            return;
        }

        int column = getColumn(getPageCount());
        Log.d(TAG, "PagedView.onLayout() column " + column);
        int currentColumn = 0;
        int currentRow = 0;
        for (int i = 0; i < getPageCount(); i++) {
            View child = getPageAt(i);
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            int marginLeft = (getMeasuredWidth() - column * width
                    - (column + 1) * mTaskViewSpacing - mInsets.left) / 2;
            int x = marginLeft + mInsets.left + currentColumn * width
                    + (currentColumn + 1) * mTaskViewSpacing;
            int y = mInsets.top + currentRow * height + (currentRow + 1) * mTaskViewSpacing;
            child.layout(x, y, x + width, y + height);

            currentColumn++;
            if (currentColumn >= column) {
                currentColumn = 0;
                currentRow++;
            }
        }

        final LayoutTransition transition = getLayoutTransition();
        // If the transition is running defer updating max scroll, as some empty pages could
        // still be present, and a max scroll change could cause sudden jumps in scroll.
        if (transition != null && transition.isRunning()) {
            transition.addTransitionListener(new LayoutTransition.TransitionListener() {

                @Override
                public void startTransition(LayoutTransition transition, ViewGroup container,
                                            View view, int transitionType) {
                }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup container,
                                          View view, int transitionType) {
                    // Wait until all transitions are complete.
                    if (!transition.isRunning()) {
                        transition.removeTransitionListener(this);
                    }
                }
            });
        }

        if (mFirstLayout && mCurrentTaskViewIndex >= 0 && mCurrentTaskViewIndex < childCount) {
            mFirstLayout = false;
        }
    }

    public void setTaskViewSpacing(int pageSpacing) {
        mTaskViewSpacing = pageSpacing;
        requestLayout();
    }

    private void dispatchPageCountChanged() {
        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
        invalidate();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        dispatchPageCountChanged();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        dispatchPageCountChanged();
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() - 1) return 0;
        return getPageAt(index).getLeft();
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusablePage;
        if (mNextTaskViewIndex != INVALID_TASK_VIEW_INDEX) {
            focusablePage = mNextTaskViewIndex;
        } else {
            focusablePage = mCurrentTaskViewIndex;
        }
        View v = getPageAt(focusablePage);
        if (v != null) {
            return v.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return;
        }

        // XXX-RTL: This will be fixed in a future CL
        if (mCurrentTaskViewIndex >= 0 && mCurrentTaskViewIndex < getPageCount()) {
            getPageAt(mCurrentTaskViewIndex).addFocusables(views, direction, focusableMode);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentTaskViewIndex > 0) {
                getPageAt(mCurrentTaskViewIndex - 1).addFocusables(views, direction, focusableMode);
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (mCurrentTaskViewIndex < getPageCount() - 1) {
                getPageAt(mCurrentTaskViewIndex + 1).addFocusables(views, direction, focusableMode);
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current page.
     * <p>
     * This happens when live folders requery, and if they're off page, they
     * end up calling requestFocus, which pulls it on page.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getPageAt(mCurrentTaskViewIndex);
        View v = focused;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View) v.getParent();
            } else {
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            // We need to make sure to cancel our long press if
            // a scrollable widget takes over touch events
            final View currentPage = getPageAt(mCurrentTaskViewIndex);
            currentPage.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */

        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return false;

        acquireVelocityTrackerAndAddMovement(ev);

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && mIsBeingDragged) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
                if (mActivePointerId != INVALID_POINTER) {
                    determineScrollingStart(ev);
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurence of a move event as a ACTION_DOWN
                // i.e. fall through to the next case (don't break)
                // (We sometimes miss ACTION_DOWN events in Workspace because it ignores all events
                // while it's small- this was causing a crash before we checked for INVALID_POINTER)
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                // Remember location of down touch
                mLastMotionX = ev.getX();
                mLastMotionXRemainder = 0;
                mActivePointerId = ev.getPointerId(0);

                updateIsBeingDraggedOnTouchDown();

                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    /**
     * If being flinged and user touches the screen, initiate drag; otherwise don't.
     */
    private void updateIsBeingDraggedOnTouchDown() {
        // mScroller.isFinished should be false when being flinged.
        final int xDist = Math.abs(mScroller.getFinalPos() - mScroller.getCurrPos());
        final boolean finishedScrolling = (mScroller.isFinished() || xDist < mTouchSlop / 3);

        mIsBeingDragged = !finishedScrolling;
    }

    public boolean isHandlingTouch() {
        return mIsBeingDragged;
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev) {
        // Disallow scrolling if we don't have a valid pointer index
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) return;

        final float x = ev.getX(pointerIndex);
        final int xDiff = (int) Math.abs(x - mLastMotionX);
        final int touchSlop = Math.round(mTouchSlop);
        boolean xMoved = xDiff > touchSlop;

        if (xMoved) {
            // Scroll if the user moved far enough along the X axis
            mIsBeingDragged = true;
            mLastMotionX = x;
            mLastMotionXRemainder = 0;
            // Stop listening for things like pinches.
            requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mScroller.isSpringing() && mSpringOverScrollX != 0) {
            int saveCount = canvas.save();

            canvas.translate(-mSpringOverScrollX, 0);
            super.dispatchDraw(canvas);

            canvas.restoreToCount(saveCount);
        } else {
            super.dispatchDraw(canvas);
        }
    }


    public void setEnableFreeScroll(boolean freeScroll) {
        if (mFreeScroll == freeScroll) {
            return;
        }

        mFreeScroll = freeScroll;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Skip touch handling if there are no pages to swipe
        if (getChildCount() <= 0) return false;

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                updateIsBeingDraggedOnTouchDown();

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    abortScrollerAnimation();
                }

                // Remember where the motion event started
                mLastMotionXRemainder = 0;
                mActivePointerId = ev.getPointerId(0);

                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);

                    if (pointerIndex == -1) return true;

                    final float x = ev.getX(pointerIndex);
                    final float deltaX = mLastMotionX + mLastMotionXRemainder - x;

                    // Only scroll and update mLastMotionX if we have moved some discrete amount.  We
                    // keep the remainder because we are actually testing if we've moved from the last
                    // scrolled position (which is discrete).
                    if (Math.abs(deltaX) >= 1.0f) {
                        scrollBy((int) deltaX, 0);
                        mLastMotionX = x;
                        mLastMotionXRemainder = deltaX - (int) deltaX;
                    } else {
                        awakenScrollBars();
                    }
                } else {
                    determineScrollingStart(ev);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                    if (mFreeScroll) {
                        if (!mScroller.isFinished()) {
                            abortScrollerAnimation();
                        }
                        invalidate();
                    }
                }

                // End any intermediate reordering states
                resetTouchState();
                break;

            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }

        return true;
    }

    private void resetTouchState() {
        releaseVelocityTracker();
        mIsBeingDragged = false;
        mActivePointerId = INVALID_POINTER;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                // Handle mouse (or ext. device) by shifting the page depending on the scroll
                final float vscroll;
                final float hscroll;
                if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                    vscroll = 0;
                    hscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                } else {
                    vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                }
                if (Math.abs(vscroll) > Math.abs(hscroll) && !isVerticalScrollable()) {
                    return true;
                }
                if (hscroll != 0 || vscroll != 0) {
                    return true;
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    protected boolean isVerticalScrollable() {
        return true;
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = ev.getX(newPointerIndex);
            mLastMotionXRemainder = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    public int getPageNearestToCenterOfScreen() {
        return getPageNearestToCenterOfScreen(getScrollX());
    }

    private int getPageNearestToCenterOfScreen(int scaledScrollX) {
        int screenCenter = scaledScrollX + (getMeasuredWidth() / 2);
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = getPageAt(i);
            int childWidth = layout.getMeasuredWidth();
            int halfChildWidth = (childWidth / 2);
            int childCenter = getChildOffset(i) + halfChildWidth;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        // Some accessibility services have special logic for ScrollView. Since we provide same
        // accessibility info as ScrollView, inform the service to handle use the same way.
        return ScrollView.class.getName();
    }

    public int[] getVisibleChildrenRange() {
        float visibleLeft = 0;
        float visibleRight = visibleLeft + getMeasuredWidth();
        float scaleX = getScaleX();
        if (scaleX < 1 && scaleX > 0) {
            float mid = getMeasuredWidth() / 2;
            visibleLeft = mid - ((mid - visibleLeft) / scaleX);
            visibleRight = mid + ((visibleRight - mid) / scaleX);
        }

        int leftChild = -1;
        int rightChild = -1;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getPageAt(i);

            float left = child.getLeft() + child.getTranslationX() - getScrollX();
            if (left <= visibleRight && (left + child.getMeasuredWidth()) >= visibleLeft) {
                if (leftChild == -1) {
                    leftChild = i;
                }
                rightChild = i;
            }
        }
        mTmpIntPair[0] = leftChild;
        mTmpIntPair[1] = rightChild;
        return mTmpIntPair;
    }

    public int getLowerVisibleTaskIndex() {
        return 0;
    }

    public int getUpperVisibleTaskIndex() {
        return Math.min(MAX_TASK_COUNT, getPageCount()) - 1;
    }

    private static int getColumn(int taskCount) {
        if (taskCount < 0 || taskCount > MAX_TASK_COUNT) {
            throw new IllegalArgumentException("Unsupported task count " + taskCount);
        }
        switch (taskCount) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
            case 4:
                return 2;
            case 3:
            case 5:
            case 6:
                return 3;
            case 7:
            case 8:
            default:
                return 4;
        }
    }

    private static int getRow(int taskCount) {
        if (taskCount < 0 || taskCount > MAX_TASK_COUNT) {
            throw new IllegalArgumentException("Unsupported task count " + taskCount);
        }
        if (taskCount == 0) {
            return 0;
        }
        int column = getColumn(taskCount);
        if (column == 0) {
            return 1;
        }
        int div = taskCount / column;
        int mod = taskCount % column;
        return mod == 0 ? div : div + 1;
    }
}