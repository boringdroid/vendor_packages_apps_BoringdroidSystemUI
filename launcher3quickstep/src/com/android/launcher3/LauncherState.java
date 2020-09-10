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
package com.android.launcher3;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;

import com.android.launcher3.states.SpringLoadedState;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

import java.util.Arrays;


/**
 * Base state for various states used for the Launcher
 */
public class LauncherState {
    protected static final int FLAG_MULTI_PAGE = 1 << 0;
    protected static final int FLAG_DISABLE_ACCESSIBILITY = 1 << 1;
    protected static final int FLAG_DISABLE_RESTORE = 1 << 2;
    protected static final int FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED = 1 << 3;
    protected static final int FLAG_DISABLE_PAGE_CLIPPING = 1 << 4;
    protected static final int FLAG_PAGE_BACKGROUNDS = 1 << 5;
    protected static final int FLAG_DISABLE_INTERACTION = 1 << 6;
    protected static final int FLAG_OVERVIEW_UI = 1 << 7;
    protected static final int FLAG_HIDE_BACK_BUTTON = 1 << 8;
    protected static final int FLAG_HAS_SYS_UI_SCRIM = 1 << 9;

    private static final LauncherState[] sAllStates = new LauncherState[7];

    private static final int NORMAL_STATE_ORDINAL = 0;
    private static final int SPRING_LOADED_STATE_ORDINAL = 1;
    private static final int OVERVIEW_STATE_ORDINAL = 2;
    private static final int OVERVIEW_PEEK_STATE_ORDINAL = 3;
    private static final int QUICK_SWITCH_STATE_ORDINAL = 4;
    private static final int BACKGROUND_APP_STATE_ORDINAL = 6;
    /**
     * TODO: Create a separate class for NORMAL state.
     */
    public static final LauncherState NORMAL = new LauncherState(NORMAL_STATE_ORDINAL,
            ContainerType.WORKSPACE, 0,
            FLAG_DISABLE_RESTORE | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED | FLAG_HIDE_BACK_BUTTON |
            FLAG_HAS_SYS_UI_SCRIM);

    /**
     * Various Launcher states arranged in the increasing order of UI layers
     */
    public static final LauncherState SPRING_LOADED = new SpringLoadedState(
            SPRING_LOADED_STATE_ORDINAL);
    public static final LauncherState OVERVIEW = new OverviewState(OVERVIEW_STATE_ORDINAL);
    public static final LauncherState OVERVIEW_PEEK =
            OverviewState.newPeekState(OVERVIEW_PEEK_STATE_ORDINAL);
    public static final LauncherState QUICK_SWITCH =
            OverviewState.newSwitchState(QUICK_SWITCH_STATE_ORDINAL);
    public static final LauncherState BACKGROUND_APP =
            OverviewState.newBackgroundState(BACKGROUND_APP_STATE_ORDINAL);

    public final int ordinal;
    public final int containerType;

    /**
     * True if the state can be persisted across activity restarts.
     */
    public final boolean disableRestore;

    /**
     * True if workspace has multiple pages visible.
     */
    public final boolean hasMultipleVisiblePages;

    /**
     * Accessibility flag for workspace and its pages.
     * @see android.view.View#setImportantForAccessibility(int)
     */
    public final int workspaceAccessibilityFlag;

    /**
     * Properties related to state transition animation
     */
    public final boolean hasWorkspacePageBackground;

    public final int transitionDuration;

    /**
     * True if the workspace pages should not be clipped relative to the workspace bounds
     * for this state.
     */
    public final boolean disablePageClipping;

    /**
     * True if launcher can not be directly interacted in this state;
     */
    public final boolean disableInteraction;

    /**
     * True if the state has overview panel visible.
     */
    public final boolean overviewUi;

    /**
     * True if the back button should be hidden when in this state (assuming no floating views are
     * open, launcher has window focus, etc).
     */
    public final boolean hideBackButton;

    public final boolean hasSysUiScrim;

    public LauncherState(int id, int containerType, int transitionDuration, int flags) {
        this.containerType = containerType;
        this.transitionDuration = transitionDuration;

        this.hasWorkspacePageBackground = (flags & FLAG_PAGE_BACKGROUNDS) != 0;
        this.hasMultipleVisiblePages = (flags & FLAG_MULTI_PAGE) != 0;
        this.workspaceAccessibilityFlag = (flags & FLAG_DISABLE_ACCESSIBILITY) != 0
                ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        this.disableRestore = (flags & FLAG_DISABLE_RESTORE) != 0;
        this.disablePageClipping = (flags & FLAG_DISABLE_PAGE_CLIPPING) != 0;
        this.disableInteraction = (flags & FLAG_DISABLE_INTERACTION) != 0;
        this.overviewUi = (flags & FLAG_OVERVIEW_UI) != 0;
        this.hideBackButton = (flags & FLAG_HIDE_BACK_BUTTON) != 0;
        this.hasSysUiScrim = (flags & FLAG_HAS_SYS_UI_SCRIM) != 0;

        this.ordinal = id;
        sAllStates[id] = this;
    }

    public static LauncherState[] values() {
        return Arrays.copyOf(sAllStates, sAllStates.length);
    }

    public float getOverviewFullscreenProgress() {
        return 0;
    }

    public LauncherState getHistoryForState() {
        // No history is supported
        return NORMAL;
    }

    public static class ScaleAndTranslation {
        public float scale;
        public float translationX;
        public float translationY;

        public ScaleAndTranslation(float scale, float translationX, float translationY) {
            this.scale = scale;
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }
}
