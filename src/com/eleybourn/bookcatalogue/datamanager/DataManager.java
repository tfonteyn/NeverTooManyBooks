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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.datamanager.accessors.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.validators.BlankValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.DataCrossValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.DataValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.FloatValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.IntegerValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.NonBlankValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.OrValidator;
import com.eleybourn.bookcatalogue.datamanager.validators.ValidatorException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;

/**
 * Class to manage a version of a set of related data.
 *
 * @author pjw
 */
public class DataManager {

    // Generic validators; if field-specific defaults are needed, create a new one.
    /** re-usable validator. */
    protected static final DataValidator INTEGER_VALIDATOR = new IntegerValidator(0);
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_INTEGER_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new IntegerValidator(0));
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_FLOAT_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new FloatValidator(0f));

    // DataValidator blankOrDateValidator =
    //     new OrValidator(new BlankValidator(), new DateValidator());

    /** Raw data storage. */
    private final Bundle mRawData = new Bundle();

    /** Storage for the {@link Datum} objects; the data-related code. */
    private final DatumMap mDatumMap = new DatumMap();

    /** The last validator exception caught by this object. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<DataCrossValidator> mCrossValidators = new ArrayList<>();


    /**
     * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
     *
     * @return the underlying raw data.
     */
    @NonNull
    public Bundle getRawData() {
        return mRawData;
    }

    /**
     * Erase everything in this instance.
     */
    public void clear() {
        mRawData.clear();
        mDatumMap.clear();
        mValidationExceptions.clear();
        mCrossValidators.clear();
    }

    /**
     * Add a validator for the specified {@link Datum}.
     *
     * @param datumKey  Key to the {@link Datum}
     * @param validator Validator
     */
    protected void addValidator(@NonNull final String datumKey,
                                @NonNull final DataValidator validator) {
        mDatumMap.get(datumKey).setValidator(validator);
    }

    /**
     * Add an {@link DataAccessor} for the specified key.
     * <p>
     * It's up to the Accessor to handle the actual key into the rawData.
     *
     * @param datumKey Key to the {@link Datum}
     * @param accessor Accessor
     */
    protected void addAccessor(@NonNull final String datumKey,
                               @NonNull final DataAccessor accessor) {
        mDatumMap.get(datumKey).setAccessor(accessor);
    }

    /**
     * Store all passed values in our collection.
     * We do the laborious method here to allow Accessors to do their thing.
     *
     * @param src bundle to copy from
     */
    protected void putAll(@NonNull final Bundle src) {
        for (String key : src.keySet()) {
            Object value = src.get(key);
            if (value instanceof String) {
                putString(key, (String) value);

            } else if (value instanceof Integer) {
                //TOMF: use putLong?
                putInt(key, (Integer) value);

            } else if (value instanceof Long) {
                putLong(key, (Long) value);

            } else if (value instanceof Double) {
                putDouble(key, (Double) value);

            } else if (value instanceof Float) {
                //TOMF: use putDouble?
                putFloat(key, (Float) value);

            } else if (value instanceof Boolean) {
                putBoolean(key, (Boolean) value);

            } else if ((value instanceof ArrayList) && ((ArrayList) value).get(
                    0) instanceof Parcelable) {
                //noinspection unchecked
                putParcelableArrayList(key, (ArrayList<Parcelable>) value);

            } else if (value instanceof Serializable) {
                putSerializable(key, (Serializable) value);

            } else {
                // THIS IS NOT IDEAL! Keep checking the log if we ever get here.
                Logger.debugWithStackTrace(this, "putAll", "key=`" + key + "`, value=" + value);
                if (value != null) {
                    putString(key, value.toString());
                }
            }
        }
    }

    /**
     * Store all passed values in our collection.
     * We do the laborious method here to allow Accessors to do their thing.
     * <p>
     * See the comments on methods in {@link android.database.CursorWindow}
     * for info on type conversions which explains our use of getLong/getDouble.
     * <p>
     * Reminder:
     * - booleans -> long (0,1)
     * - int -> long
     * - float -> double
     * - date -> string
     *
     * @param cursor to read from
     */
    protected void putAll(@NonNull final Cursor cursor) {
        cursor.moveToFirst();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            final String name = cursor.getColumnName(i);
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_STRING:
                    putString(name, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    // a null becomes 0
                    putLong(name, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    // a null becomes 0.0
                    putDouble(name, cursor.getDouble(i));
                    break;

                case Cursor.FIELD_TYPE_NULL:
                    // discard any fields with null values.
                    break;

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(name, cursor.getBlob(i));
                    break;

                default:
                    throw new IllegalTypeException("" + cursor.getType(i));
            }
        }

    }

    /**
     * Get the data object specified by the passed key.
     *
     * @param key Key of data object
     *
     * @return Data object, or {@code null} when not present
     */
    @Nullable
    public Object get(@NonNull final String key) {
        return get(mDatumMap.get(key));
    }

    /**
     * Get the data object specified by the passed {@link Datum}.
     *
     * @return Data object, or {@code null} when not present
     */
    @Nullable
    public Object get(@NonNull final Datum datum) {
        return datum.get(this, mRawData);
    }

    /**
     * @return a boolean value.
     */
    public boolean getBoolean(@NonNull final String key) {
        return mDatumMap.get(key).getBoolean(this, mRawData);
    }

    /**
     * Store a boolean value.
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        // use the key to retrieve the Datum, then pass it the value for storage.
        mDatumMap.get(key).putBoolean(this, mRawData, value);
    }

    /**
     * Store a boolean value. Mainly (only?) used by {@link DataValidator} classes.
     */
    public void putBoolean(@NonNull final Datum datum,
                           final boolean value) {
        datum.putBoolean(this, mRawData, value);
    }

    /**
     * @return a double value.
     */
    @SuppressWarnings("WeakerAccess")
    public double getDouble(@NonNull final String key) {
        return mDatumMap.get(key).getDouble(this, mRawData);
    }

    /**
     * Store a double value.
     */
    @SuppressWarnings("WeakerAccess")
    public void putDouble(@NonNull final String key,
                          final double value) {
        mDatumMap.get(key).putDouble(this, mRawData, value);
    }

    /**
     * Store a double value.
     */
    @SuppressWarnings("unused")
    public void putDouble(@NonNull final Datum datum,
                          final double value) {
        datum.putDouble(this, mRawData, value);
    }

    /** @return a float value. */
    @SuppressWarnings("unused")
    public float getFloat(@NonNull final String key) {
        return mDatumMap.get(key).getFloat(this, mRawData);
    }

    /**
     * Store a float value.
     */
    @SuppressWarnings("WeakerAccess")
    public void putFloat(@NonNull final String key,
                         final float value) {
        mDatumMap.get(key).putFloat(this, mRawData, value);
    }

    /**
     * Store a float value.
     */
    public void putFloat(@NonNull final Datum datum,
                         final float value) {
        datum.putFloat(this, mRawData, value);
    }

    /** @return an int value. */
    public int getInt(@NonNull final String key) {
        return mDatumMap.get(key).getInt(this, mRawData);
    }

    /**
     * Store an int value.
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        mDatumMap.get(key).putInt(this, mRawData, value);
    }

    /**
     * Store an int value.
     */
    public void putInt(@NonNull final Datum datum,
                       final int value) {
        datum.putInt(this, mRawData, value);
    }

    /** @return a long value. */
    public long getLong(@NonNull final String key) {
        return mDatumMap.get(key).getLong(this, mRawData);
    }

    /**
     * Store a long value.
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        mDatumMap.get(key).putLong(this, mRawData, value);
    }

    /**
     * Store a long value.
     */
    public void putLong(@NonNull final Datum datum,
                        final long value) {
        datum.putLong(this, mRawData, value);
    }

    /**
     * Note: a bitmask is read/written to the database as a long.
     *
     * @param bitmask one or more bits to test for being set
     *
     * @return {@code true} if the bit(s) was set.
     */
    public boolean isBitSet(@NonNull final String key,
                            final int bitmask) {
        return (mDatumMap.get(key).getLong(this, mRawData) & bitmask) != 0;
    }

    /**
     * Note: a bitmask is read/written to the database as a long.
     *
     * @param bitmask one or more bits to set/reset.
     */
    public void setBit(@NonNull final String key,
                       final int bitmask,
                       final boolean set) {
        long value = mDatumMap.get(key).getLong(this, mRawData);

        if (set) {
            // set the bit
            value |= bitmask;
        } else {
            // or reset the bit
            value &= ~bitmask;
        }

        mDatumMap.get(key).putLong(this, mRawData, value);
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final String key) {
        return mDatumMap.get(key).getString(this, mRawData);
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final Datum datum) {
        return datum.getString(this, mRawData);
    }

    /**
     * Store a String value.
     */
    public void putString(@NonNull final String key,
                          @NonNull final String value) {
        mDatumMap.get(key).putString(this, mRawData, value);
    }

    /**
     * Store a String value.
     */
    public void putString(@NonNull final Datum datum,
                          @NonNull final String value) {
        datum.putString(this, mRawData, value);
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
        return mDatumMap.get(key).getStringArrayList(this, mRawData);
    }

    /**
     * Set the ArrayList<String> in the collection.
     *
     * @param key   Key of object
     * @param value the ArrayList<String>
     */
    public void putStringArrayList(@NonNull final String key,
                                   @NonNull final ArrayList<String> value) {
        mDatumMap.get(key).putStringArrayList(this, mRawData, value);
    }

    /**
     * Get the Parcelable ArrayList from the collection.
     *
     * @param key Key of object
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        return mDatumMap.get(key).getParcelableArrayList(this, mRawData);
    }

    /**
     * Set the Parcelable ArrayList in the collection.
     *
     * @param key   Key of object
     * @param value The Parcelable ArrayList
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final String key,
                                                              @NonNull final ArrayList<T> value) {
        mDatumMap.get(key).putParcelableArrayList(this, mRawData, value);
    }

    /**
     * Get the serializable object from the collection.
     * We currently do not use a {@link Datum} for special access.
     *
     * @param key Key of object
     *
     * @return The data
     */
    @Nullable
    @SuppressWarnings("unused")
    protected <T extends Serializable> T getSerializable(@NonNull final String key) {
        return mDatumMap.get(key).getSerializable(this, mRawData);
    }

    /**
     * Set the serializable object in the collection.
     * We currently do not use a {@link Datum} for special access.
     *
     * @param key   Key of object
     * @param value The serializable object
     */
    @SuppressWarnings("WeakerAccess")
    public void putSerializable(@NonNull final String key,
                                @NonNull final Serializable value) {
        if (BuildConfig.DEBUG /* always */) {
            Logger.debugWithStackTrace(this, "putSerializable",
                                       "key=" + key,
                                       "type=" + value.getClass().getCanonicalName());
        }
        mDatumMap.get(key).putSerializable(mRawData, value);
    }

    /**
     * Loop through and apply validators, generating a Bundle collection as a by-product.
     * The Bundle collection is then used in cross-validation as a second pass, and finally
     * passed to each defined cross-validator.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     *
     * @return {@code true} if all validation passed.
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
     *
     * @return {@code true} if all validations passed.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(final boolean crossValidating) {
        boolean isOk = true;
        DataValidator validator;

        for (Datum datum : mDatumMap.values()) {
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
     * @param key Key of object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean containsKey(@NonNull final String key) {
        Datum datum = mDatumMap.get(key);
        if (datum.getAccessor() == null) {
            return mRawData.containsKey(key);
        } else {
            return datum.getAccessor().isPresent(mRawData);
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
        Datum datum = mDatumMap.remove(key);
        mRawData.remove(key);
        return datum;
    }

    /**
     * @return the current set of data.
     */
    @NonNull
    public Set<String> keySet() {
        return mDatumMap.keySet();
    }

    /**
     * Retrieve the text message associated with the validation exceptions (if any).
     *
     * @param res The resource manager to use when looking up strings.
     *
     * @return a user displayable list of error messages, or {@code null} if none present
     */
    @SuppressWarnings("unused")
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Resources res) {
        if (mValidationExceptions.isEmpty()) {
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

    @Override
    @NonNull
    public String toString() {
        return "DataManager{" +
                "mRawData=" + mRawData +
                ", mDatumMap=" + mDatumMap +
                ", mValidationExceptions=" + mValidationExceptions +
                ", mCrossValidators=" + mCrossValidators +
                '}';
    }

    /**
     * Class to manage the collection of {@link Datum} objects for this DataManager.
     *
     * @author pjw
     */
    private static class DatumMap
            extends HashMap<String, Datum> {

        private static final long serialVersionUID = 455375570133391482L;

        /**
         * Get the specified {@link Datum}, and create a new one if not present.
         *
         * @param key for the datum to get
         *
         * @return the datum
         */
        @NonNull
        Datum get(@NonNull final String key) {
            Datum datum = super.get(key);
            if (datum == null) {
                datum = new Datum(key, true);
                put(key, datum);
            }
            return datum;
        }
    }
}
