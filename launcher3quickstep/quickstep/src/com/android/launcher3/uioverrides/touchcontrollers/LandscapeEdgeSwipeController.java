package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.EDGE_NAV_BAR;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.RecentsModel;

/**
 * Touch controller for handling edge swipes in landscape/seascape UI
 */
public class LandscapeEdgeSwipeController extends AbstractStateChangeTouchController {

    private static final String TAG = "LandscapeEdgeSwipeCtrl";

    public LandscapeEdgeSwipeController(Launcher l) {
        super(l, SingleAxisSwipeDetector.HORIZONTAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView() != null) {
            return false;
        }
        return mLauncher.isInState(NORMAL) && (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        boolean draggingFromNav = mLauncher.getDeviceProfile().isSeascape() == isDragTowardPositive;
        return draggingFromNav ? OVERVIEW : NORMAL;
    }

    @Override
    protected int getLogContainerTypeForNormalState(MotionEvent ev) {
        return LauncherLogProto.ContainerType.NAVBAR;
    }

    @Override
    protected float initCurrentAnimation(@AnimationComponents int animComponent) {
        long maxAccuracy = (long) (2);
        mCurrentAnimation = mLauncher.getStateManager().createAnimationToNewWorkspace(mToState,
                maxAccuracy, animComponent);
        return (mLauncher.getDeviceProfile().isSeascape() ? 2 : -2);
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mStartState == NORMAL && targetState == OVERVIEW) {
            RecentsModel.INSTANCE.get(mLauncher).onOverviewShown(true, TAG);
        }
    }
}
