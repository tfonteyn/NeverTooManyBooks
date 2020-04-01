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
package com.hardbacknutter.nevertoomanybooks.fields.accessors;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Stores and retrieves data from a TextView.
 * <p>
 * The actual value is simply stored in a local variable.
 * No attempt to extract is done.
 *
 * <pre>
 *     {@code
 *             <com.google.android.material.textfield.TextInputLayout
 *             android:id="@+id/lbl_description"
 *             style="@style/Envelope.EditText"
 *             android:hint="@string/lbl_description"
 *             app:layout_constraintEnd_toEndOf="parent"
 *             app:layout_constraintStart_toStartOf="parent"
 *             app:layout_constraintTop_toBottomOf="@id/lbl_genre"
 *             >
 *
 *             <com.google.android.material.textfield.TextInputEditText
 *                 android:id="@+id/description"
 *                 style="@style/notesTextEntry"
 *                 android:layout_width="match_parent"
 *                 tools:ignore="Autofill"
 *                 tools:text="@tools:sample/lorem/random"
 *                 />
 *
 *         </com.google.android.material.textfield.TextInputLayout>}
 * </pre>
 *
 * @param <T> type of Field value.
 */
public class TextAccessor<T>
        extends BaseDataAccessor<T> {

    /** Log tag. */
    private static final String TAG = "TextAccessor";

    /** Optional formatter. */
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

    @Nullable
    @Override
    public T getValue() {
        return mRawValue;
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
