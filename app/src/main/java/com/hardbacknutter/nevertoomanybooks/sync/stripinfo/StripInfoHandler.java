/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.AnyThread;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

public class StripInfoHandler {

    /** Preferences prefix. */
    private static final String PREFERENCE_KEY = EngineId.StripInfoBe.getPreferenceKey();

    /** Whether to show any sync menus at all. */
    public static final String PK_ENABLED = PREFERENCE_KEY + ".enabled";
    static final String PK_LAST_SYNC = PREFERENCE_KEY + ".last.sync.date";

    /**
     * Check if SYNC menus should be shown at all. This does not affect searching.
     *
     * @return {@code true} if menus should be shown
     */
    @AnyThread
    public static boolean isSyncEnabled() {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return ServiceLocator.getInstance().getSharedPreferences()
                                 .getBoolean(PK_ENABLED, false);
        } else {
            return false;
        }
    }
}
