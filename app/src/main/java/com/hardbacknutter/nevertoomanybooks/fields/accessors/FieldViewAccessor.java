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

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.validators.FieldValidator;

/**
 * Handles interactions between the Field and the View (with optional {@link FieldFormatter}).
 *
 * @param <T> type of Field value.
 * @param <V> type of Field View.
 */
public interface FieldViewAccessor<T, V extends View> {

    /**
     * Hook up the field.
     *
     * @param field to use
     */
    void setField(@NonNull Field<T, V> field);

    /**
     * Get the previously set view.
     *
     * @return view
     */
    @Nullable
    V getView();

    /**
     * Hook up the view. Reminder: do <strong>NOT</strong> set the view in the constructor.
     * <strong>Implementation note</strong>: we don't provide an onCreateViewHolder()
     * method on purpose.
     * Using that would need to deal with {@code null} values.
     *
     * @param view to use
     */
    void setView(@NonNull V view);

    /**
     * Get the previously set view.
     *
     * @return view
     *
     * @throws NullPointerException if the View is not set
     */
    @NonNull
    default V requireView() {
        return Objects.requireNonNull(getView());
    }

    /**
     * Set a view for displaying an error.
     *
     * @param errorView view
     */
    void setErrorView(@Nullable View errorView);

    /**
     * Display an error message in the previously set error view.
     *
     * @param errorText to display.
     */
    void setError(@Nullable String errorText);

    /**
     * Get the value from the view associated with the Field.
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
     * <p>
     * If the View is not available, the implementation should silently ignore this,
     * <strong>but should still set the raw value if applicable.</strong>
     *
     * @param value to set.
     */
    void setValue(@Nullable T value);

    /**
     * Get the value from the view associated with the Field
     * and put a <strong>native typed value</strong> in the passed {@link DataManager}.
     *
     * @param target Collection to save value into.
     */
    void getValue(@NonNull DataManager target);

    /**
     * Fetch the value from the passed {@link DataManager}, and set the Field.
     * <p>
     * This is used for the <strong>INITIAL LOAD</strong>, i.e. the value as stored
     * in the database.
     *
     * @param source {@link DataManager} to load the Field value from
     */
    void setInitialValue(@NonNull DataManager source);

    /**
     * Check if the given value is considered to be 'empty'.
     *
     * @return {@code true} if empty.
     */
    boolean isEmpty(@Nullable T value);

    /**
     * Convenience method to check if the current value is considered to be 'empty'.
     *
     * @return {@code true} if empty.
     */
    default boolean isEmpty() {
        return isEmpty(getValue());
    }

    /**
     * Convenience method to facilitate creating a non-empty {@link FieldValidator}.
     *
     * @param errorText to display if the field is empty.
     */
    default void setErrorIfEmpty(@NonNull final String errorText) {
        setError(isEmpty() ? errorText : null);
    }
}
