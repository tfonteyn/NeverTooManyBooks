/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.database.tasks;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

public class Scheduler {

    /** Flag to indicate FTS rebuild is required at startup. */
    public static final String PREF_REBUILD_FTS =
            StartupViewModel.PREF_PREFIX + "rebuild.fts";
    /** Flag to indicate OrderBy columns must be rebuild at startup. */
    public static final String PREF_REBUILD_ORDERBY_COLUMNS =
            StartupViewModel.PREF_PREFIX + "rebuild.ob.title";
    /** Flag to indicate all indexes must be rebuild at startup. */
    public static final String PREF_REBUILD_INDEXES =
            StartupViewModel.PREF_PREFIX + "rebuild.index";

    /**
     * Set the flag to indicate an FTS rebuild is required.
     *
     * @param context Current context
     */
    @SuppressWarnings("WeakerAccess")
    public static void scheduleFtsRebuild(@NonNull final Context context,
                                          final boolean flag) {
        schedule(context, PREF_REBUILD_FTS, flag);
    }

    /**
     * Set or remove the flag to indicate an OrderBy column rebuild is required.
     *
     * @param context Current context
     */
    public static void scheduleOrderByRebuild(@NonNull final Context context,
                                              final boolean flag) {
        schedule(context, PREF_REBUILD_ORDERBY_COLUMNS, flag);
    }

    /**
     * Set or remove the flag to indicate an index rebuild is required.
     *
     * @param context Current context
     */
    @SuppressWarnings("WeakerAccess")
    public static void scheduleIndexRebuild(@NonNull final Context context,
                                            final boolean flag) {
        schedule(context, PREF_REBUILD_INDEXES, flag);
    }

    private static void schedule(@NonNull final Context context,
                                 @NonNull final String key,
                                 final boolean flag) {
        final SharedPreferences.Editor ed = PreferenceManager
                .getDefaultSharedPreferences(context).edit();
        if (flag) {
            ed.putBoolean(key, true);
        } else {
            ed.remove(key);
        }
        ed.apply();
    }
}
