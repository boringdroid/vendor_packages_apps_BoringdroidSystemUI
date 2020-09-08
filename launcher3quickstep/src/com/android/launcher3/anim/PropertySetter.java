/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.util.Property;

/**
 * Utility class for setting a property with or without animation
 */
public class PropertySetter {

    public static final PropertySetter NO_ANIM_PROPERTY_SETTER = new PropertySetter();

    public <T> void setFloat(T target, Property<T, Float> property, float value,
            TimeInterpolator interpolator) {
        property.set(target, value);
    }

    public static class AnimatedPropertySetter extends PropertySetter {

        private final long mDuration;
        private final AnimatorSetBuilder mStateAnimator;

        public AnimatedPropertySetter(long duration, AnimatorSetBuilder builder) {
            mDuration = duration;
            mStateAnimator = builder;
        }

        @Override
        public <T> void setFloat(T target, Property<T, Float> property, float value,
                TimeInterpolator interpolator) {
            if (property.get(target) == value) {
                return;
            }
            Animator anim = ObjectAnimator.ofFloat(target, property, value);
            anim.setDuration(mDuration).setInterpolator(interpolator);
            mStateAnimator.play(anim);
        }

    }
}
