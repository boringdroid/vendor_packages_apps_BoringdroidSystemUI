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
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

import java.util.List;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

/**
 * Helper class to make drag-and-drop in a {@link CellLayout} accessible.
 */
public abstract class DragAndDropAccessibilityDelegate extends ExploreByTouchHelper
        implements OnClickListener {
    protected final CellLayout mView;
    protected final Context mContext;
    protected final LauncherAccessibilityDelegate mDelegate;

    private final Rect mTempRect = new Rect();

    public DragAndDropAccessibilityDelegate(CellLayout forView) {
        super(forView);
        mView = forView;
        mContext = mView.getContext();
        mDelegate = Launcher.getLauncher(mContext).getAccessibilityDelegate();
    }

    @Override
    protected int getVirtualViewAt(float x, float y) {
        return -1;
    }

    @Override
    protected void getVisibleVirtualViews(List<Integer> virtualViews) {
    }

    @Override
    protected boolean onPerformActionForVirtualView(int viewId, int action, Bundle args) {
        if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && viewId != INVALID_ID) {
            String confirmation = "";
            mDelegate.handleAccessibleDrop(mView, getItemBounds(viewId), confirmation);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        onPerformActionForVirtualView(getFocusedVirtualView(),
                AccessibilityNodeInfoCompat.ACTION_CLICK, null);
    }

    @Override
    protected void onPopulateEventForVirtualView(int id, AccessibilityEvent event) {
        if (id == INVALID_ID) {
            throw new IllegalArgumentException("Invalid virtual view id");
        }
        event.setContentDescription(mContext.getString(R.string.action_move_here));
    }

    @Override
    protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat node) {
        if (id == INVALID_ID) {
            throw new IllegalArgumentException("Invalid virtual view id");
        }

        node.setBoundsInParent(getItemBounds(id));

        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        node.setClickable(true);
        node.setFocusable(true);
    }

    private Rect getItemBounds(int id) {
        int cellX = id % mView.getCountX();
        int cellY = id / mView.getCountX();
        LauncherAccessibilityDelegate.DragInfo dragInfo = mDelegate.getDragInfo();
        mView.cellToRect(cellX, cellY, dragInfo.info.spanX, dragInfo.info.spanY, mTempRect);
        return mTempRect;
    }
}