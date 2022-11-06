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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Interface definition for Field formatter.
 * <p>
 * <strong>Do not store Context or View in a formatter.</strong>
 *
 * @param <T> type of Field value.
 */
public interface EditFieldFormatter<T>
        extends FieldFormatter<T> {

    /**
     * Extract the native typed value from the displayed version.
     * This should basically do the reverse operation of {@link #format(Context, Object)}.
     *
     * @param context Current context
     * @param text    to extract the value from
     *
     * @return The extracted value
     */
    @NonNull
    T extract(@NonNull Context context,
              @NonNull String text);
}
