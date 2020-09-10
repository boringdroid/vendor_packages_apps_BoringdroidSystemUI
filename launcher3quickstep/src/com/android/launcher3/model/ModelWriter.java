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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Class for handling model updates.
 */
public class ModelWriter {
    private final Context mContext;
    private final LauncherModel mModel;
    private final BgDataModel mBgDataModel;
    private final Handler mUiHandler;

    private final boolean mVerifyChanges;

    // Keep track of delete operations that occur when an Undo option is present; we may not commit.
    private final List<Runnable> mDeleteRunnables = new ArrayList<>();
    private boolean mPreparingToUndo;

    public ModelWriter(Context context, LauncherModel model, BgDataModel dataModel,
                       boolean verifyChanges) {
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mVerifyChanges = verifyChanges;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(ItemInfoMatcher matcher) {
        deleteItemsFromDatabase(matcher.filterItemInfos(mBgDataModel.itemsIdMap));
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Collection<? extends ItemInfo> items) {
        ModelVerifier verifier = new ModelVerifier();
        enqueueDeleteRunnable(() -> {
            for (ItemInfo item : items) {
                mBgDataModel.removeItem(item);
                verifier.verifyModel();
            }
        });
    }

    private void enqueueDeleteRunnable(Runnable r) {
        if (mPreparingToUndo) {
            mDeleteRunnables.add(r);
        } else {
            ((Executor) MODEL_EXECUTOR).execute(r);
        }
    }

    /**
     * Utility class to verify model updates are propagated properly to the callback.
     */
    public class ModelVerifier {

        final int startId;

        ModelVerifier() {
            startId = mBgDataModel.lastBindId;
        }

        void verifyModel() {
            if (!mVerifyChanges || mModel.getCallback() == null) {
                return;
            }

            int executeId = mBgDataModel.lastBindId;

            mUiHandler.post(() -> {
                int currentId = mBgDataModel.lastBindId;
                if (currentId > executeId) {
                    // Model was already bound after job was executed.
                    return;
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return;
                }
                // Bound model was changed between submitting the job and executing the job
                Callbacks callbacks = mModel.getCallback();
                if (callbacks != null) {
                    callbacks.rebindModel();
                }
            });
        }
    }
}
