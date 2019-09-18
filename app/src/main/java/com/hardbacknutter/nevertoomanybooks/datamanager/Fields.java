/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Format;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <ul>Features provides are:
 * <li> handling of visibility via preferences</li>
 * <li> handling of 'group' visibility via the 'group' property of a field.</li>
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work
 * as expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.</li>
 * <li> Custom data accessors and formatter to provide application-specific data rules.</li>
 * <li> simplified extraction of data to a {@link ContentValues} collection.</li>
 * </ul>
 * <p>
 * Formatter and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatter defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 * <p>
 * On a set(), the accessor should call format() function then apply the value
 * On a get() the accessor should retrieve the value and apply the extract() function.
 * <p>
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 * <ul>Data flows to and from a view as follows:
 * <li>IN  ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> View</li>
 * <li>IN  (with formatter):
 * <br>(DataManager/Bundle) -> format() (via accessor) -> transform (in accessor) -> View</li>
 * <li>OUT ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> Object</li>
 * <li>OUT (with formatter):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> extract (via accessor) -> Object</li>
 * </ul>
 * <p>
 * <b>Usage:</b>
 * <ol>
 * <li>Which Views to Add?
 * <br>
 * It is not necessary to add every control to the 'Fields' collection, but as a general rule
 * any control that displays data from a database, or related derived data, or labels for such
 * data should be added.
 * <br>
 * Typical controls NOT added, are 'Save' and 'Cancel' buttons, or other controls whose
 * interactions are purely functional.</li>
 * <li>Handlers?
 * <br>
 * The add() method of Fields returns a new {@link Field} object which exposes the 'View' member;
 * this can be used to perform view-specific tasks like setting onClick() handlers.</li>
 * </ol>
 * TODO: Integrate the use of this collection with the {@link DataManager}.
 */
public class Fields {

    /** the list with all fields. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Field> mAllFields = new HashMap<>();
    /**
     * The activity or fragment related to this object.
     * Uses a WeakReference to the Activity/Fragment.
     */
    @NonNull
    private final FieldsContext mFieldContext;
    /** TextEdit fields will be watched. */
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener;

