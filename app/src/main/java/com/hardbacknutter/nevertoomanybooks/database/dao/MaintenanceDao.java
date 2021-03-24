/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;

public interface MaintenanceDao {

    /**
     * Purge anything that is no longer in use.
     * <p>
     * Purging is no longer done at every occasion where it *might* be needed.
     * It was noticed (in the logs) that it was done far to often.
     * <ul>It is now called only:
     * <li>Before a (Zip) backup.</li>
     * <li>After an import of data (all sources).</li>
     * </ul>
     * So orphaned data will stay around a little longer which in fact may be beneficial
     * while entering/correcting a book collection.
     */
    void purge();

    /**
     * Repopulate all OrderBy TITLE columns.
     * Cleans up whitespace and non-ascii characters.
     * Optional reordering.
     *
     * <p>
     * Book:     KEY_TITLE  ==> KEY_TITLE_OB
     * TOCEntry: KEY_TITLE  ==> KEY_TITLE_OB
     * Series:   KEY_SERIES_TITLE => KEY_SERIES_TITLE_OB
     *
     * @param context Current context
     * @param reorder flag whether to reorder or not
     */
    void rebuildOrderByTitleColumns(@NonNull Context context,
                                    boolean reorder);

}
