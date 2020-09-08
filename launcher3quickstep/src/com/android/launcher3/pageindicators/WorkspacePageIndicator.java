package com.android.launcher3.pageindicators;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * A PageIndicator that briefly shows a fraction of a line when moving between pages
 *
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
public class WorkspacePageIndicator extends View implements Insettable, PageIndicator {

    private static final int LINE_ANIMATE_DURATION = ViewConfiguration.getScrollBarFadeDuration();

    private static final int NUM_PAGES_ANIMATOR_INDEX = 1;
    private static final int ANIMATOR_COUNT = 3;

    private ValueAnimator[] mAnimators = new ValueAnimator[ANIMATOR_COUNT];

    private final Launcher mLauncher;

    // A float value representing the number of pages, to allow for an animation when it changes.
    private float mNumPagesFloat;
    private int mCurrentScroll;
    private int mTotalScroll;
    private Paint mLinePaint;
    private final int mLineHeight;

    private static final Property<WorkspacePageIndicator, Float> NUM_PAGES
            = new Property<WorkspacePageIndicator, Float>(Float.class, "num_pages") {
        @Override
        public Float get(WorkspacePageIndicator obj) {
            return obj.mNumPagesFloat;
        }

        @Override
        public void set(WorkspacePageIndicator obj, Float numPages) {
            obj.mNumPagesFloat = numPages;
            obj.invalidate();
        }
    };

    public WorkspacePageIndicator(Context context) {
        this(context, null);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = context.getResources();
        mLinePaint = new Paint();
        mLinePaint.setAlpha(0);

        mLauncher = Launcher.getLauncher(context);
        mLineHeight = res.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_line_height);

        boolean darkText = WallpaperColorInfo.getInstance(context).supportsDarkText();
        mLinePaint.setColor(darkText ? Color.BLACK : Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTotalScroll == 0 || mNumPagesFloat == 0) {
            return;
        }

        // Compute and draw line rect.
        float progress = Utilities.boundToRange(((float) mCurrentScroll) / mTotalScroll, 0f, 1f);
        int availableWidth = getWidth();
        int lineWidth = (int) (availableWidth / mNumPagesFloat);
        int lineLeft = (int) (progress * (availableWidth - lineWidth));
        int lineRight = lineLeft + lineWidth;

        canvas.drawRoundRect(lineLeft, getHeight() / 2 - mLineHeight / 2, lineRight,
                getHeight() / 2 + mLineHeight / 2, mLineHeight, mLineHeight, mLinePaint);
    }

    @Override
    public void setActiveMarker(int activePage) { }

    @Override
    public void setMarkersCount(int numMarkers) {
        if (Float.compare(numMarkers, mNumPagesFloat) != 0) {
            setupAndRunAnimation(ObjectAnimator.ofFloat(this, NUM_PAGES, numMarkers),
                    NUM_PAGES_ANIMATOR_INDEX);
        } else {
            if (mAnimators[NUM_PAGES_ANIMATOR_INDEX] != null) {
                mAnimators[NUM_PAGES_ANIMATOR_INDEX].cancel();
                mAnimators[NUM_PAGES_ANIMATOR_INDEX] = null;
            }
        }
    }

    /**
     * Starts the given animator and stores it in the provided index in {@link #mAnimators} until
     * the animation ends.
     *
     * If an animator is already at the index (i.e. it is already playing), it is canceled and
     * replaced with the new animator.
     */
    private void setupAndRunAnimation(ValueAnimator animator, final int animatorIndex) {
        if (mAnimators[animatorIndex] != null) {
            mAnimators[animatorIndex].cancel();
        }
        mAnimators[animatorIndex] = animator;
        mAnimators[animatorIndex].addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimators[animatorIndex] = null;
            }
        });
        mAnimators[animatorIndex].setDuration(LINE_ANIMATE_DURATION);
        mAnimators[animatorIndex].start();
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();

        if (grid.isVerticalBarLayout()) {
            Rect padding = grid.workspacePadding;
            lp.leftMargin = padding.left + grid.workspaceCellPaddingXPx;
            lp.rightMargin = padding.right + grid.workspaceCellPaddingXPx;
            lp.bottomMargin = padding.bottom;
        } else {
            lp.leftMargin = lp.rightMargin = 0;
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            lp.bottomMargin = insets.bottom;
        }
        setLayoutParams(lp);
    }
}
