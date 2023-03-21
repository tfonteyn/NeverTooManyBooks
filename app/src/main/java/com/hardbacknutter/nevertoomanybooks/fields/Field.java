/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Field definition contains all information and methods necessary
 * to manage display and extraction of data in a view.
 *
 * <ul>Features provides are:
 *      <li>Handling of visibility via preferences / 'mIsUsedKey' property of a field.</li>
 *      <li>Understanding of kinds of views (setting a Checkbox (Checkable) value to 'true'
 *          will work as expected as will setting the value of an ExposedDropDownMenu).
 *          As new view types are added, it will be necessary to add new {@link Field}
 *          implementations.</li>
 *      <li>Data formatters to provide application-specific data rules.</li>
 *      <li>Simplified extraction of data.</li>
 * </ul>
 * <p>
 * Formatters
 * <p>
 * A Formatter can be set on {@link android.widget.TextView}
 * and any class extending {@link EditTextField}
 * i.e. for TextView and EditText elements.
 * Formatters should implement {@link FieldFormatter#format(Context, Object)} where the Object
 * is transformed to a String - DO NOT CHANGE class variables while doing this.
 * In contrast {@link FieldFormatter#apply} CAN change class variables
 * but should leave the real formatter to the format method.
 * <p>
 * This way, other code can access {@link FieldFormatter#format(Context, Object)}
 * without side-effects.
 * <p>
 * <ul>Data flows to and from a view as follows:
 *      <li>IN  (no formatter ):<br>
 *          {@link Field#setInitialValue(Context, DataManager, RealNumberParser)} ->
 *          {@link Field#setValue(Object)} ->
 *          populates the View.</li>
 *      <li>IN  (with FieldFormatter):<br>
 *          {@link Field#setInitialValue(Context, DataManager, RealNumberParser)} ->
 *          {@link Field#setValue(Object)} ->
 *          {@link FieldFormatter#apply} ->
 *          populates the View.</li>
 *
 *       <li>OUT (no formatter ):
 *          View ->
 *          {@link Field#getValue()} ->
 *          {@link Field#putValue(DataManager)}</li>
 *      <li>OUT (with EditFieldFormatter):
 *          View ->
 *          {@link EditFieldFormatter#extract(Context, String)} ->
 *          {@link Field#getValue()} ->
 *          {@link Field#putValue(DataManager)}</li>
 * </ul>
 *
 * @param <T> type of Field value.
 * @param <V> type of View for this field
 */
public interface Field<T, V extends View> {

    /**
     * Get the {@link FragmentId} in which this Field is handled.
     *
     * @return id
     */
    @NonNull
    FragmentId getFragmentId();

    /**
     * Get the id for the Field view.
     *
     * @return view id
     */
    @IdRes
    int getFieldViewId();

    /**
     * Hook up the views.
     * Reminder: do <strong>NOT</strong> set the view in the constructor.
     * <strong>Implementation note</strong>: we don't provide an onCreateViewHolder()
     * method on purpose.
     * Using that would need to deal with {@code null} values.
     *
     * @param parent to use
     */
    void setParentView(@NonNull View parent);

    /**
     * <strong>Conditionally</strong> set the visibility for the field and its related fields.
     *
     * @param parent                 parent view; used to find the <strong>related fields</strong>
     * @param hideEmptyFields        hide empty field:
     *                               Use {@code true} when displaying;
     *                               and {@code false} when editing.
     * @param keepHiddenFieldsHidden keep a field hidden if it's already hidden
     */
    void setVisibility(@NonNull View parent,
                       boolean hideEmptyFields,
                       boolean keepHiddenFieldsHidden);

    /**
     * Get the previously set view.
     *
     * @return view
     *
     * @throws NullPointerException if the View is not set
     */
    @NonNull
    V requireView();

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param context Current context
     *
     * @return {@code true} if the field *can* be visible
     */
    boolean isUsed(@NonNull Context context);

    /**
     * Check if this field can be automatically populated.
     *
     * @return {@code true} if it can
     */
    boolean isAutoPopulated();


    /**
     * Load the field from the passed {@link DataManager}.
     * <p>
     * This is used for the <strong>INITIAL LOAD</strong>, i.e. the value as stored
     * in the database.
     *
     * @param context          Current context
     * @param source           DataManager to load the Field objects from
     * @param realNumberParser to use for parsing
     */
    void setInitialValue(@NonNull Context context,
                         @NonNull DataManager source,
                         @NonNull RealNumberParser realNumberParser);

    /**
     * Get the value from the view associated with the Field.
     *
     * @return the value
     */
    @Nullable
    T getValue();

    /**
     * Set the value directly. (e.g. upon another field changing... etc...)
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
     * Put the <strong>native typed value</strong> in the passed {@link DataManager}.
     *
     * @param target {@link DataManager} to save the Field value into.
     */
    void putValue(@NonNull DataManager target);

    /**
     * Check if the current value is considered to be 'empty'.
     *
     * @return {@code true} if empty.
     */
    boolean isEmpty();


    /**
     * Propagate the fact that this field was changed to the {@link AfterChangedListener}.
     * The current value should be compared to the initial and/or given previous values.
     *
     * @param previous value to compare to
     */
    void notifyIfChanged(@Nullable T previous);

    void setAfterFieldChangeListener(@Nullable AfterChangedListener listener);

    /**
     * Display an error message in the previously set error view.
     * <p>
     * Supports setting the text on an {@link TextInputLayout} or {@link TextView}.
     * Fails silently if the view is not present.
     *
     * @param errorText to show
     */
    void setError(@Nullable String errorText);

    /**
     * Convenience method to facilitate creating a non-empty {@link Validator}.
     *
     * @param errorText to display if the field is empty.
     */
    default void setErrorIfEmpty(@NonNull final String errorText) {
        setError(isEmpty() ? errorText : null);
    }

    @FunctionalInterface
    interface AfterChangedListener {

        void onAfterChanged(@NonNull Field<?, ? extends View> field);
    }

    /**
     * Interface for all field-level validators.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field
     */
    @FunctionalInterface
    interface Validator<T, V extends View> {

        /**
         * Validation method.
         *
         * @param field to validate
         */
        void validate(@NonNull Field<T, V> field);
    }
}
