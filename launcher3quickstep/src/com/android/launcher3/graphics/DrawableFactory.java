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

package com.android.launcher3.graphics;

import static com.android.launcher3.graphics.IconShape.getShapePath;
import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.content.Context;
import android.content.pm.ActivityInfo;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Factory for creating new drawables.
 */
public class DrawableFactory implements ResourceBasedOverride {

    public static final MainThreadInitializedObject<DrawableFactory> INSTANCE =
            forOverride(DrawableFactory.class, R.string.drawable_factory_class);

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Context context, ItemInfoWithIcon info) {
        FastBitmapDrawable drawable = info.usingLowResIcon()
                ? new PlaceHolderIconDrawable(info, getShapePath(), context)
                : new FastBitmapDrawable(info);
        drawable.setIsDisabled(info.isDisabled());
        return drawable;
    }

    public FastBitmapDrawable newIcon(Context context, BitmapInfo info, ActivityInfo target) {
        return info.isLowRes()
                ? new PlaceHolderIconDrawable(info, getShapePath(), context)
                : new FastBitmapDrawable(info);
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public PreloadIconDrawable newPendingIcon(Context context, ItemInfoWithIcon info) {
        return new PreloadIconDrawable(info, getShapePath(), context);
    }
}
