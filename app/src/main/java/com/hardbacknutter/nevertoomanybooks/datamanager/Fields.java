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
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
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
    private final ArrayList<Field> mAllFields = new ArrayList<>();
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
     * This should NEVER happen, but it does. See Issue #505. So we need more info about why & when.
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
    private static String debugNullView(@NonNull final Field field) {
        String msg = "NULL View: col=" + field.mKey + ", id=" + field.id
                     + ", group=" + field.group;
        Fields fields = field.getFields();
        if (fields == null) {
            msg += ". Fields is NULL.";
        } else {
            msg += ". Fields is valid.";
            FieldsContext fieldContext = fields.getFieldContext();
            msg += ". Context is " + fieldContext.getClass().getCanonicalName() + '.';
            Object ownerClass = fieldContext.dbgGetOwnerClass();
            msg += ". Owner is ";
            if (ownerClass == null) {
                msg += "NULL.";
            } else {
                msg += ownerClass.getClass().getCanonicalName() + " (" + ownerClass + ')';
            }
        }
        return msg;
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
    public Field add(@IdRes final int fieldId,
                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field field = Field.newField(this, fieldId, key, key);
        mAllFields.add(field);
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
    public Field add(@IdRes final int fieldId,
                     @NonNull final String key,
                     @NonNull final String visibilityGroup) {
        Field field = Field.newField(this, fieldId, key, visibilityGroup);
        mAllFields.add(field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID.
     *
     * @return Associated Field.
     */
    @NonNull
    public Field getField(@IdRes final int fieldId) {
        for (Field field : mAllFields) {
            if (field.id == fieldId) {
                return field;
            }
        }
        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(fieldId));
    }

    /**
     * Convenience function: For an AutoCompleteTextView, set the adapter.
     *
     * @param fieldId Layout ID of View
     * @param adapter Adapter to use
     */
    public void setAdapter(@IdRes final int fieldId,
                           @NonNull final ArrayAdapter<String> adapter) {
        Field field = getField(fieldId);
        TextView textView = field.getView();
        if (textView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) textView).setAdapter(adapter);
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
            for (Field field : mAllFields) {
                field.setValueFrom(rawData);
            }
        } else {
            for (Field field : mAllFields) {
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
        for (Field field : mAllFields) {
            field.setValueFrom(dataManager);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void putAllInto(@NonNull final DataManager dataManager) {
        for (Field field : mAllFields) {
            if (!field.mKey.isEmpty()) {
                field.putValueInto(dataManager);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     */
    public void resetVisibility() {
        for (Field field : mAllFields) {
            field.resetVisibility();
        }
    }

    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true).
     */
    public interface AfterFieldChangeListener {

        void afterFieldChange(@NonNull Field field,
                              @Nullable Object newValue);
    }

    /**
     * Interface for view-specific accessors. One of these must be implemented for
     * each view type that is supported.
     */
    private interface FieldDataAccessor<T> {

        void setFormatter(@NonNull FieldFormatter formatter);

        @NonNull
        String format(@Nullable T source);

        @NonNull
        String extract(@NonNull String source);

        /**
         * Get the value from the view associated with the Field and return it as an Object.
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        T getValue();

        /**
         * Use the passed String to set the Field.
         *
         * @param source String value to set.
         */
        void setValue(@NonNull T source);

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
         * and store a <strong>native typed value</strong> in the passed collection.
         *
         * @param target Collection to save value into.
         */
        void putValueInto(@NonNull Bundle target);

        /**
         * Get the value from the view associated with the Field
         * and store a <strong>native typed value</strong> in the passed collection.
         *
         * @param target Collection to save value into.
         */
        void putValueInto(@NonNull DataManager target);
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
        String format(@NonNull Field field,
                      @Nullable T source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}.
         * It's only needed if the field is some sort of EditText.
         * Optional to implement if the field it's set on is read-only.
         * The default implementation throws an UnsupportedOperationException.
         * i.e. the developer goofed and should fix the bug.
         * <p>
         * Extract a formatted {@code String} from the displayed version.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        default String extract(@NonNull final Field field,
                               @NonNull final String source) {
            throw new UnsupportedOperationException();
        }
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
        private final Field field;

        EditTextWatcher(@NonNull final Field field) {
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

        @NonNull
        final Field field;

        @Nullable
        private FieldFormatter<T> mFormatter;

        BaseDataAccessor(@NonNull final Field field) {
            this.field = field;
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
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        public String extract(@NonNull final String source) {
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
    public static class StringDataAccessor
            extends BaseDataAccessor<String> {

        @NonNull
        private String mLocalValue = "";

        StringDataAccessor(@NonNull final Field field) {
            super(field);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putString(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putString(field.mKey, getValue());
        }

        @NonNull
        @Override
        public String getValue() {
            return mLocalValue.trim();
        }

        @Override
        public void setValue(@NonNull final String source) {
            mLocalValue = source;
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(Objects.requireNonNull(source.getString(field.mKey)));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(field.mKey));
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
    public static class TextViewAccessor
            extends BaseDataAccessor<String> {

        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        TextViewAccessor(@NonNull final Field field) {
            super(field);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putString(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putString(field.mKey, getValue());
        }

        @NonNull
        @Override
        public String getValue() {
            return mRawValue;
        }

        @Override
        public void setValue(@NonNull final String source) {
            mRawValue = source.trim();
            TextView view = field.getView();
            if (mFormatHtml) {
                view.setText(Html.fromHtml(format(mRawValue)));
                view.setFocusable(true);
                view.setTextIsSelectable(true);
                view.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            } else {
                view.setText(format(mRawValue));
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(Objects.requireNonNull(source.getString(field.mKey)));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(field.mKey));
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
    public static class EditTextAccessor
            extends BaseDataAccessor<String> {

        @NonNull
        private final TextWatcher mTextWatcher;

        EditTextAccessor(@NonNull final EditText view,
                         @NonNull final Field field) {
            super(field);
            mTextWatcher = new EditTextWatcher(field);
            view.addTextChangedListener(mTextWatcher);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putString(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putString(field.mKey, getValue());
        }

        @NonNull
        @Override
        public String getValue() {
            EditText view = field.getView();
            return extract(view.getText().toString().trim());
        }

        /**
         * 2018-12-11: There was recursion due to the setText call.
         * So now disabling the TextWatcher while doing the latter.
         *
         * @param source String value to set.
         */
        @Override
        public void setValue(@NonNull final String source) {
            EditText view = field.getView();
            // We don't want another thread re-enabling the listener before we're done
            synchronized (mTextWatcher) {
                view.removeTextChangedListener(mTextWatcher);

                String newVal = format(source);
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
        public void setValue(@NonNull final Bundle source) {
            setValue(Objects.requireNonNull(source.getString(field.mKey)));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(field.mKey));
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     */
    public static class SpinnerAccessor
            extends BaseDataAccessor<String> {

        SpinnerAccessor(@NonNull final Field field) {
            super(field);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putString(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putString(field.mKey, getValue());
        }

        @Override
        @NonNull
        public String getValue() {
            Spinner spinner = field.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return extract(selItem.toString().trim());
            } else {
                return "";
            }
        }

        @Override
        public void setValue(@Nullable final String source) {
            Spinner spinner = field.getView();
            String value = format(source);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(value)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(Objects.requireNonNull(source.getString(field.mKey)));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(field.mKey));
        }
    }

    /**
     * Checkable accessor.
     * <ul>{@link Checkable} covers more then just a Checkbox:
     * <li>CheckBox, RadioButton, Switch</li>
     * <li>ToggleButton extend CompoundButton</li>
     * <li>CheckedTextView extends TextView</li>
     * </ul>
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    public static class CheckableAccessor
            extends BaseDataAccessor<Boolean> {

        CheckableAccessor(@NonNull final Field field) {
            super(field);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putBoolean(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putBoolean(field.mKey, getValue());
        }

        @NonNull
        @Override
        public Boolean getValue() {
            Checkable cb = field.getView();
            return cb.isChecked();
        }

        @Override
        public void setValue(@Nullable final Boolean source) {
            Checkable cb = field.getView();
            if (source != null) {
                cb.setChecked(source);
            } else {
                cb.setChecked(false);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getBoolean(field.mKey));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getBoolean(field.mKey));
        }
    }

    /**
     * RatingBar accessor.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    public static class RatingBarAccessor
            extends BaseDataAccessor<Float> {

        RatingBarAccessor(@NonNull final Field field) {
            super(field);
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            target.putFloat(field.mKey, getValue());
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            target.putFloat(field.mKey, getValue());
        }

        @NonNull
        @Override
        public Float getValue() {
            RatingBar bar = field.getView();
            return bar.getRating();
        }

        @Override
        public void setValue(@Nullable final Float source) {
            RatingBar bar = field.getView();
            if (source != null) {
                bar.setRating(source);
            } else {
                bar.setRating(0.0f);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getFloat(field.mKey));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getFloat(field.mKey));
        }
    }

    /**
     * ImageView accessor. Uses the UUID to load the image into the view.
     * Sets a tag {@link R.id#TAG_UUID} on the view with the UUID.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    public static class ImageViewAccessor
            extends BaseDataAccessor<String> {

        private int mMaxWidth;
        private int mMaxHeight;

        /**
         * Constructor.
         *
         * @param scale to apply
         */
        ImageViewAccessor(@NonNull final Field field,
                          final int scale) {
            super(field);
            setScale(scale);
        }

        public void setScale(final int scale) {

            int maxSize = ImageUtils.getMaxImageSize(scale);

            mMaxHeight = maxSize;
            mMaxWidth = maxSize;
        }

        @Override
        public void putValueInto(@NonNull final Bundle target) {
            // not applicable
        }

        @Override
        public void putValueInto(@NonNull final DataManager target) {
            // not applicable
        }

        /**
         * Not really used, but returning the uuid makes sense.
         *
         * @return the UUID
         */
        @NonNull
        @Override
        public String getValue() {
            return (String) field.getView().getTag(R.id.TAG_UUID);
        }

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param source the book UUID
         */
        @Override
        public void setValue(@Nullable final String source) {
            ImageView imageView = field.getView();

            if (source != null) {
                File imageFile;
                if (source.isEmpty()) {
                    imageFile = StorageUtils.getTempCoverFile();
                } else {
                    imageView.setTag(R.id.TAG_UUID, source);
                    imageFile = StorageUtils.getCoverFile(source);
                }
                ImageUtils.setImageView(imageView, imageFile, mMaxWidth, mMaxHeight, true);
            } else {
                imageView.setImageResource(R.drawable.ic_image);
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(Objects.requireNonNull(source.getString(field.mKey)));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(field.mKey));
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
        public String format(@NonNull final Field field,
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
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            Date d = DateUtils.parseDate(source);
            if (d != null) {
                return DateUtils.utcSqlDate(d);
            }
            return source;
        }
    }

    /**
     * Formatter for boolean fields. Displays "Yes" or "No" (localized).
     * <p>
     * Can be reused for multiple fields.
     * <p>
     * Does not support {@link FieldFormatter#extract}
     */
    public static class BinaryYesNoEmptyFormatter
            implements FieldFormatter<String> {

        private final String mYes;
        private final String mNo;

        /**
         * @param context Current context
         */
        public BinaryYesNoEmptyFormatter(@NonNull final Context context) {
            mYes = context.getString(R.string.yes);
            mNo = context.getString(R.string.no);
        }

        /**
         * Display as a human-friendly yes/no string.
         */
        @NonNull
        @Override
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null) {
                return "";
            }
            try {
                boolean val = DataManager.toBoolean(source, false);
                return val ? mYes : mNo;
            } catch (@NonNull final NumberFormatException e) {
                return source;
            }
        }
    }

    /**
     * Formatter for price fields.
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
        public String format(@NonNull final Field field,
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

        private String jdkFormat(@NonNull final Field field,
                                 @NonNull final String source) {
            Locale locale = field.getLocale();
            Float price = Float.parseFloat(source);
            Currency currency = Currency.getInstance(mCurrencyCode);
            // the result is rather dire... most currency 'symbol' are shown as 3-char codes
            // e.g. 'EUR','US$',...
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(locale);
            currencyFormatter.setCurrency(currency);
            return currencyFormatter.format(price);
        }

        // experimental code... as it turned out, the ICU NumberFormatter is ICU level 60
        // which means Android 9. Keeping this comment, but pointless to integrate for now.
//        @TargetApi(24)
//        private String icuFormat(@NonNull final Field field,
//                                @NonNull final String source) {
//            https://github.com/unicode-org/icu/blob/master/icu4j/main/classes/core/src/
//            com/ibm/icu/number/NumberFormatter.java
//            and UnitWidth.NARROW
//        }
    }

    /**
     * Formatter for 'page' fields. If the value is numerical, output "x pages"
     * Otherwise outputs the original source value.
     * <p>
     * Uses the context from the Field view.
     * <p>
     * Does not support {@link FieldFormatter#extract}.
     */
    public static class PagesFormatter
            implements FieldFormatter<String> {

        @NonNull
        @Override
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source != null && !source.isEmpty() && !"0".equals(source)) {
                try {
                    int pages = Integer.parseInt(source);
                    return field.getView().getContext().getString(R.string.lbl_x_pages, pages);
                } catch (@NonNull final NumberFormatException ignore) {
                    // don't log, both formats are valid.
                }
                // stored pages was alphanumeric.
                return source;
            }
            return "";
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
        @Override
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return LocaleUtils.getDisplayName(field.getLocale(), source);
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
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {

            return LocaleUtils.getIso3fromDisplayName(source);
        }
    }

    /**
     * Field Formatter for a bitmask based field.
     * Formats the checked items as a CSV String.
     * <p>
     * Does not support {@link FieldFormatter#extract}.
     */
    public static class BitMaskFormatter
            implements FieldFormatter<String> {

        private final Map<Integer, Integer> mMap;

        public BitMaskFormatter(final Map<Integer, Integer> editions) {
            mMap = editions;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field field,
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
//        @NonNull
//        @Override
//        public String extract(@NonNull final Field field,
//                              @NonNull final String source) {
//            ...
//        }
    }

    /**
     * Formatter for {@code List<Entity>} fields that should be displayed as a CSV string.
     * <p>
     * Uses the context from the Field view.
     * <p>
     * Does not support {@link FieldFormatter#extract}.
     */
    public static class CsvFormatter
            implements FieldFormatter<List<Entity>> {

        @Nullable
        private Csv.Formatter<Entity> mCsvFormatter;

        /**
         * Constructor will use the default {@link Entity#getLabel()} to format the list.
         */
        public CsvFormatter() {
        }

        /**
         * Constructor with a custom CSV formatter.
         *
         * @param csvFormatter to use
         */
        public CsvFormatter(@NonNull final Csv.Formatter<Entity> csvFormatter) {
            this.mCsvFormatter = csvFormatter;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field field,
                             @Nullable final List<Entity> source) {
            if (source == null || source.isEmpty()) {
                return "";
            }
            if (mCsvFormatter == null) {
                return Csv.join(", ", source,
                                element -> element.getLabel(field.getView().getContext()));
            } else {
                return Csv.join(", ", source, mCsvFormatter);
            }
        }

        // theoretically we should support the extract method as this formatter is used on
        // 'edit' fragments. But the implementation only ever sets the text on the screen
        // and stores the actual value directly. (dialog/listener setup).
//        @NonNull
//        @Override
//        public String extract(@NonNull final Field field,
//                              @NonNull final String source) {
//            return source.split(",");
//        }
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
     * ENHANCE: make generic? and use an actual type
     */
    public static class Field {

        /** Field ID. */
        @IdRes
        public final int id;
        /** Owning collection. */
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

        /**
         * Constructor.
         *
         * @param fields          Parent collection
         * @param fieldId         Layout ID
         * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
         *                        Set to "" to suppress all access.
         * @param visibilityGroup Visibility group. Can be blank.
         */
        private Field(@NonNull final Fields fields,
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
                mFieldDataAccessor = new CheckableAccessor(this);
                addTouchSignalsDirty(view);

            } else if (!((view instanceof MaterialButton)) && (view instanceof Checkable)) {
                // the opposite, do not accept the MaterialButton here.
                mFieldDataAccessor = new CheckableAccessor(this);
                addTouchSignalsDirty(view);

            } else if (view instanceof EditText) {
                mFieldDataAccessor = new EditTextAccessor((EditText) view, this);

            } else if (view instanceof Button) {
                // a Button *is* a TextView, but this is cleaner
                mFieldDataAccessor = new TextViewAccessor(this);

            } else if (view instanceof TextView) {
                mFieldDataAccessor = new TextViewAccessor(this);

            } else if (view instanceof ImageView) {
                mFieldDataAccessor = new ImageViewAccessor(this, ImageUtils.SCALE_MEDIUM);

            } else if (view instanceof RatingBar) {
                mFieldDataAccessor = new RatingBarAccessor(this);
                addTouchSignalsDirty(view);

            } else if (view instanceof Spinner) {
                mFieldDataAccessor = new SpinnerAccessor(this);

            } else {
                // field has no layout, store in a dummy String.
                mFieldDataAccessor = new StringDataAccessor(this);
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
        static Field newField(@NonNull final Fields fields,
                              @IdRes final int fieldId,
                              @NonNull final String key,
                              @NonNull final String visibilityGroup) {
            return new Field(fields, fieldId, key, visibilityGroup);
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "id=" + id
                   //+ ", mFields=" + mFields
                   + ", group='" + group + '\''
                   + ", mKey='" + mKey + '\''
                   + ", mZeroIsEmpty=" + mZeroIsEmpty
                   + ", mIsUsed=" + mIsUsed
                   + ", mDoNoFetch=" + mDoNoFetch
                   + ", mFieldDataAccessor=" + mFieldDataAccessor
                   + '}';
        }

        /**
         * @param formatter to use
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field setFormatter(@NonNull final FieldFormatter formatter) {
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
        public Field setShowHtml(final boolean showHtml) {
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
        public Field setScale(final int scale) {
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
        public Field setDoNotFetch(final boolean doNoFetch) {
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
        public Field setZeroIsEmpty(final boolean zeroIsEmpty) {
            mZeroIsEmpty = zeroIsEmpty;
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
         * Reset one fields visibility based on user preferences.
         */
        private void resetVisibility() {
            mIsUsed = App.isUsed(group);
            getView().setVisibility(mIsUsed ? View.VISIBLE : View.GONE);
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
                    mFields.get().afterFieldChange(Field.this, null);
                }
                return false;
            });
        }

        /**
         * Accessor; added for debugging only. Try not to use!
         *
         * @return all fields
         */
        @Nullable
        protected Fields getFields() {
            return mFields.get();
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
            View view = mFields.get().getFieldContext().findViewById(id);
            // see comment on debugNullView
            if (view == null) {
                throw new NullPointerException("Unable to get associated View object\n"
                                               + debugNullView(this));
            }
            //noinspection unchecked
            return (V) view;
        }

        /**
         * syntax sugar.
         *
         * @return the Locale of the fields' context.
         */
        @NonNull
        public Locale getLocale() {
            Locale locale = getView().getResources().getConfiguration().locale;
            return locale != null ? locale : Locale.ENGLISH;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public Object getValue() {
            return mFieldDataAccessor.getValue();
        }

        /**
         * Set the value of this Field.
         *
         * @param source New value
         */
        public void setValue(@NonNull final Object source) {
            mFieldDataAccessor.setValue(source);
            mFields.get().afterFieldChange(this, source);
        }

        /**
         * Convenience method to check if the value is considered 'empty'.
         *
         * @return {@code true} if this field is empty.
         */
        public boolean isEmpty() {
            String value = getValue().toString();
            return value.isEmpty() || (mZeroIsEmpty && "0".equals(value));
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         **/
        void putValueInto(@NonNull final Bundle target) {
            mFieldDataAccessor.putValueInto(target);
        }

        /**
         * Get the current value of this field and put it into the DataManager.
         */
        void putValueInto(@NonNull final DataManager target) {
            mFieldDataAccessor.putValueInto(target);
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
        public String format(@Nullable final String source) {
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
         * @return The extracted value. Or "" if the source is {@code null}.
         */
        @NonNull
        String extract(@Nullable final String source) {
            if (source == null) {
                return "";
            }
            return mFieldDataAccessor.extract(source);
        }
    }
}
