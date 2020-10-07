package com.boringdroid.systemui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.annotations.Requires;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Requires(target = OverlayPlugin.class, version = OverlayPlugin.VERSION)
public class SystemUIOverlay implements OverlayPlugin {
    private static final String TAG = "SystemUIOverlay";
    // Copied from systemui source code, please keep it update to source code.
    private static final String ACTION_PLUGIN_CHANGED =
            "com.android.systemui.action.PLUGIN_CHANGED";

    private static final String TAG_ALL_APPS_GROUP = "tag-bt-all-apps-group";
    private static final String TAG_APP_STATE_LAYOUT = "tag-app-state-layout";

    private Context mPluginContext;
    private Context mSystemUIContext;
    private View mNavBarButtonGroup;
    private ViewGroup mBtAllAppsGroup;
    private AppStateLayout mAppStateLayout;
    private View mBtAllApps;
    private AllAppsWindow mAllAppsWindow;
    private int mNavBarButtonGroupId = -1;
    private ContentResolver mResolver;
    private List<String> mTunerKeys = new ArrayList<>();
    private ContentObserver mTunerKeyObserver = new TunerKeyObserver();

    private BroadcastReceiver mCloseSystemDialogsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "receive intent " + intent);
            if (mAllAppsWindow == null) {
                return;
            }
            if (intent == null || !Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                return;
            }
            mAllAppsWindow.dismiss();
        }
    };

    @Override
    public void setup(View statusBar, View navBar) {
        Log.d(TAG, "setup status bar " + statusBar + ", nav bar " + navBar);
        if (mNavBarButtonGroupId > 0) {
            View buttonGroup = navBar.findViewById(mNavBarButtonGroupId);
            if (buttonGroup instanceof ViewGroup) {
                mNavBarButtonGroup = buttonGroup;
                // We must set the height to match parent programmatically
                // to let all apps button group be center of navigation
                // bar view.
                FrameLayout.LayoutParams layoutParams =
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        );
                ViewGroup group = (ViewGroup) buttonGroup;
                View oldBtAllAppsGroup = group.findViewWithTag(TAG_ALL_APPS_GROUP);
                if (oldBtAllAppsGroup != null) {
                    group.removeView(oldBtAllAppsGroup);
                }
                mBtAllAppsGroup.setTag(TAG_ALL_APPS_GROUP);
                group.addView(mBtAllAppsGroup, 0, layoutParams);

                View oldAppStateLayout = group.findViewWithTag(TAG_APP_STATE_LAYOUT);
                if (oldAppStateLayout != null) {
                    group.removeView(oldAppStateLayout);
                }
                mAppStateLayout.setTag(TAG_APP_STATE_LAYOUT);
                // The first item is all apps group.
                // The next three item is back button, home button, recents button.
                // So we should add app state layout to the 5th, index 4.
                group.addView(mAppStateLayout, 4, layoutParams);
            }
        }
    }

    @Override
    public boolean holdStatusBarOpen() {
        return false;
    }

    @Override
    public void setCollapseDesired(boolean collapseDesired) {
        // Do nothing
    }

    @Override
    public void onCreate(Context sysUIContext, Context pluginContext) {
        mSystemUIContext = sysUIContext;
        mPluginContext = pluginContext;
        mNavBarButtonGroupId =
                sysUIContext
                        .getResources()
                        .getIdentifier(
                                "ends_group",
                                "id",
                                "com.android.systemui"
                        );
        mBtAllAppsGroup = initializeAllAppsButton(mPluginContext, mBtAllAppsGroup);
        mAppStateLayout = initializeAppStateLayout(mPluginContext, mAppStateLayout);
        mAppStateLayout.reloadActivityManager(mSystemUIContext);
        mBtAllApps = mBtAllAppsGroup.findViewById(R.id.bt_all_apps);
        mAllAppsWindow = new AllAppsWindow(mPluginContext);
        mBtAllApps.setOnClickListener(mAllAppsWindow);
        mResolver = sysUIContext.getContentResolver();
        initializeTuningServiceSettingKeys(mResolver, mTunerKeyObserver);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        mSystemUIContext.registerReceiver(mCloseSystemDialogsReceiver, filter);
    }

    @Override
    public void onDestroy() {
        if (mSystemUIContext != null) {
            mSystemUIContext.unregisterReceiver(mCloseSystemDialogsReceiver);
        }
        mResolver.unregisterContentObserver(mTunerKeyObserver);
        mBtAllAppsGroup.setOnClickListener(null);
        if (mNavBarButtonGroup instanceof ViewGroup) {
            ((ViewGroup) mNavBarButtonGroup).removeView(mBtAllAppsGroup);
            ((ViewGroup) mNavBarButtonGroup).removeView(mAppStateLayout);
        }
        mPluginContext = null;
    }

    @SuppressLint("PrivateApi")
    private void initializeTuningServiceSettingKeys(ContentResolver resolver,
                                                    ContentObserver observer) {
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method getMethod =
                    systemPropertiesClass.getMethod("get", String.class, String.class);
            String tunerKeys =
                    (String) getMethod.invoke(null, "persist.sys.bd.tunerkeys", "");
            Log.d(TAG, "Got tuner keys " + tunerKeys);
            List<String> tunerKeyList =
                    Arrays
                            .stream(tunerKeys.split("--"))
                            .map(String::trim)
                            .filter(key -> !key.isEmpty())
                            .collect(Collectors.toList());
            mTunerKeys.clear();
            mTunerKeys.addAll(tunerKeyList);
            for (String key : mTunerKeys) {
                Log.d(TAG, "Got key " + key);
                Uri uri = Settings.Secure.getUriFor(key);
                resolver.registerContentObserver(uri, false, observer);
            }
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            Log.e(TAG, "Failed to get tuner keys from properties, so fallback to default");
        }
    }

    @SuppressLint("InflateParams")
    private ViewGroup initializeAllAppsButton(Context context, ViewGroup btAllAppsGroup) {
        if (btAllAppsGroup != null) {
            return btAllAppsGroup;
        }
        return (ViewGroup) LayoutInflater
                .from(context)
                .inflate(R.layout.layout_bt_all_apps, null);
    }

    @SuppressLint("InflateParams")
    private AppStateLayout initializeAppStateLayout(Context context,
                                                    AppStateLayout appStateLayout) {
        if (appStateLayout != null) {
            return appStateLayout;
        }
        return (AppStateLayout) LayoutInflater
                .from(context)
                .inflate(R.layout.layout_app_state, null);
    }

    private void onTunerChange(@NonNull Uri uri) {
        String keyName = uri.getLastPathSegment();
        String value = Settings.Secure.getString(mResolver, keyName);
        Log.d(TAG, "onTunerChange " + uri + ", value " + value);
        Uri packageUri = Uri.fromParts("package", mPluginContext.getPackageName(), null);
        Log.d(TAG, "onTunerChange packageUri " + packageUri);
        Intent pluginChangedIntent = new Intent(ACTION_PLUGIN_CHANGED, packageUri);
        mPluginContext.sendBroadcast(pluginChangedIntent);
    }

    private class TunerKeyObserver extends ContentObserver {
        public TunerKeyObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            Log.d(TAG, "TunerKeyChanged " + uri + ", self changed " + selfChange);
            onTunerChange(uri);
        }
    }
}
