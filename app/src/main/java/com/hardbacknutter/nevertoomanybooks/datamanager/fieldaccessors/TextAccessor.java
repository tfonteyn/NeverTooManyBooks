/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Implementation that stores and retrieves data from a TextView.
 * <p>
 * The actual value is simply stored in a local variable.
 * No attempt to extract is done.
 *
 * @param <T> type of Field value.
 */
public class TextAccessor<T>
        extends BaseDataAccessor<T> {

    private static final String TAG = "TextAccessor";

    @Nullable
    final FieldFormatter<T> mFormatter;

    public TextAccessor() {
        mFormatter = null;
    }

    public TextAccessor(@Nullable final FieldFormatter<T> formatter) {
        mFormatter = formatter;
    }

    @Nullable
    public FieldFormatter<T> getFormatter() {
        return mFormatter;
    }

    @NonNull
    @Override
    public T getValue() {
        if (mRawValue != null) {
            return mRawValue;
        } else {
            // all non-String field should have formatters.
            // This means at this point that <T> MUST be a String.
            // If we get an Exception here then the developer made a boo-boo.
            //noinspection unchecked
            return (T) "";
        }
    }

    @Override
    public void setValue(@NonNull final T value) {
        mRawValue = value;

        TextView view = (TextView) getView();
        if (mFormatter != null) {
            try {
                mFormatter.apply(mRawValue, view);
                return;

            } catch (@NonNull final ClassCastException e) {
                // Due to the way a Book loads data from the database,
                // it's possible that it gets the column type wrong.
                // See {@link BookCursor} class docs.
                Logger.error(view.getContext(), TAG, e, value);
            }
        }

        // if we don't have a formatter, or if we had a ClassCastException
        view.setText(String.valueOf(value));
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        Object obj = source.get(mField.getKey());
        if (obj != null) {
            //noinspection unchecked
            setValue((T) obj);
        }
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        // We don't know the type <T> so put as Object (DataManager will auto-detect).
        // It will be the original rawValue.
        target.put(mField.getKey(), getValue());
    }
}
