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

package com.android.launcher3.model;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.os.Looper;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LooperIdleLock;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Executor;

/**
 * Base Helper class to handle results of {@link com.android.launcher3.model.LoaderTask}.
 */
public abstract class BaseLoaderResults {

    protected final Executor mUiExecutor;

    protected final LauncherAppState mApp;
    protected final BgDataModel mBgDataModel;
    protected final int mPageToBindFirst;

    protected final WeakReference<Callbacks> mCallbacks;

    public BaseLoaderResults(LauncherAppState app, BgDataModel dataModel,
                             int pageToBindFirst, WeakReference<Callbacks> callbacks) {
        mUiExecutor = MAIN_EXECUTOR;
        mApp = app;
        mBgDataModel = dataModel;
        mPageToBindFirst = pageToBindFirst;
        mCallbacks = callbacks == null ? new WeakReference<>(null) : callbacks;
    }

    /** Filters the set of items who are directly or indirectly (via another container) on the
     * specified screen. */
    public static <T extends ItemInfo> void filterCurrentWorkspaceItems(int currentScreenId,
            ArrayList<T> allWorkspaceItems,
            ArrayList<T> currentScreenItems,
            ArrayList<T> otherScreenItems) {
        // Purge any null ItemInfos
        Iterator<T> iter = allWorkspaceItems.iterator();
        while (iter.hasNext()) {
            ItemInfo i = iter.next();
            if (i == null) {
                iter.remove();
            }
        }

        // Order the set of items by their containers first, this allows use to walk through the
        // list sequentially, build up a list of containers that are in the specified screen,
        // as well as all items in those containers.
        IntSet itemsOnScreen = new IntSet();
        Collections.sort(allWorkspaceItems,
                (lhs, rhs) -> Integer.compare(lhs.container, rhs.container));

        for (T info : allWorkspaceItems) {
            if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (info.screenId == currentScreenId) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            } else {
                if (itemsOnScreen.contains(info.container)) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    otherScreenItems.add(info);
                }
            }
        }
    }

    public LooperIdleLock newIdleLock(Object lock) {
        LooperIdleLock idleLock = new LooperIdleLock(lock, Looper.getMainLooper());
        // If we are not binding, there is no reason to wait for idle.
        if (mCallbacks.get() == null) {
            idleLock.queueIdle();
        }
        return idleLock;
    }
}
