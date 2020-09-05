/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.view.KeyEvent;

/**
 * Calculates the next item that a {@link KeyEvent} should change the focus to.
 *<p>
 * Note, this utility class calculates everything regards to icon index and its (x,y) coordinates.
 * Currently supports:
 * <ul>
 *  <li> full matrix of cells that are 1x1
 *  <li> sparse matrix of cells that are 1x1
 *     [ 1][  ][ 2][  ]
 *     [  ][  ][ 3][  ]
 *     [  ][ 4][  ][  ]
 *     [  ][ 5][ 6][ 7]
 * </ul>
 * *<p>
 * For testing, one can use a BT keyboard, or use following adb command.
 * ex. $ adb shell input keyevent 20 // KEYCODE_DPAD_LEFT
 */
public class FocusLogic {
    /**
     * Returns true only if this utility class handles the key code.
     */
    public static boolean shouldConsume(int keyCode) {
        return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END ||
                keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN);
    }
}
