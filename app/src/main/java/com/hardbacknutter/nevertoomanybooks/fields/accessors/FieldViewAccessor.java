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

import android.view.View;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Interface for view-specific accessors.
 * Handles interactions between the data and the View (with optional {@link FieldFormatter}).
 *
 * @param <T> type of Field value.
 */
public interface FieldViewAccessor<T> {

    View getView();

    /**
     * Hook up the view. Reminder: do <strong>NOT</strong> set the view in the constructor.
     * <strong>Implementation note</strong>: we don't provide a onCreateViewHolder()
     * method on purpose.
     * Using that would need to deal with {@code null} values.
     *
     * @param view to use
     */
    void setView(@NonNull View view);

    void setErrorView(@Nullable View errorView);

    void setError(@Nullable String errorText);

    void setField(@NonNull Field<T> field);

    @Nullable
    default FieldFormatter<T> getFormatter() {
        return null;
    }

    /**
     * Set the formatter to use for this field.
     *
     * @param formatter to use
     */
    default void setFormatter(@NonNull FieldFormatter<T> formatter) {
        throw new IllegalStateException("Formatter not supported");
    }

    /**
     * Get the value from the view associated with the Field and return it as an Object.
     *
     * @return the value
     */
    @Nullable
    T getValue();

    /**
     * Use the passed value to set the Field.
     * <p>
     * If {@code null} is passed in, the implementation should set the widget to
     * its native default value. (e.g. string -> "", number -> 0, etc)
     *
     * @param value to set.
     */
    void setValue(@NonNull T value);

    /**
     * Fetch the value from the passed DataManager, and set the Field.
     *
     * @param source Collection to load data from.
     */
    void setValue(@NonNull DataManager source);

    /**
     * Get the value from the view associated with the Field
     * and put a <strong>native typed value</strong> in the passed collection.
     *
     * @param target Collection to save value into.
     */
    void getValue(@NonNull DataManager target);

    /**
     * Check if this field is considered to be 'empty'.
     * The encapsulated type decides what 'empty' means.
     * <p>
     * This default implementation considers
     * <ul>
     *      <li>{@code null}</li>
     *      <li>Number == 0</li>
     *      <li>Boolean == false</li>
     *      <li>empty Collection</li>
     *      <li>checkable not checked</li>
     *      <li>empty String</li>
     * </ul>
     * to be empty.
     * If the test can be optimized (i.e. if {@link #getValue} can be shortcut),
     * then you should override this default method.
     *
     * @return {@code true} if empty.
     */
    default boolean isEmpty() {
        final T value = getValue();
        return value == null
               || value instanceof Number && ((Number) value).doubleValue() == 0.0d
               || value instanceof Boolean && !(Boolean) value
               || value instanceof Collection && ((Collection) value).isEmpty()
               || value instanceof Checkable && !((Checkable) value).isChecked()
               || value.toString().isEmpty();
    }
}
