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

import static android.content.pm.ActivityInfo.CONFIG_ORIENTATION;
import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_PEEK;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_LAUNCHER_LOAD;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;
import static com.android.launcher3.util.RaceConditionTracker.ENTER;
import static com.android.launcher3.util.RaceConditionTracker.EXIT;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.StrictMode;
import android.text.method.TextKeyListener;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.keyboard.CustomActionsPopup;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.states.InternalStateHandler;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.util.ActivityResultInfo;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.RaceConditionTracker;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Default launcher application.
 */
public class Launcher extends BaseDraggingActivity implements LauncherExterns,
        Callbacks, LauncherProviderChangeListener,
        InvariantDeviceProfile.OnIDPChangeListener {
    public static final String TAG = "Launcher";

    static final boolean DEBUG_STRICT_MODE = false;

    private static final int REQUEST_CREATE_SHORTCUT = 1;

    private static final int REQUEST_PERMISSION_CALL_PHONE = 14;

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: PendingRequestArgs
    private static final String RUNTIME_STATE_PENDING_REQUEST_ARGS = "launcher.request_args";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_REQUEST_CODE = "launcher.request_code";
    // Type: ActivityResultInfo
    private static final String RUNTIME_STATE_PENDING_ACTIVITY_RESULT = "launcher.activity_result";
    // Type: SparseArray<Parcelable>
    private static final String RUNTIME_STATE_WIDGET_PANEL = "launcher.widget_panel";
    public static final String ON_CREATE_EVT = "Launcher.onCreate";
    private static final String ON_START_EVT = "Launcher.onStart";
    private static final String ON_RESUME_EVT = "Launcher.onResume";

    private LauncherStateManager mStateManager;

    private static final int SCRIM_VIEW_ALPHA_CHANNEL_INDEX = 0;

    private LauncherAppTransitionManager mAppTransitionManager;
    private Configuration mOldConfig;

    private View mLauncherView;
    @Thunk
    DragLayer mDragLayer;
    private DragController mDragController;

    private DropTargetBar mDropTargetBar;

    // Scrim view for the all apps and overview state.
    @Thunk
    ScrimView mScrimView;

    // UI and state for the overview panel
    private View mOverviewPanel;

    @Thunk
    boolean mWorkspaceLoading = true;

    private ArrayList<OnResumeCallback> mOnResumeCallbacks = new ArrayList<>();

    private ViewOnDrawExecutor mPendingExecutor;

    private LauncherModel mModel;
    private ModelWriter mModelWriter;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    // Activity result which needs to be processed after workspace has loaded.
    private ActivityResultInfo mPendingActivityResult;
    /**
     * Holds extra information required to handle a result from an external call, like
     * {@link #startActivityForResult(Intent, int)} or {@link #requestPermissions(String[], int)}
     */
    private PendingRequestArgs mPendingRequestArgs;
    // Request id for any pending activity result
    private int mPendingActivityRequestCode = -1;

    public ViewGroupFocusHelper mFocusHandler;

    private RotationHelper mRotationHelper;
    private Runnable mCancelTouchController;

    final Handler mHandler = new Handler();
    private final Runnable mHandleDeferredResume = this::handleDeferredResume;
    private boolean mDeferredResumePending;

    private DeviceProfile mStableDeviceProfile;
    private RotationMode mRotationMode = RotationMode.NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RaceConditionTracker.onEvent(ON_CREATE_EVT, ENTER);
        if (DEBUG_STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
        TraceHelper.beginSection("Launcher-onCreate");

        super.onCreate(savedInstanceState);
        TraceHelper.partitionSection("Launcher-onCreate", "super call");

        LauncherAppState app = LauncherAppState.getInstance(this);
        mOldConfig = new Configuration(getResources().getConfiguration());
        mModel = app.setLauncher(this);
        mRotationHelper = new RotationHelper(this);
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();
        initDeviceProfile(idp);
        idp.addOnChangeListener(this);
        mSharedPrefs = Utilities.getPrefs(this);
        mAccessibilityDelegate = new LauncherAccessibilityDelegate(this);

        mDragController = new DragController(this);
        mStateManager = new LauncherStateManager(this);
        UiFactory.onCreate(this);

        mLauncherView = LayoutInflater.from(this).inflate(R.layout.launcher, null);

        setupViews();

        mAppTransitionManager = LauncherAppTransitionManager.newInstance(this);

        boolean internalStateHandled = InternalStateHandler.handleCreate(this, getIntent());
        if (internalStateHandled) {
            if (savedInstanceState != null) {
                // InternalStateHandler has already set the appropriate state.
                // We dont need to do anything.
                savedInstanceState.remove(RUNTIME_STATE);
            }
        }
        restoreState(savedInstanceState);
        mStateManager.reapplyState();

        // We only load the page synchronously if the user rotates (or triggers a
        // configuration change) while launcher is in the foreground
        int currentScreen = PagedView.INVALID_RESTORE_PAGE;
        if (savedInstanceState != null) {
            currentScreen = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_SCREEN, currentScreen);
        }

        if (!mModel.startLoader(currentScreen)) {
            if (!internalStateHandled) {
                // If we are not binding synchronously, show a fade in animation when
                // the first page bind completes.
                mDragLayer.getAlphaProperty(ALPHA_INDEX_LAUNCHER_LOAD).setValue(0);
            }
        } else {
            setWorkspaceLoading(true);
        }

        // For handling default keys
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        setContentView(mLauncherView);
        getRootView().dispatchInsets();

        // Listen for broadcasts
        registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        getSystemUiController().updateUiState(SystemUiController.UI_STATE_BASE_WINDOW,
                Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onCreate(savedInstanceState);
        }
        mRotationHelper.initialize();

        TraceHelper.endSection("Launcher-onCreate");
        RaceConditionTracker.onEvent(ON_CREATE_EVT, EXIT);
        mStateManager.addStateListener(new LauncherStateManager.StateListener() {
            @Override
            public void onStateTransitionStart(LauncherState toState) {
            }

            @Override
            public void onStateTransitionComplete(LauncherState finalState) {
            }
        });
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        UiFactory.onEnterAnimationComplete(this);
        mRotationHelper.setCurrentTransitionRequest(REQUEST_NONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int diff = newConfig.diff(mOldConfig);

        if ((diff & (CONFIG_ORIENTATION | CONFIG_SCREEN_SIZE)) != 0) {
            onIdpChanged(mDeviceProfile.inv);
        }

        mOldConfig.setTo(newConfig);
        UiFactory.onLauncherStateOrResumeChanged(this);
        super.onConfigurationChanged(newConfig);
    }

    public void reload() {
        onIdpChanged(mDeviceProfile.inv);
    }

    private boolean supportsFakeLandscapeUI() {
        return FeatureFlags.FAKE_LANDSCAPE_UI.get() && !mRotationHelper.homeScreenCanRotate();
    }

    @Override
    public void reapplyUi() {
        reapplyUi(true /* cancelCurrentAnimation */);
    }

    public void reapplyUi(boolean cancelCurrentAnimation) {
        if (supportsFakeLandscapeUI()) {
            mRotationMode = mStableDeviceProfile == null
                    ? RotationMode.NORMAL : UiFactory.getRotationMode(mDeviceProfile);
        }
        getRootView().dispatchInsets();
        getStateManager().reapplyState(cancelCurrentAnimation);
    }

    @Override
    public void rebindModel() {
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile idp) {
        onIdpChanged(idp);
    }

    private void onIdpChanged(InvariantDeviceProfile idp) {
        DeviceProfile oldWallpaperProfile = getWallpaperDeviceProfile();
        initDeviceProfile(idp);
        dispatchDeviceProfileChanged();
        reapplyUi();
        mDragLayer.recreateControllers();

        // Calling onSaveInstanceState ensures that static cache used by listWidgets is
        // initialized properly.
        onSaveInstanceState(new Bundle());
        if (oldWallpaperProfile != getWallpaperDeviceProfile()) {
            rebindModel();
        }
    }

    public void onAssistantVisibilityChanged(float visibility) {
        float alpha = 1f - visibility;
        LauncherState state = mStateManager.getState();
        if (state == NORMAL) {
        } else if (state == OVERVIEW || state == OVERVIEW_PEEK) {
            mScrimView.getAlphaProperty(SCRIM_VIEW_ALPHA_CHANNEL_INDEX).setValue(alpha);
        }
    }

    private void initDeviceProfile(InvariantDeviceProfile idp) {
        // Load configuration-specific DeviceProfile
        mDeviceProfile = idp.getDeviceProfile(this);
        if (isInMultiWindowMode()) {
            // Note: Calls to getSize() can't rely on our cached DefaultDisplay since it can return
            // the app window size
            Display display = getWindowManager().getDefaultDisplay();
            Point mwSize = new Point();
            display.getSize(mwSize);
            mDeviceProfile = mDeviceProfile.getMultiWindowProfile(this, mwSize);
        }

        if (supportsFakeLandscapeUI() && mDeviceProfile.isVerticalBarLayout()) {
            mStableDeviceProfile = mDeviceProfile.inv.portraitProfile;
            mRotationMode = UiFactory.getRotationMode(mDeviceProfile);
        } else {
            mStableDeviceProfile = null;
            mRotationMode = RotationMode.NORMAL;
        }

        mRotationHelper.updateRotationAnimation();
        onDeviceProfileInitiated();
        mModelWriter = mModel.getWriter(true);
    }

    public void updateInsets(Rect insets) {
        mDeviceProfile.updateInsets(insets);
        if (mStableDeviceProfile != null) {
            Rect r = mStableDeviceProfile.getInsets();
            mRotationMode.mapInsets(this, insets, r);
            mStableDeviceProfile.updateInsets(r);
        }
    }

    @Override
    public RotationMode getRotationMode() {
        return mRotationMode;
    }

    /**
     * Device profile to be used by UI elements which are shown directly on top of the wallpaper
     * and whose presentation is tied to the wallpaper (and physical device) and not the activity
     * configuration.
     */
    @Override
    public DeviceProfile getWallpaperDeviceProfile() {
        return mStableDeviceProfile == null ? mDeviceProfile : mStableDeviceProfile;
    }

    public RotationHelper getRotationHelper() {
        return mRotationHelper;
    }

    public LauncherStateManager getStateManager() {
        return mStateManager;
    }

    @Override
    public <T extends View> T findViewById(int id) {
        return mLauncherView.findViewById(id);
    }

    @Override
    public void onAppWidgetHostReset() {
    }

    private LauncherCallbacks mLauncherCallbacks;

    /**
     * Call this after onCreate to set or clear overlay.
     */
    public void setLauncherOverlay(LauncherOverlay overlay) {
        if (overlay != null) {
            overlay.setOverlayCallbacks(new LauncherOverlayCallbacksImpl());
        }
    }

    public boolean setLauncherCallbacks(LauncherCallbacks callbacks) {
        mLauncherCallbacks = callbacks;
        return true;
    }

    @Override
    public void onLauncherProviderChanged() {
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onLauncherProviderChange();
        }
    }

    @Override
    public void invalidateParent(ItemInfo info) {
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private int completeAdd(
            int requestCode, Intent intent, PendingRequestArgs info) {
        int screenId = info.screenId;
        if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(info.screenId);
        }

        return screenId;
    }

    private void handleActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        if (isWorkspaceLoading()) {
            // process the result once the workspace has loaded.
            mPendingActivityResult = new ActivityResultInfo(requestCode, resultCode, data);
            return;
        }
        mPendingActivityResult = null;

        // Reset the startActivity waiting flag
        final PendingRequestArgs requestArgs = mPendingRequestArgs;
        setWaitingForResult(null);
        if (requestArgs == null) {
            return;
        }

        if (requestCode == REQUEST_CREATE_SHORTCUT) {
            // Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
            if (resultCode == RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
                completeAdd(requestCode, data, requestArgs);

            } else if (resultCode == RESULT_CANCELED) {
            }
        }
        mDragLayer.clearAnimatedView();
    }

    @Override
    public void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        mPendingActivityRequestCode = -1;
        handleActivityResult(requestCode, resultCode, data);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        PendingRequestArgs pendingArgs = mPendingRequestArgs;
        if (requestCode == REQUEST_PERMISSION_CALL_PHONE && pendingArgs != null
                && pendingArgs.getRequestCode() == REQUEST_PERMISSION_CALL_PHONE) {
            setWaitingForResult(null);

            View v = null;
            Intent intent = pendingArgs.getPendingIntent();

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivitySafely(v, intent, null, null);
            } else {
                // TODO: Show a snack bar with link to settings
                Toast.makeText(this, getString(R.string.msg_no_phone_permission,
                        getString(R.string.derived_app_name)), Toast.LENGTH_SHORT).show();
            }
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onRequestPermissionsResult(requestCode, permissions,
                    grantResults);
        }
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private int ensurePendingDropLayoutExists(int screenId) {
        return screenId;
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onStop();
        }

        getStateManager().moveToRestState();

        UiFactory.onLauncherStateOrResumeChanged(this);

        // Workaround for b/78520668, explicitly trim memory once UI is hidden
        onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
    }

    @Override
    protected void onStart() {
        RaceConditionTracker.onEvent(ON_START_EVT, ENTER);
        super.onStart();
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onStart();
        }
        RaceConditionTracker.onEvent(ON_START_EVT, EXIT);
    }

    private void handleDeferredResume() {
        if (hasBeenResumed() && !mStateManager.getState().disableInteraction) {
            UiFactory.onLauncherStateOrResumeChanged(this);
            AppLaunchTracker.INSTANCE.get(this).onReturnedToHome();

            if (mPendingActivityRequestCode != -1 && isInState(NORMAL)) {
                UiFactory.resetPendingActivityResults(this, mPendingActivityRequestCode);
            }
            mDeferredResumePending = false;
        } else {
            mDeferredResumePending = true;
        }
    }

    public void onStateSetStart(LauncherState state) {
        if (mDeferredResumePending) {
            handleDeferredResume();
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onStateChanged();
        }
    }

    public void onStateSetEnd(LauncherState state) {
        finishAutoCancelActionMode();
    }

    @Override
    protected void onResume() {
        RaceConditionTracker.onEvent(ON_RESUME_EVT, ENTER);
        TraceHelper.beginSection("ON_RESUME");
        super.onResume();
        TraceHelper.partitionSection("ON_RESUME", "superCall");

        mHandler.removeCallbacks(mHandleDeferredResume);
        Utilities.postAsyncCallback(mHandler, mHandleDeferredResume);

        if (!mOnResumeCallbacks.isEmpty()) {
            final ArrayList<OnResumeCallback> resumeCallbacks = new ArrayList<>(mOnResumeCallbacks);
            mOnResumeCallbacks.clear();
            for (int i = resumeCallbacks.size() - 1; i >= 0; i--) {
                resumeCallbacks.get(i).onLauncherResume();
            }
            resumeCallbacks.clear();
        }

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onResume();
        }

        TraceHelper.endSection("ON_RESUME");
        RaceConditionTracker.onEvent(ON_RESUME_EVT, EXIT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDragController.cancelDrag();
        mDragController.resetLastGestureUpTime();
        mDropTargetBar.animateToVisibility(false);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onPause();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        UiFactory.onLauncherStateOrResumeChanged(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mStateManager.onWindowFocusChanged();
    }

    public interface LauncherOverlay {

        /**
         * Called when the launcher is ready to use the overlay
         * @param callbacks A set of callbacks provided by Launcher in relation to the overlay
         */
        void setOverlayCallbacks(LauncherOverlayCallbacks callbacks);
    }

    public interface LauncherOverlayCallbacks {
    }

    static class LauncherOverlayCallbacksImpl implements LauncherOverlayCallbacks {

    }

    public boolean isInState(LauncherState state) {
        return mStateManager.getState() == state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        int stateOrdinal = savedState.getInt(RUNTIME_STATE, NORMAL.ordinal);
        LauncherState[] stateValues = LauncherState.values();
        LauncherState state = stateValues[stateOrdinal];
        if (!state.disableRestore) {
            mStateManager.goToState(state, false /* animated */);
        }

        PendingRequestArgs requestArgs = savedState.getParcelable(
                RUNTIME_STATE_PENDING_REQUEST_ARGS);
        if (requestArgs != null) {
            setWaitingForResult(requestArgs);
        }
        mPendingActivityRequestCode = savedState.getInt(RUNTIME_STATE_PENDING_REQUEST_CODE);

        mPendingActivityResult = savedState.getParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT);
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        mDragLayer = findViewById(R.id.drag_layer);
        mFocusHandler = mDragLayer.getFocusIndicatorHelper();
        mOverviewPanel = findViewById(R.id.overview_panel);

        mLauncherView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // Setup the drag layer
        mCancelTouchController = UiFactory.enableLiveUIChanges(this);

        // Get the search/delete/uninstall bar
        mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

        // Setup Scrim
        mScrimView = findViewById(R.id.scrim_view);
    }

    private final BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Reset AllApps to its initial state only if we are not in the middle of
            // processing a multi-step drop
            if (mPendingRequestArgs == null) {
                mStateManager.goToState(NORMAL);
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onAttachedToWindow();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onDetachedFromWindow();
        }
    }

    @Override
    public LauncherRootView getRootView() {
        return (LauncherRootView) mLauncherView;
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public <T extends View> T getOverviewPanel() {
        return (T) mOverviewPanel;
    }

    public DropTargetBar getDropTargetBar() {
        return mDropTargetBar;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public ModelWriter getModelWriter() {
        return mModelWriter;
    }

    public SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    public int getOrientation() {
        return mOldConfig.orientation;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        TraceHelper.beginSection("NEW_INTENT");
        super.onNewIntent(intent);

        boolean alreadyOnHome = hasWindowFocus() && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Check this condition before handling isActionMain, as this will get reset.
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        boolean internalStateHandled = InternalStateHandler
                .handleNewIntent(this, intent, isStarted());

        if (isActionMain) {
            if (!internalStateHandled) {
                // In all these cases, only animate if we're already on home
                AbstractFloatingView.closeAllOpenViews(this, isStarted());
                UiFactory.closeSystemWindows();

                if (!isInState(NORMAL)) {
                    // Only change state, if not already the same. This prevents cancelling any
                    // animations running as part of resume
                    mStateManager.goToState(NORMAL);
                }
            }

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                UiThreadHelper.hideKeyboardAsync(this, v.getWindowToken());
            }

            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onHomeIntent(internalStateHandled);
            }
        }

        TraceHelper.endSection("NEW_INTENT");
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);


        AbstractFloatingView widgets = AbstractFloatingView
                .getOpenView(this, AbstractFloatingView.TYPE_WIDGETS_FULL_SHEET);
        if (widgets != null) {
            SparseArray<Parcelable> widgetsState = new SparseArray<>();
            widgets.saveHierarchyState(widgetsState);
            outState.putSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL, widgetsState);
        } else {
            outState.remove(RUNTIME_STATE_WIDGET_PANEL);
        }

        // We close any open folders and shortcut containers that are not safe for rebind,
        // and we need to make sure this state is reflected.
        AbstractFloatingView.closeOpenViews(this, false, TYPE_ALL & ~TYPE_REBIND_SAFE);
        finishAutoCancelActionMode();

        if (mPendingRequestArgs != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS, mPendingRequestArgs);
        }
        outState.putInt(RUNTIME_STATE_PENDING_REQUEST_CODE, mPendingActivityRequestCode);

        if (mPendingActivityResult != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, mPendingActivityResult);
        }

        super.onSaveInstanceState(outState);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mScreenOffReceiver);

        if (mCancelTouchController != null) {
            mCancelTouchController.run();
            mCancelTouchController = null;
        }

        // Stop callbacks from LauncherModel
        // It's possible to receive onDestroy after a new Launcher activity has
        // been created. In this case, don't interfere with the new Launcher.
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
            LauncherAppState.getInstance(this).setLauncher(null);
        }
        mRotationHelper.destroy();

        TextKeyListener.getInstance().release();
        LauncherAppState.getIDP(this).removeOnChangeListener(this);
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onDestroy();
        }
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        if (requestCode == -1
                || !UiFactory.startActivityForResult(this, intent, requestCode, options)) {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        if (requestCode == -1 || !UiFactory.startIntentSenderForResult(this, intent, requestCode,
                fillInIntent, flagsMask, flagsValues, extraFlags, options)) {
            try {
                super.startIntentSenderForResult(intent, requestCode,
                        fillInIntent, flagsMask, flagsValues, extraFlags, options);
            } catch (IntentSender.SendIntentException e) {
                throw new ActivityNotFoundException();
            }
        }
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }

        if (mLauncherCallbacks == null ||
                !mLauncherCallbacks.startSearch(initialQuery, selectInitialQuery, appSearchData)) {
            // Starting search from the callbacks failed. Start the default global search.
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, true);
        }

        // We need to show the workspace after starting the search
        mStateManager.goToState(NORMAL);
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mPendingRequestArgs != null;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        mWorkspaceLoading = value;
    }

    public void setWaitingForResult(PendingRequestArgs args) {
        mPendingRequestArgs = args;
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (event.getKeyCode() == KeyEvent.KEYCODE_HOME) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (finishAutoCancelActionMode()) {
            return;
        }
        if (mLauncherCallbacks != null && mLauncherCallbacks.handleBackPressed()) {
            return;
        }

        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
            return;
        }

        // Note: There should be at most one log per method call. This is enforced implicitly
        // by using if-else statements.
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
        if (topView != null && topView.onBackPressed()) {
            // Handled by the floating view.
        } else {
            mStateManager.getState().onBackPressed(this);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ActivityOptions getActivityLaunchOptions(View v) {
        return mAppTransitionManager.getActivityLaunchOptions(this, v);
    }

    public LauncherAppTransitionManager getAppTransitionManager() {
        return mAppTransitionManager;
    }

    public boolean startActivitySafely(View v, Intent intent, ItemInfo item,
            @Nullable String sourceContainer) {
        if (!hasBeenResumed()) {
            // Workaround an issue where the WM launch animation is clobbered when finishing the
            // recents animation into launcher. Defer launching the activity until Launcher is
            // next resumed.
            addOnResumeCallback(() -> startActivitySafely(v, intent, item, sourceContainer));
            UiFactory.clearSwipeSharedState(true /* finishAnimation */);
            return true;
        }

        boolean success = super.startActivitySafely(v, intent, item, sourceContainer);
        if (success && v instanceof BubbleTextView) {
            // This is set to the view that launched the activity that navigated the user away
            // from launcher. Since there is no callback for when the activity has finished
            // launching, enable the press state and keep this reference to reset the press
            // state when we return to launcher.
            BubbleTextView btv = (BubbleTextView) v;
            btv.setStayPressed(true);
            addOnResumeCallback(btv);
        }
        return success;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The widget preview db can result in holding onto over
            // 3MB of memory for caching which isn't necessary.
            SQLiteDatabase.releaseMemory();

            // This clears all widget bitmaps from the widget tray
            // TODO(hyunyoungs)
        }
        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onTrimMemory(level);
        }
        UiFactory.onTrimMemory(this, level);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        return result;
    }

    public void addOnResumeCallback(OnResumeCallback callback) {
        mOnResumeCallbacks.add(callback);
    }

    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        if (mPendingExecutor == executor) {
            mPendingExecutor = null;
        }
    }

    /**
     * $ adb shell dumpsys activity com.android.launcher3.Launcher [--all]
     */
    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);

        writer.println(prefix + "Misc:");
        dumpMisc(prefix + "\t", writer);
        writer.println(prefix + "\tmWorkspaceLoading=" + mWorkspaceLoading);
        writer.println(prefix + "\tmPendingRequestArgs=" + mPendingRequestArgs
                + " mPendingActivityResult=" + mPendingActivityResult);
        writer.println(prefix + "\tmRotationHelper: " + mRotationHelper);

        // Extra logging for b/116853349
        mDragLayer.dump(prefix, writer);
        mStateManager.dump(prefix, writer);

        mModel.dumpState(prefix, fd, writer, args);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.dump(prefix, fd, writer, args);
        }
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {

        ArrayList<KeyboardShortcutInfo> shortcutInfos = new ArrayList<>();
        if (isInState(NORMAL)) {
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.all_apps_button_label),
                    KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.widget_button_text),
                    KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON));
        }
        final View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            if (new CustomActionsPopup(this, currentFocus).canShow()) {
                shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.custom_actions),
                        KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON));
            }
        }
        if (!shortcutInfos.isEmpty()) {
            data.add(new KeyboardShortcutGroup(getString(R.string.home_screen), shortcutInfos));
        }

        super.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (isInState(NORMAL)) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_S: {
                    // TODO recheck it
                    break;
                }
                case KeyEvent.KEYCODE_O:
                    if (new CustomActionsPopup(this, getCurrentFocus()).show()) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_W:
                    if (isInState(NORMAL)) {
                        return true;
                    }
                    break;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // KEYCODE_MENU is sent by some tests, for example
            // LauncherJankTests#testWidgetsContainerFling. Don't just remove its handling.
            if (!mDragController.isDragging() && isInState(NORMAL)) {
                // Close any open floating views.
                AbstractFloatingView.closeAllOpenViews(this);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public static Launcher getLauncher(Context context) {
        return (Launcher) fromContext(context);
    }

    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        getStateManager().goToState(LauncherState.NORMAL);
    }

    /**
     * Just a wrapper around the type cast to allow easier tracking of calls.
     */
    public static <T extends Launcher> T cast(ActivityContext activityContext) {
        return (T) activityContext;
    }

    /**
     * Callback for listening for onResume
     */
    public interface OnResumeCallback {

        void onLauncherResume();
    }
}
