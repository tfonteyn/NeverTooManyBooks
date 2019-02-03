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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
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
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;

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

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <p>
 * Features provides are:
 * <ul>
 * <li> handling of visibility via preferences
 * <li> handling of 'group' visibility via the 'group' property of a field.
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work
 * as expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.
 * <li> Custom data accessors and formatters to provide application-specific data rules.
 * <li> validation: calling {@link #validateAllFields} will call user-defined or predefined
 * validation routines. The text of any exceptions will be available after the call.
 * <li> simplified loading of data from a Cursor.
 * <li> simplified extraction of data to a {@link ContentValues} collection.
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
 * Data Flow
 * <p>
 * Data flows to and from a view as follows:
 * IN  ( no formatter ):
 * (Cursor or other source) -> transform (in accessor) -> View
 * <p>
 * IN  (with formatter):
 * (Cursor or other source) -> format() (via accessor) -> transform (in accessor) -> View
 * <p>
 * OUT ( no formatter ):
 * (Cursor or other source) -> transform (in accessor) -> validator -> (ContentValues or Object)
 * <p>
 * OUT (with formatter):
 * (Cursor or other source) -> transform (in accessor) -> extract (via accessor)
 * -> validator -> (ContentValues or Object)
 * <p>
 * Usage Note:
 * <p>
 * 1. Which Views to Add?
 * <p>
 * It is not necessary to add every control to the 'Fields' collection, but as a general rule
 * any control that displays data from a database, or related derived data, or labels for such
 * data should be added.
 * <p>
 * Typical controls NOT added, are 'Save' and 'Cancel' buttons, or other controls whose
 * interactions are purely functional.
 * <p>
 * 2. Handlers?
 * <p>
 * The add() method of Fields returns a new {@link Field} object which exposes the 'View' member;
 * this can be used to perform view-specific tasks like setting onClick() handlers.
 * <p>
 * TODO: Rationalize the use of this collection with the {@link DataManager}.
 *
 * @author Philip Warner
 */
public class Fields
        extends ArrayList<Fields.Field> {

    /**
     * Each field has an entry in the Preferences.
     * The key is suffixed with the name of the field.
     */
    public static final String PREFS_FIELD_VISIBILITY = "fields.visibility.";

    private static final long serialVersionUID = -4528188641451863555L;
    /** The activity related to this object. */
    @NonNull
    private final FieldsContext mContext;
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    /** All validator exceptions caught. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** TextEdit fields will be watched. */
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener;

    /**
     * Constructor.
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    public Fields(@NonNull final Fragment fragment) {
        super();
        mContext = new FragmentContext(fragment);
    }

    /**
     * Constructor.
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    @SuppressWarnings("unused")
    Fields(@NonNull final Activity activity) {
        super();
        mContext = new ActivityContext(activity);
    }

    /**
     * This should NEVER happen, but it does. See Issue #505. So we need more info about why & when.
     * <p>
     * // Allow for the (apparent) possibility that the view may have been removed due
     * // to a tab change or similar. See Issue #505.
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
            FieldsContext context = fields.getContext();
            msg += ". Context is " + context.getClass().getCanonicalName() + '.';
            Object ownerContext = context.dbgGetOwnerContext();
            msg += ". Owner is ";
            if (ownerContext == null) {
                msg += "NULL.";
            } else {
                msg += ownerContext.getClass().getCanonicalName() + " (" + ownerContext + ')';
            }
        }
        throw new IllegalStateException("Unable to get associated View object\n" + msg);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isVisible(@NonNull final String fieldName) {
        return Prefs.getPrefs().getBoolean(PREFS_FIELD_VISIBILITY + fieldName, true);
    }

    public static void setVisibility(@NonNull final String fieldName,
                                     final boolean isVisible) {
        Prefs.getPrefs().edit().putBoolean(PREFS_FIELD_VISIBILITY + fieldName, isVisible).apply();
    }

    /**
     * @param listener the listener for field changes
     */
    @SuppressWarnings("WeakerAccess")
    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener;
    }

    /**
     * Accessor for related FieldsContext.
     *
     * @return FieldsContext for this collection.
     */
    @NonNull
    private FieldsContext getContext() {
        return mContext;
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
        this.add(field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID.
     *
     * @return Associated Field.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public Field getField(@IdRes final int fieldId) {
        for (Field f : this) {
            if (f.id == fieldId) {
                return f;
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
     * Load all fields from the passed Cursor.
     *
     * @param cursor with data to load.
     *
     * @throws ColumnNotPresentException if the Cursor does not have a required column
     */
    @SuppressWarnings("unused")
    public void setAllFrom(@NonNull final Cursor cursor) {
        for (Field field : this) {
            field.setValueFrom(cursor);
        }
    }

    /**
     * Load all fields from the passed bundle.
     *
     * @param values with data to load.
     */
    @SuppressWarnings("WeakerAccess")
    public void setAllFrom(@NonNull final Bundle values) {
        for (Field field : this) {
            field.setValueFrom(values);
        }
    }

    /**
     * Load fields from the passed bundle.
     *
     * @param values        with data to load.
     * @param withOverwrite if <tt>true</tt>, all fields are copied.
     *                      If <tt>false</tt>, only non-existent fields are copied.
     */
    public void setAllFrom(@NonNull final Bundle values,
                           final boolean withOverwrite) {
        if (withOverwrite) {
            setAllFrom(values);
        } else {
            for (Field field : this) {
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
        for (Field field : this) {
            field.setValueFrom(dataManager);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void putAllInto(@NonNull final DataManager dataManager) {
        for (Field field : this) {
            if (!field.mColumn.isEmpty()) {
                field.putValueInto(dataManager);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     */
    @SuppressWarnings("WeakerAccess")
    public void resetVisibility() {
        for (Field field : this) {
            field.resetVisibility();
        }
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     * <p>
     * 2018-11-11: this and all related code is not used at all in the original code.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @param values The Bundle collection to fill
     *
     * @return <tt>true</tt> if all validation passed.
     */
    @SuppressWarnings("unused")
    private boolean validateAllFields(@NonNull final Bundle values) {
        boolean isOk = true;
        mValidationExceptions.clear();

        // First, just validate all fields with the cross-val flag set false
        if (!validateAllFields(values, false)) {
            isOk = false;
        }

        // Now re-run with cross-val set to true.
        if (!validateAllFields(values, true)) {
            isOk = false;
        }

        // Finally run the local cross-validation
        for (FieldCrossValidator validator : mCrossValidators) {
            try {
                validator.validate(this, values);
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
     * 2018-11-11: Called by {@link #validateAllFields(Bundle)} which however is not called at all
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @param values          The Bundle to fill in/use.
     * @param crossValidating flag indicating if this is a cross validation pass.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateAllFields(@NonNull final Bundle values,
                                      final boolean crossValidating) {
        boolean isOk = true;
        for (Field field : this) {
            if (field.mFieldValidator != null) {
                try {
                    field.mFieldValidator.validate(this, field, values, crossValidating);
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
    @SuppressWarnings("unused")
    public String getValidationExceptionMessage(@NonNull final Resources res) {
        if (mValidationExceptions.size() == 0) {
            return "No error";
        } else {
            StringBuilder message = new StringBuilder();
            Iterator<ValidatorException> iterator = mValidationExceptions.iterator();
            int cnt = 1;
            if (iterator.hasNext()) {
                message.append('(').append(cnt).append(") ").append(
                        iterator.next().getFormattedMessage(res));
            }
            while (iterator.hasNext()) {
                cnt++;
                message.append(" (").append(cnt).append(") ").append(
                        iterator.next().getFormattedMessage(res)).append('\n');
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
    @SuppressWarnings("WeakerAccess")
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
     * This is done in {@link #validateAllFields(Bundle)}
     * <p>
     * This is an alternate method of applying cross-validation.
     * 2018-11-11: the original code never actively used this.
     * It seems {@link DataManager} replaced/implemented validation instead.
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
     *
     * @author Philip Warner
     */
    public interface FieldDataAccessor {

        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field  which defines the View details
         * @param cursor with data to load.
         *
         * @throws CursorIndexOutOfBoundsException if the cursor does not have a required column
         */
        void setFieldValueFrom(@NonNull Field field,
                               @NonNull Cursor cursor)
                throws CursorIndexOutOfBoundsException;

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
         * @return The formatted value. If the source is null, should return "" (and log an error)
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
        String extract(@NonNull Field field,
                       @NonNull String source);
    }

    /**
     * fronts an Activity/Fragment context.
     */
    private interface FieldsContext {

        /** DEBUG only. */
        Object dbgGetOwnerContext();

        @Nullable
        View findViewById(@IdRes int id);
    }

    /** Base implementation. */
    private abstract static class BaseDataAccessor
            implements FieldDataAccessor {

        @Override
        public void setFieldValueFrom(@NonNull final Field field,
                                      @NonNull final Cursor cursor)
                throws CursorIndexOutOfBoundsException {
            set(field, cursor.getString(cursor.getColumnIndex(field.mColumn)));
        }

        @Override
        public void setFieldValueFrom(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            String v = values.getString(field.mColumn);
            Objects.requireNonNull(v);
            set(field, v);
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
         * @param showHtml if <tt>true</tt> this view will display HTML
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

        public synchronized void set(@NonNull final Field field,
                                     @NonNull final String value) {
            EditText view = field.getView();
            view.removeTextChangedListener(field.mTextWatcher);

            String newVal = field.format(value);
            String oldVal = view.getText().toString().trim();
            if (!newVal.equals(oldVal)) {
                view.setText(newVal);
            }
            view.addTextChangedListener(field.mTextWatcher);
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            EditText view = field.getView();
            values.putString(field.mColumn, field.extract(view.getText().toString()).trim());
        }

        @Override
        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            EditText view = field.getView();
            values.putString(field.mColumn, field.extract(view.getText().toString()).trim());
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
     * RatingBar accessor. Attempt to convert data to/from a Float.
     */
    public static class RatingBarAccessor
            extends BaseDataAccessor {

        public void setFieldValueFrom(@NonNull final Field field,
                                      @NonNull final Cursor cursor)
                throws CursorIndexOutOfBoundsException {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                ratingBar.setRating(Float.parseFloat(field.formatter.format(field, cursor.getString(
                        cursor.getColumnIndex(field.mColumn)))));
            } else {
                ratingBar.setRating(cursor.getFloat(cursor.getColumnIndex(field.mColumn)));
            }
        }

        public void set(@NonNull final Field field,
                        @Nullable final String value) {
            RatingBar ratingBar = field.getView();
            float f = 0.0f;
            try {
                f = Float.parseFloat(field.format(value));
            } catch (NumberFormatException ignored) {
            }
            ratingBar.setRating(f);
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final Bundle values) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                values.putString(field.mColumn,
                                 field.formatter
                                         .extract(field, String.valueOf(ratingBar.getRating())));
            } else {
                values.putFloat(field.mColumn, ratingBar.getRating());
            }
        }

        public void putFieldValueInto(@NonNull final Field field,
                                      @NonNull final DataManager values) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                values.putString(field.mColumn,
                                 field.formatter
                                         .extract(field, String.valueOf(ratingBar.getRating())));
            } else {
                values.putFloat(field.mColumn, ratingBar.getRating());
            }
        }

        @NonNull
        public Object get(@NonNull final Field field) {
            RatingBar ratingBar = field.getView();
            return ratingBar.getRating();
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

            Date d = DateUtils.parseDate(source);
            if (d != null) {
                return DateUtils.toPrettyDate(d);
            }

            return source;
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
     *
     * @author Philip Warner
     */
    public static class BinaryYesNoEmptyFormatter
            implements FieldFormatter {

        @NonNull
        private final Context mContext;

        /**
         * @param context so we can get the strings to display
         */
        @SuppressWarnings("WeakerAccess")
        public BinaryYesNoEmptyFormatter(@NonNull final Context context) {
            mContext = context;
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
                return mContext.getString(val ? R.string.yes : R.string.no);
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
     * Does not support {@link FieldFormatter#extract}
     */
    public static class PriceFormatter
            implements FieldFormatter {

        @NonNull
        private final String mCurrencyCode;

        /**
         */
        @SuppressWarnings("WeakerAccess")
        public PriceFormatter(@NonNull final String currencyCode) {
            this.mCurrencyCode = currencyCode;
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
            if (mCurrencyCode.isEmpty()) {
                return source;
            }

            // quick return for the pre-decimal UK Shilling/Pence prices.
            // ISFDB provides those types of prices.
            if (source.contains("/")) {
                return source;
            }

            try {
                Float price = Float.parseFloat(source);
                final NumberFormat currencyInstance = NumberFormat.getCurrencyInstance();
                currencyInstance.setCurrency(Currency.getInstance(mCurrencyCode));
                return currencyInstance.format(price);
            } catch (IllegalArgumentException e) {
                Logger.error(e, "currencyCode=`" + mCurrencyCode + "`," +
                        " source=`" + source + '`');
                return mCurrencyCode + ' ' + source;
            }
        }

        @NonNull
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Formatter for language fields.
     * <p>
     * The format implementation is fairly simplistic.
     * If the database column contains a standard ISO language code, then 'format'
     * will return a current Locale based display name of that language.
     * Otherwise, we just use the source String
     * <p>
     * The extract implementation will attempt to convert the value string to an ISO language code.
     * <p>
     * Note: the use of extract is not actually needed, as the insert of the book into
     * the database checks the ISO3 code. But at least this class works "correct".
     */
    public static class LanguageFormatter
            implements FieldFormatter {

        /**
         * If the source is a valid Locale language code, returns the full display
         * name of that language in the currently active locale.
         * Otherwise, simply return the source string itself.
         */
        @NonNull
        public String format(@NonNull final Field field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            Locale locale = new Locale(source);
            if (LocaleUtils.isValid(locale)) {
                return locale.getDisplayLanguage();
            }
            return source;
        }

        @NonNull
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            // we need to transform a localised language name to it's ISO equivalent.
            return LocaleUtils.getISO3Language(source);
        }
    }

    /**
     * Formatter for a bitmask based Book Editions field.
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

            List<String> list = new ArrayList<>();
            for (Integer edition : Book.EDITIONS.keySet()) {
                if ((edition & bitmask) != 0) {
                    list.add(BookCatalogueApp.getResString(Book.EDITIONS.get(edition)));
                }
            }
            return Csv.toDisplayString(list);
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field field,
                              @NonNull final String source) {
            throw new UnsupportedOperationException();
        }
    }

    public static class FieldUsage {

        /** a key, usually from {@link UniqueId}. */
        @NonNull
        public final String fieldId;
        /** is the field a list type. */
        private final boolean mIsList;
        /** label to show to the user. */
        @StringRes
        private final int mNameStringId;
        /** how to use this field. */
        @NonNull
        public Usage usage;


        public FieldUsage(@NonNull final String fieldId,
                          @StringRes final int nameStringId,
                          @NonNull final Usage usage,
                          final boolean isList) {
            this.fieldId = fieldId;
            mNameStringId = nameStringId;
            this.usage = usage;
            mIsList = isList;
        }

        public boolean isSelected() {
            return (usage != Usage.Skip);
        }

        public String getLabel(@NonNull final Context context) {
            return context.getString(mNameStringId);
        }

        public String getUsageInfo(@NonNull final Context context) {
            return context.getString(usage.getStringId());
        }

        /**
         * Cycle to the next Usage stage.
         * <p>
         * if (isList): Skip -> CopyIfBlank -> AddExtra -> Overwrite -> Skip
         * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
         */
        public void nextState() {
            usage = usage.nextState(mIsList);
        }

        public enum Usage {
            Skip, CopyIfBlank, AddExtra, Overwrite;

            @NonNull
            public Usage nextState(final boolean isList) {
                switch (this) {
                    case Skip:
                        return CopyIfBlank;
                    case CopyIfBlank:
                        if (isList) {
                            return AddExtra;
                        } else {
                            return Overwrite;
                        }
                    case AddExtra:
                        return Overwrite;

                    //case Overwrite:
                    default:
                        return Skip;
                }
            }

            @StringRes
            int getStringId() {
                switch (this) {
                    case CopyIfBlank:
                        return R.string.lbl_field_usage_copy_if_blank;
                    case AddExtra:
                        return R.string.lbl_field_usage_add_extra;
                    case Overwrite:
                        return R.string.lbl_field_usage_overwrite;
                    default:
                        return R.string.usage_skip;
                }
            }
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
        public Object dbgGetOwnerContext() {
            return mActivity.get();
        }

        @Override
        public View findViewById(@IdRes final int id) {
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
        public Object dbgGetOwnerContext() {
            return mFragment.get();
        }

        @Override
        @Nullable
        public View findViewById(@IdRes final int id) {
            if (mFragment.get() == null) {
                if (/* always show debug */ BuildConfig.DEBUG) {
                    Logger.debug("Fragment is NULL");
                }
                return null;
            }
            final View view = mFragment.get().getView();
            if (view == null) {
                if (/* always show debug */ BuildConfig.DEBUG) {
                    Logger.debug("View is NULL");
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
     *
     * @author Philip Warner
     */
    public class Field {

        /** Field ID. */
        @IdRes
        public final int id;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views. */
        @NonNull
        public final String group;
        /**
         * column name (can be blank) used to access a {@link DataManager} (or Bundle/Cursor).
         * <p>
         * - column is set, and doNoFetch==false:
         * ===> fetched from the {@link DataManager} (or Bundle/Cursor), and populated on the screen
         * ===> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is set, and doNoFetch==true:
         * ===> fetched from the {@link DataManager} (or Bundle/Cursor), but populating
         * the screen must be done manually.
         * ===> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - column is not set: field is defined, but data handling is fully manual.
         */
        @NonNull
        private final String mColumn;
        /** Owning collection. */
        @NonNull
        private final WeakReference<Fields> mFields;
        /** FieldFormatter to use (can be null). */
        @Nullable
        public FieldFormatter formatter;
        /** FieldValidator to use (can be null). */
        @Nullable
        private FieldValidator mFieldValidator;
        /** Has the field been set to visible. **/
        private boolean mIsVisible;
        /** Accessor to use (automatically defined). */
        private FieldDataAccessor mFieldDataAccessor;
        /** TextWatcher, used for EditText fields only. */
        private TextWatcher mTextWatcher;

        /**
         * Option indicating that even though field has a column name, it should NOT be fetched
         * from a {@link DataManager} (or Bundle/Cursor).
         * This is usually done for synthetic fields needed when putting the data
         * into the {@link DataManager} (or Bundle).
         */
        private boolean mDoNoFetch;

        /** Optional field-specific tag object. */
        @Nullable
        private Object mTag;

        /**
         * Constructor.
         *
         * @param fields              Parent object
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

            // Lookup the view
            final View view = mFields.get().getContext().findViewById(this.id);

            // Set the appropriate accessor
            if (view == null) {
                // if the field does not have a View, then we provide generic String storage for it.
                mFieldDataAccessor = new StringDataAccessor();
            } else {
                if (view instanceof Spinner) {
                    mFieldDataAccessor = new SpinnerAccessor();

                } else if (view instanceof Checkable) {
                    mFieldDataAccessor = new CheckableAccessor();
                    addTouchSignalsDirty(view);

                } else if (view instanceof EditText) {
                    mFieldDataAccessor = new EditTextAccessor();
                    EditText et = (EditText) view;
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
                            Field.this.setValue(s.toString());
                        }
                    };
                    et.addTextChangedListener(mTextWatcher);

                } else if (view instanceof Button) {
                    // a Button *is* a TextView, but this is cleaner
                    mFieldDataAccessor = new TextViewAccessor();
                } else if (view instanceof TextView) {
                    mFieldDataAccessor = new TextViewAccessor();
                } else if (view instanceof ImageView) {
                    mFieldDataAccessor = new TextViewAccessor();

                } else if (view instanceof RatingBar) {
                    mFieldDataAccessor = new RatingBarAccessor();
                    addTouchSignalsDirty(view);

                } else {
                    //noinspection ConstantConditions
                    throw new RTE.IllegalTypeException(view.getClass().getCanonicalName());
                }
                mIsVisible = Fields.isVisible(group);
                if (!mIsVisible) {
                    view.setVisibility(View.GONE);
                }
            }
        }

        /**
         * Allows overriding the automatic assigned accessor.
         *
         * @param accessor to use
         *
         * @return field (for chaining)
         */
        public Field setAccessor(@NonNull final FieldDataAccessor accessor) {
            this.mFieldDataAccessor = accessor;
            return this;
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
            this.mFieldValidator = validator;
            return this;
        }

        /**
         * If a text field, set the TextViewAccessor to support HTML.
         *
         * @return field (for chaining)
         */
        @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
        @NonNull
        public Field setShowHtml(final boolean showHtml) {
            if (mFieldDataAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mFieldDataAccessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * @param doNoFetch <tt>true</tt> to stop the field being fetched from the database
         *
         * @return field (for chaining)
         */
        @SuppressWarnings({"UnusedReturnValue", "unused"})
        public Field setDoNotFetch(final boolean doNoFetch) {
            this.mDoNoFetch = doNoFetch;
            return this;
        }

        /**
         * @return visibility status
         */
        public boolean isVisible() {
            return mIsVisible;
        }

        /**
         * Reset one fields visibility based on user preferences.
         */
        private void resetVisibility() {
            // Lookup the view
            final View view = mFields.get().getContext().findViewById(this.id);
            if (view != null) {
                mIsVisible = Fields.isVisible(group);
                view.setVisibility(mIsVisible ? View.VISIBLE : View.GONE);
            }
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        private void addTouchSignalsDirty(@NonNull final View view) {
            // Touching this is considered a change
            //TODO: We need to introduce a better way to handle this.
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(@NonNull final View v,
                                       @NonNull final MotionEvent event) {
                    if (MotionEvent.ACTION_UP == event.getAction()) {
                        if (mAfterFieldChangeListener != null) {
                            mAfterFieldChangeListener.afterFieldChange(Field.this,
                                                                       null);
                        }
                    }
                    return false;
                }
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
         * Get the view associated with this Field, if available.
         *
         * @return Resulting View
         *
         * @throws NullPointerException if view is not found, which should never happen
         * @see #debugNullView
         */
        @SuppressWarnings("unchecked")
        @NonNull
        public <T extends View> T getView() {
            T view = (T) mFields.get().getContext().findViewById(this.id);
            if (view == null) {
                debugNullView(this);
                throw new NullPointerException("view is NULL");
            }
            return view;
        }

        /**
         * Return the current value of the tag field.
         *
         * @return Current value of tag.
         */
        @Nullable
        public Object getTag() {
            return mTag;
        }

        /**
         * Set the current value of the tag field.
         */
        public void setTag(@NonNull final Object tag) {
            mTag = tag;
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
         * Set the value of this field from the passed cursor.
         * Useful for getting access to raw data values from the database.
         *
         * @throws ColumnNotPresentException if the cursor does not have a required column
         */
        void setValueFrom(@NonNull final Cursor cursor) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                try {
                    mFieldDataAccessor.setFieldValueFrom(this, cursor);
                } catch (CursorIndexOutOfBoundsException e) {
                    throw new ColumnNotPresentException(mColumn, e);
                }
            }
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
        void setValueFrom(@NonNull final DataManager dataManager) {
            if (!mColumn.isEmpty() && !mDoNoFetch) {
                mFieldDataAccessor.setFieldValueFrom(this, dataManager);

            }
        }

        /**
         * Wrapper to call the formatters format() method if present, or just return the raw value.
         *
         * @param s String to format
         *
         * @return The formatted value. If the source is null, should return "" (and log an error)
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

