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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.SQLException;

import androidx.annotation.NonNull;

/**
 * Debug exception to make it clear when we've got SQL issues...
 */
public class ColumnNotPresentException
        extends SQLException {

    private static final long serialVersionUID = -5065796313450875326L;

    /**
     * Constructor.
     *
     * @param columnName the name
     */
    public ColumnNotPresentException(@NonNull final String columnName) {
        super("Column `" + columnName + "` not present in cursor");
    }
}
