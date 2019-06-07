/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.datamanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
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

import java.io.File;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.google.android.material.button.MaterialButton;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <p>
 * Features provides are:
 * <ul>
 * <li> handling of visibility via preferences</li>
 * <li> handling of 'group' visibility via the 'group' property of a field.</li>
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work
 * as expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.</li>
 * <li> Custom data accessors and formatters to provide application-specific data rules.</li>
 * <li> validation: calling {@link #validate} will call user-defined or predefined</li>
 * validation routines. The text of any exceptions will be available after the call.</li>
 * <li> simplified extraction of data to a {@link ContentValues} collection.</li>
 * </ul>
 * <p>
 * Formatters and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatters defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 * <p>
 * On a set(), the accessor should call format() function then apply the value
 * On a get() the accessor should retrieve the value and apply the extract() function.
 * <p>
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 * <p>
 * Data flows to and from a view as follows:
 * <ul>
 * <li>IN  ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> View</li>
 * <li>IN  (with formatter):
 * <br>(DataManager/Bundle) -> format() (via accessor) -> transform (in accessor) -> View</li>
 * <li>OUT ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> validator -> (ContentValues or Object)</li>
 * <li>OUT (with formatter):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> extract (via accessor)
 * -> validator -> (ContentValues or Object)</li>
 * </ul>
 * <p>
 * Usage Note:
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
 * TODO: Rationalize the use of this collection with the {@link DataManager}.
 *
 * @author Philip Warner
 */
public class Fields {

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    /** All validator exceptions caught. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** the list with all fields. */
    private final ArrayList<Fields.Field> mAllFields = new ArrayList<>();
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
     * <p>
     * NOTE: This does NOT entirely fix the problem, it gathers debug info.
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
        return add(fieldId, sourceColumn, sourceColumn);
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
        Field field = new Field(this, fieldId, sourceColumn, visibilityGroup);
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
     * @param values        with data to load.
     * @param withOverwrite if {@code true}, all fields are copied.
     *                      If {@code false}, only non-existent fields are copied.
     */
    public void setAllFrom(@NonNull final Bundle values,
                           final boolean withOverwrite) {
        if (withOverwrite) {
            for (Field field : mAllFields) {
                field.setValueFrom(values);
            }
        } else {
            for (Field field : mAllFields) {
                if (!field.mColumn.isEmpty() && values.containsKey(field.mColumn)) {
                    String val = values.getString(field.mColumn);
                    if (val != null) {
                        field.setValue(val);
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
                if (BuildConfig.DEBUG) {
                    Logger.debug(this, "validate", validator.toString());
                }

            } catch (ValidatorException e) {
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
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(@NonNull final Bundle values,
                             final boolean crossValidating) {
        boolean isOk = true;
        for (Field field : mAllFields) {
            if (field.mFieldValidator != null) {
                try {
                    field.mFieldValidator.validate(this, field, values, crossValidating);
                    if (BuildConfig.DEBUG) {
                        Logger.debug(this, "validate",
                                     "column=" + field.mColumn,
                                     "crossValidating=" + crossValidating);
                    }

                } catch (ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                    // Always save the value...even if invalid. Or at least try to.
                    if (!crossValidating) {
                        try {
                            values.putString(field.mColumn, field.getValue().toString().trim());
                        } catch (RuntimeException ignored) {
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
     * @return the text message associated with the last validation exception to occur.
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
                        iterator.next().getFormattedMessage(context));
            }
            while (iterator.hasNext()) {
                cnt++;
                message.append(" (").append(cnt).append(") ").append(
                        iterator.next().getFormattedMessage(context)).append('\n');
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
     * <p>
     * Serializable: because it's a member of a serializable class (and lint...)
     */
    public interface AfterFieldChangeListener
            extends Serializable {

        void afterFieldChange(@NonNull Field field,
                              @Nullable String newValue);
    }

    /**
     * Interface for all field-level validators. Each field validator is called twice; once
     * with the crossValidating flag set to false, then, if all validations were successful,
     * they are all called a second time with the flag set to true.
     * This is done in {@link #validate(Bundle)}
     *
     * @author Philip Warner
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
     * <p>
     * Serializable: because it's a member of a serializable class (and lint...)
     *
     * @author Philip Warner
     */
    public interface FieldCrossValidator
            extends Serializable {

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
     * Interface for view-specific accessors. One of these will be implemented for
     * each view type that is supported.
     * <p>
     * TOMF: would it be enough to make this generic ? i.e instead of String be able to use a boolean ?
     *
     * @author Philip Warner
     */
    public interface FieldDataAccessor {

        /**
         * Passed a Field and a Bundle get the column from the Bundle and set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void setFieldValueFrom(@NonNull Field field,
                               @NonNull Bundle values);

        /**
         * Passed a Field and a DataManager get the column from the DataManager and
         * set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void setFieldValueFrom(@NonNull Field field,
                               @NonNull DataManager values);

        /**
         * Passed a Field and a String, use the string to set the view value.
         *
         * @param field which defines the View details
         * @param value to set.
         */
        void set(@NonNull Field field,
                 @NonNull String value);

        /**
         * Get the value from the view associated with Field and store a native version
         * in the passed values collection.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void putFieldValueInto(@NonNull Field field,
                               @NonNull Bundle values);

        /**
         * Get the value from the view associated with Field and store a native version
         * in the passed DataManager.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void putFieldValueInto(@NonNull Field field,
                               @NonNull DataManager values);

        /**
         * Get the value from the view associated with Field and return it as an Object.
         *
         * @param field associated with the View object
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        Object get(@NonNull Field field);
    }

    /**
     * Interface definition for Field formatters.
     *
     * @author Philip Warner
     */
    public interface FieldFormatter {

        /**
         * Format a string for applying to a View.
         *
         * @param source Input value
         *
         * @return The formatted value. If the source is {@code null}, should return "" (and log an error)
         */
        @NonNull
        String format(@NonNull Field field,
                      @Nullable String source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}.
         * <p>
         * Extract a formatted string from the displayed version.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        default String extract(@NonNull final Fields.Field field,
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
        public void setFieldValueFrom(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            set(field, Objects.requireNonNull(values.getString(field.mColumn)));
        }

        @Override
        public void setFieldValueFrom(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            set(field, values.getString(field.mColumn));
        }
    }

    /**
     * Implementation that stores and retrieves data from a local string variable.
     * Only used when a Field fails to find a layout.
     */
    public static class StringDataAccessor
            extends BaseDataAccessor {

        @NonNull
        private String mLocalValue = "";

        @Override
        public void set(@NonNull final Field field,
                        @NonNull final String value) {
            mLocalValue = field.format(value);
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            values.putString(field.mColumn, field.extract(mLocalValue).trim());
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            values.putString(field.mColumn, field.extract(mLocalValue).trim());
        }

        @NonNull
        @Override
        public Object get(@NonNull final Field field) {
            return field.extract(mLocalValue);
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     * <p>
     * The actual value is stored in a local variable.
     * Does not use a {@link FieldFormatter}
     */
    public static class TextViewAccessor
            extends BaseDataAccessor {

        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        public void set(@NonNull final Field field,
                        @NonNull final String value) {
            mRawValue = value;
            TextView view = field.getView();
            if (mFormatHtml) {
                view.setText(Html.fromHtml(field.format(value)));
                view.setFocusable(true);
                view.setTextIsSelectable(true);
                view.setAutoLinkMask(Linkify.ALL);
            } else {
                view.setText(field.format(value));
            }
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            values.putString(field.mColumn, mRawValue.trim());
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            values.putString(field.mColumn, mRawValue.trim());
        }

        @NonNull
        public Object get(@NonNull final Field field) {
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
     * Implementation that stores and retrieves data from an EditText.
     * Uses the defined {@link FieldFormatter} and setText() and getText().
     * <p>
     * 2018-12-11: set is now synchronized. There was recursion builtin due to the setText call.
     * So now, disabling the TextWatcher while doing the latter.
     */
    public static class EditTextAccessor
            extends BaseDataAccessor {

        @NonNull
        private final TextWatcher mTextWatcher;

        EditTextAccessor(@NonNull final EditText view,
                         @NonNull final Field field) {
            mTextWatcher = new TextWatcher() {
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
                    //FIXME: not very efficient... gets called whenever the field content is set
                    // This includes when the device is rotated, when a tab is changed...
                    if (BuildConfig.DEBUG) {
                        Logger.debug(this,"afterTextChanged",
                                     "s=`" + s.toString() + '`',
                                     "extract=`" + field.extract(s.toString()) + '`'
                        );
                    }
                    field.setValue(field.extract(s.toString()));
                }
            };
            view.addTextChangedListener(mTextWatcher);
        }

        public synchronized void set(@NonNull final Field field,
                                     @NonNull final String value) {
            EditText view = field.getView();
            view.removeTextChangedListener(mTextWatcher);

            String newVal = field.format(value);
            String oldVal = view.getText().toString().trim();
            if (!newVal.equals(oldVal)) {
                view.setText(newVal);
            }
            view.addTextChangedListener(mTextWatcher);
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            EditText view = field.getView();
            values.putString(field.mColumn, field.extract(view.getText().toString().trim()));
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            EditText view = field.getView();
            values.putString(field.mColumn, field.extract(view.getText().toString().trim()));
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            EditText view = field.getView();
            return field.extract(view.getText().toString().trim());
        }
    }

    /**
     * Checkable accessor. Attempt to convert data to/from a boolean.
     * <p>
     * {@link Checkable} covers more then just a Checkbox:
     * CheckBox, RadioButton, Switch, ToggleButton extend CompoundButton
     * CheckedTextView extends TextView
     */
    public static class CheckableAccessor
            extends BaseDataAccessor {

        public void set(@NonNull final Field field,
                        @Nullable final String value) {
            Checkable cb = field.getView();
            if (value != null) {
                try {
                    cb.setChecked(Datum.toBoolean(field.format(value), true));
                } catch (NumberFormatException e) {
                    cb.setChecked(false);
                }
            } else {
                cb.setChecked(false);
            }
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                values.putString(field.mColumn,
                                 field.formatter.extract(field, cb.isChecked() ? "1" : "0"));
            } else {
                values.putBoolean(field.mColumn, cb.isChecked());
            }
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                values.putString(field.mColumn,
                                 field.formatter.extract(field, cb.isChecked() ? "1" : "0"));
            } else {
                values.putBoolean(field.mColumn, cb.isChecked());
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                return field.formatter.extract(field, cb.isChecked() ? "1" : "0");
            } else {
                return cb.isChecked() ? 1 : 0;
            }
        }
    }

    /**
     * ImageView accessor. Uses the UUID to load the image into the view.
     * Sets a tag {@link R.id#TAG_UUID} on the view with the UUID.
     * <p>
     * ENHANCE: currently limited to handling the cover image ONLY. Make this generic handling filenames instead of uuid's
     */
    public static class ImageViewAccessor
            extends BaseDataAccessor {

        private int mMaxWidth;
        private int mMaxHeight;

        /**
         * Constructor.
         * <p>
         * A default maximum size gets calculated. Override by calling {@link #setMaxSize}
         * BEFORE the displaying is done.
         *
         * @param context Current context
         */
        ImageViewAccessor(@NonNull final Context context) {
            int maxSize = 2 * (int) context.getResources().getDimension(R.dimen.cover_base_size);

            mMaxHeight = maxSize;
            mMaxWidth = maxSize;
        }

        /**
         * Override the defaults with custom sizes.
         *
         * @param maxWidth  maximum width
         * @param maxHeight maximum height
         */
        public void setMaxSize(final int maxWidth,
                               final int maxHeight) {
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
        }

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param field which defines the View details
         * @param value to set: the book uuid !
         */
        public void set(@NonNull final Field field,
                        @Nullable final String value) {
            ImageView imageView = field.getView();

            if (value != null) {
                File imageFile;
                if (value.isEmpty()) {
                    imageFile = StorageUtils.getTempCoverFile();
                } else {
                    imageView.setTag(R.id.TAG_UUID, value);
                    imageFile = StorageUtils.getCoverFile(value);
                }
                ImageUtils.setImageView(imageView, imageFile, mMaxWidth, mMaxHeight, true);
            } else {
                imageView.setImageResource(R.drawable.ic_broken_image);
            }
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            // not applicable
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            // not applicable
        }

        /**
         * Not really used, but returning the uuid makes sense.
         *
         * @param field associated with the View object
         *
         * @return the uuid
         */
        @NonNull
        public Object get(@NonNull final Field field) {
            return field.getView().getTag(R.id.TAG_UUID);
        }
    }

    /**
     * RatingBar accessor. Attempt to convert data to/from a Float.
     */
    public static class RatingBarAccessor
            extends BaseDataAccessor {

        public void set(@NonNull final Field field,
                        @Nullable final String value) {
            RatingBar bar = field.getView();
            float f = 0.0f;
            try {
                f = Float.parseFloat(field.format(value));
            } catch (NumberFormatException ignored) {
            }
            bar.setRating(f);
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            RatingBar bar = field.getView();
            if (field.formatter != null) {
                String value = field.formatter.extract(field, String.valueOf(bar.getRating()));
                values.putString(field.mColumn, value);
            } else {
                values.putFloat(field.mColumn, bar.getRating());
            }
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            RatingBar bar = field.getView();
            if (field.formatter != null) {
                String value = field.formatter.extract(field, String.valueOf(bar.getRating()));
                values.putString(field.mColumn, value);
            } else {
                values.putFloat(field.mColumn, bar.getRating());
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            RatingBar bar = field.getView();
            return bar.getRating();
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     */
    public static class SpinnerAccessor
            extends BaseDataAccessor {

        public void set(@NonNull final Field field,
                        @Nullable final String value) {
            Spinner spinner = field.getView();
            String s = field.format(value);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(s)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            values.putString(field.mColumn, getValue(field));
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            values.putString(field.mColumn, getValue(field));
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            return field.extract(getValue(field));
        }

        @NonNull
        private String getValue(@NonNull final Field field) {
            Spinner spinner = field.getView();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return selItem.toString().trim();
            } else {
                return "";
            }

        }
    }

    /**
     * Formatter for date fields.
     * <p>
     * Can be shared among multiple fields.
     * Uses the context/locale from the field itself.
     */
    public static class DateFieldFormatter
            implements FieldFormatter {

        /**
         * Display as a human-friendly date, local timezone.
         */
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
     * Formatter for boolean fields.
     * <p>
     * Can be reused for multiple fields.
     *
     * @author Philip Warner
     */
    public static class BinaryYesNoEmptyFormatter
            implements FieldFormatter {

        private final String mYes;
        private final String mNo;

        /**
         * @param context for strings
         */
        public BinaryYesNoEmptyFormatter(@NonNull final Context context) {
            mYes = context.getString(R.string.yes);
            mNo = context.getString(R.string.no);
        }

        /**
         * Display as a human-friendly yes/no string.
         */
        @NonNull
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null) {
                return "";
            }
            try {
                boolean val = Datum.toBoolean(source, false);
                return val ? mYes : mNo;
            } catch (NumberFormatException e) {
                return source;
            }
        }

        /**
         * @return "0" or "1": a boolean string.
         */
        @NonNull
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            try {
                return Datum.toBoolean(source, false) ? "1" : "0";
            } catch (NumberFormatException e) {
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
                Float price = Float.parseFloat(source);
                final NumberFormat currencyInstance = NumberFormat.getCurrencyInstance(
                        field.getLocale());
                currencyInstance.setCurrency(Currency.getInstance(mCurrencyCode));
                return currencyInstance.format(price);

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") IllegalArgumentException e) {
                Logger.error(this, e, "currencyCode=`" + mCurrencyCode + "`," +
                        " source=`" + source + '`');
                return mCurrencyCode + ' ' + source;
            }
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
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return LocaleUtils.getDisplayName(field.getLocale(), source);
        }

        @NonNull
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            // we need to transform a localised language name to its ISO equivalent.
            return LocaleUtils.getISO3Language(source);
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
                    Logger.debugWithStackTrace(this, "findViewById", "Activity is NULL");
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
     * Field Formatter for a bitmask based Book Editions field.
     * <p>
     * Does not support {@link FieldFormatter#extract}.
     */
    public static class BookEditionsFormatter
            implements FieldFormatter {

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
            } catch (NumberFormatException ignore) {
                return source;
            }
            Context context = field.getView().getContext();
            List<String> list = new ArrayList<>();
            for (Integer edition : Book.EDITIONS.keySet()) {
                if ((edition & bitmask) != 0) {
                    //noinspection ConstantConditions
                    list.add(context.getString(Book.EDITIONS.get(edition)));
                }
            }
            return Csv.join(", ", list, null);
        }
    }

    /**
     * Field definition contains all information and methods necessary to manage display and
     * extraction of data in a view.
     * ENHANCE: make generic? and use an actual type
     *
     * @author Philip Warner
     */
    public class Field {

        /** Field ID. */
        @IdRes
        public final int id;
        /** Owning collection. */
        @NonNull
        private final WeakReference<Fields> mFields;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views. */
        @NonNull
        private final String group;
        /**
         * column name (can be blank) used to access a {@link DataManager} (or Bundle).
         * <p>
         * - column is set, and doNoFetch==false:
         * ===> fetched from the {@link DataManager} (or Bundle), and populated on the screen
         * ===> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is set, and doNoFetch==true:
         * ===> fetched from the {@link DataManager} (or Bundle), but populating
         * the screen must be done manually.
         * ===> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is not set: field is defined, but data handling is fully manual.
         */
        @NonNull
        private final String mColumn;
        /** FieldFormatter to use (can be {@code null}). */
        @Nullable
        FieldFormatter formatter;
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
        /** Accessor to use (automatically defined). */
        @NonNull
        private FieldDataAccessor mFieldDataAccessor;


        /**
         * Constructor.
         *
         * @param fields              Parent collection
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         */
        Field(@NonNull final Fields fields,
              @IdRes final int fieldId,
              @NonNull final String sourceColumn,
              @NonNull final String visibilityGroupName) {

            mFields = new WeakReference<>(fields);
            id = fieldId;
            mColumn = sourceColumn;
            group = visibilityGroupName;

            // Lookup the view. Fields will have the context set to the activity/fragment.
            final View view = getView();

            // Set the appropriate accessor
            if ((view instanceof MaterialButton) && ((MaterialButton) view).isCheckable()) {
                // this was nasty... a MaterialButton implements Checkable,
                // but you have to double check (pardon the pun) whether it IS checkable.
                //TOMF: this emphasizes the need for having an actual type for the field.
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
                //ENHANCE: ImageViewAccessor needs more work
                Logger.debug(this, "Field", "ImageViewAccessor needs more work, disabled.");
//                    mFieldDataAccessor = new ImageViewAccessor(fields.getFieldContext().getContext());
                // temp dummy, does not actually work for images of course
                mFieldDataAccessor = new StringDataAccessor();

            } else if (view instanceof RatingBar) {
                mFieldDataAccessor = new RatingBarAccessor();
                addTouchSignalsDirty(view);

            } else if (view instanceof Spinner) {
                mFieldDataAccessor = new SpinnerAccessor();

            } else {
                //noinspection ConstantConditions
                throw new IllegalTypeException(view.getClass().getCanonicalName());
            }

            mIsUsed = App.isUsed(group);
            if (!mIsUsed) {
                view.setVisibility(View.GONE);
            }
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{" +
                    "id=" + id +
//                    ", mFields=" + mFields +
                    ", group='" + group + '\'' +
                    ", mColumn='" + mColumn + '\'' +
                    ", formatter=" + formatter +
                    ", mIsUsed=" + mIsUsed +
                    ", mDoNoFetch=" + mDoNoFetch +
                    ", mFieldValidator=" + mFieldValidator +
                    ", mFieldDataAccessor=" + mFieldDataAccessor +
                    '}';
        }

        /**
         * Allows overriding the automatic assigned accessor.
         *
         * @param accessor to use
         *
         * @return field (for chaining)
         */
        public Field setAccessor(@NonNull final FieldDataAccessor accessor) {
            mFieldDataAccessor = accessor;
            return this;
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
        public Field setFormatter(@NonNull final FieldFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * @param validator to use
         *
         * @return field (for chaining)
         */
        public Field setValidator(@NonNull final FieldValidator validator) {
            mFieldValidator = validator;
            return this;
        }

        /**
         * If a text field, set the TextViewAccessor to support HTML.
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field setShowHtml(final boolean showHtml) {
            if (mFieldDataAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mFieldDataAccessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * @param doNoFetch {@code true} to stop the field being fetched from the database
         *
         * @return field (for chaining)
         */
        public Field setDoNotFetch(final boolean doNoFetch) {
            mDoNoFetch = doNoFetch;
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
                    if (mAfterFieldChangeListener != null) {
                        mAfterFieldChangeListener.afterFieldChange(Field.this, null);
                    }
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
            return getView().getResources().getConfiguration().locale;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public Object getValue() {
            return mFieldDataAccessor.get(this);
        }

        /**
         * Set the value to the passed string value.
         *
         * @param s New value
         */
        public void setValue(@NonNull final String s) {
            mFieldDataAccessor.set(this, s);
            if (mAfterFieldChangeListener != null) {
                mAfterFieldChangeListener.afterFieldChange(this, s);
            }
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         **/
        void putValueInto(@NonNull final Bundle values) {
            mFieldDataAccessor.putFieldValueInto(this, values);
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         **/
        void putValueInto(@NonNull final DataManager data) {
            mFieldDataAccessor.putFieldValueInto(this, data);
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        void setValueFrom(@NonNull final Bundle bundle) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setFieldValueFrom(this, bundle);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(@NonNull final DataManager dataManager) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setFieldValueFrom(this, dataManager);

            }
        }

        /**
         * Wrapper to call the formatters format() method if present, or just return the raw value.
         *
         * @param s String to format
         *
         * @return The formatted value. If the source is {@code null}, should return "" (and log an error)
         */
        @NonNull
        public String format(@Nullable final String s) {
            if (s == null) {
                return "";
            }
            if (formatter == null) {
                return s;
            }
            return formatter.format(this, s);
        }

        /**
         * Wrapper to call the formatters extract() method if present, or just return the raw value.
         */
        @SuppressWarnings("WeakerAccess")
        public String extract(@NonNull final String s) {
            if (formatter == null) {
                return s;
            }
            return formatter.extract(this, s);
        }
    }
}
