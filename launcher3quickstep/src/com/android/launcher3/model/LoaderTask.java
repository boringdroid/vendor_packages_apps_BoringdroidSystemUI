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

import static com.android.launcher3.model.LoaderResults.filterCurrentWorkspaceItems;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherActivityCachingLogic;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.TraceHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    private final LauncherAppState mApp;
    private final AllAppsList mBgAllAppsList;
    private final BgDataModel mBgDataModel;

    private FirstScreenBroadcast mFirstScreenBroadcast;

    private final LoaderResults mResults;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final PackageInstallerCompat mPackageInstaller;
    private final IconCache mIconCache;

    private boolean mStopped;

    public LoaderTask(LauncherAppState app, AllAppsList bgAllAppsList, BgDataModel dataModel,
            LoaderResults results) {
        mApp = app;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = dataModel;
        mResults = results;

        mLauncherApps = LauncherAppsCompat.getInstance(mApp.getContext());
        mUserManager = UserManagerCompat.getInstance(mApp.getContext());
        mPackageInstaller = PackageInstallerCompat.getInstance(mApp.getContext());
        mIconCache = mApp.getIconCache();
    }

    protected synchronized void waitForIdle() {
        // Wait until the either we're stopped or the other threads are done.
        // This way we don't start loading all apps until the workspace has settled
        // down.
        LooperIdleLock idleLock = mResults.newIdleLock(this);
        // Just in case mFlushingWorkerThread changes but we aren't woken up,
        // wait no longer than 1sec at a time
        while (!mStopped && idleLock.awaitLocked(1000));
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    private void sendFirstScreenActiveInstallsBroadcast() {
        ArrayList<ItemInfo> firstScreenItems = new ArrayList<>();

        ArrayList<ItemInfo> allItems = new ArrayList<>();
        synchronized (mBgDataModel) {
            allItems.addAll(mBgDataModel.workspaceItems);
        }
        // Screen set is never empty
        final int firstScreen = mBgDataModel.collectWorkspaceScreens().get(0);

        filterCurrentWorkspaceItems(firstScreen, allItems, firstScreenItems,
                new ArrayList<>() /* otherScreenItems are ignored */);
        mFirstScreenBroadcast.sendBroadcasts(mApp.getContext(), firstScreenItems);
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        TraceHelper.beginSection(TAG);
        try (LauncherModel.LoaderTransaction transaction = mApp.getModel().beginLoader(this)) {
            TraceHelper.partitionSection(TAG, "step 1.1: loading workspace");
            loadWorkspace();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 1.2: bind workspace workspace");

            // Notify the installer packages of packages with active installs on the first screen.
            TraceHelper.partitionSection(TAG, "step 1.3: send first screen broadcast");
            sendFirstScreenActiveInstallsBroadcast();

            // Take a break
            TraceHelper.partitionSection(TAG, "step 1 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // second step
            TraceHelper.partitionSection(TAG, "step 2.1: loading all apps");
            List<LauncherActivityInfo> allActivityList = loadAllApps();

            TraceHelper.partitionSection(TAG, "step 2.2: Binding all apps");
            verifyNotStopped();

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 2.3: Update icon cache");
            IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
            setIgnorePackages(updateHandler);
            updateHandler.updateIcons(allActivityList,
                    LauncherActivityCachingLogic.newInstance(mApp.getContext()),
                    (updatedPackages, updatedPackages2) -> mApp.getModel().onPackageIconsUpdated());

            // Take a break
            TraceHelper.partitionSection(TAG, "step 2 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            // third step
            TraceHelper.partitionSection(TAG, "step 3.1: loading deep shortcuts");

            verifyNotStopped();
            TraceHelper.partitionSection(TAG, "step 3.2: bind deep shortcuts");

            // Take a break
            TraceHelper.partitionSection(TAG, "step 3 completed, wait for idle");
            waitForIdle();
            verifyNotStopped();

            TraceHelper.partitionSection(TAG, "step 5: Finish icon cache update");
            updateHandler.finish();

            transaction.commit();
        } catch (CancellationException e) {
            // Loader stopped, ignore
            TraceHelper.partitionSection(TAG, "Cancelled");
        }
        TraceHelper.endSection(TAG);
    }

    public synchronized void stopLocked() {
        mStopped = true;
        this.notify();
    }

    private void loadWorkspace() {
        final Context context = mApp.getContext();
        final ContentResolver contentResolver = context.getContentResolver();
        final PackageManagerHelper pmHelper = new PackageManagerHelper(context);
        final boolean isSdCardReady = Utilities.isBootCompleted();
        final MultiHashMap<UserHandle, String> pendingPackages = new MultiHashMap<>();

        boolean clearDb = false;

        if (!clearDb && !GridSizeMigrationTask.migrateGridIfNeeded(context)) {
            // Migration failed. Clear workspace.
            clearDb = true;
        }

        if (clearDb) {
            Log.d(TAG, "loadWorkspace: resetting launcher database");
            LauncherSettings.Settings.call(contentResolver,
                    LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        }

        Log.d(TAG, "loadWorkspace: loading default favorites");
        LauncherSettings.Settings.call(contentResolver,
                LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES);

        synchronized (mBgDataModel) {
            mBgDataModel.clear();

            final HashMap<PackageUserKey, SessionInfo> installingPkgs =
                    mPackageInstaller.updateAndGetActiveSessionCache();
            final PackageUserKey tempPackageKey = new PackageUserKey(null, null);
            mFirstScreenBroadcast = new FirstScreenBroadcast(installingPkgs);

            final LoaderCursor c = new LoaderCursor(contentResolver.query(
                    LauncherSettings.Favorites.CONTENT_URI, null, null, null, null), mApp);

            try {

                final LongSparseArray<UserHandle> allUsers = c.allUsers;
                final LongSparseArray<Boolean> quietMode = new LongSparseArray<>();
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                for (UserHandle user : mUserManager.getUserProfiles()) {
                    long serialNo = mUserManager.getSerialNumberForUser(user);
                    allUsers.put(serialNo, user);
                    quietMode.put(serialNo, mUserManager.isQuietModeEnabled(user));

                    boolean userUnlocked = mUserManager.isUserUnlocked(user);

                    // We can only query for shortcuts when the user is unlocked.
                    if (userUnlocked) {
                        userUnlocked = false;
                    }
                    unlockedUsers.put(serialNo, userUnlocked);
                }

                Intent intent;
                String targetPkg;

                while (!mStopped && c.moveToNext()) {
                    try {
                        if (c.user == null) {
                            // User has been deleted, remove the item.
                            c.markDeleted();
                            continue;
                        }

                        switch (c.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            intent = c.parseIntent();
                            if (intent == null) {
                                c.markDeleted();
                                continue;
                            }

                            ComponentName cn = intent.getComponent();
                            targetPkg = cn == null ? intent.getPackage() : cn.getPackageName();

                            if (allUsers.indexOfValue(c.user) < 0) {
                                if (c.restoreFlag != 0) {
                                    // Don't restore items for other profiles.
                                    c.markDeleted();
                                    continue;
                                }
                            }
                            if (TextUtils.isEmpty(targetPkg)) {
                                c.markDeleted();
                                continue;
                            }

                            // If there is no target package, its an implicit intent
                            // (legacy shortcut) which is always valid
                            boolean validTarget = TextUtils.isEmpty(targetPkg) ||
                                    mLauncherApps.isPackageEnabledForProfile(targetPkg, c.user);

                            // If it's a deep shortcut, we'll use pinned shortcuts to restore it
                            if (cn != null && validTarget) {
                                // If the apk is present and the shortcut points to a specific
                                // component.

                                // If the component is already present
                                if (mLauncherApps.isActivityEnabledForProfile(cn, c.user)) {
                                    // no special handling necessary for this item
                                    c.markRestored();
                                } else {
                                    // Gracefully try to find a fallback activity.
                                    intent = pmHelper.getAppLaunchIntent(targetPkg, c.user);
                                    if (intent != null) {
                                        c.restoreFlag = 0;
                                        c.updater().put(
                                                LauncherSettings.Favorites.INTENT,
                                                intent.toUri(0)).commit();
                                    } else {
                                        c.markDeleted();
                                        continue;
                                    }
                                }
                            }
                            // else if cn == null => can't infer much, leave it
                            // else if !validPkg => could be restored icon or missing sd-card

                            if (!TextUtils.isEmpty(targetPkg) && !validTarget) {
                                // Points to a valid app (superset of cn != null) but the apk
                                // is not available.

                                if (c.restoreFlag != 0) {
                                    tempPackageKey.update(targetPkg, c.user);
                                    if (installingPkgs.containsKey(tempPackageKey)) {
                                        // App restore has started. Update the flag
                                        c.updater().put(LauncherSettings.Favorites.RESTORED,
                                                c.restoreFlag).commit();
                                    } else {
                                        c.markDeleted();
                                        continue;
                                    }
                                } else if (pmHelper.isAppOnSdcard(targetPkg, c.user)) {
                                    // Add the icon on the workspace anyway.
                                } else if (!isSdCardReady) {
                                    // SdCard is not ready yet. Package might get available,
                                    // once it is ready.
                                    Log.d(TAG, "Missing pkg, will check later: " + targetPkg);
                                    pendingPackages.addToList(c.user, targetPkg);
                                    // Add the icon on the workspace anyway.
                                } else {
                                    // Do not wait for external media load anymore.
                                    c.markDeleted();
                                    continue;
                                }
                            }

                            if (validTarget) {
                                // The shortcut points to a valid target (either no target
                                // or something which is ready to be used)
                                c.markRestored();
                            }

                            if (c.restoreFlag != 0) {
                                // Already verified above that user is same as default user
                            } else if (c.itemType ==
                                    LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                            } else { // item type == ITEM_TYPE_SHORTCUT
                                // Shortcuts are only available on the primary profile
                                if (!TextUtils.isEmpty(targetPkg)
                                        && pmHelper.isAppSuspended(targetPkg, c.user)) {
                                }

                                // App shortcuts that used to be automatically added to Launcher
                                // didn't always have the correct intent flags set, so do that
                                // here
                                if (intent.getAction() != null &&
                                    intent.getCategories() != null &&
                                    intent.getAction().equals(Intent.ACTION_MAIN) &&
                                    intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                                    intent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                }
                            }
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Desktop items loading interrupted", e);
                    }
                }
            } finally {
                IOUtils.closeSilently(c);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            c.commitRestoredItems();
            if (!isSdCardReady && !pendingPackages.isEmpty()) {
                context.registerReceiver(
                        new SdCardAvailableReceiver(mApp, pendingPackages),
                        new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                        null,
                        MODEL_EXECUTOR.getHandler());
            }
        }
    }

    private void setIgnorePackages(IconCacheUpdateHandler updateHandler) {
        // Ignore packages which have a promise icon.
        HashSet<String> packagesToIgnore = new HashSet<>();
        updateHandler.setPackagesToIgnore(Process.myUserHandle(), packagesToIgnore);
    }

    private List<LauncherActivityInfo> loadAllApps() {
        final List<UserHandle> profiles = mUserManager.getUserProfiles();
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();
        for (UserHandle user : profiles) {
            // Query for the set of apps
            final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            // TODO: Fix this. Only fail for the current user.
            if (apps == null || apps.isEmpty()) {
                return allActivityList;
            }
            boolean quietMode = mUserManager.isQuietModeEnabled(user);
            // Create the ApplicationInfos
            for (int i = 0; i < apps.size(); i++) {
                LauncherActivityInfo app = apps.get(i);
                // This builds the icon bitmaps.
                mBgAllAppsList.add(new AppInfo(app, user, quietMode), app);
            }
            allActivityList.addAll(apps);
        }

        if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
            // get all active sessions and add them to the all apps list
            for (PackageInstaller.SessionInfo info :
                    mPackageInstaller.getAllVerifiedSessions()) {
                mBgAllAppsList.addPromiseApp(mApp.getContext(),
                        PackageInstallerCompat.PackageInstallInfo.fromInstallingState(info));
            }
        }

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }
}
