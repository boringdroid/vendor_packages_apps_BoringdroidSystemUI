package com.boringdroid.systemui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.annotations.Requires;
import com.boringdroid.systemui.R;

@Requires(target = OverlayPlugin.class, version = OverlayPlugin.VERSION)
public class SystemUIOverlay implements OverlayPlugin {
    private static final String TAG = "SystemUIOverlay";
    private Context mPluginContext;
    private View mNavBarButtonGroup;
    private ViewGroup mBtAllAppsGroup;
    private AppStateLayout mAppStateLayout;
    private View mBtAllApps;
    private AllAppsWindow mAllAppsWindow;
    private int mNavBarButtonGroupId = -1;

    @Override
    public void setup(View statusBar, View navBar) {
        Log.e(TAG, "setup status bar " + statusBar + ", nav bar " + navBar);
        if (mNavBarButtonGroupId > 0) {
            View buttonGroup = navBar.findViewById(mNavBarButtonGroupId);
            if (buttonGroup instanceof ViewGroup) {
                mNavBarButtonGroup = buttonGroup;
                // We must set the height to match parent programatically
                // to let all apps button group be center of navigation
                // bar view.
                FrameLayout.LayoutParams layoutParams =
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        );
                ViewGroup group = (ViewGroup) buttonGroup;
                group.addView(mBtAllAppsGroup, 0, layoutParams);
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

    }

    @Override
    public void onCreate(Context sysUIContext, Context pluginContext) {
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
        mBtAllApps = mBtAllAppsGroup.findViewById(R.id.bt_all_apps);
        mAllAppsWindow = new AllAppsWindow(mPluginContext);
        mBtAllApps.setOnClickListener(mAllAppsWindow);
    }

    @Override
    public void onDestroy() {
        mBtAllAppsGroup.setOnClickListener(null);
        if (mNavBarButtonGroup instanceof ViewGroup) {
            ((ViewGroup) mNavBarButtonGroup).removeView(mBtAllAppsGroup);
            ((ViewGroup) mNavBarButtonGroup).removeView(mAppStateLayout);
        }
        mPluginContext = null;
    }

    @SuppressLint("InflateParams")
    private ViewGroup initializeAllAppsButton(Context context, ViewGroup btAllAppsGroup) {
        if (btAllAppsGroup != null) {
            return btAllAppsGroup;
        }
        btAllAppsGroup =
                (ViewGroup) LayoutInflater
                        .from(context)
                        .inflate(R.layout.layout_bt_all_apps, null);
        return btAllAppsGroup;
    }

    @SuppressLint("InflateParams")
    private AppStateLayout initializeAppStateLayout(Context context,
                                                    AppStateLayout appStateLayout) {
        if (appStateLayout != null) {
            return appStateLayout;
        }
        appStateLayout =
                (AppStateLayout) LayoutInflater
                        .from(context)
                        .inflate(R.layout.layout_app_state, null);
        return appStateLayout;
    }
}
