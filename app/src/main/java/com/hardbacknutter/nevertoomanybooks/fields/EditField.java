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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * Field definition contains all information and methods necessary
 * to manage display and extraction of data in a view.
 * <ul>Features provides are:
 *      <li>Handling of visibility via preferences / 'mIsUsedKey' property of a field.</li>
 *      <li>Understanding of kinds of views (setting a Checkbox (Checkable) value to 'true'
 *          will work as expected as will setting the value of an ExposedDropDownMenu).
 *          As new view types are added, it will be necessary to add new {@link FieldViewAccessor}
 *          implementations.
 *          In some specific circumstances, an accessor can be defined manually.</li>
 *      <li> Custom data accessors and formatters to provide application-specific data rules.</li>
 *      <li> simplified extraction of data.</li>
 * </ul>
 * <p>
 * Accessors
 * <p>
 * A {@link FieldViewAccessor} handles interactions between the value and the View
 * (with an optional {@link FieldFormatter}).
 * <p>
 * Formatters
 * <p>
 * A Formatter can be set on {@link android.widget.TextView}
 * and any class extending {@link EditTextAccessor}
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
 *          {@link FieldViewAccessor#setInitialValue(DataManager)} ->
 *          {@link FieldViewAccessor#setValue(Object)} ->
 *          populates the View.</li>
 *      <li>IN  (with formatter):<br>
 *          {@link FieldViewAccessor#setInitialValue(DataManager)} ->
 *          {@link FieldViewAccessor#setValue(Object)} ->
 *          {@link FieldFormatter#apply} ->
 *          populates the View.</li>
 *       <li>OUT (no formatter ):
 *          View ->
 *          {@link FieldViewAccessor#getValue()} ->
 *          {@link FieldViewAccessor#getValue(DataManager)}</li>
 *      <li>OUT (with formatter):
 *          View ->
 *          {@link EditFieldFormatter#extract} ->
 *          {@link FieldViewAccessor#getValue()} ->
 *          {@link FieldViewAccessor#getValue(DataManager)}</li>
 * </ul>
 *
 * @param <T> type of Field value.
 * @param <V> type of View for this field
 */
public interface EditField<T, V extends View>
        extends Field<T, V> {

    void setError(@Nullable String errorText);

    void setErrorIfEmpty(@NonNull String errorText);

    boolean isEmpty();

    @Nullable
    T getValue();

    /**
     * Store the current value into the given {@link DataManager}.
     *
     * @param target to put the value in
     */
    void putValue(@NonNull DataManager target);

    /**
     * Propagate the fact that this field was changed to the {@link AfterFieldChangeListener}.
     */
    void onChanged();

    void setAfterFieldChangeListener(@Nullable AfterFieldChangeListener listener);

    @FunctionalInterface
    interface AfterFieldChangeListener {

        void onAfterFieldChange(@NonNull EditField<?, ? extends View> field);
    }
}
