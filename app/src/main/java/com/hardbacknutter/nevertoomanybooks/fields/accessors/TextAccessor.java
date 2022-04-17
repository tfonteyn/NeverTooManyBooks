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

import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Base implementation for {@link TextViewAccessor} and {@link EditTextAccessor}.
 * <p>
 * Supports an optional {@link FieldFormatter}.
 *
 * @param <T> type of Field value. Usually just String, but any type supported by the
 *            {@link DataManager} should work (if not -> bug).
 * @param <V> type of Field View, must extend TextView
 */
public abstract class TextAccessor<T, V extends TextView>
        extends BaseFieldViewAccessor<T, V> {

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

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        final Object obj = source.get(mField.getKey());
        if (obj != null) {
            //noinspection unchecked
            mInitialValue = (T) obj;
            setValue(mInitialValue);
        }
    }

    @Override
    public void getValue(@NonNull final DataManager target) {
        // We don't know the type <T> so put as Object (DataManager will auto-detect).
        // It will be the original rawValue.
        target.put(mField.getKey(), getValue());
    }

    /**
     * Check if the given value is considered to be 'empty'.
     * The encapsulated type decides what 'empty' means.
     * <p>
     * An Object is considered to be empty if:
     * <ul>
     *      <li>{@code null}</li>
     *      <li>{@code Money.isZero()}</li>
     *      <li>{@code Number.doubleValue() == 0.0d}</li>
     *      <li>{@code Boolean == false}</li>
     *      <li>{@code Collection.isEmpty}</li>
     *      <li>{@code !Checkable.isChecked()}</li>
     *      <li>{@code String.isEmpty()}</li>
     * </ul>
     *
     * @return {@code true} if empty.
     */
    public boolean isEmpty(@Nullable final T o) {
        //noinspection rawtypes
        return o == null
               || o instanceof Money && ((Money) o).isZero()
               || o instanceof Number && ((Number) o).doubleValue() == 0.0d
               || o instanceof Boolean && !(Boolean) o
               || o instanceof Collection && ((Collection) o).isEmpty()
               || o instanceof Checkable && !((Checkable) o).isChecked()
               || o.toString().isEmpty();
    }

}
