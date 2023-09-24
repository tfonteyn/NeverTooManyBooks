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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

@SuppressWarnings("UnusedReturnValue")
public interface SeriesDao
        extends EntityBookLinksDao<Series> {

    /**
     * Get a unique list of all {@link Series} titles.
     *
     * @return The list
     */
    @NonNull
    List<String> getNames();

    /**
     * Get all series; mainly for the purpose of exports.
     *
     * @return Cursor over all series
     */
    @NonNull
    Cursor fetchAll();

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    default boolean pruneList(@NonNull final Context context,
                              @NonNull final Collection<Series> list,
                              @NonNull final Function<Series, Locale> localeSupplier) {
        return pruneList(context, list, Prefs.normalizeSeriesTitle(context), localeSupplier);
    }

    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Series> list,
                      boolean normalizeTitles,
                      @NonNull Function<Series, Locale> localeSupplier);

    /**
     * Get the language (ISO3) code for a {@link Series}.
     * This is defined as the language code for the first book in the {@link Series}.
     *
     * @param id series to get
     *
     * @return the ISO3 code, or the empty String when none found.
     */
    @NonNull
    String getLanguage(long id);

    /**
     * Update the 'complete' status for the given {@link Series}.
     * <p>
     * If successful, the series object will have been updated with the new status.
     *
     * @param series   to update
     * @param complete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(@NonNull Series series,
                        boolean complete);
}
