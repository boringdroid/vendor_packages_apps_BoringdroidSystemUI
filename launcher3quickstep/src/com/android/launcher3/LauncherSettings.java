/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.provider.BaseColumns;

/**
 * Settings related utilities.
 */
public class LauncherSettings {

    /**
     * Favorites.
     */
    public static final class Favorites implements BaseColumns {

        /**
         * The type of the gesture
         *
         * <P>Type: INTEGER</P>
         */
        public static final String ITEM_TYPE = "itemType";

        /**
         * The gesture is a package
         */
        public static final int ITEM_TYPE_NON_ACTIONABLE = -1;
        /**
         * The gesture is an application
         */
        public static final int ITEM_TYPE_APPLICATION = 0;

        /**
         * The container holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String CONTAINER = "container";

        /**
         * The icon is a resource identified by a package name and an integer id.
         */
        public static final int CONTAINER_DESKTOP = -100;
        public static final int CONTAINER_PREDICTION = -102;

        static final String containerToString(int container) {
            switch (container) {
                case CONTAINER_DESKTOP: return "desktop";
                case CONTAINER_PREDICTION: return "prediction";
                default: return String.valueOf(container);
            }
        }

        static final String itemTypeToString(int type) {
            switch(type) {
                case ITEM_TYPE_APPLICATION: return "APP";
                default: return String.valueOf(type);
            }
        }

        /**
         * The screen holding the favorite (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        public static final String SCREEN = "screen";

        /**
         * The X coordinate of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String CELLX = "cellX";

        /**
         * The Y coordinate of the cell holding the favorite
         * (if container is CONTAINER_DESKTOP)
         * <P>Type: INTEGER</P>
         */
        public static final String CELLY = "cellY";

        /**
         * The X span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String SPANX = "spanX";

        /**
         * The Y span of the cell holding the favorite
         * <P>Type: INTEGER</P>
         */
        public static final String SPANY = "spanY";

        /**
         * Indicates the position of the item inside an auto-arranged view like folder.
         * <p>Type: INTEGER</p>
         */
        public static final String RANK = "rank";

    }
}
