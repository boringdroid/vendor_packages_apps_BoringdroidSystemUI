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

import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;

/**
 * Interface defining an object that can receive a drag.
 *
 */
public interface DropTarget {

    class DragObject {
        public int x = -1;
        public int y = -1;

        /** This indicates whether a drag is in final stages, either drop or cancel. It
         * differentiates onDragExit, since this is called when the drag is ending, above
         * the current drag target, or when the drag moves off the current drag object.
         */
        public boolean dragComplete = false;

        /** The view that moves around while you drag.  */
        public DragView dragView = null;

        /** Where the drag originated */
        public DragSource dragSource = null;

        /** Indicates that the drag operation was cancelled */
        public boolean cancelled = false;

        /** Defers removing the DragView from the DragLayer until after the drop animation. */
        public boolean deferDragViewCleanupPostAnimation = true;

        public DragObject() {
        }

    }

    void onDragExit(DragObject dragObject);
}