    /**
     * Constructor.
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    public Fields(@NonNull final Fragment fragment) {
        mFieldContext = new FragmentContext(fragment);
    }

    /**
     * Constructor.
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    Fields(@NonNull final Activity activity) {
        mFieldContext = new ActivityContext(activity);
    }

    /**
     * @param listener the listener for field changes
     */
    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener;
    }

    private void afterFieldChange(@NonNull final Field field,
                                  @Nullable final Object newValue) {
        if (mAfterFieldChangeListener != null) {
            //noinspection unchecked
            mAfterFieldChangeListener.afterFieldChange(field, newValue);
        }
    }

    /**
     * Accessor for related FieldsContext.
     * <p>
     * Provides access to {@link FieldsContext#findViewById(int)}
     *
     * @return FieldsContext for this collection.
     */
    @NonNull
    private FieldsContext getFieldContext() {
        return mFieldContext;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> add(@IdRes final int fieldId,
                             @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<String> field = Field.newField(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<Boolean> addBoolean(@IdRes final int fieldId,
                                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Boolean> field = Field.newField(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Field<Float> addFloat(@IdRes final int fieldId,
                                 @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Float> field = Field.newField(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId         Layout ID
     * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
     *                        Set to "" to suppress all access.
     * @param visibilityGroup Group name to determine visibility.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> add(@IdRes final int fieldId,
                             @NonNull final String key,
                             @NonNull final String visibilityGroup) {
        Field<String> field = Field.newField(this, fieldId, key, visibilityGroup);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Check if the field exists in this collection.
     *
     * @param fieldId to test
     *
     * @return {@code true} if present
     */
    public boolean contains(@IdRes final int fieldId) {
        return mAllFields.get(fieldId) != null;
    }

    /**
     * Return the Field associated with the passed layout ID.
     * <p>
     * Use {@link #contains(int)} for to check if needed.
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    public Field getField(@IdRes final int fieldId) {
        Field field = mAllFields.get(fieldId);
        if (field != null) {
            return field;
        }

        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(fieldId));
    }

    /**
     * Convenience function: For an AutoCompleteTextView, set the adapter.
     *
     * @param fieldId Layout id of View
     * @param adapter Adapter to use
     */
    public void setAdapter(@IdRes final int fieldId,
                           @NonNull final ArrayAdapter<String> adapter) {
        Field field = getField(fieldId);
        View view = field.getView();
        if (view instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) view).setAdapter(adapter);
        }
    }

    /**
     * Load fields from the passed bundle.
     *
     * @param rawData       with data to load.
     * @param withOverwrite if {@code true}, all fields are copied.
     *                      If {@code false}, only non-existent fields are copied.
     */
    public void setAllFrom(@NonNull final Bundle rawData,
                           final boolean withOverwrite) {
        if (withOverwrite) {
            for (Field field : mAllFields.values()) {
                field.setValueFrom(rawData);
            }
        } else {
            for (Field field : mAllFields.values()) {
                if (!field.mKey.isEmpty() && rawData.containsKey(field.mKey)) {
                    String value = rawData.getString(field.mKey);
                    if (value != null) {
                        field.setValue(value);
                    }
                }
            }
        }
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     *
     * @param dataManager DataManager to load Field objects from.
     */
    public void setAllFrom(@NonNull final DataManager dataManager) {
        for (Field field : mAllFields.values()) {
            field.setValueFrom(dataManager);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void putAllInto(@NonNull final DataManager dataManager) {
        for (Field field : mAllFields.values()) {
            if (!field.mKey.isEmpty()) {
                field.putValueInto(dataManager);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     */
    public void resetVisibility(final boolean hideIfEmpty) {
        for (Field field : mAllFields.values()) {
            field.resetVisibility(hideIfEmpty);
        }
    }

    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true).
     */
    public interface AfterFieldChangeListener<T> {

        void afterFieldChange(@NonNull Field<T> field,
                              @Nullable T newValue);
    }

    /**
     * Interface for view-specific accessors. One of these must be implemented for
     * each view type that is supported.
     */
    public interface FieldDataAccessor<T> {

        void setFormatter(@NonNull FieldFormatter formatter);

        @NonNull
        String format(@NonNull Field<T> field,
                      @Nullable T source);

        @NonNull
        String extract(@NonNull Field<T> field,
                       @NonNull String source);

        /**
         * Get the value from the view associated with the Field and return it as an Object.
         *
         * @param field Field
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        T getValue(@NonNull Field<T> field);

        /**
         * Use the passed String to set the Field.
         *
         * @param source String value to set.
         * @param target Field to set the value on
         */
        void setValue(@NonNull T source,
                      @NonNull Field<T> target);

        /**
         * Fetch the value from the passed Bundle, and set the Field.
         *
         * @param source Collection to load data from.
         * @param target Field to set the value on
         */
        void setValue(@NonNull Bundle source,
                      @NonNull Field<T> target);

        /**
         * Fetch the value from the passed DataManager, and set the Field.
         *
         * @param source Collection to load data from.
         * @param target Field to set the value on
         */
        void setValue(@NonNull DataManager source,
                      @NonNull Field<T> target);

        /**
         * Get the value from the view associated with the Field
         * and store a <strong>native typed value</strong> in the passed collection.
         *
         * @param source Field
         * @param target Collection to save value into.
         */
        void putValueInto(@NonNull Field<T> source,
                          @NonNull DataManager target);
    }

    /**
     * Interface definition for Field formatter.
     */
    public interface FieldFormatter<T> {

        /**
         * Format a string for applying to a View.
         * If the source is {@code null}, implementations should return "" (and log an error)
         *
         * @param source Input value
         *
         * @return The formatted value.
         */
        @NonNull
        String format(@NonNull Field<T> field,
                      @Nullable T source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}.
         * <p>
         * Extract a formatted {@code String} from the displayed version.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        String extract(@NonNull Field<T> field,
                       @NonNull String source);
    }

    /**
     * fronts an Activity/Fragment context.
     */
    private interface FieldsContext {

        /** DEBUG only. */
        @Nullable
        Object dbgGetOwnerClass();

        @Nullable
        View findViewById(@IdRes int id);
    }

    /**
     * TextWatcher for EditText fields. Sets the Field value after each EditText change.
     */
    private static class EditTextWatcher
            implements TextWatcher {

        @NonNull
        private final Field<String> field;

        EditTextWatcher(@NonNull final Field<String> field) {
            this.field = field;
        }

        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {
        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {
        }

        @Override
        public void afterTextChanged(@NonNull final Editable s) {
            // extract to native value
            String value = field.extract(s.toString().trim());

            if (BuildConfig.DEBUG) {
                Logger.debug(this, "afterTextChanged",
                             "s=`" + s.toString() + '`',
                             "extract=`" + value + '`'
                            );
            }

            // Set the field with the new data.
            // This will also redisplay the data, re-formatted as needed.
            field.setValue(value);
        }
    }


    /** Base implementation. */
    private abstract static class BaseDataAccessor<T>
            implements FieldDataAccessor<T> {

        @Nullable
        private FieldFormatter<T> mFormatter;


        public void setFormatter(@NonNull final FieldFormatter formatter) {
            //noinspection unchecked
            mFormatter = formatter;
        }

        /**
         * Wrapper around {@link FieldFormatter#format}.
         *
         * @param field  whose FieldFormatter will be used
         * @param source Input value
         *
         * @return The formatted value
         */
        @NonNull
        public String format(@NonNull final Field<T> field,
                             @Nullable final T source) {
            if (mFormatter != null) {
                return mFormatter.format(field, source);
            } else if (source != null) {
                return source.toString();
            } else {
                return "";
            }
        }

        /**
         * Wrapper around {@link FieldFormatter#extract}.
         *
         * @param field  whose FieldFormatter will be used
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        public String extract(@NonNull final Field<T> field,
                              @NonNull final String source) {
            if (mFormatter != null) {
                return mFormatter.extract(field, source);
            } else {
                return source;
            }
        }
    }

    /**
     * Implementation that stores and retrieves data from a local string variable
     * for fields without a layout.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class StringDataAccessor
            extends BaseDataAccessor<String> {

        @NonNull
        private String mLocalValue = "";

        @Override
        public void putValueInto(@NonNull final Field<String> source,
                                 @NonNull final DataManager target) {
            target.putString(source.mKey, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field<String> field) {
            return mLocalValue.trim();
        }

        @Override
        public void setValue(@NonNull final String source,
                             @NonNull final Field<String> target) {
            mLocalValue = source;
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey, ""), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey), target);
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     * <p>
     * The actual value is simply stored in a local variable. No attempt to extract is done.
     * <p>
     * Uses {@link FieldFormatter#format} only.
     */
    private static class TextViewAccessor
            extends BaseDataAccessor<String> {

        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        @Override
        public void putValueInto(@NonNull final Field<String> source,
                                 @NonNull final DataManager target) {
            target.putString(source.mKey, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field<String> field) {
            return mRawValue;
        }

        @Override
        public void setValue(@NonNull final String source,
                             @NonNull final Field<String> target) {
            mRawValue = source.trim();
            TextView view = target.getView();
            if (mFormatHtml) {
                view.setText(Html.fromHtml(format(target, mRawValue)));
                view.setFocusable(true);
                view.setTextIsSelectable(true);
                view.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            } else {
                view.setText(format(target, mRawValue));
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey, ""), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey), target);
        }

        /**
         * Set the TextViewAccessor to support HTML.
         *
         * @param showHtml if {@code true} this view will display HTML
         */
        @SuppressWarnings("WeakerAccess")
        public void setShowHtml(final boolean showHtml) {
            mFormatHtml = showHtml;
        }
    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     */
    private static class EditTextAccessor
            extends BaseDataAccessor<String> {

        @NonNull
        private final TextWatcher mTextWatcher;

        EditTextAccessor(@NonNull final EditText view,
                         @NonNull final Field<String> field) {
            mTextWatcher = new EditTextWatcher(field);
            view.addTextChangedListener(mTextWatcher);
        }

        @Override
        public void putValueInto(@NonNull final Field<String> source,
                                 @NonNull final DataManager target) {
            target.putString(source.mKey, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field<String> field) {
            EditText view = field.getView();
            return extract(field, view.getText().toString().trim());
        }

        @Override
        public void setValue(@NonNull final String source,
                             @NonNull final Field<String> target) {
            EditText view = target.getView();
            // 2018-12-11: There was recursion due to the setText call.
            // So now disabling the TextWatcher while doing the latter.
            // We don't want another thread re-enabling the listener before we're done
            synchronized (mTextWatcher) {
                view.removeTextChangedListener(mTextWatcher);

                String newVal = format(target, source);
                // do not use extract, we compare formatted/formatted value
                String oldVal = view.getText().toString().trim();
                if (!newVal.equals(oldVal)) {
                    if (view instanceof AutoCompleteTextView) {
                        // prevent auto-completion to kick in / stop the dropdown from opening.
                        // this happened if the field had the focus when we'd be populating it.
                        ((AutoCompleteTextView) view).setText(newVal, false);
                    } else {
                        view.setText(newVal);
                    }
                }
                view.addTextChangedListener(mTextWatcher);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey, ""), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey), target);
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     */
    private static class SpinnerAccessor
            extends BaseDataAccessor<String> {

        @Override
        public void putValueInto(@NonNull final Field<String> source,
                                 @NonNull final DataManager target) {
            target.putString(source.mKey, getValue(source));
        }

        @Override
        @NonNull
        public String getValue(@NonNull final Field<String> field) {
            Spinner spinner = field.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return extract(field, selItem.toString().trim());
            } else {
                return "";
            }
        }

        @Override
        public void setValue(@Nullable final String source,
                             @NonNull final Field<String> target) {
            Spinner spinner = target.getView();
            String value = format(target, source);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(value)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey, ""), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey), target);
        }
    }

    /**
     * Checkable accessor.
     *
     * When set to {@code false} we make it {@code View.GONE} as well.
     *
     * <ul>{@link Checkable} covers more then just a Checkbox:
     * <li>CheckBox, RadioButton, Switch</li>
     * <li>ToggleButton extend CompoundButton</li>
     * <li>CheckedTextView extends TextView</li>
     * </ul>
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class CheckableAccessor
            extends BaseDataAccessor<Boolean> {

        @Override
        public void putValueInto(@NonNull final Field<Boolean> source,
                                 @NonNull final DataManager target) {
            target.putBoolean(source.mKey, getValue(source));
        }

        @NonNull
        @Override
        public Boolean getValue(@NonNull final Field<Boolean> field) {
            Checkable cb = field.getView();
            return cb.isChecked();
        }

        @Override
        public void setValue(@Nullable final Boolean source,
                             @NonNull final Field<Boolean> target) {
            Checkable cb = target.getView();
            if (source != null) {
                ((View) cb).setVisibility(source ? View.VISIBLE : View.GONE);
                cb.setChecked(source);
            } else {
                cb.setChecked(false);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<Boolean> target) {
            setValue(source.getBoolean(target.mKey), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<Boolean> target) {
            setValue(source.getBoolean(target.mKey), target);
        }
    }

    /**
     * RatingBar accessor.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class RatingBarAccessor
            extends BaseDataAccessor<Float> {

        @Override
        public void putValueInto(@NonNull final Field<Float> source,
                                 @NonNull final DataManager target) {
            target.putFloat(source.mKey, getValue(source));
        }

        @NonNull
        @Override
        public Float getValue(@NonNull final Field<Float> field) {
            RatingBar bar = field.getView();
            return bar.getRating();
        }

        @Override
        public void setValue(@Nullable final Float source,
                             @NonNull final Field<Float> target) {
            RatingBar bar = target.getView();
            if (source != null) {
                bar.setRating(source);
            } else {
                bar.setRating(0.0f);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<Float> target) {
            setValue(source.getFloat(target.mKey), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<Float> target) {
            setValue(source.getFloat(target.mKey), target);
        }
    }

    /**
     * ImageView accessor. Uses the UUID to load the image into the view.
     * Sets a tag {@link R.id#TAG_UUID} on the view with the UUID.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class ImageViewAccessor
            extends BaseDataAccessor<String> {

        private int mMaxWidth;
        private int mMaxHeight;

        /**
         * Constructor.
         *
         * @param scale to apply
         */
        ImageViewAccessor(final int scale) {
            setScale(scale);
        }

        public void setScale(final int scale) {
            int maxSize = ImageUtils.getMaxImageSize(scale);
            mMaxHeight = maxSize;
            mMaxWidth = maxSize;
        }

        @Override
        public void putValueInto(@NonNull final Field<String> source,
                                 @NonNull final DataManager target) {
            // not applicable
        }

        /**
         * Not really used, but returning the uuid makes sense.
         *
         * @param field to get the value of
         *
         * @return the UUID
         */
        @NonNull
        @Override
        public String getValue(@NonNull final Field<String> field) {
            return (String) field.getView().getTag(R.id.TAG_UUID);
        }

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param uuid   the book UUID
         * @param target Field to set the value on
         */
        @Override
        public void setValue(@Nullable final String uuid,
                             @NonNull final Field<String> target) {
            ImageView imageView = target.getView();

            if (uuid != null) {
                File imageFile;
                if (uuid.isEmpty()) {
                    imageFile = StorageUtils.getTempCoverFile();
                } else {
                    imageView.setTag(R.id.TAG_UUID, uuid);
                    imageFile = StorageUtils.getCoverFileForUuid(uuid);
                }
                ImageUtils.setImageView(imageView, imageFile, mMaxWidth, mMaxHeight, true);
            } else {
                imageView.setImageResource(R.drawable.ic_image);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey, ""), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field<String> target) {
            setValue(source.getString(target.mKey), target);
        }
    }


    /**
     * Formatter/Extractor for date fields.
     * <p>
     * Can be shared among multiple fields.
     * Uses the context/locale from the field itself.
     */
    public static class DateFieldFormatter
            implements FieldFormatter<String> {

        /**
         * Display as a human-friendly date, local timezone.
         */
        @Override
        @NonNull
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return DateUtils.toPrettyDate(field.getLocale(), source);
        }

        /**
         * Extract as an SQL date, UTC timezone.
         */
        @Override
        @NonNull
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            Date d = DateUtils.parseDate(source);
            if (d != null) {
                return DateUtils.utcSqlDate(d);
            }
            return source;
        }
    }

    /**
     * Formatter for price fields.
     * Note this is {@code String} typed due to backwards compatibility.
     * <p>
     * e.g. pre-decimal UK "Shilling/Pence" is in effect a string.
     *
     * <p>
     * Uses the context/locale from the field itself.
     * <p>
     * Does not support {@link FieldFormatter#extract}
     */
    public static class PriceFormatter
            implements FieldFormatter<String> {

        @Nullable
        private String mCurrencyCode;

        /**
         * Set the currency code.
         *
         * @param currencyCode to use (if any)
         */
        public void setCurrencyCode(@Nullable final String currencyCode) {
            mCurrencyCode = currencyCode;
        }

        /**
         * Display with the currency symbol.
         */
        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }
            if (mCurrencyCode == null || mCurrencyCode.isEmpty()) {
                return source;
            }

            // quick return for the pre-decimal UK "Shilling/Pence" prices.
            // ISFDB provides those types of prices. Bit hackish...
            if (source.contains("/")) {
                return source;
            }

            try {
                return jdkFormat(field, source);

            } catch (@NonNull final IllegalArgumentException e) {
                Logger.error(this, e, "currencyCode=`" + mCurrencyCode + "`,"
                                      + " source=`" + source + '`');
                return mCurrencyCode + ' ' + source;
            }
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            try {
                // parse with Locales, and return non-Locale representation.
                return String.valueOf(ParseUtils.parseFloat(source));
            } catch (@NonNull final NumberFormatException e) {
                return source;
            }
        }

        private String jdkFormat(@NonNull final Field<String> field,
                                 @NonNull final Object source) {
            // all Locales taken into account for parsing
            Float price = ParseUtils.toFloat(source);
            // but the current Locale is used for formatting
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(field.getLocale());

            Currency currency = Currency.getInstance(mCurrencyCode);
            currencyFormatter.setCurrency(currency);
            // the result is rather dire... most currency symbols are shown as 3-char codes
            // e.g. 'EUR','US$',...
            return currencyFormatter.format(price);
        }

        // The ICU NumberFormatter is only available from ICU level 60, but Android lags behind:
        // https://developer.android.com/guide/topics/resources/internationalization#versioning-nougat
        // So you need Android 9 (API level 28) and even then, the NumberFormatter
        // is not available in android.icu.* so you still would need to bundle the full ICU lib
        // For now, this is to much overkill.
//        @TargetApi(28)
//        private String icuFormat(@NonNull final Field<String> field,
//                                @NonNull final Object source) {
//            https://github.com/unicode-org/icu/blob/master/icu4j/main/classes/core/src/
//            com/ibm/icu/number/NumberFormatter.java
//            and UnitWidth.NARROW
//            return "";
//        }
    }

    /**
     * Formatter for 'page' fields. If the value is numerical, output "x pages"
     * Otherwise outputs the original source value.
     * <p>
     * Uses the context from the Field view.
     */
    public static class PagesFormatter
            implements FieldFormatter<String> {

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source != null && !source.isEmpty() && !"0".equals(source)) {
                try {
                    int pages = Integer.parseInt(source);
                    Context context = field.getView().getContext();
                    LocaleUtils.insanityCheck(context);
                    return context.getString(R.string.lbl_x_pages, pages);
                } catch (@NonNull final NumberFormatException ignore) {
                    // don't log, both formats are valid.
                }
                // stored pages was alphanumeric.
                return source;
            }
            return "";
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return source;
        }
    }

    /**
     * Formatter for 'format' fields. Attempts to standardize the format descriptor
     * before displaying. This is not an internal code, but purely text based.
     * <p>
     * Uses the context from the Field view.
     */
    public static class FormatFormatter
            implements FieldFormatter<String> {

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source != null && !source.isEmpty()) {
                Context context = field.getView().getContext();
                LocaleUtils.insanityCheck(context);
                Locale locale = LocaleUtils.getLocale(context);
                return Format.map(context, locale, source);
            }
            return "";
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return source;
        }
    }

    /**
     * Formatter for language fields.
     * <p>
     * Uses the locale from the Field.
     */
    public static class LanguageFormatter
            implements FieldFormatter<String> {

        @NonNull
        private final Locale mLocale;

        public LanguageFormatter(@NonNull final Locale locale) {
            this.mLocale = locale;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return LanguageUtils.getDisplayName(field.getLocale(), source);
        }

        /**
         * Transform a localised language name to its ISO equivalent.
         *
         * @param source The value to be back-translated
         *
         * @return the ISO3 code for the language
         */
        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return LanguageUtils.getIso3fromDisplayName(source, mLocale);
        }
    }

    /**
     * Field Formatter for a bitmask based field.
     * Formats the checked items as a CSV String.
     * <p>
     * {@link #extract} returns "dummy";
     */
    public static class BitMaskFormatter
            implements FieldFormatter<String> {

        @NonNull
        private final Map<Integer, Integer> mMap;

        public BitMaskFormatter(@NonNull final Map<Integer, Integer> editions) {
            mMap = editions;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            int bitmask;
            try {
                bitmask = Integer.parseInt(source);
            } catch (@NonNull final NumberFormatException ignore) {
                return source;
            }
            Context context = field.getView().getContext();
            return TextUtils.join(", ", Csv.bitmaskToList(context, mMap, bitmask));
        }

        // theoretically we should support the extract method as this formatter is used on
        // 'edit' fragments. But the implementation only ever sets the text on the screen
        // and stores the actual value directly. (dialog/listener setup).
        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return "dummy";
        }
    }

    /** fronts an Activity context. */
    private static class ActivityContext
            implements FieldsContext {

        @NonNull
        private final WeakReference<Activity> mActivity;

        ActivityContext(@NonNull final Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        @Nullable
        public Object dbgGetOwnerClass() {
            return mActivity.get();
        }

        @Override
        @Nullable
        public View findViewById(@IdRes final int id) {
            if (mActivity.get() == null) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debugWithStackTrace(this, "findViewById",
                                               "Activity is NULL");
                }
                return null;
            }
            return mActivity.get().findViewById(id);
        }
    }

    /** fronts a Fragment context. */
    private static class FragmentContext
            implements FieldsContext {

        @NonNull
        private final WeakReference<Fragment> mFragment;

        FragmentContext(@NonNull final Fragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        @Nullable
        public Object dbgGetOwnerClass() {
            return mFragment.get();
        }

        @Override
        @Nullable
        public View findViewById(@IdRes final int id) {
            if (mFragment.get() == null) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debugWithStackTrace(this, "findViewById", "Fragment is NULL");
                }
                return null;
            }
            View view = mFragment.get().getView();
            if (view == null) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debugWithStackTrace(this, "findViewById", "Fragment View is NULL");
                }
                return null;
            }

            return view.findViewById(id);
        }
    }

    /**
     * Field definition contains all information and methods necessary to manage display and
     * extraction of data in a view.
     */
    public static class Field<T> {

        /** Field ID. */
        @IdRes
        public final int id;
        /** DEBUG - Owning collection. */
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final WeakReference<Fields> mFields;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views. */
        @NonNull
        private final String group;
        /**
         * Key (can be blank) used to access a {@link DataManager} or {@code Bundle}.
         * <p>
         * - key is set, and doNoFetch==false:
         * ==> fetched from the {@link DataManager} (or Bundle), and populated on the screen
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - key is set, and doNoFetch==true:
         * ==> fetched from the {@link DataManager} (or Bundle), but populating
         * the screen must be done manually.
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - key is not set: field is defined, but data handling is fully manual.
         */
        @NonNull
        private final String mKey;

        /**
         * Accessor to use (automatically defined).
         * Encapsulates the formatter.
         */
        @NonNull
        private final FieldDataAccessor mFieldDataAccessor;

        /** indicates that "0" should be seen as "". Used for boolean/int type fields. */
        private boolean mZeroIsEmpty;

        /** Is the field in use; i.e. is it enabled in the user-preferences. **/
        private boolean mIsUsed;

        /**
         * Option indicating that even though field has a key, it should NOT be fetched
         * from a {@link DataManager} (or Bundle).
         * This is usually done for synthetic fields needed when putting the data
         * into the {@link DataManager} (or Bundle).
         */
        private boolean mDoNoFetch;

        @Nullable
        private int[] mRelatedFields;

        /**
         * Constructor.
         *
         * @param fields          Parent collection
         * @param fieldId         Layout ID
         * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
         *                        Set to "" to suppress all access.
         * @param visibilityGroup Visibility group. Can be blank.
         */
        @VisibleForTesting
        public Field(@NonNull final Fields fields,
                     @IdRes final int fieldId,
                     @NonNull final String key,
                     @NonNull final String visibilityGroup) {

            mFields = new WeakReference<>(fields);
            id = fieldId;
            mKey = key;
            group = visibilityGroup;

            // Lookup the view. {@link Fields} will have the context set to the activity/fragment.
            final View view = getView();

            // check if the user actually uses this group.
            mIsUsed = App.isUsed(group);
            if (!mIsUsed) {
                view.setVisibility(View.GONE);
            }

            // Set the appropriate accessor
            if ((view instanceof MaterialButton) && ((MaterialButton) view).isCheckable()) {
                // this was nasty... a MaterialButton implements Checkable,
                // but you have to double check (pardon the pun) whether it IS checkable.
                mFieldDataAccessor = new CheckableAccessor();
                addTouchSignalsDirty(view);

            } else if (!((view instanceof MaterialButton)) && (view instanceof Checkable)) {
                // the opposite, do not accept the MaterialButton here.
                mFieldDataAccessor = new CheckableAccessor();
                addTouchSignalsDirty(view);

            } else if (view instanceof EditText) {
                //noinspection unchecked
                mFieldDataAccessor = new EditTextAccessor((EditText) view, (Field<String>) this);

            } else if (view instanceof Button) {
                // a Button *is* a TextView, but this is cleaner
                mFieldDataAccessor = new TextViewAccessor();

            } else if (view instanceof TextView) {
                mFieldDataAccessor = new TextViewAccessor();

            } else if (view instanceof ImageView) {
                mFieldDataAccessor = new ImageViewAccessor(ImageUtils.SCALE_MEDIUM);

            } else if (view instanceof RatingBar) {
                mFieldDataAccessor = new RatingBarAccessor();
                addTouchSignalsDirty(view);

            } else if (view instanceof Spinner) {
                mFieldDataAccessor = new SpinnerAccessor();

            } else {
                // field has no layout, store in a dummy String.
                mFieldDataAccessor = new StringDataAccessor();
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debug(this, "Field",
                                 "Using StringDataAccessor",
                                 "key=" + key);
                }
            }
        }

        /**
         * Factory method to get a new auto-type-detection field.
         *
         * @param fields          Parent collection
         * @param fieldId         Layout ID
         * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
         *                        Set to "" to suppress all access.
         * @param visibilityGroup Visibility group. Can be blank.
         *
         * @return new Field
         */
        static <T> Field<T> newField(@NonNull final Fields fields,
                                     @IdRes final int fieldId,
                                     @NonNull final String key,
                                     @NonNull final String visibilityGroup) {
            return new Field<>(fields, fieldId, key, visibilityGroup);
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "id=" + id
                   + ", group='" + group + '\''
                   + ", mKey='" + mKey + '\''
                   + ", mZeroIsEmpty=" + mZeroIsEmpty
                   + ", mIsUsed=" + mIsUsed
                   + ", mDoNoFetch=" + mDoNoFetch
                   + ", mFieldDataAccessor=" + mFieldDataAccessor
                   + ", mRelatedFields=" + Arrays.toString(mRelatedFields)
                   + '}';
        }

        /**
         * @param formatter to use
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setFormatter(@NonNull final FieldFormatter formatter) {
            mFieldDataAccessor.setFormatter(formatter);
            return this;
        }

        /**
         * If a text field, set the TextViewAccessor to support HTML.
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setShowHtml(final boolean showHtml) {
            if (mFieldDataAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mFieldDataAccessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * Set scaling (if the field type supports it).
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setScale(final int scale) {
            if (mFieldDataAccessor instanceof ImageViewAccessor) {
                ((ImageViewAccessor) mFieldDataAccessor).setScale(scale);
            }
            return this;
        }

        /**
         * @param doNoFetch {@code true} to stop the field being fetched from the database
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setDoNotFetch(final boolean doNoFetch) {
            mDoNoFetch = doNoFetch;
            return this;
        }

        /**
         * To be called if a "0" content should be treated as an 'empty field'.
         * Used for (not) displaying boolean and integer fields.
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setZeroIsEmpty(final boolean zeroIsEmpty) {
            mZeroIsEmpty = zeroIsEmpty;
            return this;
        }

        /**
         * set the field ids which should follow visibility with this Field.
         *
         * @param relatedFields labels etc
         */
        public Field<T> setRelatedFieldIds(@NonNull @IdRes final int... relatedFields) {
            mRelatedFields = relatedFields;
            return this;
        }

        /**
         * Is the field in use; i.e. is it enabled in the user-preferences.
         *
         * @return {@code true} if the field *can* be visible
         */
        public boolean isUsed() {
            return mIsUsed;
        }

        /**
         * Set the visibility for the field.
         *
         * @param hideIfEmpty hide the field if it's empty
         */
        private void resetVisibility(final boolean hideIfEmpty) {

            mIsUsed = App.isUsed(group);
            int visibility = mIsUsed ? View.VISIBLE : View.GONE;
            View fieldView = getView();

            if (mIsUsed && hideIfEmpty) {
                if (fieldView instanceof Checkable) {
                    // hide any unchecked Checkable.
                    visibility = ((Checkable) fieldView).isChecked() ? View.VISIBLE : View.GONE;

                } else if (!(fieldView instanceof ImageView)) {
                    // don't act on ImageView, but all other fields can be tested on being empty
                    visibility = !isEmpty() ? View.VISIBLE : View.GONE;
                }
            }

            fieldView.setVisibility(visibility);

            if (mRelatedFields != null) {
                FieldsContext context = mFields.get().getFieldContext();
                for (int fieldId : mRelatedFields) {
                    View field = context.findViewById(fieldId);
                    if (field != null) {
                        field.setVisibility(visibility);
                    }
                }
            }
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        @SuppressLint("ClickableViewAccessibility")
        private void addTouchSignalsDirty(@NonNull final View view) {
            // Touching this is considered a change
            //TODO: We need to introduce a better way to handle this.
            view.setOnTouchListener((v, event) -> {
                if (MotionEvent.ACTION_UP == event.getAction()) {
                    mFields.get().afterFieldChange(this, null);
                }
                return false;
            });
        }

        /**
         * Get the view associated with this Field.
         *
         * @return Resulting View
         *
         * @see #debugNullView
         */
        @NonNull
        public <V extends View> V getView() {
            Fields fields = mFields.get();
            if (fields == null) {
                throw new NullPointerException("mFields was NULL");
            }
            View view = fields.getFieldContext().findViewById(id);
            // see comment on debugNullView
            if (view == null) {
                throw new NullPointerException("View object not found\n" + debugNullView());
            }
            //noinspection unchecked
            return (V) view;
        }

        /**
         * 2018-10: the below description is from the original code.
         * This issue has not been seen running this forked code on Android 5+.
         * <p>
         * This should NEVER happen, but it does. See Issue #505.
         * So we need more info about why & when.
         * <p>
         * Allow for the (apparent) possibility that the view may have been removed due
         * to a tab change or similar. See Issue #505.
         * <p>
         * Every field MUST have an associated View object, but sometimes it is not found.
         * When not found, the app crashes.
         * <p>
         * The following code is to help diagnose these cases, not avoid them.
         * This does NOT entirely fix the problem, it gathers debug info.
         * but we have implemented one work-around
         * <p>
         * Work-around #1:
         * <p>
         * It seems that sometimes the afterTextChanged() event fires after the text field
         * is removed from the screen. In this case, there is no need to synchronize the values
         * since the view is gone.
         */
        private String debugNullView() {
            String msg = "NULL View: key=" + mKey + ", id=" + id + ", group=" + group;
            Fields fields = mFields.get();
            if (fields == null) {
                msg += "|mFields=NULL";
            } else {
                msg += "|mFields=valid";
                FieldsContext fieldContext = fields.getFieldContext();
                msg += "|Context=" + fieldContext.getClass().getCanonicalName();
                Object ownerClass = fieldContext.dbgGetOwnerClass();
                msg += "|Owner=";
                if (ownerClass == null) {
                    msg += "NULL";
                } else {
                    msg += ownerClass.getClass().getCanonicalName() + " (" + ownerClass + ')';
                }
            }
            return msg;
        }

        /**
         * syntax sugar.
         *
         * @return the Locale of the fields' context.
         */
        @NonNull
        public Locale getLocale() {
            Locale locale;
            if (Build.VERSION.SDK_INT >= 24) {
                locale = getView().getResources().getConfiguration().getLocales().get(0);
            } else {
                locale = getView().getResources().getConfiguration().locale;
            }
            return locale != null ? locale : Locale.ENGLISH;
        }

        @NonNull
        public FieldDataAccessor<T> getFieldDataAccessor() {
            return mFieldDataAccessor;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public Object getValue() {
            return mFieldDataAccessor.getValue(this);
        }

        /**
         * Set the value of this Field.
         *
         * @param source New value
         */
        public void setValue(@NonNull final Object source) {
            mFieldDataAccessor.setValue(source, this);
            mFields.get().afterFieldChange(this, source);
        }

        /**
         * Convenience method to check if the value is considered empty.
         *
         * @return {@code true} if this field is empty.
         */
        public boolean isEmpty() {
            String value = mFieldDataAccessor.getValue(this).toString();
            if (value.isEmpty() || (mZeroIsEmpty && "0".equals(value))) {
                return true;
            }

            return "false".equalsIgnoreCase(value);
        }

        /**
         * Get the current value of this field and put it into the DataManager.
         */
        void putValueInto(@NonNull final DataManager target) {
            mFieldDataAccessor.putValueInto(this, target);
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        void setValueFrom(@NonNull final Bundle source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source, this);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(@NonNull final DataManager source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source, this);
            }
        }

        /**
         * Wrapper to {@link FieldDataAccessor#format}.
         *
         * @param source String to format
         *
         * @return The formatted value. Or "" if the source is {@code null}.
         */
        @NonNull
        public String format(@Nullable final T source) {
            if (source == null) {
                return "";
            }
            return mFieldDataAccessor.format(this, source);
        }

        /**
         * Wrapper to {@link FieldDataAccessor#extract(Field, String)}.
         *
         * @param source String to extract
         *
         * @return The extracted value. Or "" if the source is {@code null}.
         */
        @NonNull
        String extract(@Nullable final String source) {
            if (source == null) {
                return "";
            }
            return mFieldDataAccessor.extract(this, source);
        }
    }
}
