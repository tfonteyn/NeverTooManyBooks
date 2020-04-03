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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.FieldViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

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
    private final SparseArray<Field> mAllFields = new SparseArray<>();

    public boolean isEmpty() {
        return mAllFields.size() == 0;
    }

    /**
     * Add a Field.
     *
     * @param <T>      type of Field value.
     * @param id       for the field/view
     * @param accessor to use
     * @param key      Key used to access a {@link DataManager}
     *
     * @return The resulting Field.
     */
    @NonNull
    public <T> Field<T> add(@IdRes final int id,
                            @NonNull final FieldViewAccessor<T> accessor,
                            @NonNull final String key) {
        return add(id, accessor, key, key);
    }

    /**
     * Add an Array based Field.
     *
     * @param <T>       type of Field value.
     * @param id        for the field/view
     * @param accessor  to use
     * @param key       Key used to access a {@link DataManager}
     * @param entityKey The preference key to check if this Field is used or not
     *
     * @return The resulting Field.
     */
    @NonNull
    public <T> Field<T> add(@IdRes final int id,
                            @NonNull final FieldViewAccessor<T> accessor,
                            @NonNull final String key,
                            @NonNull final String entityKey) {

        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException(ErrorMsg.KEY_SHOULD_NOT_BE_EMPTY);
            }
        }

        Field<T> field = new Field<>(id, accessor, key, entityKey);
        mAllFields.put(id, field);
        return field;
    }

    /**
     * Return the Field associated with the passed ID.
     *
     * @param id  Field/View ID
     * @param <T> type of Field value.
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    public <T> Field<T> getField(@IdRes final int id)
            throws IllegalArgumentException {
        //noinspection unchecked
        Field<T> field = (Field<T>) mAllFields.get(id);
        if (field != null) {
            return field;
        }

        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(id));
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     *
     * @param dataManager DataManager to load Field objects from.
     */
    public void setAll(@NonNull final DataManager dataManager) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
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
            Field field = mAllFields.valueAt(f);
            if (field.isAutoPopulated()) {
                field.getAccessor().getValue(dataManager);
                field.validate();
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     *
     * @param parent      parent view for all fields.
     * @param hideIfEmpty hide the field if it's empty
     *                    set to {@code true} when displaying; {@code false} when editing.
     * @param keepHidden  keep a field hidden if it's already hidden
     */
    public void resetVisibility(@NonNull final View parent,
                                final boolean hideIfEmpty,
                                final boolean keepHidden) {
        for (int f = 0; f < mAllFields.size(); f++) {
            mAllFields.valueAt(f).setVisibility(parent, hideIfEmpty, keepHidden);
        }
    }

    /**
     * Prepare all fields.
     * <ol>
     *      <li>Find/update their View from the passed parent View</li>
     *      <li>Disables the {@link AfterChangeListener}</li>
     * </ol>
     *
     * @param parentView for the view of each field
     */
    public void prepareViewsForPopulating(@NonNull final View parentView) {
        for (int f = 0; f < mAllFields.size(); f++) {
            Field field = mAllFields.valueAt(f);
            field.setParentView(parentView);
            field.setAfterFieldChangeListener(null);
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
               + "mAllFields=" + mAllFields
               + '}';
    }

    public interface AfterChangeListener {

        void afterFieldChange(@IdRes int fieldId);
    }

    public static class FormattedDiacriticArrayAdapter
            extends DiacriticArrayAdapter<String> {

        /** The formatter to apply on each line item. */
        @Nullable
        private final FieldFormatter<String> mFormatter;

        /**
         * Constructor.
         *
         * @param context   Current context.
         * @param objects   The objects to represent in the list view
         * @param formatter to use
         */
        public FormattedDiacriticArrayAdapter(@NonNull final Context context,
                                              @NonNull final List<String> objects,
                                              @Nullable final FieldFormatter<String> formatter) {
            super(context, R.layout.dropdown_menu_popup_item, 0, objects);
            mFormatter = formatter;
        }

        @Nullable
        @Override
        public String getItem(final int position) {
            if (mFormatter != null) {
                return mFormatter.format(getContext(), super.getItem(position));
            } else {
                return super.getItem(position);
            }
        }
    }
}
