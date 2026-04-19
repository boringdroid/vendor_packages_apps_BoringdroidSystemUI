package com.boringdroid.systemui

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boringdroid.systemui.taskbar.TaskFilter
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the taskbar's task-filter predicate. The predicate used to live on
 * `AppStateLayout` — it was relocated to [TaskFilter] when the View-based layout was replaced by a
 * Compose surface, but the behavior (and therefore the assertions here) is unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class AppStateLayoutTest {
    private lateinit var mContext: Context

    @Before
    fun before() {
        mContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForTaskbar() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName("com.farmerbb.taskbar", TEST_CLASS_NAME),
                )
            )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForTeslaLauncher() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName("com.teslacoilsw.launcher", TEST_CLASS_NAME),
                )
            )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForLawnchair() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName("ch.deletescape.lawnchair.plah", TEST_CLASS_NAME),
                )
            )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForSelf() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName(mContext.packageName, TEST_CLASS_NAME),
                )
            )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForAndroidPackage() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(mContext, ComponentName("android", TEST_CLASS_NAME))
            )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForSystemUI() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName("com.android.systemui", TEST_CLASS_NAME),
                )
            )
            .isTrue()
    }

    @Test
    @Ignore("TODO")
    fun shouldIgnoreTopTask_TrueForLauncher() {
        // TODO
    }

    @Test
    fun shouldIgnoreTopTask_FalseForOtherPackages() {
        Truth.assertThat(
                TaskFilter.shouldIgnoreTopTask(
                    mContext,
                    ComponentName(TEST_PACKAGE_NAME, TEST_CLASS_NAME),
                )
            )
            .isFalse()
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test-app-state-layout"
        private const val TEST_CLASS_NAME = "test-app-class-name"
    }
}
