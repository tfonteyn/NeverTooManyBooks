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
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
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
 * <li> validation: calling {@link #validate} will call user-defined or predefined</li>
 * validation routines. The text of any exceptions will be available after the call.</li>
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
 * <br>(DataManager/Bundle) -> transform (in accessor) -> validator
 * -> (ContentValues or Object)</li>
 * <li>OUT (with formatter):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> extract (via accessor)
 * -> validator -> (ContentValues or Object)</li>
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

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    /** All validator exceptions caught. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** the list with all fields. */
    private final ArrayList<Field> mAllFields = new ArrayList<>();
    /**
     * The activity or fragment related to this object.
     * Uses a WeakReference to the Activity/Fragment.
     */
    @NonNull
    private FieldsContext mFieldContext;
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
    private static void debugNullView(@NonNull final Field field) {
        String msg = "NULL View: col=" + field.mColumn + ", id=" + field.id
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
        throw new NullPointerException("Unable to get associated View object\n" + msg);
    }

    /**
     * @param listener the listener for field changes
     */
    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener;
    }

    private void afterFieldChange(@NonNull final Field field,
                                  @Nullable final String newValue) {
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
     * Allow re-setting the context so we can use the Fields class from a ViewModel.
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    public void setFieldContext(@NonNull final Fragment fragment) {
        mFieldContext = new FragmentContext(fragment);
    }

    /**
     * Allow re-setting the context so we can use the Fields class from a ViewModel.
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    public void setFieldContext(@NonNull final Activity activity) {
        mFieldContext = new ActivityContext(activity);
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId      Layout ID
     * @param sourceColumn Source DB column (can be blank)
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn) {
        Field field = Field.newField(this, fieldId, sourceColumn, sourceColumn);
        mAllFields.add(field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(@IdRes final int fieldId,
                     @NonNull final String sourceColumn,
                     @NonNull final String visibilityGroup) {
        Field field = Field.newField(this, fieldId, sourceColumn, visibilityGroup);
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
                if (!field.mColumn.isEmpty() && rawData.containsKey(field.mColumn)) {
                    String value = rawData.getString(field.mColumn);
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
            if (!field.mColumn.isEmpty()) {
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
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     * Use {@link #getValidationExceptionMessage} for the results.
     * <p>
     * 2019-06-11: right now we do not actually have any Field specific validators installed.
     * so first 2 checks will always pass. We do have a FieldCrossValidator.
     *
     * @param values The Bundle collection to fill
     *
     * @return {@code true} if all validation passed.
     */
    public boolean validate(@NonNull final Bundle values) {
        boolean isOk = true;
        mValidationExceptions.clear();

        // First, just validate all fields with the cross-val flag set false
        if (!validate(values, false)) {
            isOk = false;
        }

        // Now re-run with cross-val set to true.
        if (!validate(values, true)) {
            isOk = false;
        }

        // Finally run the cross-validators
        for (FieldCrossValidator validator : mCrossValidators) {
            try {
                validator.validate(this, values);

            } catch (@NonNull final ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }
        return isOk;
    }

    /**
     * Perform a loop validating all fields.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @param values          The Bundle to fill in/use.
     * @param crossValidating flag indicating if this is a cross validation pass.
     *
     * @return {@code true} is all validations are fine.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(@NonNull final Bundle values,
                             final boolean crossValidating) {
        boolean isOk = true;
        for (Field field : mAllFields) {
            FieldValidator validator = field.getValidator();
            if (validator != null) {
                try {
                    validator.validate(this, field, values, crossValidating);

                } catch (@NonNull final ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                    // Always save the value...even if invalid. Or at least try to.
                    if (!crossValidating) {
                        try {
                            values.putString(field.mColumn, field.getValue().toString());
                        } catch (@NonNull final RuntimeException ignored) {
                        }
                    }
                }
            } else {
                if (!field.mColumn.isEmpty()) {
                    field.putValueInto(values);
                }
            }
        }
        return isOk;
    }

    /**
     * Get the text message associated with the last validation exception to occur.
     *
     * @param context Current context
     *
     * @return the message
     */
    @NonNull
    public String getValidationExceptionMessage(@NonNull final Context context) {
        if (mValidationExceptions.isEmpty()) {
            return "No error";
        } else {
            StringBuilder message = new StringBuilder();
            Iterator<ValidatorException> iterator = mValidationExceptions.iterator();
            int cnt = 1;
            if (iterator.hasNext()) {
                message.append('(').append(cnt).append(") ").append(
                        iterator.next().getLocalizedMessage(context));
            }
            while (iterator.hasNext()) {
                cnt++;
                message.append(" (").append(cnt).append(") ").append(
                        iterator.next().getLocalizedMessage(context)).append('\n');
            }
            return message.toString();
        }
    }

    /**
     * Append a cross-field validator to the collection. These will be applied after
     * the field-specific validators have all passed.
     *
     * @param validator An instance of FieldCrossValidator to append
     */
    public void addCrossValidator(@NonNull final FieldCrossValidator validator) {
        mCrossValidators.add(validator);
    }

    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true).
     */
    public interface AfterFieldChangeListener {

        void afterFieldChange(@NonNull Field field,
                              @Nullable String newValue);
    }

    /**
     * Interface for all field-level validators. Each field validator is called twice; once
     * with the crossValidating flag set to false, then, if all validations were successful,
     * they are all called a second time with the flag set to true.
     * This is done in {@link #validate(Bundle)}
     */
    public interface FieldValidator {

        /**
         * Validation method. Must throw a {@link ValidatorException} if validation fails.
         *
         * @param fields          The Fields object containing the Field being validated
         * @param field           The Field to validate
         * @param values          A ContentValues collection to store the validated value.
         *                        On a cross-validation pass this collection will have all
         *                        field values set and can be read.
         * @param crossValidating flag indicating if this is the cross-validation pass.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(@NonNull Fields fields,
                      @NonNull Field field,
                      @NonNull Bundle values,
                      boolean crossValidating)
                throws ValidatorException;
    }

    /**
     * Interface for all cross-validators; these are applied after all field-level validators
     * have succeeded.
     */
    public interface FieldCrossValidator {

        /**
         * @param fields The Fields object containing the Field being validated
         * @param values A Bundle collection with all validated field values.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(@NonNull Fields fields,
                      @NonNull Bundle values)
                throws ValidatorException;
    }

    /**
     * Interface for view-specific accessors. One of these must be implemented for
     * each view type that is supported.
     */
    private interface FieldDataAccessor {

        /**
         * Passed a Field and a Bundle get the value from the Bundle and set the Field.
         *
         * @param source Collection to load data from.
         * @param target Field to update
         */
        void setValue(@NonNull Bundle source,
                      @NonNull Field target);

        /**
         * Passed a Field and a DataManager get the value from the DataManager and set the Field.
         *
         * @param source Collection to load data from.
         * @param target Field to update
         */
        void setValue(@NonNull DataManager source,
                      @NonNull Field target);

        /**
         * Passed a Field and a String, use the string to set the Field.
         *
         * @param source String value to set.
         * @param target Field to update
         */
        void setValue(@NonNull String source,
                      @NonNull Field target);


        /**
         * Get the value from the view associated with the Field and store a native version
         * in the passed collection.
         *
         * @param source Field to get the value from
         * @param target Collection to save value into.
         */
        void setValue(@NonNull Field source,
                      @NonNull Bundle target);

        /**
         * Get the value from the view associated with the Field and store a native version
         * in the passed collection.
         *
         * @param source Field to get the value from
         * @param target Collection to save value into.
         */
        void setValue(@NonNull Field source,
                      @NonNull DataManager target);

        /**
         * Get the value from the view associated with Field and return it as an Object.
         * <p>
         * Implementations should indicate (if possible) the correct return type.
         *
         * @param source Field to get the value from
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        Object getValue(@NonNull Field source);
    }

    /**
     * Interface definition for Field formatter.
     */
    public interface FieldFormatter {

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
                      @Nullable String source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}.
         * It's only needed if the field is some sort of EditText.
         * Optional to implement if the field it's set on is read-only.
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

    /** Base implementation. */
    private abstract static class BaseDataAccessor
            implements FieldDataAccessor {

        @Override
        public void setValue(@NonNull final Bundle source,
                             @NonNull final Field target) {
            setValue(Objects.requireNonNull(source.getString(target.mColumn)), target);
        }

        @Override
        public void setValue(@NonNull final DataManager source,
                             @NonNull final Field target) {
            setValue(source.getString(target.mColumn), target);
        }
    }

    /**
     * Implementation that stores and retrieves data from a local string variable
     * for fields without a layout.
     * <p>
     * Does not support {@link FieldFormatter}. Output is String.
     */
    public static class StringDataAccessor
            extends BaseDataAccessor {

        @NonNull
        private String mLocalValue = "";

        @Override
        public void setValue(@NonNull final String source,
                             @NonNull final Field target) {
            mLocalValue = source;
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putString(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putString(source.mColumn, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field source) {
            return mLocalValue.trim();
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     * <p>
     * The actual value is simply stored in a local variable. No attempt to extract is done.
     * <p>
     * Supports {@link FieldFormatter#format} only. Output is (the original) String.
     */
    public static class TextViewAccessor
            extends BaseDataAccessor {

        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        @Override
        public void setValue(@NonNull final String source,
                             @NonNull final Field target) {
            mRawValue = source.trim();
            TextView view = target.getView();
            if (mFormatHtml) {
                view.setText(Html.fromHtml(target.format(mRawValue)));
                view.setFocusable(true);
                view.setTextIsSelectable(true);
                view.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            } else {
                view.setText(target.format(mRawValue));
            }
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putString(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putString(source.mColumn, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field source) {
            return mRawValue;
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
            String value;
            if (field.formatter == null) {
                value = s.toString().trim();
            } else {
                value = field.formatter.extract(field, s.toString().trim());
            }

            if (BuildConfig.DEBUG) {
                Logger.debug(this, "afterTextChanged",
                             "s=`" + s.toString() + '`',
                             "extract=`" + value + '`'
                            );
            }
            field.setValue(value);
        }
    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * <p>
     * Supports {@link FieldFormatter}. Output is String.
     */
    public static class EditTextAccessor
            extends BaseDataAccessor {

        @NonNull
        private final TextWatcher mTextWatcher;

        EditTextAccessor(@NonNull final EditText view,
                         @NonNull final Field field) {
            mTextWatcher = new EditTextWatcher(field);
            view.addTextChangedListener(mTextWatcher);
        }

        /**
         * 2018-12-11: now synchronized. There was recursion due to the setText call.
         * So now disabling the TextWatcher while doing the latter.
         *
         * @param source String value to set.
         * @param target Field to update
         */
        @Override
        public synchronized void setValue(@NonNull final String source,
                                          @NonNull final Field target) {
            EditText view = target.getView();
            view.removeTextChangedListener(mTextWatcher);

            String newVal = target.format(source);
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

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putString(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putString(source.mColumn, getValue(source));
        }

        @NonNull
        @Override
        public String getValue(@NonNull final Field source) {
            EditText view = source.getView();
            if (source.formatter == null) {
                return view.getText().toString().trim();
            } else {
                return source.formatter.extract(source, view.getText().toString().trim());
            }
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     * <p>
     * Supports {@link FieldFormatter} which can be used if the internal String is different
     * from the displayed String.
     */
    public static class SpinnerAccessor
            extends BaseDataAccessor {

        @Override
        public void setValue(@Nullable final String source,
                             @NonNull final Field target) {
            Spinner spinner = target.getView();
            String value = target.format(source);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(value)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putString(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putString(source.mColumn, getValue(source));
        }

        @Override
        @NonNull
        public String getValue(@NonNull final Field source) {
            if (source.formatter == null) {
                return getSpinnerValue(source);
            } else {
                return source.formatter.extract(source, getSpinnerValue(source));
            }
        }

        /**
         * Get the raw String value from the Spinner.
         *
         * @param source Field to read.
         *
         * @return raw String
         */
        @NonNull
        private String getSpinnerValue(@NonNull final Field source) {
            Spinner spinner = source.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return selItem.toString().trim();
            } else {
                return "";
            }
        }
    }

    /**
     * Checkable accessor. Attempt to convert data to a boolean when setting to the Field.
     * <p>
     * Supports {@link FieldFormatter#format} only.
     * Output is <strong>strictly</strong> {@code Boolean}.
     * <ul>{@link Checkable} covers more then just a Checkbox:
     * <li>CheckBox, RadioButton, Switch</li>
     * <li>ToggleButton extend CompoundButton</li>
     * <li>CheckedTextView extends TextView</li>
     * </ul>
     */
    public static class CheckableAccessor
            extends BaseDataAccessor {

        @Override
        public void setValue(@Nullable final String source,
                             @NonNull final Field target) {
            Checkable cb = target.getView();
            if (source != null) {
                try {
                    cb.setChecked(Datum.toBoolean(target.format(source), true));
                } catch (@NonNull final NumberFormatException e) {
                    cb.setChecked(false);
                }
            } else {
                cb.setChecked(false);
            }
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putBoolean(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putBoolean(source.mColumn, getValue(source));
        }

        @NonNull
        @Override
        public Boolean getValue(@NonNull final Field source) {
            Checkable cb = source.getView();
            return cb.isChecked();
        }
    }

    /**
     * RatingBar accessor.
     * <p>
     * Supports {@link FieldFormatter#format} only.
     * Output is <strong>strictly</strong> {@code Float}.
     */
    public static class RatingBarAccessor
            extends BaseDataAccessor {

        @Override
        public void setValue(@Nullable final String source,
                             @NonNull final Field target) {
            RatingBar bar = target.getView();
            float rating;
            try {
                rating = Float.parseFloat(target.format(source));
            } catch (@NonNull final NumberFormatException ignored) {
                rating = 0.0f;
            }
            bar.setRating(rating);
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            target.putFloat(source.mColumn, getValue(source));
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            target.putFloat(source.mColumn, getValue(source));
        }

        @NonNull
        @Override
        public Float getValue(@NonNull final Field source) {
            RatingBar bar = source.getView();
            return bar.getRating();
        }
    }

    /**
     * ImageView accessor. Uses the UUID to load the image into the view.
     * Sets a tag {@link R.id#TAG_UUID} on the view with the UUID.
     */
    public static class ImageViewAccessor
            extends BaseDataAccessor {

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

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param source the book UUID
         * @param target which defines the View details
         */
        @Override
        public void setValue(@Nullable final String source,
                             @NonNull final Field target) {
            ImageView imageView = target.getView();

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
        public void setValue(@NonNull final Field source,
                             @NonNull final Bundle target) {
            // not applicable
        }

        @Override
        public void setValue(@NonNull final Field source,
                             @NonNull final DataManager target) {
            // not applicable
        }

        /**
         * Not really used, but returning the uuid makes sense.
         *
         * @param source associated with the View object
         *
         * @return the UUID
         */
        @NonNull
        @Override
        public String getValue(@NonNull final Field source) {
            return (String) source.getView().getTag(R.id.TAG_UUID);
        }
    }


    /**
     * Formatter/Extractor for date fields.
     * <p>
     * Can be shared among multiple fields.
     * Uses the context/locale from the field itself.
     */
    public static class DateFieldFormatter
            implements FieldFormatter {

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
            implements FieldFormatter {

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
                boolean val = Datum.toBoolean(source, false);
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
            implements FieldFormatter {

        @Nullable
        private String mCurrencyCode;

        /**
         * Can't/shouldn't pass the code into the constructor as it may change.
         * So must call this before displaying.
         * TODO: this defeats the ease of use of the formatter... populate manually or something...
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
            Float price = Float.parseFloat(source);
            Currency currency = java.util.Currency.getInstance(mCurrencyCode);
            // the result is rather dire... most currency 'symbol' are shown as 3-char codes
            // e.g. 'EUR','US$',...
            java.text.NumberFormat currencyFormatter =
                    java.text.NumberFormat.getCurrencyInstance(field.getLocale());
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
     * Uses the context from the Field.
     * <p>
     * Does not support {@link FieldFormatter#extract}.
     */
    public static class PagesFormatter
            implements FieldFormatter {

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
     * Uses the context from the Field to determine the output Locale.
     */
    public static class LanguageFormatter
            implements FieldFormatter {

        @NonNull
        @Override
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return LocaleUtils.getDisplayName(field.getLocale(), source);
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            // we need to transform a localised language name to its ISO equivalent.
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
            implements FieldFormatter {

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
         * column name (can be blank) used to access a {@link DataManager} (or Bundle).
         * <p>
         * - column is set, and doNoFetch==false:
         * ==> fetched from the {@link DataManager} (or Bundle), and populated on the screen
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is set, and doNoFetch==true:
         * ==> fetched from the {@link DataManager} (or Bundle), but populating
         * the screen must be done manually.
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is not set: field is defined, but data handling is fully manual.
         */
        @NonNull
        private final String mColumn;

        /** Accessor to use (automatically defined). */
        @NonNull
        private final FieldDataAccessor mFieldDataAccessor;

        /** FieldFormatter to use (can be {@code null}). */
        @Nullable
        FieldFormatter formatter;

        /** indicates that "0" should be seen as "". Used for boolean/int type fields. */
        private boolean mZeroIsEmpty;

        /** Is the field in use; i.e. is it enabled in the user-preferences. **/
        private boolean mIsUsed;

        /**
         * Option indicating that even though field has a column name, it should NOT be fetched
         * from a {@link DataManager} (or Bundle).
         * This is usually done for synthetic fields needed when putting the data
         * into the {@link DataManager} (or Bundle).
         */
        private boolean mDoNoFetch;

        /** FieldValidator to use (can be {@code null}). */
        @Nullable
        private FieldValidator mFieldValidator;

        /**
         * Constructor.
         *
         * @param fields              Parent collection
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         */
        private Field(@NonNull final Fields fields,
                      @IdRes final int fieldId,
                      @NonNull final String sourceColumn,
                      @NonNull final String visibilityGroupName) {

            mFields = new WeakReference<>(fields);
            id = fieldId;
            mColumn = sourceColumn;
            group = visibilityGroupName;

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
                mFieldDataAccessor = new EditTextAccessor((EditText) view, this);

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
                                 "sourceColumn=" + sourceColumn);
                }
            }
        }

        /**
         * Factory method to get a new auto-type-detection field.
         *
         * @param fields              Parent collection
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         *
         * @return new Field
         */
        static Field newField(@NonNull final Fields fields,
                              @IdRes final int fieldId,
                              @NonNull final String sourceColumn,
                              @NonNull final String visibilityGroupName) {
            return new Field(fields, fieldId, sourceColumn, visibilityGroupName);
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "id=" + id
                   //+ ", mFields=" + mFields
                   + ", group='" + group + '\''
                   + ", mColumn='" + mColumn + '\''
                   + ", formatter=" + formatter
                   + ", mZeroIsEmpty=" + mZeroIsEmpty
                   + ", mIsUsed=" + mIsUsed
                   + ", mDoNoFetch=" + mDoNoFetch
                   + ", mFieldValidator=" + mFieldValidator
                   + ", mFieldDataAccessor=" + mFieldDataAccessor
                   + '}';
        }

        /**
         * For specialized access.
         *
         * @return the field formatter.
         */
        @Nullable
        public FieldFormatter getFormatter() {
            return formatter;
        }

        /**
         * @param formatter to use
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field setFormatter(@NonNull final FieldFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * @return the field validator
         */
        @Nullable
        public FieldValidator getValidator() {
            return mFieldValidator;
        }

        /**
         * @param validator to use
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field setValidator(@NonNull final FieldValidator validator) {
            mFieldValidator = validator;
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
         * Set to {@code true} if a "0" content should be treated as an 'empty field'.
         * Used for (not) displaying boolean and integer fields.
         *
         * @param zeroIsEmpty flag
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
        public <T extends View> T getView() {
            View view = mFields.get().getFieldContext().findViewById(id);
            // see comment on debugNullView
            if (view == null) {
                // the debug call ends with throwing an exception.
                debugNullView(this);
            }

            //noinspection ConstantConditions,unchecked
            return (T) view;
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
            return mFieldDataAccessor.getValue(this);
        }

        /**
         * Set the value to the passed string value.
         *
         * @param source New value
         */
        public void setValue(@NonNull final String source) {
            mFieldDataAccessor.setValue(source, this);
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
            mFieldDataAccessor.setValue(this, target);
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         */
        void putValueInto(@NonNull final DataManager target) {
            mFieldDataAccessor.setValue(this, target);
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        void setValueFrom(@NonNull final Bundle source) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source, this);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(@NonNull final DataManager source) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setValue(source, this);
            }
        }

        /**
         * Wrapper to call the formatter's format() method if present, or just return the raw value.
         *
         * @param source String to format
         *
         * @return The formatted value. If the source is {@code null},
         * should return "" (and log an error)
         */
        @NonNull
        public String format(@Nullable final String source) {
            if (source == null) {
                return "";
            }
            if (formatter == null) {
                return source;
            }
            return formatter.format(this, source);
        }
    }
}
