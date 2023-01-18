/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.entities.Series;

@SuppressWarnings("UnusedReturnValue")
public interface SeriesDao
        extends EntityBookLinksDao<Series> {

    /**
     * Get a unique list of all {@link Series} titles.
     *
     * @return The list
     */
    @NonNull
    ArrayList<String> getNames();

    /**
     * Get all series; mainly for the purpose of exports.
     *
     * @return Cursor over all series
     */
    @NonNull
    Cursor fetchAll();

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
     * Update the 'complete' status of a {@link Series}.
     *
     * @param seriesId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(long seriesId,
                        boolean isComplete);
}
