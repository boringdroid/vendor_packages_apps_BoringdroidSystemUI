/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.util.Log;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Utilities;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;

/**
 * Utility class for helpful methods related to {@link TaskView} objects and their tasks.
 */
public final class TaskViewUtils {

    private static final String TAG = "TaskViewUtils";

    private TaskViewUtils() {}

    /**
     * @return Animator that controls the window of the opening targets for the recents launch
     * animation.
     */
    public static ValueAnimator getRecentsWindowAnimator(TaskView v, boolean skipViewChanges,
            RemoteAnimationTargetCompat[] targets, final ClipAnimationHelper inOutHelper) {
        SyncRtSurfaceTransactionApplierCompat applier =
                new SyncRtSurfaceTransactionApplierCompat(v);
        ClipAnimationHelper.TransformParams params = new ClipAnimationHelper.TransformParams()
                .setSyncTransactionApplier(applier);

        final RemoteAnimationTargetSet targetSet =
                new RemoteAnimationTargetSet(targets, MODE_OPENING);
        targetSet.addDependentTransactionApplier(applier);

        final RecentsView recentsView = v.getRecentsView();
        final ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setInterpolator(TOUCH_RESPONSE_INTERPOLATOR);
        appAnimator.addUpdateListener(new MultiValueUpdateListener() {

            // Defer fading out the view until after the app window gets faded in
            final FloatProp mViewAlpha = new FloatProp(1f, 0f, 75, 75, LINEAR);
            final FloatProp mTaskAlpha = new FloatProp(0f, 1f, 0, 75, LINEAR);


            final RectF mThumbnailRect;

            {
                inOutHelper.setTaskAlphaCallback((t, alpha) -> mTaskAlpha.value);

                inOutHelper.prepareAnimation(
                        BaseActivity.fromContext(v.getContext()).getDeviceProfile(),
                        true /* isOpening */);
                inOutHelper.fromTaskThumbnailView(v.getThumbnail(), (RecentsView) v.getParent(),
                        targetSet.apps.length == 0 ? null : targetSet.apps[0]);

                mThumbnailRect = new RectF(inOutHelper.getTargetRect());
                mThumbnailRect.offset(-v.getTranslationX(), -v.getTranslationY());
                Utilities.scaleRectFAboutCenter(mThumbnailRect, 1 / v.getScaleX());
            }

            @Override
            public void onUpdate(float percent) {
                // TODO: Take into account the current fullscreen progress for animating the insets
                params.setProgress(1 - percent);
                RectF taskBounds = inOutHelper.applyTransform(targetSet, params);
                // TODO recheck animation for task
                if (!skipViewChanges) {
                    float scale = taskBounds.width() / mThumbnailRect.width();
                    if (Float.isNaN(scale)) {
                        Log.d(TAG, "Ignore scaleX NaN");
                        return;
                    }
                    v.setScaleX(scale);
                    v.setScaleY(scale);
                    v.setTranslationX(taskBounds.centerX() - mThumbnailRect.centerX());
                    v.setTranslationY(taskBounds.centerY() - mThumbnailRect.centerY());
                    v.setAlpha(mViewAlpha.value);
                }
            }
        });
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                targetSet.release();
            }
        });
        return appAnimator;
    }
}
