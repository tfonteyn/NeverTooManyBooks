/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.SharedPreferences;

import androidx.core.math.MathUtils;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

public final class Tuning {
    static final String PK_OFFSCREEN_CACHE_SIZE = "booklist.view.cache.size";
    /**
     * Number of views to cache offscreen arbitrarily set to 15 based on Google "advice".
     * The default is 2.
     */
    static final int MIN_OFFSCREEN_CACHE_SIZE = 2;
    static final int DEFAULT_OFFSCREEN_CACHE_SIZE = 15;
    static final int MAX_OFFSCREEN_CACHE_SIZE = 30;

    private Tuning() {
    }

    public static int getOffscreenCacheSize() {
        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        // Protect against silly values
        return MathUtils.clamp(prefs.getInt(Tuning.PK_OFFSCREEN_CACHE_SIZE,
                                            Tuning.DEFAULT_OFFSCREEN_CACHE_SIZE),
                               Tuning.MIN_OFFSCREEN_CACHE_SIZE,
                               Tuning.MAX_OFFSCREEN_CACHE_SIZE);
    }
}
