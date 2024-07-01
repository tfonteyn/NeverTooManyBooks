/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * A persistable {@link Filter}. See {@link DBDefinitions#TBL_BOOKSHELF_FILTERS}.
 * <p>
 * When a filter is based on a {@link com.hardbacknutter.nevertoomanybooks.database.DBKey},
 * it will check the global visibility for that field.
 * i.e. a Style can have a particular field hidden from display, but the filter
 * will respect the <strong>global USAGE</strong> of that field.
 *
 * @param <T> actual type of the value
 */
public interface PFilter<T>
        extends Filter {

    /**
     * The "preference" name; used as the key in the database table.
     *
     * @return name
     *
     * @see FilterFactory
     */
    @NonNull
    String getDBKey();

    /**
     * Get the stringified version of the value to store in the database table.
     *
     * @return string
     */
    @Nullable
    String getPersistedValue();

    /**
     * Set the stringified version value of the value as loaded from the database table.
     *
     * @param value to set
     */
    void setPersistedValue(@Nullable String value);

    /**
     * Get the typed value.
     * <p>
     * Implementations <strong>MUST</strong> return a copy of the internal value.
     *
     * @return value or {@code null}
     */
    @Nullable
    T getValue();

    /**
     * Set the typed value.
     *
     * @param context Current context
     * @param value   to set
     */
    void setValue(@NonNull Context context,
                  @Nullable T value);


    /**
     * UI usage: get a human readable name for this filter.
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

    /**
     * UI usage: get the text to show the user representing the given value.
     * The current filter value is not modified.
     *
     * @param context Current context
     * @param value   to stringify
     *
     * @return string
     */
    @Nullable
    String getValueText(@NonNull Context context,
                        @Nullable T value);

    /**
     * UI usage: get the text to show the user representing the current value.
     *
     * @param context Current context
     *
     * @return string
     */
    @Nullable
    default String getValueText(@NonNull final Context context) {
        return getValueText(context, getValue());
    }

    /**
     * UI usage: get the layout id for use in the Filter setup dialog.
     * <p>
     * Dev. note: we needed a unique id for filter types to use
     * as the viewType in the adapter. Instead of inventing one... the layout id is
     * already unique so we're using that.
     *
     * @return layout res id
     */
    @LayoutRes
    int getPrefLayoutId();
}
