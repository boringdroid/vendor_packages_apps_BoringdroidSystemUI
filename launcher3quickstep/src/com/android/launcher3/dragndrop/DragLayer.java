
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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getMode;
import static android.view.View.MeasureSpec.getSize;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.Transposable;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends BaseDragLayer<Launcher> {

    public static final int ALPHA_INDEX_LAUNCHER_LOAD = 1;
    public static final int ALPHA_INDEX_TRANSITIONS = 2;
    private static final int ALPHA_CHANNEL_COUNT = 3;

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    @Thunk DragController mDragController;

    // Variables relating to animation of views after drop
    private ValueAnimator mDropAnim = null;
    private final TimeInterpolator mCubicEaseOutInterpolator = Interpolators.DEACCEL_1_5;
    @Thunk DragView mDropView = null;
    @Thunk int mAnchorViewInitialScrollX = 0;
    @Thunk View mAnchorView = null;

    private int mTopViewIndex;
    private int mChildCountOnLastUpdate = -1;

    // Related to adjacent page hints
    private final ViewGroupFocusHelper mFocusIndicatorHelper;
    private final OverviewScrim mOverviewScrim;

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, ALPHA_CHANNEL_COUNT);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
        setChildrenDrawingOrderEnabled(true);

        mFocusIndicatorHelper = new ViewGroupFocusHelper(this);
        mOverviewScrim = new OverviewScrim(this);
    }

    public void setup(DragController dragController) {
        mDragController = dragController;
        recreateControllers();
    }

    public void recreateControllers() {
        mControllers = UiFactory.createTouchControllers(mActivity);
    }

    public ViewGroupFocusHelper getFocusIndicatorHelper() {
        return mFocusIndicatorHelper;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        // TODO recheck it
        if (mActivity == null) {
            return false;
        }
        return false;
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        // If we've received this, we've already done the necessary handling
        // in onInterceptHoverEvent. Return true to consume the event.
        return false;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        View topView = AbstractFloatingView.getTopOpenViewWithType(mActivity,
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            addAccessibleChildToList(topView, childrenForAccessibility);
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return super.dispatchUnhandledMove(focused, direction)
                || mDragController.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        ev.offsetLocation(getTranslationX(), 0);
        try {
            return super.dispatchTouchEvent(ev);
        } finally {
            ev.offsetLocation(-getTranslationX(), 0);
        }
    }

    public void animateViewIntoPosition(DragView dragView, final int[] pos, float alpha,
            float scaleX, float scaleY, int animationEndStyle, Runnable onFinishRunnable,
            int duration) {
        Rect r = new Rect();
        getViewRectRelativeToSelf(dragView, r);
        final int fromX = r.left;
        final int fromY = r.top;

        animateViewIntoPosition(dragView, fromX, fromY, pos[0], pos[1], alpha, 1, 1, scaleX, scaleY,
                onFinishRunnable, animationEndStyle, duration, null);
    }

    public void animateViewIntoPosition(final DragView view, final int fromX, final int fromY,
            final int toX, final int toY, float finalAlpha, float initScaleX, float initScaleY,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect from = new Rect(fromX, fromY, fromX +
                view.getMeasuredWidth(), fromY + view.getMeasuredHeight());
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, from, to, finalAlpha, initScaleX, initScaleY, finalScaleX, finalScaleY, duration,
                null, null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     *
     * @param view The view to be animated. This view is drawn directly into DragLayer, and so
     *        doesn't need to be a child of DragLayer.
     * @param from The initial location of the view. Only the left and top parameters are used.
     * @param to The final location of the view. Only the left and top parameters are used. This
     *        location doesn't account for scaling, and so should be centered about the desired
     *        final location (including scaling).
     * @param finalAlpha The final alpha of the view, in case we want it to fade as it animates.
     * @param finalScaleX The final scale of the view. The view is scaled about its center.
     * @param finalScaleY The final scale of the view. The view is scaled about its center.
     * @param duration The duration of the animation.
     * @param motionInterpolator The interpolator to use for the location of the view.
     * @param alphaInterpolator The interpolator to use for the alpha of the view.
     * @param onCompleteRunnable Optional runnable to run on animation completion.
     * @param animationEndStyle Whether or not to fade out the view once the animation completes.
     *        {@link #ANIMATION_END_DISAPPEAR} or {@link #ANIMATION_END_REMAIN_VISIBLE}.
     * @param anchorView If not null, this represents the view which the animated view stays
     *        anchored to in case scrolling is currently taking place. Note: currently this is
     *        only used for the X dimension for the case of the workspace.
     */
    public void animateView(final DragView view, final Rect from, final Rect to,
            final float finalAlpha, final float initScaleX, final float initScaleY,
            final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator, final Interpolator alphaInterpolator,
            final Runnable onCompleteRunnable, final int animationEndStyle, View anchorView) {

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from.left, to.top - from.top);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= mCubicEaseOutInterpolator.getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator = null;
        if (alphaInterpolator == null || motionInterpolator == null) {
            interpolator = mCubicEaseOutInterpolator;
        }

        // Animate the view
        final float initAlpha = view.getAlpha();
        final float dropViewScale = view.getScaleX();
        AnimatorUpdateListener updateCb = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                final int width = view.getMeasuredWidth();
                final int height = view.getMeasuredHeight();

                float alphaPercent = alphaInterpolator == null ? percent :
                        alphaInterpolator.getInterpolation(percent);
                float motionPercent = motionInterpolator == null ? percent :
                        motionInterpolator.getInterpolation(percent);

                float initialScaleX = initScaleX * dropViewScale;
                float initialScaleY = initScaleY * dropViewScale;
                float scaleX = finalScaleX * percent + initialScaleX * (1 - percent);
                float scaleY = finalScaleY * percent + initialScaleY * (1 - percent);
                float alpha = finalAlpha * alphaPercent + initAlpha * (1 - alphaPercent);

                float fromLeft = from.left + (initialScaleX - 1f) * width / 2;
                float fromTop = from.top + (initialScaleY - 1f) * height / 2;

                int x = (int) (fromLeft + Math.round(((to.left - fromLeft) * motionPercent)));
                int y = (int) (fromTop + Math.round(((to.top - fromTop) * motionPercent)));

                int anchorAdjust = mAnchorView == null ? 0 : (int) (mAnchorView.getScaleX() *
                    (mAnchorViewInitialScrollX - mAnchorView.getScrollX()));

                int xPos = x - mDropView.getScrollX() + anchorAdjust;
                int yPos = y - mDropView.getScrollY();

                mDropView.setTranslationX(xPos);
                mDropView.setTranslationY(yPos);
                mDropView.setScaleX(scaleX);
                mDropView.setScaleY(scaleY);
                mDropView.setAlpha(alpha);
            }
        };
        animateView(view, updateCb, duration, interpolator, onCompleteRunnable, animationEndStyle,
                anchorView);
    }

    public void animateView(final DragView view, AnimatorUpdateListener updateCb, int duration,
            TimeInterpolator interpolator, final Runnable onCompleteRunnable,
            final int animationEndStyle, View anchorView) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        mDropView.cancelAnimation();
        mDropView.requestLayout();

        // Set the anchor view if the page is scrolling
        if (anchorView != null) {
            mAnchorViewInitialScrollX = anchorView.getScrollX();
        }
        mAnchorView = anchorView;

        // Create and start the animation
        mDropAnim = new ValueAnimator();
        mDropAnim.setInterpolator(interpolator);
        mDropAnim.setDuration(duration);
        mDropAnim.setFloatValues(0f, 1f);
        mDropAnim.addUpdateListener(updateCb);
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                switch (animationEndStyle) {
                case ANIMATION_END_DISAPPEAR:
                    clearAnimatedView();
                    break;
                case ANIMATION_END_REMAIN_VISIBLE:
                    break;
                }
                mDropAnim = null;
            }
        });
        mDropAnim.start();
    }

    public void clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        mDropAnim = null;
        if (mDropView != null) {
            mDragController.onDeferredEndDrag(mDropView);
        }
        mDropView = null;
        invalidate();
    }

    public View getAnimatedView() {
        return mDropView;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateChildIndices();
        UiFactory.onLauncherStateOrFocusChanged(mActivity);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateChildIndices();
        UiFactory.onLauncherStateOrFocusChanged(mActivity);
    }

    @Override
    public void bringChildToFront(View child) {
        super.bringChildToFront(child);
        updateChildIndices();
    }

    private void updateChildIndices() {
        mTopViewIndex = -1;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i) instanceof DragView) {
                mTopViewIndex = i;
            }
        }
        mChildCountOnLastUpdate = childCount;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mChildCountOnLastUpdate != childCount) {
            // between platform versions 17 and 18, behavior for onChildViewRemoved / Added changed.
            // Pre-18, the child was not added / removed by the time of those callbacks. We need to
            // force update our representation of things here to avoid crashing on pre-18 devices
            // in certain instances.
            updateChildIndices();
        }

        // i represents the current draw iteration
        if (mTopViewIndex == -1) {
            // in general we do nothing
            return i;
        } else if (i == childCount - 1) {
            // if we have a top index, we return it when drawing last item (highest z-order)
            return mTopViewIndex;
        } else if (i < mTopViewIndex) {
            return i;
        } else {
            // for indexes greater than the top index, we fetch one item above to shift for the
            // displacement of the top index
            return i + 1;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the background below children.
        mOverviewScrim.updateCurrentScrimmedView(this);
        mFocusIndicatorHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mOverviewScrim.getScrimmedView()) {
            mOverviewScrim.draw(canvas);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        mOverviewScrim.onInsetsChanged();
    }

    public OverviewScrim getOverviewScrim() {
        return mOverviewScrim;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        RotationMode rotation = mActivity.getRotationMode();
        int count = getChildCount();

        if (!rotation.isTransposed
                || getMode(widthMeasureSpec) != EXACTLY
                || getMode(heightMeasureSpec) != EXACTLY) {

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                child.setRotation(rotation.surfaceRotation);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                if (!(child instanceof Transposable)) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                } else {
                    measureChildWithMargins(child, heightMeasureSpec, 0, widthMeasureSpec, 0);

                    child.setPivotX(child.getMeasuredWidth() / 2);
                    child.setPivotY(child.getMeasuredHeight() / 2);
                    child.setRotation(rotation.surfaceRotation);
                }
            }
            setMeasuredDimension(getSize(widthMeasureSpec), getSize(heightMeasureSpec));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        RotationMode rotation = mActivity.getRotationMode();
        if (!rotation.isTransposed) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        final int count = getChildCount();

        final int parentWidth = right - left;
        final int parentHeight = bottom - top;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

            if (lp instanceof LayoutParams) {
                final LayoutParams dlp = (LayoutParams) lp;
                if (dlp.customPosition) {
                    child.layout(dlp.x, dlp.y, dlp.x + dlp.width, dlp.y + dlp.height);
                    continue;
                }
            }

            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();

            int childLeft;
            int childTop;

            int gravity = lp.gravity;
            if (gravity == -1) {
                gravity = Gravity.TOP | Gravity.START;
            }

            final int layoutDirection = getLayoutDirection();

            int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);

            if (child instanceof Transposable) {
                absoluteGravity = rotation.toNaturalGravity(absoluteGravity);

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childTop = (parentHeight - height) / 2 +
                                lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.RIGHT:
                        childTop = width / 2 + lp.rightMargin - height / 2;
                        break;
                    case Gravity.LEFT:
                    default:
                        childTop = parentHeight - lp.leftMargin - width / 2 - height / 2;
                }

                switch (absoluteGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.CENTER_VERTICAL:
                        childLeft = (parentWidth - width) / 2 +
                                lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.BOTTOM:
                        childLeft = parentWidth - width / 2 - height / 2 - lp.bottomMargin;
                        break;
                    case Gravity.TOP:
                    default:
                        childLeft = height / 2 - width / 2 + lp.topMargin;
                }
            } else {
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (parentWidth - width) / 2 +
                                lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = parentWidth - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (absoluteGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.TOP:
                        childTop = lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = (parentHeight - height) / 2 +
                                lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = parentHeight - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }
            }

            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }
}
