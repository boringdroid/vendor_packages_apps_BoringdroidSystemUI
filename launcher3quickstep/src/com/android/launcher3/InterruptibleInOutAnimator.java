/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.Property;

import com.android.launcher3.util.Thunk;

/**
 * A convenience class for two-way animations, e.g. a fadeIn/fadeOut animation.
 * With a regular ValueAnimator, if you call reverse to show the 'out' animation, you'll get
 * a frame-by-frame mirror of the 'in' animation -- i.e., the interpolated values will
 * be exactly reversed. Using this class, both the 'in' and the 'out' animation use the
 * interpolator in the same direction.
 */
public class InterruptibleInOutAnimator {

    private static final Property<InterruptibleInOutAnimator, Float> VALUE =
            new Property<InterruptibleInOutAnimator, Float>(Float.TYPE, "value") {
                @Override
                public Float get(InterruptibleInOutAnimator anim) {
                    return anim.mValue;
                }

                @Override
                public void set(InterruptibleInOutAnimator anim, Float value) {
                    anim.mValue = value;
                }
            };

    private ValueAnimator mAnimator;

    private float mValue;

    private Object mTag = null;

    private static final int STOPPED = 0;

    // TODO: This isn't really necessary, but is here to help diagnose a bug in the drag viz
    @Thunk int mDirection = STOPPED;

    public InterruptibleInOutAnimator(long duration, float fromValue, float toValue) {
        mAnimator = ObjectAnimator.ofFloat(this, VALUE, fromValue, toValue).setDuration(duration);

        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDirection = STOPPED;
            }
        });
    }

    public void cancel() {
        mAnimator.cancel();
        mDirection = STOPPED;
    }

    public void end() {
        mAnimator.end();
        mDirection = STOPPED;
    }

    /**
     * Return true when the animation is not running and it hasn't even been started.
     */
    public boolean isStopped() {
        return mDirection == STOPPED;
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    public Object getTag() {
        return mTag;
    }

    public ValueAnimator getAnimator() {
        return mAnimator;
    }
}
