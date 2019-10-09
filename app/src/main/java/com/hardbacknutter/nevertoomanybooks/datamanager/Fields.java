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
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.LinkMovementMethod;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Format;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <ul>Features provides are:
 * <li> handling of visibility via preferences</li>
 * <li> handling of 'mGroup' visibility via the 'mGroup' property of a field.</li>
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

    private void afterFieldChange(@NonNull final Field<?> field,
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
    public Field<String> addString(@IdRes final int fieldId,
                                   @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<String> field = new Field<>(this, fieldId, key, key);
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
    public Field<String> addString(@IdRes final int fieldId,
                                   @NonNull final String key,
                                   @NonNull final String visibilityGroup) {
        Field<String> field = new Field<>(this, fieldId, key, visibilityGroup);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Boolean field to this collection.
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
        Field<Boolean> field = new Field<>(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Long field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Field<Long> addLong(@IdRes final int fieldId,
                               @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Long> field = new Field<>(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Float field to this collection.
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
        Field<Float> field = new Field<>(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Monetary field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field<Double> addMonetary(@IdRes final int fieldId,
                                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Double> field = new Field<>(this, fieldId, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Monetary field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field<Double> addMonetary(@IdRes final int fieldId,
                                     @NonNull final String key,
                                     @NonNull final String visibilityGroup) {
        Field<Double> field = new Field<>(this, fieldId, key, visibilityGroup);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID.
     *
     * @param <T> type of Field value.
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    public <T> Field<T> getField(@IdRes final int fieldId) {
        Field<T> field = (Field<T>) mAllFields.get(fieldId);
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
                    Object value = rawData.get(field.mKey);
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
     *
     * @param <T> type of Field value.
     */
    public interface AfterFieldChangeListener<T> {

        void afterFieldChange(@NonNull Field<T> field,
                              @Nullable T newValue);
    }

    /**
     * Interface for view-specific accessors. One of these must be implemented for
     * each view type that is supported.
     *
     * @param <T> type of Field value.
     */
    public interface FieldDataAccessor<T> {

        /**
         * Set the optional formatter.
         *
         * @param formatter to use
         */
        void setFormatter(@NonNull FieldFormatter formatter);

        /**
         * Call the wrapped formatter. See {@link FieldFormatter}.
         *
         * @param source Input value
         *
         * @return The formatted value.
         */
        @NonNull
        String format(@Nullable T source);

        /**
         * Call the wrapped formatter. See {@link FieldFormatter}.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull String source);

        /**
         * Get the value from the view associated with the Field and return it as an Object.
         *
         * <strong>Note:</strong> an implementation should always return a value.
         * This would/should usually be the default the Widget.
         * e.g. a text based widget should return "" even if the value was never set.
         *
         * @return the value
         */
        @NonNull
        T getValue();

        /**
         * Use the passed value to set the Field.
         * <p>
         * If {@code null} is passed in, the implementation should set the widget to
         * its native default value.
         *
         * @param value to set.
         */
        void setValue(@Nullable T value);

        /**
         * Fetch the value from the passed Bundle, and set the Field.
         *
         * @param source Collection to load data from.
         */
        void setValue(@NonNull Bundle source);

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
        void getValueAndPut(@NonNull DataManager target);

        /**
         * Check if this field is considered to be 'empty'.
         * The encapsulated type decides what 'empty' means.
         *
         * @return {@code true} if empty.
         */
        boolean isEmpty();
    }

    /**
     * Interface definition for Field formatter.
     *
     * @param <T> type of Field value.
     */
    public interface FieldFormatter<T> {

        /**
         * Format a string for applying to a View.
         * If the source is {@code null}, implementations should return "" (and log an error)
         *
         * @param field  The field
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
         * @param field  The field
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull Field<T> field,
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
     *
     * @param <T> type of Field value.
     */
    private static class EditTextWatcher<T>
            implements TextWatcher {

        @NonNull
        private final Field<T> field;

        EditTextWatcher(@NonNull final Field<T> field) {
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
            T value = field.extract(s.toString().trim());

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.FIELD_FORMATTER) {
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


    /**
     * Base implementation.
     *
     * @param <T> type of Field value.
     */
    private abstract static class BaseDataAccessor<T>
            implements FieldDataAccessor<T> {

        @NonNull
        final Field<T> mField;
        @Nullable
        private FieldFormatter<T> mFormatter;

        BaseDataAccessor(@NonNull final Field<T> field) {
            mField = field;
        }

        public void setFormatter(@NonNull final FieldFormatter formatter) {
            //noinspection unchecked
            mFormatter = formatter;
        }

        /**
         * Wrapper around {@link FieldFormatter#format}.
         *
         * @param source Input value
         *
         * @return The formatted value
         */
        @NonNull
        public String format(@Nullable final T source) {
            if (mFormatter != null) {
                try {
                    return mFormatter.format(mField, source);

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link BookCursor} class docs.
                    Logger.error(this, e, source);
                }
            }

            // if we don't have a formatter, or if we had a ClassCastException:
            if (source != null) {
                return source.toString();
            } else {
                return "";
            }
        }

        /**
         * Wrapper around {@link FieldFormatter#extract}.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        public T extract(@NonNull final String source) {
            if (mFormatter != null) {
                return mFormatter.extract(mField, source);
            } else {
                // all non-String field should have formatters. The remaining should be String.
                // If they are not, then we get an Exception as the developer made a boo-boo.
                return (T) source;
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

        StringDataAccessor(@NonNull final Field<String> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putString(mField.mKey, getValue());
        }

        @Override
        public boolean isEmpty() {
            return mLocalValue.isEmpty();
        }

        @NonNull
        @Override
        public String getValue() {
            return mLocalValue.trim();
        }

        @Override
        public void setValue(@Nullable final String value) {
            mLocalValue = value != null ? value : "";
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.mKey, ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.mKey));
        }
    }

    /**
     * Base implementation for TextView/EditText.
     *
     * @param <T> type of Field value.
     */
    private abstract static class TextFieldAccessor<T>
            extends BaseDataAccessor<T> {

        /**
         * Constructor.
         *
         * @param field to use
         */
        TextFieldAccessor(@NonNull final Field<T> field) {
            super(field);
        }

        @Override
        public boolean isEmpty() {
            String value = getValue().toString();
            return value.isEmpty()
                   // Integer/Long
                   || "0".equals(value)
                   // Float/Double
                   || "0.0".equals(value)
                   // Boolean (theoretically this should not happen... but paranoia... ).
                   || "false".equals(value);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.put(mField.mKey, getValue());
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue((T) source.get(mField.mKey));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue((T) source.get(mField.mKey));
        }

    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     * <p>
     * The actual value is simply stored in a local variable. No attempt to extract is done.
     * <p>
     * Uses {@link FieldFormatter#format} only.
     *
     * @param <T> type of Field value.
     */
    private static class TextViewAccessor<T>
            extends TextFieldAccessor<T> {

        private boolean mFormatHtml;

        @Nullable
        private T mRawValue;

        /**
         * Constructor.
         *
         * @param field to use
         */
        TextViewAccessor(@NonNull final Field<T> field) {
            super(field);
        }

        @NonNull
        @Override
        public T getValue() {
            if (mRawValue != null) {
                return mRawValue;
            } else {
                // The only situation where mRawValue would be null is when T is a String.
                // If we violate that, the next line will cause an Exception
                return (T) "";
            }
        }

        @Override
        public void setValue(@Nullable final T value) {
            mRawValue = value;
            TextView view = mField.getView();
            if (mFormatHtml) {
                String body = format(mRawValue);

                view.setText(LinkifyUtils.fromHtml(body));
                view.setMovementMethod(LinkMovementMethod.getInstance());

                view.setFocusable(true);
                view.setTextIsSelectable(true);

            } else {
                view.setText(format(mRawValue));
            }
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
     *
     * @param <T> type of Field value.
     */
    private static class EditTextAccessor<T>
            extends TextFieldAccessor<T> {

        @NonNull
        private final TextWatcher mTextWatcher;

        /**
         * Constructor.
         *
         * @param field to use
         */
        EditTextAccessor(@NonNull final Field<T> field) {
            super(field);
            mTextWatcher = new EditTextWatcher<>(field);
            EditText view = field.getView();
            view.addTextChangedListener(mTextWatcher);
        }

        @NonNull
        @Override
        public T getValue() {
            EditText view = mField.getView();
            return extract(view.getText().toString().trim());
        }

        @Override
        public void setValue(@Nullable final T value) {
            EditText view = mField.getView();
            // 2018-12-11: There was recursion due to the setText call.
            // So now disabling the TextWatcher while doing the latter.
            // We don't want another thread re-enabling the listener before we're done
            synchronized (mTextWatcher) {
                view.removeTextChangedListener(mTextWatcher);

                String newVal = format(value);
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

        /**
         * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
         * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
         */
        void setInputIsDecimal() {

            DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
            DecimalFormatSymbols symbols = nf.getDecimalFormatSymbols();
            final String decimalSeparator = Character.toString(symbols.getDecimalSeparator());

            final EditText view = mField.getView();

            view.addTextChangedListener(new TextWatcher() {

                @Override
                public void beforeTextChanged(CharSequence s,
                                              int start,
                                              int count,
                                              int after) {

                }

                @Override
                public void onTextChanged(CharSequence s,
                                          int start,
                                          int before,
                                          int count) {

                }

                @Override
                public void afterTextChanged(@NonNull final Editable editable) {
                    // allow only one decimal separator
                    if (editable.toString().contains(decimalSeparator)) {
                        view.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
                    } else {
                        view.setKeyListener(DigitsKeyListener.getInstance("0123456789"
                                                                          + decimalSeparator));
                    }
                }
            });
        }
//    public static class DecimalDigitsInputFilter implements InputFilter {
//
//        Pattern mPattern;
//
//        public DecimalDigitsInputFilter(int digitsBeforeZero,
//                                        int digitsAfterZero) {
//            DecimalFormatSymbols d = new DecimalFormatSymbols(Locale.getDefault());
//            String s = "\\" + d.getDecimalSeparator();
//            mPattern = Pattern.compile(
//                    "[0-9]{0," + (digitsBeforeZero - 1) + "}+"
//                    + "((" + s + "[0-9]{0," + (digitsAfterZero - 1) + "})?)"
//                    + ""
//                    + "|(" + s + ")?");
//        }
//
//        @Override
//        public CharSequence filter(CharSequence source,
//                                   int start,
//                                   int end,
//                                   Spanned dest,
//                                   int dstart,
//                                   int dend) {
//
//            Matcher matcher = mPattern.matcher(dest);
//            if (!matcher.matches())
//                return "";
//            return null;
//        }
//    }

    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     */
    private static class SpinnerAccessor
            extends BaseDataAccessor<String> {

        SpinnerAccessor(@NonNull final Field<String> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putString(mField.mKey, getValue());
        }

        @Override
        public boolean isEmpty() {
            return getValue().isEmpty();
        }

        @Override
        @NonNull
        public String getValue() {
            Spinner spinner = mField.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return extract(selItem.toString().trim());
            } else {
                return "";
            }
        }

        @Override
        public void setValue(@Nullable final String value) {
            Spinner spinner = mField.getView();
            String formatted = format(value);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(formatted)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.mKey, ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.mKey));
        }
    }

    /**
     * Checkable accessor.
     * <p>
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

        CheckableAccessor(@NonNull final Field<Boolean> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putBoolean(mField.mKey, getValue());
        }

        @Override
        public boolean isEmpty() {
            return getValue();
        }

        @NonNull
        @Override
        public Boolean getValue() {
            Checkable cb = mField.getView();
            return cb.isChecked();
        }

        @Override
        public void setValue(@Nullable final Boolean value) {
            Checkable cb = mField.getView();
            if (value != null) {
                ((View) cb).setVisibility(value ? View.VISIBLE : View.GONE);
                cb.setChecked(value);
            } else {
                cb.setChecked(false);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getBoolean(mField.mKey));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getBoolean(mField.mKey));
        }
    }

    /**
     * RatingBar accessor.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class RatingBarAccessor
            extends BaseDataAccessor<Float> {

        RatingBarAccessor(@NonNull final Field<Float> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putFloat(mField.mKey, getValue());
        }

        @Override
        public boolean isEmpty() {
            return getValue().equals(0.0f);
        }

        @NonNull
        @Override
        public Float getValue() {
            RatingBar bar = mField.getView();
            return bar.getRating();
        }

        @Override
        public void setValue(@Nullable final Float value) {
            RatingBar bar = mField.getView();
            if (value != null) {
                bar.setRating(value);
            } else {
                bar.setRating(0.0f);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getFloat(mField.mKey));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getFloat(mField.mKey));
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
         * Sets the scale to the default {@link ImageUtils#SCALE_MEDIUM}.
         * Override with {@link #setScale(int)}.
         */
        @SuppressWarnings("SameParameterValue")
        ImageViewAccessor(@NonNull final Field<String> field) {
            super(field);
            setScale(ImageUtils.SCALE_MEDIUM);
        }

        public void setScale(final int scale) {
            int maxSize = ImageUtils.getMaxImageSize(scale);
            mMaxHeight = maxSize;
            mMaxWidth = maxSize;
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            // not applicable; do NOT put the uuid into the target!
        }

        @Override
        public boolean isEmpty() {
            // should really get the view, and check if it has a bitmap; but this will do for now.
            return getValue().isEmpty();
        }

        /**
         * Not really used, but returning the uuid makes sense.
         *
         * @return the UUID
         */
        @NonNull
        @Override
        public String getValue() {
            return (String) mField.getView().getTag(R.id.TAG_UUID);
        }

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param uuid the book UUID
         */
        @Override
        public void setValue(@Nullable final String uuid) {
            ImageView imageView = mField.getView();

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
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.mKey, ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.mKey));
        }
    }


    /**
     * Formatter/Extractor for date fields.
     * <p>
     * Can be shared among multiple fields.
     * Uses the Context/Locale from the field itself.
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

            return DateUtils.toPrettyDate(source);
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
     */
    public static class MonetaryFormatter
            implements FieldFormatter<Double> {

        /**
         * This is used for error logging only, and is normally always the app context
         * except in unit tests.
         */
        @NonNull
        private final Context mContext;

        /** Optional; if null we use the default Locale. */
        @Nullable
        private Locale mLocale;

        /** Optional. */
        @Nullable
        private String mCurrencyCode;

        /**
         * Constructor.
         */
        public MonetaryFormatter() {
            mContext = App.getAppContext();
        }

        /**
         * TEST Constructor; unit tests cannot use App.getAppContext().
         *
         * @param context to use
         */
        @VisibleForTesting
        MonetaryFormatter(@NonNull final Context context) {
            mContext = context;
        }

        /**
         * Set the Locale for the formatter/parser.
         *
         * @param locale to use (if any)
         */
        public MonetaryFormatter setLocale(@Nullable final Locale locale) {
            mLocale = locale;
            return this;
        }

        /**
         * Set the currency code.
         *
         * @param currencyCode to use (if any)
         */
        public MonetaryFormatter setCurrencyCode(@Nullable final String currencyCode) {
            mCurrencyCode = currencyCode;
            return this;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<Double> field,
                             @Nullable final Double source) {
            if (source == null) {
                return "";
            }
            if (mCurrencyCode == null || mCurrencyCode.isEmpty()) {
                return String.valueOf(source);
            }

            if (mLocale == null) {
                mLocale = Locale.getDefault();
            }

            try {
                // Always use the current Locale for formatting
                DecimalFormat nf = (DecimalFormat) DecimalFormat.getCurrencyInstance(mLocale);
                nf.setCurrency(Currency.getInstance(mCurrencyCode));
                // the result is rather dire... most currency symbols are shown as 3-char codes
                // e.g. 'EUR','US$',...
                return nf.format(source);

            } catch (@NonNull final IllegalArgumentException e) {
                Logger.error(mContext, this, e,
                             "currencyCode=`" + mCurrencyCode + "`,"
                             + " source=`" + source + '`');

                // fallback if getting a Currency instance fail.
                return mCurrencyCode + ' ' + String.format(mLocale, "%.2f", source);
            }
        }

        @NonNull
        @Override
        public Double extract(@NonNull final Field<Double> field,
                              @NonNull final String source) {
            return Double.parseDouble(source);
        }

        // The ICU NumberFormatter is only available from ICU level 60, but Android lags behind:
        // https://developer.android.com/guide/topics/resources/internationalization#versioning-nougat
        // So you need Android 9 (API level 28) and even then, the NumberFormatter
        // is not available in android.icu.* so you still would need to bundle the full ICU lib
        // For now, this is to much overkill.
//        @TargetApi(28)
//        private String icuFormat(@NonNull final Float money) {
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
                if (Format.isMappingAllowed(context)) {
                    return Format.map(context, source);
                } else {
                    return source;
                }
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
     * Uses the Locale from the Field.
     */
    public static class LanguageFormatter
            implements FieldFormatter<String> {

        public LanguageFormatter() {
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            String formatted = LanguageUtils.getDisplayName(Locale.getDefault(), source);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.FIELD_FORMATTER) {
                Logger.debug(this, "format",
                             "source=`" + source + '`',
                             "formatted=`" + formatted + '`');
            }
            return formatted;
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
            return LanguageUtils.getIso3fromDisplayName(source, Locale.getDefault());
        }
    }

    /**
     * Field Formatter for a bitmask based field.
     * Formats the checked items as a CSV String.
     * <p>
     * {@link #extract} returns "dummy";
     */
    public static class BitMaskFormatter
            implements FieldFormatter<Long> {

        @NonNull
        private final Map<Integer, Integer> mMap;

        public BitMaskFormatter(@NonNull final Map<Integer, Integer> editions) {
            mMap = editions;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<Long> field,
                             @Nullable final Long source) {
            if (source == null || source == 0) {
                return "";
            }

            Context context = field.getView().getContext();
            return TextUtils.join(", ", Csv.bitmaskToList(context, mMap, source));
        }

        // theoretically we should support the extract method as this formatter is used on
        // 'edit' fragments. But the implementation only ever sets the text on the screen
        // and stores the actual value directly. (dialog/listener setup).
        @NonNull
        @Override
        public Long extract(@NonNull final Field<Long> field,
                            @NonNull final String source) {
            return Long.parseLong(source);
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
     *
     * @param <T> type of Field value.
     */
    public static class Field<T> {

        /** Field ID. */
        @IdRes
        private final int mId;
        /** DEBUG - Owning collection. */
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final WeakReference<Fields> mFields;
        /** Visibility mGroup name. Used in conjunction with preferences to show/hide Views. */
        @NonNull
        private final String mGroup;
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
        private final FieldDataAccessor<T> mFieldDataAccessor;

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
         * @param visibilityGroup Visibility mGroup. Can be blank.
         */
        @VisibleForTesting
        public Field(@NonNull final Fields fields,
                     @IdRes final int fieldId,
                     @NonNull final String key,
                     @NonNull final String visibilityGroup) {

            mFields = new WeakReference<>(fields);
            mId = fieldId;
            mKey = key;
            mGroup = visibilityGroup;

            // Lookup the view. {@link Fields} will have the context set to the activity/fragment.
            final View view = getView();

            // check if the user actually uses this mGroup.
            mIsUsed = App.isUsed(mGroup);
            if (!mIsUsed) {
                view.setVisibility(View.GONE);
            }

            mFieldDataAccessor = createAccessor(view);
        }

        /**
         * Automatically determine a suitable FieldDataAccessor based on the View type.
         *
         * @param view to process
         *
         * @return FieldDataAccessor
         */
        @NonNull
        private FieldDataAccessor<T> createAccessor(@NonNull final View view) {

            FieldDataAccessor accessor;

            // this was nasty... a MaterialButton implements Checkable,
            // but you have to double check (pardon the pun) whether it IS checkable.
            if ((view instanceof MaterialButton) && ((MaterialButton) view).isCheckable()) {
                //noinspection unchecked
                accessor = new CheckableAccessor((Field<Boolean>) this);
                addTouchSignalsDirty(view);

            } else if (!((view instanceof MaterialButton)) && (view instanceof Checkable)) {
                // the opposite of above, do not accept the MaterialButton.
                //noinspection unchecked
                accessor = new CheckableAccessor((Field<Boolean>) this);
                addTouchSignalsDirty(view);

            } else if (view instanceof EditText) {
                accessor = new EditTextAccessor<>(this);

            } else if (view instanceof Button) {
                // a Button *is* a TextView
                accessor = new TextViewAccessor<>(this);

            } else if (view instanceof TextView) {
                accessor = new TextViewAccessor<>(this);

            } else if (view instanceof ImageView) {
                //noinspection unchecked
                accessor = new ImageViewAccessor((Field<String>) this);

            } else if (view instanceof RatingBar) {
                //noinspection unchecked
                accessor = new RatingBarAccessor((Field<Float>) this);
                addTouchSignalsDirty(view);

            } else if (view instanceof Spinner) {
                //noinspection unchecked
                accessor = new SpinnerAccessor((Field<String>) this);

            } else {
                // field has no layout, store in a dummy String.
                //noinspection unchecked
                accessor = new StringDataAccessor((Field<String>) this);
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debug(this, "Field",
                                 "Using StringDataAccessor",
                                 "key=" + mKey);
                }
            }

            return accessor;
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "mId=" + mId
                   + ", mGroup='" + mGroup + '\''
                   + ", mKey='" + mKey + '\''
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
         * Enable HTML (only applicable to TextView based fields).
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setShowHtml(final boolean showHtml) {
            if (mFieldDataAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mFieldDataAccessor).setShowHtml(showHtml);
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not a TextView");
            }
            return this;
        }

        /**
         * Enable decimal editing restriction(only applicable to EditText based fields).
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setInputIsDecimal() {
            if (mFieldDataAccessor instanceof EditTextAccessor) {
                ((EditTextAccessor) mFieldDataAccessor).setInputIsDecimal();
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not an EditText");
            }
            return this;
        }

        /**
         * Set scaling (only applicable to ImageView based fields).
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setScale(final int scale) {
            if (mFieldDataAccessor instanceof ImageViewAccessor) {
                ((ImageViewAccessor) mFieldDataAccessor).setScale(scale);
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not an ImageView");
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
         * set the field ids which should follow visibility with this Field.
         *
         * @param relatedFields labels etc
         */
        public Field<T> setRelatedFields(@NonNull @IdRes final int... relatedFields) {
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

            mIsUsed = App.isUsed(mGroup);
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
            // Touching this View is considered a change to the field.
            //TODO: We need to find a better way to handle this as this can cause a false-positive
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
            View view = fields.getFieldContext().findViewById(mId);
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
            String msg = "NULL View: key=" + mKey + ", id=" + mId + ", mGroup=" + mGroup;
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

        @IdRes
        public int getId() {
            return mId;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public T getValue() {
            return mFieldDataAccessor.getValue();
        }

        /**
         * Set the value of this Field.
         *
         * @param source New value
         */
        public void setValue(@NonNull final T source) {
            mFieldDataAccessor.setValue(source);
            mFields.get().afterFieldChange(this, source);
        }

        /**
         * Convenience method to check if the value is considered empty.
         *
         * @return {@code true} if this field is empty.
         */
        public boolean isEmpty() {
            return mFieldDataAccessor.isEmpty();
        }

        /**
         * Get the current value of this field and put it into the DataManager.
         */
        void putValueInto(@NonNull final DataManager target) {
            mFieldDataAccessor.getValueAndPut(target);
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        void setValueFrom(@NonNull final Bundle source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(@NonNull final DataManager source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source);
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
            return mFieldDataAccessor.format(source);
        }

        /**
         * Wrapper to {@link FieldDataAccessor#extract(String)}.
         *
         * @param source String to extract
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull final String source) {
            return mFieldDataAccessor.extract(source);
        }
    }
}
