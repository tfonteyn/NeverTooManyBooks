/*
 * @copyright 2013 Philip Warner
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

import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.BlankValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.DataCrossValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.DataValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.FloatValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.IntegerValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.NonBlankValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.OrValidator;
import com.eleybourn.bookcatalogue.datamanager.datavalidators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

/**
 * Class to manage a version of a set of related data.
 *
 * @author pjw
 */
public class DataManager {

    // Generic validators; if field-specific defaults are needed, create a new one.
    protected static final DataValidator INTEGER_VALIDATOR = new IntegerValidator("0");
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    protected static final DataValidator BLANK_OR_INTEGER_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new IntegerValidator("0"));
    protected static final DataValidator BLANK_OR_FLOAT_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new FloatValidator("0.00"));

    // DataValidator blankOrDateValidator =
    //     new OrValidator(new BlankValidator(), new DateValidator());

    /** Raw data storage. */
    protected final Bundle mBundle = new Bundle();
    /** Storage for the data-related code. */
    private final DatumHash mData = new DatumHash();

    /** The last validator exception caught by this object. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<DataCrossValidator> mCrossValidators = new ArrayList<>();

    /**
     * Erase everything in this instance.
     */
    public void clear() {
        mBundle.clear();
        mData.clear();
        mValidationExceptions.clear();
        mCrossValidators.clear();
    }

    /**
     * Add a validator for the specified {@link Datum}.
     *
     * @param key       Key to the {@link Datum}
     * @param validator Validator
     *
     * @return the DataManager, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected DataManager addValidator(@NonNull final String key,
                                       @NonNull final DataValidator validator) {
        mData.get(key).addValidator(validator);
        return this;
    }

    /**
     * Add an {@link DataAccessor} for the specified {@link Datum}.
     *
     * @param key      Key to the {@link Datum}
     * @param accessor Accessor
     *
     * @return the DataManager, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected DataManager addAccessor(@NonNull final String key,
                                      @NonNull final DataAccessor accessor) {
        mData.get(key).addAccessor(accessor);
        return this;
    }

    /**
     * Get the data object specified by the passed key.
     *
     * @param key Key of data object
     *
     * @return Data object
     */
    @Nullable
    public Object get(@NonNull final String key) {
        return get(mData.get(key));
    }

    /**
     * Get the data object specified by the passed {@link Datum}.
     *
     * @return Data object
     */
    @Nullable
    public Object get(@NonNull final Datum datum) {
        return datum.get(this, mBundle);
    }

    /** Retrieve a boolean value. */
    public boolean getBoolean(@NonNull final String key) {
        return mData.get(key).getBoolean(this, mBundle);
    }

    /** Store a boolean value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putBoolean(@NonNull final String key,
                                  final boolean value) {
        mData.get(key).putBoolean(this, mBundle, value);
        return this;
    }

    /** Store a boolean value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putBoolean(@NonNull final Datum datum,
                                  final boolean value) {
        datum.putBoolean(this, mBundle, value);
        return this;
    }

    /** Get a double value. */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public double getDouble(@NonNull final String key) {
        return mData.get(key).getDouble(this, mBundle);
    }

    /** Store a double value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putDouble(@NonNull final String key,
                                 final double value) {
        mData.get(key).putDouble(this, mBundle, value);
        return this;
    }

    /** Store a double value. */
    @SuppressWarnings("unused")
    @NonNull
    public DataManager putDouble(@NonNull final Datum datum,
                                 final double value) {
        datum.putDouble(this, mBundle, value);
        return this;
    }

    /** Get a float value. */
    @SuppressWarnings("unused")
    public float getFloat(@NonNull final String key) {
        return mData.get(key).getFloat(this, mBundle);
    }

    /** Store a float value. */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public DataManager putFloat(@NonNull final String key,
                                final float value) {
        mData.get(key).putFloat(this, mBundle, value);
        return this;
    }

    /** Store a float value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putFloat(@NonNull final Datum datum,
                                final float value) {
        datum.putFloat(this, mBundle, value);
        return this;
    }

    /** Get an int value. */
    public int getInt(@NonNull final String key) {
        return mData.get(key).getInt(this, mBundle);
    }

    /** Store an int value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putInt(@NonNull final String key,
                              final int value) {
        mData.get(key).putInt(this, mBundle, value);
        return this;
    }

    /** Store an int value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putInt(@NonNull final Datum datum,
                              final int value) {
        datum.putInt(this, mBundle, value);
        return this;
    }

    /** Get a long value. */
    public long getLong(@NonNull final String key) {
        return mData.get(key).getLong(this, mBundle);
    }

    /** Store a long value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putLong(@NonNull final String key,
                               final long value) {
        mData.get(key).putLong(this, mBundle, value);
        return this;
    }

    /** Store a long value. */
    @NonNull
    public DataManager putLong(@NonNull final Datum datum,
                               final long value) {
        datum.putLong(this, mBundle, value);
        return this;
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never null
     */
    @NonNull
    public String getString(@NonNull final String key) {
        return mData.get(key).getString(this, mBundle);
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never null
     */
    @NonNull
    public String getString(@NonNull final Datum datum) {
        return datum.getString(this, mBundle);
    }

    /** Store a String value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putString(@NonNull final String key,
                                 @NonNull final String value) {
        mData.get(key).putString(this, mBundle, value);
        return this;
    }

    /** Store a String value. */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putString(@NonNull final Datum datum,
                                 @NonNull final String value) {
        datum.putString(this, mBundle, value);
        return this;
    }

    /**
     * Store all passed values in our collection.
     * We do the laborious method here to allow Accessors to do their thing.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected DataManager putAll(@NonNull final Bundle src) {
        for (String key : src.keySet()) {
            Object value = src.get(key);
            if (value instanceof String) {
                putString(key, (String) value);
            } else if (value instanceof Integer) {
                putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                putLong(key, (Long) value);
            } else if (value instanceof Double) {
                putDouble(key, (Double) value);
            } else if (value instanceof Float) {
                putFloat(key, (Float) value);
            } else if (value instanceof Boolean) {
                putBoolean(key, (Boolean) value);
            } else if (value instanceof ArrayList) {
                this.putParcelableArrayList(key, (ArrayList) value);
            } else if (value instanceof Serializable) {
                this.putSerializable(key, (Serializable) value);
            } else {
                // THIS IS NOT IDEAL! Keep checking the log if we ever get here.
                Logger.debug("key=`" + key + "`, value=" + value);
                if (value != null) {
                    putString(key, value.toString());
                }
            }
        }
        return this;
    }

    /**
     * Store all passed values in our collection.
     * We do the laborious method here to allow Accessors to do their thing.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected DataManager putAll(@NonNull final Cursor cursor) {
        cursor.moveToFirst();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            final String name = cursor.getColumnName(i);
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_STRING:
                    putString(name, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    putLong(name, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    putDouble(name, cursor.getDouble(i));
                    break;

                case Cursor.FIELD_TYPE_NULL:
                    // no action for nulls.
                    break;

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(name, cursor.getBlob(i));
                    break;

                default:
                    throw new RTE.IllegalTypeException("" + cursor.getType(i));
            }
        }

        return this;
    }

    /**
     * Get the serializable object from the collection.
     * We currently do not use a {@link Datum} for special access.
     *
     * @param key Key of object
     *
     * @return The data
     */
    @SuppressWarnings("unused")
    @Nullable
    protected <T extends Serializable> T getSerializable(@NonNull final String key) {
        return mData.get(key).getSerializable(this, mBundle);
    }

    /**
     * Set the serializable object in the collection.
     * We currently do not use a {@link Datum} for special access.
     *
     * @param key   Key of object
     * @param value The serializable object
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public DataManager putSerializable(@NonNull final String key,
                                       @NonNull final Serializable value) {
        if (BuildConfig.DEBUG) {
            Logger.debug("putSerializable, key=" + key
                                 + ", type=" + value.getClass().getCanonicalName());
        }
        mData.get(key).putSerializable(mBundle, value);
        return this;
    }

    /**
     * Get the Parcelable ArrayList from the collection.
     *
     * @param key Key of object
     *
     * @return The Parcelable ArrayList
     */
    @NonNull
    protected <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        return mData.get(key).getParcelableArrayList(this, mBundle);
    }

    /**
     * Set the Parcelable ArrayList in the collection.
     *
     * @param key   Key of object
     * @param value The Parcelable ArrayList
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public <T extends Parcelable> DataManager putParcelableArrayList(@NonNull final String key,
                                                                     @NonNull final ArrayList<T> value) {
        mData.get(key).putParcelableArrayList(this, mBundle, value);
        return this;
    }

    /**
     * Get the ArrayList<String> from the collection.
     *
     * @param key Key of object
     *
     * @return The ArrayList<String>
     */
    @NonNull
    public ArrayList<String> getStringArrayList(@NonNull final String key) {
        return mData.get(key).getStringArrayList(this, mBundle);
    }

    /**
     * Set the ArrayList<String> in the collection.
     *
     * @param key   Key of object
     * @param value the ArrayList<String>
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public DataManager putStringArrayList(@NonNull final String key,
                                          @NonNull final ArrayList<String> value) {
        mData.get(key).putStringArrayList(this, mBundle, value);
        return this;
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @return <tt>true</tt> if all validation passed.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean validate() {

        boolean isOk = true;
        mValidationExceptions.clear();

        // First, just validate individual fields with the cross-val flag set false
        if (!validate(false)) {
            isOk = false;
        }

        // Now re-run with cross-val set to true.
        if (!validate(true)) {
            isOk = false;
        }

        // Finally run the local cross-validation
        for (DataCrossValidator v : mCrossValidators) {
            try {
                v.validate(this);
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
     * @param crossValidating Options indicating if this is a cross validation pass.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(final boolean crossValidating) {
        boolean isOk = true;
        DataValidator validator;

        for (Datum datum : mData.values()) {
            validator = datum.getValidator();
            if (validator != null) {
                try {
                    validator.validate(this, datum, crossValidating);
                } catch (ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                }
            }
        }
        return isOk;
    }

    /**
     * Check if the underlying data contains the specified key.
     */
    public boolean containsKey(@NonNull final String key) {
        Datum datum = mData.get(key);
        if (datum.getAccessor() == null) {
            return mBundle.containsKey(key);
        } else {
            return datum.getAccessor().isPresent(this, datum, mBundle);
        }
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data to remove.
     *
     * @return the old {@link Datum}
     */
    @SuppressWarnings("UnusedReturnValue")
    public Datum remove(@NonNull final String key) {
        Datum datum = mData.remove(key);
        mBundle.remove(key);
        return datum;
    }

    /**
     * @return the current set of data.
     */
    @NonNull
    public Set<String> keySet() {
        return mData.keySet();
    }

    /**
     * Retrieve the text message associated with the validation exceptions (if any).
     *
     * @param res The resource manager to use when looking up strings.
     *
     * @return a user displayable list of error messages, or null if none
     */
    @SuppressWarnings("unused")
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Resources res) {
        if (mValidationExceptions.size() == 0) {
            return null;
        } else {
            StringBuilder message = new StringBuilder();
            int cnt = 0;
            for (ValidatorException e : mValidationExceptions) {
                message.append(" (").append(++cnt).append(") ")
                       .append(e.getFormattedMessage(res))
                       .append('\n');
            }
            return message.toString();
        }
    }

    /**
     * Format the passed bundle in a way that is convenient for display.
     *
     * @return Formatted string
     */
    @NonNull
    @Override
    public String toString() {
        return Datum.toString(mBundle);
    }

    /**
     * Class to manage the collection of {@link Datum} objects for this DataManager.
     *
     * @author pjw
     */
    private static class DatumHash
            extends Hashtable<String, Datum> {

        private static final long serialVersionUID = -650159534364183779L;

        /**
         * Get the specified {@link Datum}, and create a new one if not present.
         */
        @Override
        @NonNull
        public Datum get(@NonNull final Object key) {
            Datum datum = super.get(key);
            if (datum == null) {
                datum = new Datum(key.toString(), true);
                this.put(key.toString(), datum);
            }
            return datum;
        }
    }
}
