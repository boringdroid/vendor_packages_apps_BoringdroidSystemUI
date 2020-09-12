/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.WallpaperColorInfo;

/**
 * Various utility methods associated with theming.
 */
public class Themes {

    public static int getActivityThemeRes(Context context) {
        WallpaperColorInfo wallpaperColorInfo = WallpaperColorInfo.getInstance(context);
        boolean darkTheme;
        if (Utilities.ATLEAST_Q) {
            Configuration configuration = context.getResources().getConfiguration();
            int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            darkTheme = nightMode == Configuration.UI_MODE_NIGHT_YES;
        } else {
            darkTheme = wallpaperColorInfo.isDark();
        }

        if (darkTheme) {
            return wallpaperColorInfo.supportsDarkText() ?
                    R.style.AppTheme_Dark_DarkText : wallpaperColorInfo.isMainColorDark() ?
                            R.style.AppTheme_Dark_DarkMainColor : R.style.AppTheme_Dark;
        } else {
            return wallpaperColorInfo.supportsDarkText() ?
                    R.style.AppTheme_DarkText : wallpaperColorInfo.isMainColorDark() ?
                            R.style.AppTheme_DarkMainColor : R.style.AppTheme;
        }
    }

    public static String getDefaultBodyFont(Context context) {
        TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault,
                new int[]{android.R.attr.fontFamily});
        String value = ta.getString(0);
        ta.recycle();
        return value;
    }

    public static float getDialogCornerRadius(Context context) {
        return getDimension(context, android.R.attr.dialogCornerRadius,
                context.getResources().getDimension(R.dimen.default_dialog_corner_radius));
    }

    public static float getDimension(Context context, int attr, float defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        float value = ta.getDimension(0, defaultValue);
        ta.recycle();
        return value;
    }

    public static int getColorAccent(Context context) {
        return getAttrColor(context, android.R.attr.colorAccent);
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static boolean getAttrBoolean(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        boolean value = ta.getBoolean(0, false);
        ta.recycle();
        return value;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        Drawable value = ta.getDrawable(0);
        ta.recycle();
        return value;
    }

    /**
     * Creates a map for attribute-name to value for all the values in {@param attrs} which can be
     * held in memory for later use.
     */
    public static SparseArray<TypedValue> createValueMap(Context context, AttributeSet attrSet,
            IntArray keysToIgnore) {
        int count = attrSet.getAttributeCount();
        IntArray attrNameArray = new IntArray(count);
        for (int i = 0; i < count; i++) {
            attrNameArray.add(attrSet.getAttributeNameResource(i));
        }
        attrNameArray.removeAllValues(keysToIgnore);

        int[] attrNames = attrNameArray.toArray();
        SparseArray<TypedValue> result = new SparseArray<>(attrNames.length);
        TypedArray ta = context.obtainStyledAttributes(attrSet, attrNames);
        for (int i = 0; i < attrNames.length; i++) {
            TypedValue tv = new TypedValue();
            ta.getValue(i, tv);
            result.put(attrNames[i], tv);
        }

        return result;
    }
}
