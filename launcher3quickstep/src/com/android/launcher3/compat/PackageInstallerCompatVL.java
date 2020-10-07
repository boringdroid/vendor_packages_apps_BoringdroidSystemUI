/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.android.launcher3.Utilities.getPrefs;

public class PackageInstallerCompatVL extends PackageInstallerCompat {

    private static final boolean DEBUG = false;

    @Thunk final SparseArray<PackageUserKey> mActiveSessions = new SparseArray<>();

    @Thunk final PackageInstaller mInstaller;
    private final IconCache mCache;
    private final Context mAppContext;
    private final HashMap<String,Boolean> mSessionVerifiedMap = new HashMap<>();
    private final LauncherAppsCompat mLauncherApps;
    private final IntSet mPromiseIconIds;

    PackageInstallerCompatVL(Context context) {
        mAppContext = context.getApplicationContext();
        mInstaller = context.getPackageManager().getPackageInstaller();
        mCache = LauncherAppState.getInstance(context).getIconCache();
        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mLauncherApps.registerSessionCallback(MODEL_EXECUTOR, mCallback);
        mPromiseIconIds = IntSet.wrap(IntArray.fromConcatString(
                getPrefs(context).getString(PROMISE_ICON_IDS, "")));

        cleanUpPromiseIconIds();
    }

    private void cleanUpPromiseIconIds() {
        IntArray existingIds = new IntArray();
        for (SessionInfo info : updateAndGetActiveSessionCache().values()) {
            existingIds.add(info.getSessionId());
        }
        IntArray idsToRemove = new IntArray();

        for (int i = mPromiseIconIds.size() - 1; i >= 0; --i) {
            if (!existingIds.contains(mPromiseIconIds.getArray().get(i))) {
                idsToRemove.add(mPromiseIconIds.getArray().get(i));
            }
        }
        for (int i = idsToRemove.size() - 1; i >= 0; --i) {
            mPromiseIconIds.getArray().removeValue(idsToRemove.get(i));
        }
    }

    @Override
    public HashMap<PackageUserKey, SessionInfo> updateAndGetActiveSessionCache() {
        HashMap<PackageUserKey, SessionInfo> activePackages = new HashMap<>();
        for (SessionInfo info : getAllVerifiedSessions()) {
            addSessionInfoToCache(info, getUserHandle(info));
            if (info.getAppPackageName() != null) {
                activePackages.put(new PackageUserKey(info.getAppPackageName(),
                        getUserHandle(info)), info);
                mActiveSessions.put(info.getSessionId(),
                        new PackageUserKey(info.getAppPackageName(), getUserHandle(info)));
            }
        }
        return activePackages;
    }

    @Thunk void addSessionInfoToCache(SessionInfo info, UserHandle user) {
        String packageName = info.getAppPackageName();
        if (packageName != null) {
            mCache.cachePackageInstallInfo(packageName, user, info.getAppIcon(),
                    info.getAppLabel());
        }
    }

    @Thunk void sendUpdate(PackageInstallInfo info) {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.getModel().setPackageState(info);
        }
    }

    private final SessionCallback mCallback = new SessionCallback() {

        @Override
        public void onCreated(int sessionId) {
        }

        @Override
        public void onFinished(int sessionId, boolean success) {
            // For a finished session, we can't get the session info. So use the
            // packageName from our local cache.
            PackageUserKey key = mActiveSessions.get(sessionId);
            mActiveSessions.remove(sessionId);

            if (key != null && key.mPackageName != null) {
                String packageName = key.mPackageName;
                sendUpdate(PackageInstallInfo.fromState(success ? STATUS_INSTALLED : STATUS_FAILED,
                        packageName, key.mUser));
            }
        }

        @Override
        public void onProgressChanged(int sessionId, float progress) {
            SessionInfo session = verify(mInstaller.getSessionInfo(sessionId));
            if (session != null && session.getAppPackageName() != null) {
                sendUpdate(PackageInstallInfo.fromInstallingState(session));
            }
        }

        @Override
        public void onActiveChanged(int sessionId, boolean active) { }

        @Override
        public void onBadgingChanged(int sessionId) {
        }
    };

    private PackageInstaller.SessionInfo verify(PackageInstaller.SessionInfo sessionInfo) {
        if (sessionInfo == null
                || sessionInfo.getInstallerPackageName() == null
                || TextUtils.isEmpty(sessionInfo.getAppPackageName())) {
            return null;
        }
        String pkg = sessionInfo.getInstallerPackageName();
        synchronized (mSessionVerifiedMap) {
            if (!mSessionVerifiedMap.containsKey(pkg)) {
                LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mAppContext);
                boolean hasSystemFlag = launcherApps.getApplicationInfo(pkg,
                        ApplicationInfo.FLAG_SYSTEM, getUserHandle(sessionInfo)) != null;
                mSessionVerifiedMap.put(pkg, DEBUG || hasSystemFlag);
            }
        }
        return mSessionVerifiedMap.get(pkg) ? sessionInfo : null;
    }

    @Override
    public List<SessionInfo> getAllVerifiedSessions() {
        List<SessionInfo> list = new ArrayList<>(Utilities.ATLEAST_Q
                ? mLauncherApps.getAllPackageInstallerSessions()
                : mInstaller.getAllSessions());
        Iterator<SessionInfo> it = list.iterator();
        while (it.hasNext()) {
            if (verify(it.next()) == null) {
                it.remove();
            }
        }
        return list;
    }

    @Override
    public boolean promiseIconAddedForId(int sessionId) {
        return mPromiseIconIds.contains(sessionId);
    }

    @Override
    public void removePromiseIconId(int sessionId) {
        if (mPromiseIconIds.contains(sessionId)) {
            mPromiseIconIds.getArray().removeValue(sessionId);
            updatePromiseIconPrefs();
        }
    }

    private void updatePromiseIconPrefs() {
        getPrefs(mAppContext).edit()
                .putString(PROMISE_ICON_IDS, mPromiseIconIds.getArray().toConcatString())
                .apply();
    }
}
