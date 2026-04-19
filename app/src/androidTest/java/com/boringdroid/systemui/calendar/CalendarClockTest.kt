// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.boringdroid.systemui.PluginBaselineTest.Companion.FIND_TIMEOUT_MS
import com.boringdroid.systemui.PluginBaselineTest.Companion.PLUGIN_PKG
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarClockTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle()
        // Clean state: both surfaces closed. Tapping the taskbar clock toggles the calendar
        // open/closed, so a pre-open surface would flip us inside-out.
        if (device.hasObject(By.res(PLUGIN_PKG, "calendar_clock_root"))) {
            device.findObject(By.res(PLUGIN_PKG, "clock"))?.click()
            device.wait(Until.gone(By.res(PLUGIN_PKG, "calendar_clock_root")), FIND_TIMEOUT_MS)
        }
        if (device.hasObject(By.res(PLUGIN_PKG, "action_center_root"))) {
            device.findObject(By.res(PLUGIN_PKG, "action_center_bell"))?.click()
            device.wait(Until.gone(By.res(PLUGIN_PKG, "action_center_root")), FIND_TIMEOUT_MS)
        }
        device.waitForIdle(500L)
    }

    @Test
    fun tappingClock_opensCalendarPanel() {
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "clock")), FIND_TIMEOUT_MS)
            .click()
        assertThat(
                device.wait(
                    Until.findObject(By.res(PLUGIN_PKG, "calendar_clock_root")),
                    FIND_TIMEOUT_MS,
                )
            )
            .isNotNull()
        assertThat(device.hasObject(By.res(PLUGIN_PKG, "calendar_grid"))).isTrue()
    }

    @Test
    fun tappingClockAgain_dismissesCalendar() {
        val clock =
            device.wait(Until.findObject(By.res(PLUGIN_PKG, "clock")), FIND_TIMEOUT_MS)
        clock.click()
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "calendar_clock_root")),
            FIND_TIMEOUT_MS,
        )
        // Re-fetch the clock: the previous UiObject2 handle can become stale once the
        // calendar overlay is added on top of the taskbar, but the clock node is still
        // present in the taskbar below.
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "clock")), FIND_TIMEOUT_MS)
            .click()
        assertThat(device.wait(Until.gone(By.res(PLUGIN_PKG, "calendar_clock_root")), 3_000L))
            .isTrue()
    }

    /**
     * Mutual exclusion: the Calendar and Action Center share taskbar real estate, so opening one
     * must dismiss the other. Verified against both directions (calendar→action-center and
     * action-center→calendar) to lock down both sides of the switch.
     */
    @Test
    fun openingActionCenter_dismissesCalendar() {
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "clock")), FIND_TIMEOUT_MS)
            .click()
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "calendar_clock_root")),
            FIND_TIMEOUT_MS,
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        assertThat(device.wait(Until.gone(By.res(PLUGIN_PKG, "calendar_clock_root")), 3_000L))
            .isTrue()
        assertThat(
                device.wait(
                    Until.findObject(By.res(PLUGIN_PKG, "action_center_root")),
                    FIND_TIMEOUT_MS,
                )
            )
            .isNotNull()
    }

    @Test
    fun openingCalendar_dismissesActionCenter() {
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "action_center_bell")), FIND_TIMEOUT_MS)
            .click()
        device.wait(
            Until.findObject(By.res(PLUGIN_PKG, "action_center_root")),
            FIND_TIMEOUT_MS,
        )
        device
            .wait(Until.findObject(By.res(PLUGIN_PKG, "clock")), FIND_TIMEOUT_MS)
            .click()
        assertThat(device.wait(Until.gone(By.res(PLUGIN_PKG, "action_center_root")), 3_000L))
            .isTrue()
        assertThat(
                device.wait(
                    Until.findObject(By.res(PLUGIN_PKG, "calendar_clock_root")),
                    FIND_TIMEOUT_MS,
                )
            )
            .isNotNull()
    }
}
