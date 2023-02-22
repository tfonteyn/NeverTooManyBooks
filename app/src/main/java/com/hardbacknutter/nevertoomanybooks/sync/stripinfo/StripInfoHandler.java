/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

public class StripInfoHandler {

    /** Whether to show any sync menus at all. */
    public static final String PK_ENABLED = EngineId.StripInfoBe.getPreferenceKey() + ".enabled";

    /**
     * Check if SYNC menus should be shown at all. This does not affect searching.
     *
     * @param context Current context
     *
     * @return {@code true} if menus should be shown
     */
    @AnyThread
    public static boolean isSyncEnabled(@NonNull final Context context) {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(PK_ENABLED, false);
        } else {
            return false;
        }
    }
}
