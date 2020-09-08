/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.ATLEAST_Q;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TouchController;

import java.util.ArrayList;

/**
 * Class for initiating a drag within a view or across multiple views.
 */
public class DragController implements DragDriver.EventListener, TouchController {

    /**
     * When a drag is started from a deep press, you need to drag this much farther than normal to
     * end a pre-drag. See {@link DragOptions.PreDragCondition#shouldStartDrag(double)}.
     */
    private static final int DEEP_PRESS_DISTANCE_FACTOR = 3;

    @Thunk Launcher mLauncher;
    private FlingToDeleteHelper mFlingToDeleteHelper;

    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    private DragDriver mDragDriver = null;

    /** Options controlling the drag behavior. */
    private DragOptions mOptions;

    /** X coordinate of the down event. */
    private int mMotionDownX;

    /** Y coordinate of the down event. */
    private int mMotionDownY;

    private DropTarget.DragObject mDragObject;

    private ArrayList<DragListener> mListeners = new ArrayList<>();

    private View mMoveTarget;

    private DropTarget mLastDropTarget;

    private final int[] mLastTouch = new int[2];
    private long mLastTouchUpTime = -1;
    private int mLastTouchClassification;
    private int mDistanceSinceScroll = 0;

    private int mTmpPoint[] = new int[2];
    private Rect mDragLayerRect = new Rect();

    private boolean mIsInPreDrag;

    /**
     * Interface to receive notifications when a drag starts or stops
     */
    public interface DragListener {
        /**
         * A drag has begun
         *
         * @param dragObject The object being dragged
         * @param options Options used to start the drag
         */
        void onDragStart(DropTarget.DragObject dragObject, DragOptions options);

        /**
         * The drag has ended
         */
        void onDragEnd();
    }

    /**
     * Used to create a new DragLayer from XML.
     */
    public DragController(Launcher launcher) {
        mLauncher = launcher;
        mFlingToDeleteHelper = new FlingToDeleteHelper();
    }

