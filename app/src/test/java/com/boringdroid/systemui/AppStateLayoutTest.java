package com.boringdroid.systemui;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppStateLayoutTest {
    private static final String TEST_PACKAGE_NAME = "test-app-state-layout";
    private static final String TEST_CLASS_NAME = "test-app-class-name";

    private AppStateLayout mLayout;
    private Context mContext;

    @Before
    public void before() {
        mContext = ApplicationProvider.getApplicationContext();
        mLayout = new AppStateLayout(mContext);
    }

    @Test
    public void shouldIgnoreTopTask_TrueForTaskbar() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName("com.farmerbb.taskbar", TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForTeslaLauncher() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName("com.teslacoilsw.launcher", TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForLawnchair() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName(
                                        "ch.deletescape.lawnchair.plah", TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForSelf() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName(mContext.getPackageName(), TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForAndroidPackage() {
        assertThat(mLayout.shouldIgnoreTopTask(new ComponentName("android", TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForSystemUI() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName("com.android.systemui", TEST_CLASS_NAME)))
                .isTrue();
    }

    @Test
    @Ignore("TODO")
    public void shouldIgnoreTopTask_TrueForLauncher() {
        // TODO
    }

    @Test
    public void shouldIgnoreTopTask_FalseForOtherPackages() {
        assertThat(
                        mLayout.shouldIgnoreTopTask(
                                new ComponentName(TEST_PACKAGE_NAME, TEST_CLASS_NAME)))
                .isFalse();
    }
}
