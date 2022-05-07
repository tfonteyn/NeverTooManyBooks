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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * A persistable filter. See {@link DBDefinitions#TBL_BOOKSHELF_FILTERS}.
 *
 * @param <T> actual type of the value
 */
public interface PFilter<T>
        extends Filter {

    /**
     * The "preference" name; used as the key in the database table.
     *
     * @return name
     */
    @NonNull
    String getPrefName();

    /**
     * Get the stringified version of the value to store in the database table.
     *
     * @return string
     */
    @Nullable
    String getValueAsString();

    /**
     * Set the typed value.
     */
    void setValueAsString(@Nullable String value);

    /**
     * Set the typed value.
     */
    void setValue(@Nullable T value);

    /**
     * UI usage.
     * <p>
     * Get the text to show the user representing the value.
     *
     * @return string
     */
    @NonNull
    String getValueText(@NonNull Context context);

    /**
     * UI usage.
     * <p>
     * Get a human readable label/name for this filter.
     * <p>
     * Returns "Filter" for generic/dynamic filters.
     *
     * @param context Current context
     *
     * @return a human readable label/name for this filter.
     */
    @NonNull
    default String getLabel(@NonNull final Context context) {
        return context.getString(R.string.lbl_filter);
    }
}