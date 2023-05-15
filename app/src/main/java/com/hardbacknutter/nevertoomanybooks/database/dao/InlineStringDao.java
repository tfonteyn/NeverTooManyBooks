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

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Represents a String field 'inline' in a table.
 */
public interface InlineStringDao {

    /**
     * Get a unique list of all entries.
     *
     * @return The list
     */
    @NonNull
    List<String> getList();

    /**
     * Rename an entry.
     *
     * @param from name
     * @param to   name
     */
    void rename(@NonNull String from,
                @NonNull String to);
}
