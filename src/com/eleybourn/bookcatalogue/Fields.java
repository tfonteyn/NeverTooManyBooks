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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
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
import android.widget.CheckBox;
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
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
 * <li> understanding of kinds of views (setting a Checkbox value to 'true' will work as
 * expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.
 * <li> Custom data accessors and formatters to provide application-specific data rules.
 * <li> validation: calling validate will call user-defined or predefined validation routines and
 * return success or failure. The text of any exceptions will be available after the call.
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
    // Java likes this
    public static final long serialVersionUID = 1L;
    // Used for date parsing
    @SuppressLint("SimpleDateFormat")
    private static final java.text.SimpleDateFormat DATE_SQL_SDF = new java.text.SimpleDateFormat("yyyy-MM-dd");
    private static final java.text.DateFormat DATE_DISPLAY_SDF = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    // The activity and preferences related to this object.
    private final FieldsContext mContext;
    private final SharedPreferences mPrefs;
    // The last validator exception caught by this object
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    // A list of cross-validators to apply if all fields pass simple validation.
    private final List<FieldCrossValidator> mCrossValidators = new ArrayList<>();
    private AfterFieldChangeListener mAfterFieldChangeListener = null;

    /**
     * Constructor
     *
     * @param activity The parent activity which contains all Views this object will manage.
     */
    @SuppressWarnings("unused")
    Fields(@NonNull final Activity activity) {
        super();
        mContext = new ActivityContext(activity);
        mPrefs = activity.getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, android.content.Context.MODE_PRIVATE);
    }

    /**
     * Constructor
     *
     * @param fragment The parent fragment which contains all Views this object will manage.
     */
    Fields(@NonNull final Fragment fragment) {
        super();
        mContext = new FragmentContext(fragment);
        mPrefs = fragment.getActivity().getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, android.content.Context.MODE_PRIVATE);
    }

    /**
     * Utility routine to parse a date. Parses YYYY-MM-DD and DD-MMM-YYYY format.
     * Could be generalized even further if desired by supporting more formats.
     *
     * FIXME: move/use the one from {@link DateUtils}.
     *
     * @param s String to parse
     *
     * @return Parsed date
     *
     * @throws ParseException If parse failed.
     */
    @NonNull
    private static Date parseDate(@Nullable final String s) throws ParseException {
        if (s == null) {
            throw new ParseException("Can't parse null", 0);
        }
        Date date;
        try {
            date = DATE_SQL_SDF.parse(s);
        } catch (Exception e) {
            try {
                date = DATE_DISPLAY_SDF.parse(s);
            } catch (Exception e1) {
                java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
                date = df.parse(s);
            }

        }
        return date;
    }

    /**
     * @param listener the listener for field changes
     *
     * @return original listener
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public AfterFieldChangeListener setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        AfterFieldChangeListener old = mAfterFieldChangeListener;
        mAfterFieldChangeListener = listener;
        return old;
    }

    /**
     * Accessor for related Activity
     *
     * @return Activity for this collection.
     */
    private FieldsContext getContext() {
        return mContext;
    }

    /**
     * Accessor for related Preferences
     *
     * @return SharedPreferences for this collection.
     */
    private SharedPreferences getPreferences() {
        return mPrefs;
    }

    /**
     * Provides access to the underlying arrays get() method.
     */
    @SuppressWarnings("unused")
    public Field getItem(final int index) {
        return super.get(index);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId        Layout ID
     * @param sourceColumn   Source DB column (can be blank)
     * @param fieldValidator Field Validator (can be null)
     *
     * @return The resulting Field.
     */
    public Field add(final int fieldId,
                     @NonNull final String sourceColumn,
                     @Nullable final FieldValidator fieldValidator) {
        return add(fieldId, sourceColumn, sourceColumn, fieldValidator, null);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId        Layout ID
     * @param sourceColumn   Source DB column (can be blank)
     * @param fieldValidator Field Validator (can be null)
     * @param formatter      Formatter to use
     *
     * @return The resulting Field.
     */
    public Field add(final int fieldId,
                     @NonNull final String sourceColumn,
                     @Nullable final FieldValidator fieldValidator,
                     @Nullable final FieldFormatter formatter) {
        return add(fieldId, sourceColumn, sourceColumn, fieldValidator, formatter);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     * @param fieldValidator  Field Validator (can be null)
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field add(final int fieldId,
                     @NonNull final String sourceColumn,
                     @NonNull final String visibilityGroup,
                     @Nullable final FieldValidator fieldValidator) {
        return add(fieldId, sourceColumn, visibilityGroup, fieldValidator, null);
    }

    /**
     * Add a field to this collection
     *
     * @param fieldId         Layout ID
     * @param sourceColumn    Source DB column (can be blank)
     * @param visibilityGroup Group name to determine visibility.
     * @param fieldValidator  Field Validator (can be null)
     * @param formatter       Formatter to use
     *
     * @return The resulting Field.
     */
    public Field add(final int fieldId,
                     @NonNull final String sourceColumn,
                     @NonNull final String visibilityGroup,
                     @Nullable final FieldValidator fieldValidator,
                     @Nullable final FieldFormatter formatter) {
        Field fe = new Field(this, fieldId, sourceColumn, visibilityGroup, fieldValidator, formatter);
        this.add(fe);
        return fe;
    }

    /**
     * Return the Field associated with the passed layout ID
     *
     * @return Associated Field.
     */
    public Field getField(final int id) {
        for (Field f : this) {
            if (f.id == id) {
                return f;
            }
        }
        throw new IllegalArgumentException();
    }

    /**
     * Convenience function: For an AutoCompleteTextView, set the adapter
     *
     * @param fieldId Layout ID of View
     * @param adapter Adapter to use
     */
    public void setAdapter(final int fieldId, @NonNull final ArrayAdapter<String> adapter) {
        Field f = getField(fieldId);
        TextView tv = (TextView) f.getView();
        if (tv instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) tv).setAdapter(adapter);
        }
    }

    /**
     * For a View that supports onClick() (all of them?), set the listener.
     *
     * @param id       view ID
     * @param listener onClick() listener.
     */
    void setListener(@IdRes final int id, @NonNull final View.OnClickListener listener) {
        View v = getField(id).getView();
        if (v != null) {
            v.setOnClickListener(listener);
        } else {
            throw new RuntimeException("Unable to find view for field id " + id);
        }
    }

    /**
     * Load all fields from the passed cursor
     *
     * @param c Cursor to load Field objects from.
     */
    public void setAll(@NonNull final Cursor c) {
        for (Field fe : this) {
            fe.set(c);
        }
    }

    /**
     * Load all fields from the passed bundle
     *
     * @param b Bundle to load Field objects from.
     */
    public void setAll(@NonNull final Bundle b) {
        for (Field fe : this) {
            fe.set(b);
        }
    }

    /**
     * Load all fields from the passed datamanager
     *
     * @param data Cursor to load Field objects from.
     */
    public void setAll(@NonNull final DataManager data) {
        for (Field fe : this) {
            fe.set(data);
        }
    }

    /**
     * Save all fields to the passed DataManager (ie. 'get' them *into* the DataManager).
     *
     * @param data Cursor to load Field objects from.
     */
    public void getAll(@NonNull final DataManager data) {
        for (Field fe : this) {
            if (fe.column != null && !fe.column.isEmpty()) {
                fe.getValue(data);
            }
        }
    }

    /**
     * Internal utility routine to perform one loop validating all fields.
     *
     * @param values          The Bundle to fill in/use.
     * @param crossValidating Flag indicating if this is a cross validation pass.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(@NonNull final Bundle values, final boolean crossValidating) {
        Iterator<Field> fi = this.iterator();
        boolean isOk = true;

        while (fi.hasNext()) {
            Field fe = fi.next();
            if (fe.validator != null) {
                try {
                    fe.validator.validate(this, fe, values, crossValidating);
                } catch (ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                    // Always save the value...even if invalid. Or at least try to.
                    if (!crossValidating) {
                        try {
                            values.putString(fe.column, fe.getValue().toString().trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {
                if (!fe.column.isEmpty()) {
                    fe.getValue(values);
                }
            }
        }
        return isOk;
    }

    /**
     * Reset all field visibility based on user preferences
     */
    public void resetVisibility() {
        FieldsContext c = this.getContext();
        for (Field fe : this) {
            fe.resetVisibility(c);
        }
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     *
     * @param values The Bundle collection to fill
     *
     * @return boolean True if all validation passed.
     */
    public boolean validate(@NonNull final Bundle values) {
        Objects.requireNonNull(values);

        boolean isOk = true;
        mValidationExceptions.clear();

        // First, just validate individual fields with the cross-val flag set false
        if (!validate(values, false)) {
            isOk = false;
        }

        // Now re-run with cross-val set to true.
        if (!validate(values, true)) {
            isOk = false;
        }

        // Finally run the local cross-validation
        for (FieldCrossValidator v : mCrossValidators) {
            try {
                v.validate(this, values);
            } catch (ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }
        return isOk;
    }

    /**
     * Retrieve the text message associated with the last validation exception t occur.
     *
     * @return res The resource manager to use when looking up strings.
     */
    @SuppressWarnings("unused")
    public String getValidationExceptionMessage(@NonNull final Resources res) {
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
     * @param v An instance of FieldCrossValidator to append
     */
    public void addCrossValidator(@NonNull final FieldCrossValidator v) {
        mCrossValidators.add(v);
    }

    public interface AfterFieldChangeListener {
        void afterFieldChange(@NonNull final Field field, @Nullable final String newValue);
    }

    private interface FieldsContext {
        Object dbgGetOwnerContext();

        View findViewById(@IdRes int id);
    }

    /**
     * Interface for view-specific accessors. One of these will be implemented for each view type that
     * is supported.
     *
     * @author Philip Warner
     */
    public interface FieldDataAccessor {
        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field Field which defines the View details
         * @param c     Cursor with data to load.
         */
        void set(@NonNull final Field field, @NonNull final Cursor c);

        /**
         * Passed a Field and a Cursor get the column from the cursor and set the view value.
         *
         * @param field Field which defines the View details
         * @param b     Bundle with data to load.
         */
        void set(@NonNull final Field field, @NonNull final Bundle b);

        /**
         * Passed a Field and a DataManager get the column from the data manager and set the view value.
         *
         * @param field Field which defines the View details
         * @param data  Bundle with data to load.
         */
        void set(@NonNull final Field field, @NonNull final DataManager data);

        /**
         * Passed a Field and a String, use the string to set the view value.
         *
         * @param field Field which defines the View details
         * @param s     Source string for value to set.
         */
        void set(@NonNull final Field field, @Nullable final String s);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed values collection.
         *
         * @param field  Field associated with the View object
         * @param values Collection to save value.
         */
        void get(@NonNull final Field field, @NonNull final Bundle values);

        /**
         * Get the the value from the view associated with Field and store a native version
         * in the passed DataManager.
         *
         * @param field  Field associated with the View object
         * @param values Collection to save value.
         */
        void get(@NonNull final Field field, @NonNull final DataManager values);

        /**
         * Get the the value from the view associated with Field and return it as am Object.
         *
         * @param field Field associated with the View object
         *
         * @return The most natural value to associate with the View value.
         */
        Object get(@NonNull final Field field);
    }

    /**
     * Interface for all field-level validators. Each field validator is called twice; once
     * with the crossValidating flag set to false, then, if all validations were successful,
     * they are all called a second time with the flag set to true. This is an alternate
     * method of applying cross-validation.
     *
     * @author Philip Warner
     */
    public interface FieldValidator {
        /**
         * Validation method. Must throw a ValidatorException if validation fails.
         *
         * @param fields          The Fields object containing the Field being validated
         * @param field           The Field to validate
         * @param values          A ContentValues collection to store the validated value.
         *                        On a cross-validation pass this collection will have all
         *                        field values set and can be read.
         * @param crossValidating Flag indicating if this is the cross-validation pass.
         *
         * @throws ValidatorException For any validation failure.
         */
        void validate(@NonNull final Fields fields, @NonNull final Field field, @NonNull final Bundle values, final boolean crossValidating);
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
         */
        void validate(@NonNull final Fields fields, @NonNull final Bundle values);
    }

    /**
     * Interface definition for Field formatters.
     *
     * @author Philip Warner
     */
    public interface FieldFormatter {
        /**
         * // Format a string for applying to a View
         *
         * @param source Input value
         *
         * @return The formatted value
         */
        String format(@NonNull final Field f, @Nullable final String source);

        /**
         * Extract a formatted string from the display version
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        String extract(@NonNull final Field f, @NonNull final String source);
    }

    /**
     * Implementation that stores and retrieves data from a string variable.
     * Only used when a Field fails to find a layout.
     *
     * @author Philip Warner
     */
    static public class StringDataAccessor implements FieldDataAccessor {
        private String mLocalValue = "";

        @Override
        public void set(@NonNull final Field field, @NonNull final Cursor c) {
            set(field, c.getString(c.getColumnIndex(field.column)));
        }

        @Override
        public void set(@NonNull final Field field, @NonNull final Bundle b) {
            set(field, b.getString(field.column));
        }

        @Override
        public void set(@NonNull final Field field, @NonNull final DataManager data) {
            set(field, data.getString(field.column));
        }

        @Override
        public void set(@NonNull final Field field, @Nullable final String s) {
            mLocalValue = field.format(s);
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            values.putString(field.column, field.extract(mLocalValue).trim());
        }

        @Override
        public Object get(@NonNull final Field field) {
            return field.extract(mLocalValue);
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is
     * displayed properly.
     *
     * @author Philip Warner
     */
    static public class TextViewAccessor implements FieldDataAccessor {
        private boolean mFormatHtml;
        private String mRawValue;

        TextViewAccessor() {
        }

        public void set(@NonNull final Field field, @NonNull final Cursor c) {
            set(field, c.getString(c.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle b) {
            set(field, b.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager data) {
            set(field, data.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String s) {
            mRawValue = s;
            TextView v = (TextView) field.getView();
            // Allow for the (apparent) possibility that the view may have been removed due to a tab change or similar.
            // See Issue 505.
            if (v == null) {
                // Log the error. Not much more we can do.
                String msg = "NULL View: col=" + field.column + ", id=" + field.id + ", group=" + field.group;
                Fields fields = field.getFields();
                if (fields == null) {
                    msg += ". Fields is NULL.";
                } else {
                    msg += ". Fields is valid.";
                    FieldsContext context = fields.getContext();
                    if (context == null) {
                        msg += ". Context is NULL.";
                    } else {
                        msg += ". Context is " + context.getClass().getSimpleName() + ".";
                        Object ownerContext = context.dbgGetOwnerContext();
                        if (ownerContext == null) {
                            msg += ". Owner is NULL.";
                        } else {
                            msg += ". Owner is " + ownerContext.getClass().getSimpleName() + " (" + ownerContext + ")";
                        }
                    }
                }
                Tracker.handleEvent(this, msg, Tracker.States.Running);
                // This should NEVER happen, but it does. So we need more info about why & when.
                throw new IllegalStateException("Unable to get associated View object");
            } else {
                if (mFormatHtml && s != null) {
                    v.setText(Html.fromHtml(field.format(s)));
                    v.setFocusable(true);
                    v.setTextIsSelectable(true);
                    v.setAutoLinkMask(Linkify.ALL);
                } else {
                    v.setText(field.format(s));
                }
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            values.putString(field.column, mRawValue.trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            values.putString(field.column, mRawValue.trim());
        }

        public Object get(@NonNull final Field field) {
            return mRawValue;
        }

        /**
         * Set the TextViewAccessor to support HTML.
         */
        public void setShowHtml(final boolean showHtml) {
            mFormatHtml = showHtml;
        }

    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * Just uses for defined formatter and setText() and getText().
     *
     * @author Philip Warner
     */
    static public class EditTextAccessor implements FieldDataAccessor {
        private boolean mIsSetting = false;

        public void set(@NonNull final Field field, @NonNull final Cursor c) {
            set(field, c.getString(c.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle b) {
            set(field, b.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager data) {
            set(field, data.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String s) {
            synchronized (this) {
                if (mIsSetting) {
                    return; // Avoid recursion now we watch text
                }
                mIsSetting = true;
            }
            try {
                TextView view = (TextView) field.getView();
                /*
                 Every field MUST have an associated View object, but sometimes it is not found.
                 When not found, the app crashes.

                 The following code is to help diagnose these cases, not avoid them.

                 NOTE: 	This does NOT entirely fix the problem, it gathers debug info.
                        but we have implemented one work-around

                 Work-around #1:

                 It seems that sometimes the afterTextChanged() event fires after the text field
                 is removed from the screen. In this case, there is no need to synchronize the values
                 since the view is gone.
                */
                if (view == null) {
                    String msg = "NULL View: col=" + field.column + ", id=" + field.id + ", group=" + field.group;
                    Fields fs = field.getFields();
                    if (fs == null) {
                        msg += ". Fields is NULL.";
                    } else {
                        msg += ". Fields is valid.";
                        FieldsContext ctx = fs.getContext();
                        if (ctx == null) {
                            msg += ". Context is NULL.";
                        } else {
                            msg += ". Context is " + ctx.getClass().getSimpleName() + ".";
                            Object o = ctx.dbgGetOwnerContext();
                            if (o == null) {
                                msg += ". Owner is NULL.";
                            } else {
                                msg += ". Owner is " + o.getClass().getSimpleName() + " (" + o + ")";
                            }
                        }
                    }
                    Tracker.handleEvent(this, msg, Tracker.States.Running);
                    Logger.logError(new RuntimeException("Unable to get associated View object"));
                }

                // If the view is still present, make sure it is accurate.
                if (view != null) {
                    String newVal = field.format(s);
                    // Despite assurances otherwise, getText() apparently returns null sometimes
                    String oldVal = view.getText() == null ? null : view.getText().toString().trim();
                    if (newVal == null && oldVal == null) {
                        return;
                    }
                    if (newVal != null && newVal.equals(oldVal)) {
                        return;
                    }
                    view.setText(newVal);
                }
            } finally {
                mIsSetting = false;
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            TextView view = (TextView) field.getView();
            values.putString(field.column, field.extract(view.getText().toString()).trim());
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            try {
                TextView view = (TextView) field.getView();
                if (view == null) {
                    throw new RuntimeException("No view for field " + field.column);
                }
                if (view.getText() == null) {
                    throw new RuntimeException("Text is NULL for field " + field.column);
                }
                dataManager.putString(field.column, field.extract(view.getText().toString()).trim());
            } catch (Exception e) {
                throw new RuntimeException("Unable to save data", e);
            }
        }

        public Object get(@NonNull final Field field) {
            return field.extract(((TextView) field.getView()).getText().toString().trim());
        }
    }

    /**
     * CheckBox accessor. Attempt to convert data to/from a boolean.
     *
     * @author Philip Warner
     */
    static public class CheckBoxAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle bundle) {
            set(field, bundle.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager dataManager) {
            set(field, dataManager.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String value) {
            CheckBox cb = (CheckBox) field.getView();
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

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            CheckBox cb = (CheckBox) field.getView();
            if (field.formatter != null) {
                values.putString(field.column, field.extract(cb.isChecked() ? "1" : "0"));
            } else {
                values.putBoolean(field.column, cb.isChecked());
            }
        }

        @Override
        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            CheckBox v = (CheckBox) field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.extract(v.isChecked() ? "1" : "0"));
            } else {
                dataManager.putBoolean(field.column, v.isChecked());
            }
        }

        public Object get(@NonNull final Field field) {
            if (field.formatter != null) {
                return field.formatter.extract(field, (((CheckBox) field.getView()).isChecked() ? "1" : "0"));
            } else {
                return ((CheckBox) field.getView()).isChecked() ? 1 : 0;
            }
        }
    }

    /**
     * RatingBar accessor. Attempt to convert data to/from a Float.
     *
     * @author Philip Warner
     */
    static public class RatingBarAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            RatingBar ratingBar = (RatingBar) field.getView();
            if (field.formatter != null) {
                ratingBar.setRating(Float.parseFloat(field.formatter.format(field, cursor.getString(cursor.getColumnIndex(field.column)))));
            } else {
                ratingBar.setRating(cursor.getFloat(cursor.getColumnIndex(field.column)));
            }
        }

        public void set(@NonNull final Field field, @NonNull final Bundle bundle) {
            set(field, bundle.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager dataManager) {
            set(field, dataManager.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String s) {
            RatingBar ratingBar = (RatingBar) field.getView();
            Float f = 0.0f;
            try {
                f = Float.parseFloat(field.format(s));
            } catch (NumberFormatException ignored) {
            }
            ratingBar.setRating(f);
        }

        public void get(@NonNull final Field field, @NonNull final Bundle bundle) {
            RatingBar ratingBar = (RatingBar) field.getView();
            if (field.formatter != null) {
                bundle.putString(field.column, field.extract("" + ratingBar.getRating()));
            } else {
                bundle.putFloat(field.column, ratingBar.getRating());
            }
        }

        public void get(@NonNull final Field field, @NonNull final DataManager dataManager) {
            RatingBar ratingBar = (RatingBar) field.getView();
            if (field.formatter != null) {
                dataManager.putString(field.column, field.extract("" + ratingBar.getRating()));
            } else {
                dataManager.putFloat(field.column, ratingBar.getRating());
            }
        }

        public Object get(@NonNull final Field field) {
            RatingBar ratingBar = (RatingBar) field.getView();
            return ratingBar.getRating();
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     *
     * @author Philip Warner
     */
    static public class SpinnerAccessor implements FieldDataAccessor {
        public void set(@NonNull final Field field, @NonNull final Cursor cursor) {
            set(field, cursor.getString(cursor.getColumnIndex(field.column)));
        }

        public void set(@NonNull final Field field, @NonNull final Bundle bundle) {
            set(field, bundle.getString(field.column));
        }

        public void set(@NonNull final Field field, @NonNull final DataManager dataManager) {
            set(field, dataManager.getString(field.column));
        }

        public void set(@NonNull final Field field, @Nullable final String value) {
            String s = field.format(value);
            Spinner v = (Spinner) field.getView();
            if (v == null) {
                return;
            }
            for (int i = 0; i < v.getCount(); i++) {
                if (v.getItemAtPosition(i).equals(s)) {
                    v.setSelection(i);
                    return;
                }
            }
        }

        public void get(@NonNull final Field field, @NonNull final Bundle values) {
            String value;
            Spinner view = (Spinner) field.getView();
            if (view == null) {
                value = "";
            } else {
                Object selItem = view.getSelectedItem();
                if (selItem != null) {
                    value = selItem.toString().trim();
                } else {
                    value = "";
                }
            }
            values.putString(field.column, value);
        }

        public void get(@NonNull final Field field, @NonNull final DataManager values) {
            String value;
            Spinner view = (Spinner) field.getView();
            if (view == null) {
                value = "";
            } else {
                Object selItem = view.getSelectedItem();
                if (selItem != null) {
                    value = selItem.toString().trim();
                } else {
                    value = "";
                }
            }
            values.putString(field.column, value);
        }

        public Object get(@NonNull final Field field) {
            String value;
            Spinner view = (Spinner) field.getView();
            if (view == null) {
                value = "";
            } else {
                Object selItem = view.getSelectedItem();
                if (selItem != null) {
                    value = selItem.toString().trim();
                } else {
                    value = "";
                }
            }
            return field.extract(value);
        }
    }

    /**
     * Formatter for date fields. On failure just return the raw string.
     *
     * @author Philip Warner
     */
    static public class DateFieldFormatter implements FieldFormatter {

        /**
         * Display as a human-friendly date
         */
        @Nullable
        public String format(@NonNull final Field f, @Nullable final String source) {
            try {
                java.util.Date d = parseDate(source);
                return DATE_DISPLAY_SDF.format(d);
            } catch (Exception e) {
                return source;
            }
        }

        /**
         * Extract as an SQL date.
         */
        public String extract(@NonNull final Field f, @NonNull final String source) {
            try {
                java.util.Date d = parseDate(source);
                return DATE_SQL_SDF.format(d);
            } catch (Exception e) {
                return source;
            }
        }
    }

    private class ActivityContext implements FieldsContext {
        private final WeakReference<Activity> mActivity;

        ActivityContext(@NonNull final Activity a) {
            mActivity = new WeakReference<>(a);
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

    private class FragmentContext implements FieldsContext {
        private final WeakReference<Fragment> mFragment;

        FragmentContext(@NonNull final Fragment f) {
            mFragment = new WeakReference<>(f);
        }

        @Override
        public Object dbgGetOwnerContext() {
            return mFragment.get();
        }

        @Override
        @Nullable
        public View findViewById(@IdRes final int id) {
            if (mFragment.get() == null) {
                if (BuildConfig.DEBUG) {
                    System.out.println("Fragment is NULL");
                }
                return null;
            }
            final View view = mFragment.get().getView();
            if (view == null) {
                if (BuildConfig.DEBUG) {
                    System.out.println("View is NULL");
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
     * @author Philip Warner
     */
    public class Field {
        /** Field ID */
        @IdRes
        public final int id;
        /** database column name (can be blank) */
        public final String column;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views */
        public final String group;
        /** Validator to use (can be null) */
        @Nullable
        public final FieldValidator validator;
        /** Owning collection */
        final WeakReference<Fields> mFields;
        /** Has the field been set to invisible **/
        public boolean visible;
        /**
         * Flag indicating that even though field has a column name, it should NOT be fetched from a
         * Cursor. This is usually done for synthetic fields needed when saving the data
         */
        public boolean doNoFetch = false;
        /** FieldFormatter to use (can be null) */
        @Nullable
        FieldFormatter formatter;
        /** Accessor to use (automatically defined) */
        private FieldDataAccessor mAccessor = null;

        /** Optional field-specific tag object */
        private Object mTag = null;

        ///** Property used to determine if edits have been made.
        // * 
        // * Set to true in case the view is clicked
        // *
        // * This a good and simple metric to identify if a field was changed despite not being 100% accurate
        // * */ 
        //private boolean mWasClicked = false;

        /**
         * Constructor.
         *
         * @param fields              Parent object
         * @param fieldId             Layout ID
         * @param sourceColumn        Source database column. Can be empty.
         * @param visibilityGroupName Visibility group. Can be blank.
         * @param fieldValidator      Validator. Can be null.
         * @param fieldFormatter      Formatter. Can be null.
         */
        Field(@NonNull final Fields fields,
              @IdRes final int fieldId,
              @NonNull final String sourceColumn,
              @NonNull final String visibilityGroupName,
              @Nullable final FieldValidator fieldValidator,
              @Nullable final FieldFormatter fieldFormatter) {
            mFields = new WeakReference<>(fields);
            id = fieldId;
            column = sourceColumn;
            group = visibilityGroupName;
            formatter = fieldFormatter;
            validator = fieldValidator;

            /*
             * Load the layout from the passed Activity based on the ID and set visibility and accessor.
             */
            FieldsContext context = fields.getContext();
            if (context == null) {
                return;
            }

            // Lookup the view
            final View view = context.findViewById(this.id);

            // Set the appropriate accessor
            if (view == null) {
                mAccessor = new StringDataAccessor();
            } else {
                if (view instanceof Spinner) {
                    mAccessor = new SpinnerAccessor();
                } else if (view instanceof CheckBox) {
                    mAccessor = new CheckBoxAccessor();
                    addTouchSignalsDirty(view);
                } else if (view instanceof EditText) {
                    mAccessor = new EditTextAccessor();
                    EditText et = (EditText) view;
                    et.addTextChangedListener(
                            new TextWatcher() {
                                @Override
                                public void afterTextChanged(Editable arg0) {
                                    Field.this.setValue(arg0.toString());
                                }

                                @Override
                                public void beforeTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }

                                @Override
                                public void onTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
                                }
                            }
                    );

                } else if (view instanceof Button) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof TextView) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof ImageView) {
                    mAccessor = new TextViewAccessor();
                } else if (view instanceof RatingBar) {
                    mAccessor = new RatingBarAccessor();
                    addTouchSignalsDirty(view);
                } else {
                    throw new IllegalArgumentException();
                }
                visible = fields.getPreferences().getBoolean(FieldVisibilityActivity.TAG + group, true);
                if (!visible) {
                    view.setVisibility(View.GONE);
                }
            }
        }

        /**
         * If a text field, set the TextViewAccessor to support HTML.
         * Call this before loading the field.
         */
        @NonNull
        public Field setShowHtml(final boolean showHtml) {
            if (mAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mAccessor).setShowHtml(showHtml);
            }
            return this;
        }

        /**
         * Reset one fields visibility based on user preferences
         */
        private void resetVisibility(@Nullable final FieldsContext context) {
            if (context == null) {
                return;
            }
            // Lookup the view
            final View view = context.findViewById(this.id);
            if (view != null) {
                visible = BCPreferences.getBoolean(FieldVisibilityActivity.TAG + group, true);
                view.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        private void addTouchSignalsDirty(@NonNull final View view) {
            // Touching this is considered a change
            //TODO We need to introduce a better way to handle this.
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
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
            if (mFields == null) {
                return null;
            } else {
                return mFields.get();
            }
        }

        /**
         * Get the view associated with this Field, if available.
         *
         * @return Resulting View, or null.
         */
        @Nullable
        View getView() {
            Fields fs = mFields.get();
            if (fs == null) {
                if (BuildConfig.DEBUG) {
                    System.out.println("Fields is NULL");
                }
                return null;
            }
            FieldsContext c = fs.getContext();
            if (c == null) {
                if (BuildConfig.DEBUG) {
                    System.out.println("Context is NULL");
                }
                return null;
            }
            return c.findViewById(this.id);
        }

        /**
         * Return the current value of the tag field.
         *
         * @return Current value of tag.
         */
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
        public Object getValue() {
            return mAccessor.get(this);
        }

        /**
         * Set the value to the passed string value.
         *
         * @param s New value
         */
        public void setValue(@NonNull final String s) {
            mAccessor.set(this, s);
            if (mAfterFieldChangeListener != null) {
                mAfterFieldChangeListener.afterFieldChange(this, s);
            }
        }

        /**
         * Get the current value of this field and put into the Bundle collection.
         **/
        public void getValue(@NonNull final Bundle values) {
            mAccessor.get(this, values);
        }

        /**
         * Get the current value of this field and put into the Bundle collection.
         **/
        public void getValue(@NonNull final DataManager data) {
            mAccessor.get(this, data);
        }

        /**
         * Utility function to call the formatters format() method if present, or just return the raw value.
         *
         * @param s String to format
         *
         * @return Formatted value
         */
        @Nullable
        public String format(@Nullable final String s) {
            if (formatter == null) {
                return s;
            }
            return formatter.format(this, s);
        }

        /**
         * Utility function to call the formatters extract() method if present, or just return the raw value.
         */
        public String extract(@NonNull final String s) {
            if (formatter == null) {
                return s;
            }
            return formatter.extract(this, s);
        }

        /**
         * Set the value of this field from the passed cursor. Useful for getting access to
         * raw data values from the database.
         */
        public void set(@NonNull final Cursor cursor) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, cursor);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        /**
         * Set the value of this field from the passed Bundle. Useful for getting access to
         * raw data values from a saved data bundle.
         */
        public void set(@NonNull final Bundle bundle) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, bundle);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        /**
         * Set the value of this field from the passed Bundle. Useful for getting access to
         * raw data values from a saved data bundle.
         */
        public void set(@NonNull final DataManager dataManager) {
            if (!column.isEmpty() && !doNoFetch) {
                try {
                    mAccessor.set(this, dataManager);
                } catch (android.database.CursorIndexOutOfBoundsException e) {
                    throw new DBExceptions.ColumnNotPresent(column, e);
                }
            }
        }

        //public boolean isEdited(){
        //	return mWasClicked;
        //}
    }

    ///**
    // * Check if any field has been modified
    // * 
    // * @return	true if a field has been edited (or clicked)
    // */
    //public boolean isEdited(){
    //
    //	for (Field field : this){
    //		if (field.isEdited()){
    //			return true;
    //		}
    //	}
    //
    //	return false;
    //}
}

