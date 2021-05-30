package com.boringdroid.systemui

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStateLayoutTest {
    private var mLayout: AppStateLayout? = null
    private var mContext: Context? = null
    @Before
    fun before() {
        mContext = ApplicationProvider.getApplicationContext()
        mLayout = AppStateLayout(mContext)
    }

    @Test
    fun shouldIgnoreTopTask_TrueForTaskbar() {
        Truth.assertThat(
            mLayout!!.shouldIgnoreTopTask(
                ComponentName("com.farmerbb.taskbar", TEST_CLASS_NAME)
            )
        )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForTeslaLauncher() {
        Truth.assertThat(
            mLayout!!.shouldIgnoreTopTask(
                ComponentName("com.teslacoilsw.launcher", TEST_CLASS_NAME)
            )
        )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForLawnchair() {
        Truth.assertThat(
            mLayout!!.shouldIgnoreTopTask(
                ComponentName(
                    "ch.deletescape.lawnchair.plah", TEST_CLASS_NAME
                )
            )
        )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForSelf() {
        Truth.assertThat(
            mLayout!!.shouldIgnoreTopTask(
                ComponentName(mContext!!.packageName, TEST_CLASS_NAME)
            )
        )
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForAndroidPackage() {
        Truth.assertThat(mLayout!!.shouldIgnoreTopTask(ComponentName("android", TEST_CLASS_NAME)))
            .isTrue()
    }

    @Test
    fun shouldIgnoreTopTask_TrueForSystemUI() {
        Truth.assertThat(
            mLayout!!.shouldIgnoreTopTask(
                ComponentName("com.android.systemui", TEST_CLASS_NAME)
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
            mLayout!!.shouldIgnoreTopTask(
                ComponentName(TEST_PACKAGE_NAME, TEST_CLASS_NAME)
            )
        )
            .isFalse()
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test-app-state-layout"
        private const val TEST_CLASS_NAME = "test-app-class-name"
    }
}