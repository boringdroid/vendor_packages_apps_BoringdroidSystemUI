/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.ComponentName;
import android.os.UserHandle;

import com.android.launcher3.ItemInfo;

import java.util.HashSet;

/**
 * A utility class to check for {@link ItemInfo}
 */
public interface ItemInfoMatcher {

    boolean matches(ItemInfo info, ComponentName cn);

    /**
     * Filters {@param infos} to those satisfying the {@link #matches(ItemInfo, ComponentName)}.
     */
    default HashSet<ItemInfo> filterItemInfos() {
        return new HashSet<>();
    }

    /**
     * Returns a new matcher with returns true if either this or {@param matcher} returns true.
     */
    default ItemInfoMatcher or(ItemInfoMatcher matcher) {
        return (info, cn) -> matches(info, cn) || matcher.matches(info, cn);
    }

    /**
     * Returns a new matcher with returns true if both this and {@param matcher} returns true.
     */
    default ItemInfoMatcher and(ItemInfoMatcher matcher) {
        return (info, cn) -> matches(info, cn) && matcher.matches(info, cn);
    }

    /**
     * Returns a new matcher which returns the opposite boolean value of the provided
     * {@param matcher}.
     */
    static ItemInfoMatcher not(ItemInfoMatcher matcher) {
        return (info, cn) -> !matcher.matches(info, cn);
    }

    static ItemInfoMatcher ofUser(UserHandle user) {
        return (info, cn) -> info.user.equals(user);
    }

    static ItemInfoMatcher ofComponents(HashSet<ComponentName> components, UserHandle user) {
        return (info, cn) -> components.contains(cn) && info.user.equals(user);
    }

    static ItemInfoMatcher ofPackages(HashSet<String> packageNames, UserHandle user) {
        return (info, cn) -> packageNames.contains(cn.getPackageName()) && info.user.equals(user);
    }

    static ItemInfoMatcher ofItemIds(IntSparseArrayMap<Boolean> ids, Boolean matchDefault) {
        return (info, cn) -> ids.get(info.id, matchDefault);
    }
}
