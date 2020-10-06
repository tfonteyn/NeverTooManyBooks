/*
 * @Copyright 2020 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Base implementation for {@link TextViewAccessor} and {@link EditTextAccessor}.
 *
 * @param <T> type of Field value. Usually just String, but any type supported by the
 *            {@link DataManager} should work (if not -> bug).
 * @param <V> type of Field View, must extend TextView
 */
public abstract class TextAccessor<T, V extends TextView>
        extends BaseDataAccessor<T, V> {

    /** Optional formatter. */
    @Nullable
    final FieldFormatter<T> mFormatter;

    /**
     * Constructor.
     *
     * @param formatter (optional) formatter to use
     */
    TextAccessor(@Nullable final FieldFormatter<T> formatter) {
        mFormatter = formatter;
    }

    @Nullable
    public FieldFormatter<T> getFormatter() {
        return mFormatter;
    }

    @Override
    public void setValue(@NonNull final DataManager source) {
        final Object obj = source.get(mField.getKey());
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
