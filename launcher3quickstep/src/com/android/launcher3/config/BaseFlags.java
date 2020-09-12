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
