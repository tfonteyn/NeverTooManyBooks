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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;

public interface IsoLanguageDao {

    @NonNull
    String findByDisplayName(@NonNull String displayName);

    /**
     * Count all entries. This is a check on the table being empty.
     *
     * @return number of mappings
     */
    int count();

    /**
     * Create entries for the given locale.
     *
     * @param userLocale to create a cached list for.
     *
     * @throws DaoInsertException on failure
     */
    void add(@NonNull Locale userLocale)
            throws DaoInsertException;
}
