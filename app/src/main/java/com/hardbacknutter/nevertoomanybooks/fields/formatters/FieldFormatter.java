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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Interface definition for Field formatter.
 *
 * <strong>Do not store Context or View in a formatter.</strong>
 *
 * @param <T> type of Field value.
 */
@FunctionalInterface
public interface FieldFormatter<T> {

    /**
     * <strong>DO NOT CHANGE ANY CLASS VARIABLE HERE</strong>.
     *
     * @param context  Current context
     * @param rawValue Input value
     *
     * @return formatted value
     */
    @NonNull
    String format(@NonNull Context context,
                  @Nullable T rawValue);

    /**
     * Apply a value to a View.
     * <strong>Update class variables (if any) here.</strong>
     *
     * @param rawValue Input value
     * @param view     to populate
     */
    default void apply(@Nullable final T rawValue,
                       @NonNull final TextView view) {
        view.setText(format(view.getContext(), rawValue));
    }
}
