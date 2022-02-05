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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Stores and retrieves data from a TextView.
 * <p>
 * The actual value is simply stored in a local variable.
 * No attempt to extract is done.
 *
 * @param <T> type of Field value.
 */
public class TextViewAccessor<T>
        extends TextAccessor<T, TextView> {

    /** Log tag. */
    private static final String TAG = "TextViewAccessor";

    /**
     * Constructor.
     */
    public TextViewAccessor() {
        super(null);
    }

    /**
     * Constructor.
     *
     * @param formatter to use
     */
    public TextViewAccessor(@NonNull final FieldFormatter<T> formatter) {
        super(formatter);
    }

    @Nullable
    @Override
    public T getValue() {
        return mRawValue;
    }

    @Override
    public void setValue(@Nullable final T value) {
        mRawValue = value;

        final TextView view = getView();
        if (view != null) {
            if (mFormatter != null) {
                try {
                    mFormatter.apply(mRawValue, view);
                    return;

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link BookCursor} class docs.
                    // Also see {@link SearchCoordinator#accumulateStringData}
                    Logger.error(TAG, e, value);
                }
            }

            // No formatter, or ClassCastException.
            view.setText(mRawValue != null ? String.valueOf(mRawValue) : "");
        }
    }
}
