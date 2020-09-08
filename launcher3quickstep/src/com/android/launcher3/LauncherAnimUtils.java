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

import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Property;
import android.view.View;

public class LauncherAnimUtils {
    public static final int OVERVIEW_TRANSITION_MS = 250;
    public static final int SPRING_LOADED_TRANSITION_MS = 150;
    public static final int SPRING_LOADED_EXIT_DELAY = 500;

    public static final Property<Drawable, Integer> DRAWABLE_ALPHA =
            new Property<Drawable, Integer>(Integer.TYPE, "drawableAlpha") {
                @Override
                public Integer get(Drawable drawable) {
                    return drawable.getAlpha();
                }

                @Override
                public void set(Drawable drawable, Integer alpha) {
                    drawable.setAlpha(alpha);
                }
            };

    public static final FloatProperty<View> SCALE_PROPERTY =
            new FloatProperty<View>("scale") {
                @Override
                public Float get(View view) {
                    return view.getScaleX();
                }

                @Override
                public void setValue(View view, float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            };

    /** Increase the duration if we prevented the fling, as we are going against a high velocity. */
    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }

    public static final FloatProperty<View> VIEW_TRANSLATE_X =
            View.TRANSLATION_X instanceof FloatProperty ? (FloatProperty) View.TRANSLATION_X
                    : new FloatProperty<View>("translateX") {
                        @Override
                        public void setValue(View view, float v) {
                            view.setTranslationX(v);
                        }

                        @Override
                        public Float get(View view) {
                            return view.getTranslationX();
                        }
                    };

    public static final FloatProperty<View> VIEW_TRANSLATE_Y =
            View.TRANSLATION_Y instanceof FloatProperty ? (FloatProperty) View.TRANSLATION_Y
                    : new FloatProperty<View>("translateY") {
                        @Override
                        public void setValue(View view, float v) {
                            view.setTranslationY(v);
                        }

                        @Override
                        public Float get(View view) {
                            return view.getTranslationY();
                        }
                    };
}
