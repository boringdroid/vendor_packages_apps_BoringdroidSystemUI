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

package com.android.launcher3.config;

import static androidx.core.util.Preconditions.checkNotNull;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.annotation.Keep;

import com.android.launcher3.uioverrides.TogglableFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a set of flags used to control various launcher behaviors.
 *
 * <p>All the flags should be defined here with appropriate default values.
 */
@Keep
public abstract class BaseFlags {

    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final List<TogglableFlag> sFlags = new ArrayList<>();

    BaseFlags() {
        throw new UnsupportedOperationException("Don't instantiate BaseFlags");
    }

    public static final boolean IS_DOGFOOD_BUILD = false;

    // When enabled the promise icon is visible in all apps while installation an app.
    public static final boolean LAUNCHER3_PROMISE_APPS_IN_ALL_APPS = false;

    // When enabled a promise icon is added to the home screen when install session is active.
    public static final TogglableFlag PROMISE_APPS_NEW_INSTALLS =
            new TogglableFlag("PROMISE_APPS_NEW_INSTALLS", true,
                    "Adds a promise icon to the home screen for new install sessions.");

    // When true, overview shows screenshots in the orientation they were taken rather than
    // trying to make them fit the orientation the device is in.
    public static final boolean OVERVIEW_USE_SCREENSHOT_ORIENTATION = true;

    /**
     * Feature flag to handle define config changes dynamically instead of killing the process.
     */
    public static final TogglableFlag APPLY_CONFIG_AT_RUNTIME = new TogglableFlag(
            "APPLY_CONFIG_AT_RUNTIME", true, "Apply display changes dynamically");

    public static final TogglableFlag QUICKSTEP_SPRINGS = new TogglableFlag("QUICKSTEP_SPRINGS",
            false, "Enable springs for quickstep animations");

    public static final TogglableFlag ADAPTIVE_ICON_WINDOW_ANIM = new TogglableFlag(
            "ADAPTIVE_ICON_WINDOW_ANIM", true,
            "Use adaptive icons for window animations.");

    public static final TogglableFlag ENABLE_QUICKSTEP_LIVE_TILE = new TogglableFlag(
            "ENABLE_QUICKSTEP_LIVE_TILE", false, "Enable live tile in Quickstep overview");

    public static final TogglableFlag ENABLE_HINTS_IN_OVERVIEW = new TogglableFlag(
            "ENABLE_HINTS_IN_OVERVIEW", true,
            "Show chip hints and gleams on the overview screen");

    public static final TogglableFlag FAKE_LANDSCAPE_UI = new TogglableFlag(
            "FAKE_LANDSCAPE_UI", false,
            "Rotate launcher UI instead of using transposed layout");

    public static final TogglableFlag APP_SEARCH_IMPROVEMENTS = new TogglableFlag(
            "APP_SEARCH_IMPROVEMENTS", true,
            "Adds localized title and keyword search and ranking");

    public static final TogglableFlag ASSISTANT_GIVES_LAUNCHER_FOCUS = new TogglableFlag(
            "ASSISTANT_GIVES_LAUNCHER_FOCUS", false,
            "Allow Launcher to handle nav bar gestures while Assistant is running over it");

    public static abstract class BaseTogglableFlag {
        private final String key;
        // should be value that is hardcoded in client side.
        // Comparatively, getDefaultValue() can be overridden.
        private final boolean defaultValue;
        private final String description;
        private boolean currentValue;

        @SuppressLint("RestrictedApi")
        public BaseTogglableFlag(
                String key,
                boolean defaultValue,
                String description) {
            this.key = checkNotNull(key);
            this.currentValue = this.defaultValue = defaultValue;
            this.description = checkNotNull(description);

            synchronized (sLock) {
                sFlags.add((TogglableFlag)this);
            }
        }

        public String getKey() {
            return key;
        }

        protected abstract boolean getOverridenDefaultValue(boolean value);

        protected abstract void addChangeListener(Context context, Runnable r);

        boolean getDefaultValue() {
            return getOverridenDefaultValue(defaultValue);
        }

        /** Returns the value of the flag at process start, including any overrides present. */
        public boolean get() {
            return currentValue;
        }

        String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "TogglableFlag{"
                    + "key=" + key + ", "
                    + "defaultValue=" + defaultValue + ", "
                    + "overriddenDefaultValue=" + getOverridenDefaultValue(defaultValue) + ", "
                    + "currentValue=" + currentValue + ", "
                    + "description=" + description
                    + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof TogglableFlag) {
                BaseTogglableFlag that = (BaseTogglableFlag) o;
                return (this.key.equals(that.getKey()))
                        && (this.getDefaultValue() == that.getDefaultValue())
                        && (this.description.equals(that.getDescription()));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h$ = 1;
            h$ *= 1000003;
            h$ ^= key.hashCode();
            h$ *= 1000003;
            h$ ^= getDefaultValue() ? 1231 : 1237;
            h$ *= 1000003;
            h$ ^= description.hashCode();
            return h$;
        }
    }
}
