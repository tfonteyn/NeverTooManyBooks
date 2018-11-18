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

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
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

import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 *
 * Features provides are:
 * <ul>
 * <li> handling of visibility via preferences
 * <li> handling of 'group' visibility via the 'group' property of a field.
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work as
 * expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.
 * <li> Custom data accessors and formatters to provide application-specific data rules.
 * <li> validation: calling {@link #validateAllFields} will call user-defined or predefined
 * validation routines. The text of any exceptions will be available after the call.
 * <li> simplified loading of data from a Cursor.
 * <li> simplified extraction of data to a {@link ContentValues} collection.
 * </ul>
 *
 * Formatters and Accessors
 *
 * It is up to each accessor to decide what to do with any formatters defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 *
 * On a set(), the accessor should call format() function then apply the value
 *
 * On a get() the accessor should retrieve the value and apply the extract() function.
 *
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 *
 * Data Flow
 *
 * Data flows to and from a view as follows:
 * IN  (with formatter): (Cursor or other source) -> format() (via accessor) -> transform (in accessor) -> View
 * IN  ( no formatter ): (Cursor or other source) -> transform (in accessor) -> View
 * OUT (with formatter): (Cursor or other source) -> transform (in accessor) -> extract (via accessor) -> validator -> (ContentValues or Object)
 * OUT ( no formatter ): (Cursor or other source) -> transform (in accessor) -> validator -> (ContentValues or Object)
 *
 * Usage Note:
 *
 * 1. Which Views to Add?
 *
 * It is not necessary to add every control to the 'Fields' collection, but as a general rule
 * any control that displays data from a database, or related derived data, or labels for such
 * data should be added.
 *
 * Typical controls NOT added, are 'Save' and 'Cancel' buttons, or other controls whose
 * interactions are purely functional.
 *
 * 2. Handlers?
 *
 * The add() method of Fields returns a new {@link Field} object which exposes the 'View' member;
 * this can be used to perform view-specific tasks like setting onClick() handlers.
 *
 * TODO: Rationalize the use of this collection with the {@link DataManager}.
 *
 * @author Philip Warner
 */
public class Fields extends ArrayList<Fields.Field> {
    public static final long serialVersionUID = 1L;
    /** Prefix for all preferences */
    private final static String PREFS_FIELD_VISIBILITY = "field_visibility_";

    /** The activity related to this object. */
    @NonNull
    private final FieldsContext mContext;

    /** All validator exceptions caught */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener = null;

    /**
     * Constructor
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    Fields(final @NonNull Fragment fragment) {
        super();
        mContext = new FragmentContext(fragment);
    }

    /**
     * Constructor
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    @SuppressWarnings("unused")
    Fields(final @NonNull Activity activity) {
        super();
        mContext = new ActivityContext(activity);
    }

    /**
     * This should NEVER happen, but it does. See Issue 505. So we need more info about why & when.
     *
     * // Allow for the (apparent) possibility that the view may have been removed due
     * // to a tab change or similar. See Issue 505.
     *
     * Every field MUST have an associated View object, but sometimes it is not found.
     * When not found, the app crashes.
     *
     * The following code is to help diagnose these cases, not avoid them.
     *
     * NOTE: 	This does NOT entirely fix the problem, it gathers debug info.
     * but we have implemented one work-around
     *
     * Work-around #1:
     *
     * It seems that sometimes the afterTextChanged() event fires after the text field
     * is removed from the screen. In this case, there is no need to synchronize the values
     * since the view is gone.
     */
    private static void debugNullView(final @NonNull Field field) {
        String msg = "NULL View: col=" + field.column + ", id=" + field.id + ", group=" + field.group;
        Fields fields = field.getFields();
        if (fields == null) {
            msg += ". Fields is NULL.";
        } else {
            msg += ". Fields is valid.";
            FieldsContext context = fields.getContext();
            msg += ". Context is " + context.getClass().getCanonicalName() + ".";
            Object ownerContext = context.dbgGetOwnerContext();
            if (ownerContext == null) {
                msg += ". Owner is NULL.";
            } else {
                msg += ". Owner is " + ownerContext.getClass().getCanonicalName() + " (" + ownerContext + ")";
            }
        }
        throw new IllegalStateException("Unable to get associated View object\n" + msg);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isVisible(final @NonNull String fieldName) {
        return BookCatalogueApp.getBooleanPreference(PREFS_FIELD_VISIBILITY + fieldName, true);
    }

    public static void setVisibility(final String fieldName, final boolean isVisible) {
        BookCatalogueApp.getSharedPreferences().edit().putBoolean(PREFS_FIELD_VISIBILITY + fieldName, isVisible).apply();
    }

    /**
     * @param listener the listener for field changes
     *
     * @return original listener
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @Nullable
    public AfterFieldChangeListener setAfterFieldChangeListener(final @Nullable AfterFieldChangeListener listener) {
        AfterFieldChangeListener old = mAfterFieldChangeListener;
        mAfterFieldChangeListener = listener;
        return old;
    }

    /**
     * Accessor for related Activity
     *
     * @return Activity for this collection.
     */
    @NonNull
    private FieldsContext getContext() {
        return mContext;
    }

    /**
     * Provides access to the underlying arrays get() method.
     */
    @SuppressWarnings("unused")
    @CallSuper
    public Field getItem(final int index) {
        return super.get(index);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId      Layout ID
     * @param sourceColumn Source DB column (can be blank)
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(final @IdRes int fieldId,
                     final @NonNull String sourceColumn) {
        return add(fieldId, sourceColumn, sourceColumn);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field add(final @IdRes int fieldId,
                     final @NonNull String sourceColumn,
                     final @NonNull String visibilityGroup) {
        Field field = new Field(this, fieldId, sourceColumn, visibilityGroup);
        this.add(field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID
     *
     * @return Associated Field.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public Field getField(final @IdRes int fieldId) {
        for (Field f : this) {
            if (f.id == fieldId) {
                return f;
            }
        }
        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(fieldId));
    }

    /**
     * Convenience function: For an AutoCompleteTextView, set the adapter
     *
     * @param fieldId Layout ID of View
     * @param adapter Adapter to use
     */
    public void setAdapter(final @IdRes int fieldId, final @NonNull ArrayAdapter<String> adapter) {
        Field field = getField(fieldId);
        TextView textView = field.getView();
        if (textView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) textView).setAdapter(adapter);
        }
    }

    /**
     * Load all fields from the passed cursor
     *
     * @param cursor Cursor to load Field objects from.
     *
     * @throws DBExceptions.ColumnNotPresent if the cursor does not have a required column
     */
    @SuppressWarnings("unused")
    public void setAllFrom(final @NonNull Cursor cursor) {
        for (Field field : this) {
            field.setValueFrom(cursor);
        }
    }

    /**
     * Load all fields from the passed bundle
     *
     * @param values Bundle to load Field objects from.
     */
    @SuppressWarnings("unused")
    public void setAllFrom(final @NonNull Bundle values) {
        for (Field field : this) {
            field.setValueFrom(values);
        }
    }

    /**
     * Load all fields from the passed {@link DataManager}
     *
     * @param data Cursor to load Field objects from.
     */
    public void setAllFrom(final @NonNull DataManager data) {
        for (Field field : this) {
            field.setValueFrom(data);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}
     *
     * @param data Cursor to load Field objects from.
     */
    void putAllInto(final @NonNull DataManager data) {
        for (Field field : this) {
            if (!field.column.isEmpty()) {
                field.putValueInto(data);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences
     */
    @SuppressWarnings("WeakerAccess")
    public void resetVisibility() {
        FieldsContext context = getContext();
        for (Field field : this) {
            field.resetVisibility(context);
        }
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     *
     * 2018-11-11: this and all related code is not used at all in the original code.
     *
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @param values The Bundle collection to fill
     *
     * @return boolean True if all validation passed.
     */
    @SuppressWarnings("unused")
    public boolean validateAllFields(final @NonNull Bundle values) {
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
     * Internal utility routine to perform one loop validating all fields.
     *
     * 2018-11-11: Called by {@link #validateAllFields(Bundle)} which however is not called at all
     *
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @param values          The Bundle to fill in/use.
     * @param crossValidating Options indicating if this is a cross validation pass.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateAllFields(final @NonNull Bundle values, final boolean crossValidating) {
        boolean isOk = true;
        for (Field field : this) {
            if (field.validator != null) {
                try {
                    field.validator.validate(this, field, values, crossValidating);
                } catch (ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                    // Always save the value...even if invalid. Or at least try to.
                    if (!crossValidating) {
                        try {
                            values.putString(field.column, field.getValue().toString().trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {
                if (!field.column.isEmpty()) {
                    field.putValueInto(values);
                }
            }
        }
        return isOk;
    }

    /**
     * Retrieve the text message associated with the last validation exception to occur.
     */
    @NonNull
    @SuppressWarnings("unused")
    public String getValidationExceptionMessage(final @NonNull Resources res) {
        if (mValidationExceptions.size() == 0) {
            return "No error";
        } else {
            StringBuilder message = new StringBuilder();
            Iterator<ValidatorException> i = mValidationExceptions.iterator();
            int cnt = 1;
            if (i.hasNext()) {
                message.append("(").append(cnt).append(") ").append(i.next().getFormattedMessage(res));
            }
            while (i.hasNext()) {
                cnt++;
                message.append(" (").append(cnt).append(") ").append(i.next().getFormattedMessage(res)).append("\n");
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
    public void addCrossValidator(final @NonNull FieldCrossValidator validator) {
        mCrossValidators.add(validator);
    }

    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true)
     */
    public interface AfterFieldChangeListener {
        void afterFieldChange(final @NonNull Field field, final @Nullable String newValue);
    }

    /**
     * Interface for all field-level validators. Each field validator is called twice; once
     * with the crossValidating flag set to false, then, if all validations were successful,
     * they are all called a second time with the flag set to true.
     * This is done in {@link #validateAllFields(Bundle)}
     *
     * This is an alternate method of applying cross-validation.
     * 2018-11-11: the original code never actively used this.
     *
     * It basically seems {@link DataManager} replaced/implemented validation instead
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
         * @param crossValidating Options indicating if this is the cross-validation pass.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(final @NonNull Fields fields,
                      final @NonNull Field field,
                      final @NonNull Bundle values,
                      final boolean crossValidating) throws ValidatorException;
    }

    /**
     * Interface for all cross-validators; these are applied after all field-level validators
     * have succeeded.
     *
     * @author Philip Warner
     */
    public interface FieldCrossValidator {
        /**
         * @param fields The Fields object containing the Field being validated
         * @param values A Bundle collection with all validated field values.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(final @NonNull Fields fields, final @NonNull Bundle values) throws ValidatorException;
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
        void setFieldValueFrom(final @NonNull Field field, final @NonNull Cursor cursor);

        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void setFieldValueFrom(final @NonNull Field field, final @NonNull Bundle values);

        /**
         * Passed a Field and a DataManager get the column from the data manager and set the view value.
         *
         * @param field  which defines the View details
         * @param values with data to load.
         */
        void setFieldValueFrom(final @NonNull Field field, final @NonNull DataManager values);

        /**
         * Passed a Field and a String, use the string to set the view value.
         *
         * @param field which defines the View details
         * @param value to set.
         */
        void set(final @NonNull Field field, final @NonNull String value);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed values collection.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed DataManager.
         *
         * @param field  associated with the View object
         * @param values Collection to save value into.
         */
        void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager values);

        /**
         * Get the the value from the view associated with Field and return it as an Object.
         *
         * @param field associated with the View object
         *
         * @return The most natural value to associate with the View value.
         */
        @NonNull
        Object get(final @NonNull Field field);
    }

    /**
     * Interface definition for Field formatters.
     *
     * @author Philip Warner
     */
    public interface FieldFormatter {
        /**
         * Format a string for applying to a View
         *
         * @param source Input value
         *
         * @return The formatted value. If the source is null, should return "" (and log an error)
         */
        @NonNull
        String format(final @NonNull Field field, final @Nullable String source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}
         *
         * Extract a formatted string from the displayed version.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        String extract(final @NonNull Field field, final @NonNull String source);
    }

    /** fronts an Activity/Fragment context */
    private interface FieldsContext {
        /** DEBUG only */
        Object dbgGetOwnerContext();

        @Nullable
        View findViewById(@IdRes int id);
    }

    /** Base implementation */
    private static abstract class BaseDataAccessor implements FieldDataAccessor {
        @Override
        public void setFieldValueFrom(final @NonNull Field field, final @NonNull Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        @Override
        public void setFieldValueFrom(final @NonNull Field field, final @NonNull Bundle values) {
            String v = values.getString(field.column);
            Objects.requireNonNull(v);
            set(field, v);
        }

        @Override
        public void setFieldValueFrom(final @NonNull Field field, final @NonNull DataManager values) {
            set(field, values.getString(field.column));
        }
    }

    /**
     * Implementation that stores and retrieves data from a local string variable.
     * Only used when a Field fails to find a layout.
     *
     * @author Philip Warner
     */
    static public class StringDataAccessor extends BaseDataAccessor {
        @NonNull
        private String mLocalValue = "";

        @Override
        public void set(final @NonNull Field field, final @NonNull String value) {
            mLocalValue = field.format(value);
        }

        @Override
        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @Override
        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @NonNull
        @Override
        public Object get(final @NonNull Field field) {
            return field.extract(mLocalValue);
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     *
     * The actual value is stored in a local variable.
     * Does not use a {@link FieldFormatter}
     *
     * @author Philip Warner
     */
    static public class TextViewAccessor extends BaseDataAccessor {
        private boolean mFormatHtml;
        @NonNull
        private String mRawValue = "";

        public void set(final @NonNull Field field, final @NonNull String value) {
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

        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values) {
            values.putString(field.column, mRawValue.trim());
        }

        @Override
        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager values) {
            values.putString(field.column, mRawValue.trim());
        }

        @NonNull
        public Object get(final @NonNull Field field) {
            return mRawValue;
        }

        /**
         * Set the TextViewAccessor to support HTML.
         */
        @SuppressWarnings("WeakerAccess")
        public void setShowHtml(final boolean showHtml) {
            mFormatHtml = showHtml;
        }

    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * Uses the defined {@link FieldFormatter} and setText() and getText().
     *
     * @author Philip Warner
     */
    static public class EditTextAccessor extends BaseDataAccessor {
        private boolean mIsSetting = false;

        public void set(final @NonNull Field field, final @NonNull String value) {
            synchronized (this) {
                if (mIsSetting) {
                    return; // Avoid recursion now we watch text
                }
                mIsSetting = true;
            }
            try {
                TextView view = field.getView();
                String newVal = field.format(value);
                String oldVal = view.getText().toString().trim();

                if (newVal.equals(oldVal)) {
                    return;
                }
                view.setText(newVal);
            } finally {
                mIsSetting = false;
            }
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values) {
            TextView view = field.getView();
            values.putString(field.column, field.extract(view.getText().toString()).trim());
        }

        @Override
        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager dataManager) {
            try {
                TextView view = field.getView();
                dataManager.putString(field.column, field.extract(view.getText().toString()).trim());
            } catch (Exception e) {
                throw new RuntimeException("Unable to save data", e);
            }
        }

        @NonNull
        public Object get(final @NonNull Field field) {
            TextView view = field.getView();
            return field.extract(view.getText().toString().trim());
        }
    }

    /**
     * Checkable accessor. Attempt to convert data to/from a boolean.
     *
     * {@link Checkable} covers more then just a Checkbox:
     * * CheckBox, RadioButton, Switch, ToggleButton extend CompoundButton
     * * CheckedTextView extends TextView
     *
     * @author Philip Warner
     */
    static public class CheckableAccessor extends BaseDataAccessor {

        public void set(final @NonNull Field field, final @Nullable String value) {
            Checkable cb = field.getView();
            if (value != null) {
                try {
                    cb.setChecked(Datum.toBoolean(field.format(value), true));
                } catch (Exception e) {
                    cb.setChecked(false);
                }
            } else {
                cb.setChecked(false);
            }
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                values.putString(field.column, field.formatter.extract(field, cb.isChecked() ? "1" : "0"));
            } else {
                values.putBoolean(field.column, cb.isChecked());
            }
        }

        @Override
        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager dataManager) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.formatter.extract(field, cb.isChecked() ? "1" : "0"));
            } else {
                dataManager.putBoolean(field.column, cb.isChecked());
            }
        }

        @NonNull
        public Object get(final @NonNull Field field) {
            Checkable cb = field.getView();
            if (field.formatter != null) {
                return field.formatter.extract(field, (cb.isChecked() ? "1" : "0"));
            } else {
                return cb.isChecked() ? 1 : 0;
            }
        }
    }

    /**
     * RatingBar accessor. Attempt to convert data to/from a Float.
     *
     * @author Philip Warner
     */
    static public class RatingBarAccessor extends BaseDataAccessor {
        public void setFieldValueFrom(final @NonNull Field field, final @NonNull Cursor cursor) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                ratingBar.setRating(Float.parseFloat(field.formatter.format(field, cursor.getString(cursor.getColumnIndex(field.column)))));
            } else {
                ratingBar.setRating(cursor.getFloat(cursor.getColumnIndex(field.column)));
            }
        }

        public void set(final @NonNull Field field, final @Nullable String value) {
            RatingBar ratingBar = field.getView();
            Float f = 0.0f;
            try {
                f = Float.parseFloat(field.format(value));
            } catch (NumberFormatException ignored) {
            }
            ratingBar.setRating(f);
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle bundle) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                bundle.putString(field.column, field.formatter.extract(field, "" + ratingBar.getRating()));
            } else {
                bundle.putFloat(field.column, ratingBar.getRating());
            }
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager dataManager) {
            RatingBar ratingBar = field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.formatter.extract(field, "" + ratingBar.getRating()));
            } else {
                dataManager.putFloat(field.column, ratingBar.getRating());
            }
        }

        @NonNull
        public Object get(final @NonNull Field field) {
            RatingBar ratingBar = field.getView();
            return ratingBar.getRating();
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     *
     * @author Philip Warner
     */
    static public class SpinnerAccessor extends BaseDataAccessor {

        public void set(final @NonNull Field field, final @Nullable String value) {
            Spinner spinner = field.getView();
            String s = field.format(value);
            for (int i = 0; i < spinner.getCount(); i++) {
                if (spinner.getItemAtPosition(i).equals(s)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull Bundle values) {
            values.putString(field.column, getValue(field));
        }

        public void putFieldValueInto(final @NonNull Field field, final @NonNull DataManager values) {
            values.putString(field.column, getValue(field));
        }

        @NonNull
        public Object get(final @NonNull Field field) {
            return field.extract(getValue(field));
        }

        @NonNull
        private String getValue(final @NonNull Field field) {
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
     *
     * @author Philip Warner
     */
    static public class DateFieldFormatter implements FieldFormatter {
        /**
         * Display as a human-friendly date, local timezone.
         */
        @NonNull
        public String format(final @NonNull Field field, final @Nullable String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }
            try {
                java.util.Date d = DateUtils.parseDate(source);
                if (d != null) {
                    return DateUtils.toPrettyDate(d);
                }
            } catch (Exception ignore) {
            }
            return source;
        }

        /**
         * Extract as an SQL date, UTC timezone.
         */
        @NonNull
        public String extract(final @NonNull Field field, final @NonNull String source) {
            try {
                java.util.Date d = DateUtils.parseDate(source);
                if (d != null) {
                    return DateUtils.utcSqlDate(d);
                }
            } catch (Exception ignore) {
            }
            return source;
        }
    }

    /**
     * Formatter for boolean fields.
     *
     * @author Philip Warner
     */
    static public class BinaryYesNoEmptyFormatter implements FieldFormatter {

        @NonNull
        private final Resources mRes;

        /**
         * @param res resources so we can get the strings to display
         */
        @SuppressWarnings("WeakerAccess")
        public BinaryYesNoEmptyFormatter(final @NonNull Resources res) {
            mRes = res;
        }

        /**
         * Display as a human-friendly yes/no string
         */
        @NonNull
        public String format(final @NonNull Field field, final @Nullable String source) {
            if (source == null) {
                return "";
            }
            try {
                boolean val = Datum.toBoolean(source, false);
                return mRes.getString(val ? R.string.yes : R.string.no);
            } catch (IllegalArgumentException e) {
                return source;
            }
        }

        /**
         * Extract as a boolean string
         */
        @NonNull
        public String extract(final @NonNull Field field, final @NonNull String source) {
            try {
                return Datum.toBoolean(source, false) ? "1" : "0";
            } catch (IllegalArgumentException e) {
                return source;
            }
        }
    }

    /**
     * Formatter for price fields.
     *
     * Does not support {@link FieldFormatter#extract}
     */
    static public class PriceFormatter implements FieldFormatter {

        @NonNull
        private final String currencyCode;

        /**
         */
        @SuppressWarnings("WeakerAccess")
        public PriceFormatter(final @NonNull String currencyCode) {
            this.currencyCode = currencyCode;
        }

        /**
         * Display with the currency symbol
         */
        @NonNull
        public String format(final @NonNull Field field, final @Nullable String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }
            if (currencyCode.isEmpty()) {
                return source;
            }

            // quick return for the pre-decimal UK Shilling/Pence prices.
            if (source.contains("/")) {
                return source;
            }

            try {
                Float price = Float.parseFloat(source);
                final NumberFormat currencyInstance = NumberFormat.getCurrencyInstance();
                currencyInstance.setCurrency(Currency.getInstance(currencyCode));
                return currencyInstance.format(price);
            } catch (IllegalArgumentException e) {
                Logger.error(e, "currencyCode=`" + currencyCode + "`, source=`" + source + "`");
                return currencyCode + " " + source;
            }
        }

        @NonNull
        public String extract(final @NonNull Field field, final @NonNull String source) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Formatter for language fields.
     *
     * The format implementation is fairly simplistic.
     * If the database column contains a standard ISO language code, then 'format'
     * will return a current Locale based display name of that language.
     * Otherwise, we just use the source String
     *
     * The extract implementation will attempt to convert the value string to an ISO language code.
     *
     * Note: the use of extract is not actually needed, as the insert of the book into
     * the database checks the ISO3 code. But at least this class works "correct".
     */
    static public class LanguageFormatter implements FieldFormatter {

        /**
         * If the source is a valid Locale language code, returns the full display
         * name of that language in the currently active locale.
         * Otherwise, simply return the source string itself.
         */
        @NonNull
        public String format(final @NonNull Field field, final @Nullable String source) {
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
        public String extract(final @NonNull Field field, final @NonNull String source) {
            // we need to transform a localised language name to it's ISO equivalent.
            return LocaleUtils.getISO3Language(source);
        }
    }

    /**
     * Formatter for a bitmask based Book Editions field.
     *
     * Does not support {@link FieldFormatter#extract}
     */
    static public class BookEditionsFormatter implements FieldFormatter {
        @NonNull
        @Override
        public String format(@NonNull final Field field, @Nullable final String source) {
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
                    list.add(BookCatalogueApp.getResourceString(Book.EDITIONS.get(edition)));
                }
            }
            return Utils.toDisplayString(list);
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field field, @NonNull final String source) {
            throw new UnsupportedOperationException();
        }
    }

    /** fronts an Activity context */
    private class ActivityContext implements FieldsContext {
        @NonNull
        private final WeakReference<Activity> mActivity;

        ActivityContext(final @NonNull Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public Object dbgGetOwnerContext() {
            return mActivity.get();
        }

        @Override
        public View findViewById(final @IdRes int id) {
            return mActivity.get().findViewById(id);
        }
    }

    /** fronts a Fragment context */
    private class FragmentContext implements FieldsContext {
        @NonNull
        private final WeakReference<Fragment> mFragment;

        FragmentContext(final @NonNull Fragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        public Object dbgGetOwnerContext() {
            return mFragment.get();
        }

        @Override
        @Nullable
        public View findViewById(final @IdRes int id) {
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
        /** Field ID */
        @IdRes
        public final int id;
        /** database column name (can be blank) */
        @NonNull
        public final String column;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views */
        @NonNull
        public final String group;
        /** Owning collection */
        @NonNull
        final WeakReference<Fields> mFields;
        /** FieldFormatter to use (can be null) */
        @Nullable
        public FieldFormatter formatter;
        /** FieldValidator to use (can be null) */
        @Nullable
        public FieldValidator validator;
        /** Has the field been set to invisible **/
        public boolean visible;
        /** Accessor to use (automatically defined) */
        private FieldDataAccessor accessor;
        /**
         * Option indicating that even though field has a column name, it should NOT be fetched
         * from a Cursor. This is usually done for synthetic fields needed when saving the data
         */
        private boolean doNoFetch = false;

        /** Optional field-specific tag object */
        @Nullable
        private Object mTag = null;

        /**
         * Constructor.
         *
         * @param fields              Parent object
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         */
        Field(final @NonNull Fields fields,
              final @IdRes int fieldId,
              final @NonNull String sourceColumn,
              final @NonNull String visibilityGroupName) {

            mFields = new WeakReference<>(fields);
            id = fieldId;
            column = sourceColumn;
            group = visibilityGroupName;

            // Lookup the view
            final View view = fields.getContext().findViewById(this.id);

            // Set the appropriate accessor
            if (view == null) {
                accessor = new StringDataAccessor();
            } else {
                if (view instanceof Spinner) {
                    accessor = new SpinnerAccessor();

                } else if (view instanceof Checkable) {
                    accessor = new CheckableAccessor();
                    addTouchSignalsDirty(view);

                } else if (view instanceof EditText) {
                    accessor = new EditTextAccessor();
                    EditText et = (EditText) view;
                    et.addTextChangedListener(new TextWatcher() {
                                @Override
                                public void afterTextChanged(@NonNull Editable arg0) {
                                    Field.this.setValue(arg0.toString());
                                }

                                @Override
                                public void beforeTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }

                                @Override
                                public void onTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }
                            });

                } else if (view instanceof Button) { // a Button *is* a TextView, but this is cleaner
                    accessor = new TextViewAccessor();
                } else if (view instanceof TextView) {
                    accessor = new TextViewAccessor();
                } else if (view instanceof ImageView) {
                    accessor = new TextViewAccessor();

                } else if (view instanceof RatingBar) {
                    accessor = new RatingBarAccessor();
                    addTouchSignalsDirty(view);
                } else {
                    throw new RTE.IllegalTypeException(view.getClass().getCanonicalName());
                }
                visible = isVisible(group);
                if (!visible) {
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
        public Field setAccessor(final @NonNull FieldDataAccessor accessor) {
            this.accessor = accessor;
            return this;
        }

        /**
         * @param formatter to use
         *
         * @return field (for chaining)
         */
        public Field setFormatter(final @NonNull FieldFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * @param validator to use
         *
         * @return field (for chaining)
         */
        public Field setValidator(final @NonNull FieldValidator validator) {
            this.validator = validator;
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
            if (accessor instanceof TextViewAccessor) {
                ((TextViewAccessor) accessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * @param doNoFetch true to stop the field being fetched from the database
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        public Field setDoNotFetch(final boolean doNoFetch) {
            this.doNoFetch = doNoFetch;
            return this;
        }

        /**
         * Reset one fields visibility based on user preferences
         */
        private void resetVisibility(final @Nullable FieldsContext context) {
            if (context == null) {
                return;
            }
            // Lookup the view
            final View view = context.findViewById(this.id);
            if (view != null) {
                visible = isVisible(group);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        private void addTouchSignalsDirty(final @NonNull View view) {
            // Touching this is considered a change
            //TODO We need to introduce a better way to handle this.
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(final @NonNull View v, final @NonNull MotionEvent event) {
                    if (MotionEvent.ACTION_UP == event.getAction()) {
                        if (mAfterFieldChangeListener != null) {
                            mAfterFieldChangeListener.afterFieldChange(Field.this, null);
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
            Fields fields = mFields.get();

            T view = (T) fields.getContext().findViewById(this.id);
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
        public void setTag(final @NonNull Object tag) {
            mTag = tag;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public Object getValue() {
            return accessor.get(this);
        }

        /**
         * Set the value to the passed string value.
         *
         * @param s New value
         */
        public void setValue(final @NonNull String s) {
            accessor.set(this, s);
            if (mAfterFieldChangeListener != null) {
                mAfterFieldChangeListener.afterFieldChange(this, s);
            }
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         **/
        public void putValueInto(final @NonNull Bundle values) {
            accessor.putFieldValueInto(this, values);
        }

        /**
         * Get the current value of this field and put it into the Bundle collection.
         **/
        public void putValueInto(final @NonNull DataManager data) {
            accessor.putFieldValueInto(this, data);
        }

        /**
         * Set the value of this field from the passed cursor.
         * Useful for getting access to raw data values from the database.
         *
         * @throws DBExceptions.ColumnNotPresent if the cursor does not have a required column
         */
        public void setValueFrom(final @NonNull Cursor cursor) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    accessor.setFieldValueFrom(this, cursor);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(final @NonNull Bundle bundle) {
            if (!column.isEmpty() && !doNoFetch) {
                accessor.setFieldValueFrom(this, bundle);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(final @NonNull DataManager dataManager) {
            if (!column.isEmpty() && !doNoFetch) {
                accessor.setFieldValueFrom(this, dataManager);

            }
        }

        /**
         * Utility function to call the formatters format() method if present,
         * or just return the raw value.
         *
         * @param s String to format
         *
         * @return The formatted value. If the source is null, should return "" (and log an error)
         */
        @NonNull
        public String format(final @Nullable String s) {
            if (s == null) {
                return "";
            }
            if (formatter == null) {
                return s;
            }
            return formatter.format(this, s);
        }

        /**
         * Utility function to call the formatters extract() method if present,
         * or just return the raw value.
         */
        @SuppressWarnings("WeakerAccess")
        public String extract(final @NonNull String s) {
            if (formatter == null) {
                return s;
            }
            return formatter.extract(this, s);
        }
    }
}

