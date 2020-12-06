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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * This is the class that manages data and views for an Activity/Fragment;
 * access to the data that each view represents should be handled via this class
 * (and its related classes) where possible.
 * <ul>Features provides are:
 *      <li>Handling of visibility via preferences / 'mIsUsedKey' property of a field.</li>
 *      <li>Understanding of kinds of views (setting a Checkbox (Checkable) value to 'true'
 *          will work as expected as will setting the value of a Spinner).
 *          As new view types are added, it will be necessary to add new {@link FieldViewAccessor}
 *          implementations.
 *          In some specific circumstances, an accessor can be defined manually.</li>
 *      <li> Custom data accessors and formatter to provide application-specific data rules.</li>
 *      <li> simplified extraction of data.</li>
 * </ul>
 * <p>
 * Formatter and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatter defined for a field.
 * Formatters only really make sense for TextView and EditText elements.
 * Formatters should implement {@link FieldFormatter#format(Context, Object)} where the Object
 * is transformed to a String - DO NOT CHANGE class variables while doing this.
 * In contrast {@link FieldFormatter#apply} CAN change class variables
 * but should leave the real formatter to the format method.
 * <p>
 * This way, other code can access {@link FieldFormatter#format(Context, Object)}
 * without side-effects.
 *
 * <ul>Data flows to and from a view as follows:
 *      <li>IN  (no formatter ):<br>
 *          {@link FieldViewAccessor#setValue(DataManager)} ->
 *          {@link FieldViewAccessor#setValue(Object)} ->
 *          populates the View.</li>
 *      <li>IN  (with formatter):<br>
 *          {@link FieldViewAccessor#setValue(DataManager)} ->
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
 */
public class Fields {

    /** the list with all fields. */
    private final SparseArray<Field<?, ? extends View>> mAllFields = new SparseArray<>();

    public int size() {
        return mAllFields.size();
    }

    public boolean isEmpty() {
        return mAllFields.size() == 0;
    }

    /**
     * Add a Field.
     *
     * @param <T>      type of Field value.
     * @param <V>      type of View for this field.
     * @param id       for the field/view
     * @param accessor to use
     * @param key      Key used to access a {@link DataManager}
     *
     * @return The resulting Field.
     */
    @NonNull
    public <T, V extends View> Field<T, V> add(@IdRes final int id,
                                               @NonNull final FieldViewAccessor<T, V> accessor,
                                               @NonNull final String key) {
        return add(id, accessor, key, key);
    }

    /**
     * Add an Array based Field.
     *
     * @param <T>       type of Field value.
     * @param <V>       type of View for this field.
     * @param id        for the field/view
     * @param accessor  to use
     * @param key       Key used to access a {@link DataManager}
     * @param entityKey The preference key to check if this Field is used or not
     *
     * @return The resulting Field.
     */
    @NonNull
    public <T, V extends View> Field<T, V> add(@IdRes final int id,
                                               @NonNull final FieldViewAccessor<T, V> accessor,
                                               @NonNull final String key,
                                               @NonNull final String entityKey) {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(key, "key");
        }

        final Field<T, V> field = new Field<>(id, accessor, key, entityKey);
        mAllFields.put(id, field);
        return field;
    }

    /**
     * Return the Field associated with the passed ID.
     *
     * @param <T> type of Field value.
     * @param <V> type of View for this field.
     * @param id  Field/View ID
     *
     * @return Associated Field.
     */
    @NonNull
    public <T, V extends View> Field<T, V> getField(@IdRes final int id) {
        //noinspection unchecked
        final Field<T, V> field = (Field<T, V>) mAllFields.get(id);
        if (field != null) {
            return field;
        }

        throw new IllegalArgumentException("mAllFields.size()=" + mAllFields.size()
                                           + "|fieldId= " + id);
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     *
     * @param dataManager DataManager to load Field objects from.
     */
    public void setAll(@NonNull final DataManager dataManager) {
        for (int f = 0; f < mAllFields.size(); f++) {
            final Field<?, ?> field = mAllFields.valueAt(f);
            if (field.isAutoPopulated()) {
                // do NOT call onChanged, as this is the initial load
                field.getAccessor().setValue(dataManager);
            }
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void getAll(@NonNull final DataManager dataManager) {
        for (int f = 0; f < mAllFields.size(); f++) {
            final Field<?, ?> field = mAllFields.valueAt(f);
            if (field.isAutoPopulated()) {
                field.getAccessor().getValue(dataManager);
                field.validate();
            }
        }
    }

    /**
     * Set field visibility based on their content and user preferences.
     *
     * @param parent                 parent view for all fields in this collection.
     * @param hideEmptyFields        hide empty field:
     *                               Use {@code true} when displaying;
     *                               and {@code false} when editing.
     * @param keepHiddenFieldsHidden keep a field hidden if it's already hidden
     *                               (even when it has content)
     */
    public void setVisibility(@NonNull final View parent,
                              final boolean hideEmptyFields,
                              final boolean keepHiddenFieldsHidden) {
        for (int f = 0; f < mAllFields.size(); f++) {
            mAllFields.valueAt(f).setVisibility(parent, hideEmptyFields, keepHiddenFieldsHidden);
        }
    }

    /**
     * Update the Views for all fields.
     *
     * @param parentView for the view of each field
     */
    public void setParentView(@NonNull final View parentView) {
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(parentView.getContext());
        for (int f = 0; f < mAllFields.size(); f++) {
            mAllFields.valueAt(f).setParentView(global, parentView);
        }
    }

    /**
     * Set the AfterChangeListener on all fields.
     * This should be called <strong>after</strong> the bulk setting of the values.
     *
     * @param listener the listener for field changes
     */
    public void setAfterChangeListener(@Nullable final AfterChangeListener listener) {
        for (int f = 0; f < mAllFields.size(); f++) {
            mAllFields.valueAt(f).setAfterFieldChangeListener(listener);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "Fields{"
               + ", mAllFields=" + mAllFields
               + '}';
    }

    public interface AfterChangeListener {

        void afterFieldChange(@IdRes int fieldId);
    }

}
