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

package com.android.launcher3.icons;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.IconProvider;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.BaseFlags;
import com.android.launcher3.icons.cache.BaseIconCache;
import com.android.launcher3.icons.cache.CachingLogic;

import java.util.function.Supplier;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache extends BaseIconCache {

    private static final String TAG = "Launcher.IconCache";

    private final CachingLogic<LauncherActivityInfo> mLauncherActivityInfoCachingLogic;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;
    private final IconProvider mIconProvider;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        super(context, LauncherFiles.APP_ICONS_DB, MODEL_EXECUTOR.getLooper(),
                inv.fillResIconDpi, inv.iconBitmapSize, true /* inMemoryCache */);
        mLauncherActivityInfoCachingLogic = LauncherActivityCachingLogic.newInstance(context);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mUserManager = UserManagerCompat.getInstance(mContext);
        mIconProvider = IconProvider.INSTANCE.get(context);
    }

    @Override
    protected long getSerialNumberForUser(UserHandle user) {
        return mUserManager.getSerialNumberForUser(user);
    }

    @Override
    protected BaseIconFactory getIconFactory() {
        return LauncherIcons.obtain(mContext);
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandle user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfo app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDBAndMemCache(app, mLauncherActivityInfoCachingLogic, info, userSerial,
                        false /*replace existing*/);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
        }
    }

    /**
     * Fill in {@param info} with the icon and label for {@param activityInfo}
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info,
            LauncherActivityInfo activityInfo, boolean useLowResIcon) {
        // If we already have activity info, no need to use package icon
        getTitleAndIcon(info, () -> activityInfo, false, useLowResIcon);
    }

    /**
     * Fill in {@param info} with the icon and label. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon) {
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (info.getTargetComponent() == null) {
            info.applyFrom(getDefaultIcon(info.user));
            info.title = "";
            info.contentDescription = "";
        } else {
            Intent intent = info.getIntent();
            getTitleAndIcon(info, () -> mLauncherApps.resolveActivity(intent, info.user),
                    true, useLowResIcon);
        }
    }

    /**
     * Fill in {@param mWorkspaceItemInfo} with the icon and label for {@param info}
     */
    private synchronized void getTitleAndIcon(
            @NonNull ItemInfoWithIcon infoInOut,
            @NonNull Supplier<LauncherActivityInfo> activityInfoProvider,
            boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(infoInOut.getTargetComponent(), infoInOut.user,
                activityInfoProvider, mLauncherActivityInfoCachingLogic, usePkgIcon, useLowResIcon);
        applyCacheEntry(entry, infoInOut);
    }


    protected void applyCacheEntry(CacheEntry entry, ItemInfoWithIcon info) {
        info.title = Utilities.trim(entry.title);
        info.contentDescription = entry.contentDescription;
        info.applyFrom((entry.icon == null) ? getDefaultIcon(info.user) : entry);
    }

    @Override
    protected String getIconSystemState(String packageName) {
        return mIconProvider.getSystemStateForPackage(mSystemState, packageName)
                + ",flags_asi:" + BaseFlags.APP_SEARCH_IMPROVEMENTS.get();
    }
}
