package com.boringdroid.systemui;

import static com.google.common.truth.Truth.assertThat;

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

    private AppStateLayout mLayout;
    private Context mContext;

    @Before
    public void before() {
        mContext = ApplicationProvider.getApplicationContext();
        mLayout = new AppStateLayout(mContext);
    }

    @Test
    public void shouldIgnoreTopTask_TrueForTaskbar() {
        assertThat(mLayout.shouldIgnoreTopTask("com.farmerbb.taskbar")).isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForSelf() {
        assertThat(mLayout.shouldIgnoreTopTask(mContext.getPackageName())).isTrue();
    }

    @Test
    public void shouldIgnoreTopTask_TrueForSystemUI() {
        assertThat(mLayout.shouldIgnoreTopTask("com.android.systemui")).isTrue();
    }

    @Test
    @Ignore("TODO")
    public void shouldIgnoreTopTask_TrueForLauncher() {
        // TODO
    }

    @Test
    public void shouldIgnoreTopTask_FalseForOtherPackages() {
        assertThat(mLayout.shouldIgnoreTopTask(TEST_PACKAGE_NAME)).isFalse();
    }
}