    private void callOnDragStart() {
        if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, true /* dragStarted*/);
        }
        mIsInPreDrag = false;
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject, mOptions);
        }
    }

    public void addFirstFrameAnimationHelper(ValueAnimator anim) {
        if (mDragObject != null && mDragObject.dragView != null) {
            mDragObject.dragView.mFirstFrameAnimatorHelper.addTo(anim);
        }
    }

    /**
     * Call this from a drag source view like this:
     *
     * <pre>
     *  @Override
     *  public boolean dispatchKeyEvent(KeyEvent event) {
     *      return mDragController.dispatchKeyEvent(this, event)
     *              || super.dispatchKeyEvent(event);
     * </pre>
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragDriver != null;
    }

    public boolean isDragging() {
        return mDragDriver != null || (mOptions != null && mOptions.isAccessibleDrag);
    }

    /**
     * Stop dragging without dropping.
     */
    public void cancelDrag() {
        if (isDragging()) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mDragObject.deferDragViewCleanupPostAnimation = false;
            mDragObject.cancelled = true;
            mDragObject.dragComplete = true;
            if (!mIsInPreDrag) {
                dispatchDropComplete(null, false);
            }
        }
        endDrag();
    }

    private void dispatchDropComplete(View dropTarget, boolean accepted) {
        if (!accepted) {
            // If it was not accepted, cleanup the state. If it was accepted, it is the
            // responsibility of the drop target to cleanup the state.
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            mDragObject.deferDragViewCleanupPostAnimation = false;
        }

        mDragObject.dragSource.onDropCompleted(dropTarget, mDragObject, accepted);
    }

    private void endDrag() {
        if (isDragging()) {
            mDragDriver = null;
            boolean isDeferred = false;
            if (mDragObject.dragView != null) {
                isDeferred = mDragObject.deferDragViewCleanupPostAnimation;
                if (!isDeferred) {
                    mDragObject.dragView.remove();
                } else if (mIsInPreDrag) {
                    animateDragViewToOriginalPosition(null, null, -1);
                }
                mDragObject.dragView = null;
            }

            // Only end the drag if we are not deferred
            if (!isDeferred) {
                callOnDragEnd();
            }
        }

        mFlingToDeleteHelper.releaseVelocityTracker();
    }

    public void animateDragViewToOriginalPosition(final Runnable onComplete,
            final View originalIcon, int duration) {
        Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                if (originalIcon != null) {
                    originalIcon.setVisibility(View.VISIBLE);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        };
        mDragObject.dragView.animateTo(mMotionDownX, mMotionDownY, onCompleteRunnable, duration);
    }

    private void callOnDragEnd() {
        if (mIsInPreDrag && mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, false /* dragStarted*/);
        }
        mIsInPreDrag = false;
        mOptions = null;
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragEnd();
        }
    }

    /**
     * This only gets called as a result of drag view cleanup being deferred in endDrag();
     */
    void onDeferredEndDrag(DragView dragView) {
        dragView.remove();

        if (mDragObject.deferDragViewCleanupPostAnimation) {
            // If we skipped calling onDragEnd() before, do it now
            callOnDragEnd();
        }
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    private int[] getClampedDragLayerPos(float x, float y) {
        mLauncher.getDragLayer().getLocalVisibleRect(mDragLayerRect);
        mTmpPoint[0] = (int) Math.max(mDragLayerRect.left, Math.min(x, mDragLayerRect.right - 1));
        mTmpPoint[1] = (int) Math.max(mDragLayerRect.top, Math.min(y, mDragLayerRect.bottom - 1));
        return mTmpPoint;
    }

    public void resetLastGestureUpTime() {
        mLastTouchUpTime = -1;
    }

    @Override
    public void onDriverDragMove(float x, float y) {
        final int[] dragLayerPos = getClampedDragLayerPos(x, y);

        handleMoveEvent(dragLayerPos[0], dragLayerPos[1]);
    }

    @Override
    public void onDriverDragExitWindow() {
        if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
            mLastDropTarget = null;
        }
    }

    @Override
    public void onDriverDragEnd(float x, float y) {
    }

    @Override
    public void onDriverDragCancel() {
        cancelDrag();
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mOptions != null && mOptions.isAccessibleDrag) {
            return false;
        }

        // Update the velocity tracker
        mFlingToDeleteHelper.recordMotionEvent(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];
        mLastTouch[0] = dragLayerX;
        mLastTouch[1] = dragLayerY;
        if (ATLEAST_Q) {
            mLastTouchClassification = ev.getClassification();
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = System.currentTimeMillis();
                break;
        }

        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);
    }

    /**
     * Call this from a drag view.
     */
    public void onDragViewAnimationEnd() {
        if (mDragDriver != null) {
            mDragDriver.onDragViewAnimationEnd();
        }
    }

    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mMoveTarget != null && mMoveTarget.dispatchUnhandledMove(focused, direction);
    }

    private void handleMoveEvent(int x, int y) {
        mDragObject.dragView.move(x, y);

        // Drop on someone?
        final int[] coordinates = mCoordinatesTemp;
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];

        // Check if we are hovering over the scroll areas
        mDistanceSinceScroll += Math.hypot(mLastTouch[0] - x, mLastTouch[1] - y);
        mLastTouch[0] = x;
        mLastTouch[1] = y;

        int distanceDragged = mDistanceSinceScroll;
        if (ATLEAST_Q && mLastTouchClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS) {
            distanceDragged /= DEEP_PRESS_DISTANCE_FACTOR;
        }
        if (mIsInPreDrag && mOptions.preDragCondition != null
                && mOptions.preDragCondition.shouldStartDrag(distanceDragged)) {
            callOnDragStart();
        }
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (mDragDriver == null || mOptions == null || mOptions.isAccessibleDrag) {
            return false;
        }

        // Update the velocity tracker
        mFlingToDeleteHelper.recordMotionEvent(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Remember where the motion event started
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                break;
        }

        return mDragDriver.onTouchEvent(ev);
    }
}
