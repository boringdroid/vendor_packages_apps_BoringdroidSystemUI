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

package com.android.launcher3.accessibility;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.CellLayout;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.dragndrop.DragLayer;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Implementation of {@link DragAndDropAccessibilityDelegate} to support DnD on workspace.
 */
public class WorkspaceAccessibilityHelper extends DragAndDropAccessibilityDelegate {

    private final Rect mTempRect = new Rect();
    private final int[] mTempCords = new int[2];

    public WorkspaceAccessibilityHelper(CellLayout layout) {
        super(layout);
    }

    @Override
    protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
        super.onPopulateNodeForVirtualView(id, node);


        // ExploreByTouchHelper does not currently handle view scale.
        // Update BoundsInScreen to appropriate value.
        DragLayer dragLayer = Launcher.getLauncher(mView.getContext()).getDragLayer();
        mTempCords[0] = mTempCords[1] = 0;
        float scale = dragLayer.getDescendantCoordRelativeToSelf(mView, mTempCords);

        node.getBoundsInParent(mTempRect);
        mTempRect.left = mTempCords[0] + (int) (mTempRect.left * scale);
        mTempRect.right = mTempCords[0] + (int) (mTempRect.right * scale);
        mTempRect.top = mTempCords[1] + (int) (mTempRect.top * scale);
        mTempRect.bottom = mTempCords[1] + (int) (mTempRect.bottom * scale);
        node.setBoundsInScreen(mTempRect);
    }

    public static String getDescriptionForDropOver(View overChild, Context context) {
        ItemInfo info = (ItemInfo) overChild.getTag();
        if (info instanceof WorkspaceItemInfo) {
            return context.getString(R.string.create_folder_with, info.title);
        }
        return "";
    }
}
