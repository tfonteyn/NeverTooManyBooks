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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.datamanager.accessors.DataAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.BlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataCrossValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.FloatValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.IntegerValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.NonBlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.OrValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

/**
 * Class to manage a version of a set of related data.
 * <ul>
 * <li>mRawData: stores the actual data</li>
 * <li>mDataAccessorsMap: accessors which can 'translate' data</li>
 * <li>mValidatorsMap: validators applied at 'save' time</li>
 * <li>mCrossValidators: cross-validators applied at 'save' time</li>
 * <li></li>
 * </ul>
 */
public class DataManager {

    /** re-usable validator. */
    protected static final DataValidator INTEGER_VALIDATOR = new IntegerValidator();
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_FLOAT_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new FloatValidator());

    /** DataValidators. */
    private final Map<String, DataValidator> mValidatorsMap = new UniqueMap<>();
    /** DataValidators. Sake key as mValidatorsMap; value: @StringRes. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Map<String, Integer> mValidatorErrorIdMap = new UniqueMap<>();

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<DataCrossValidator> mCrossValidators = new ArrayList<>();
    /** The last validator exception caught by this object. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();

    /** Raw data storage. */
    private final Bundle mRawData = new Bundle();
    /** DataAccessors. */
    private final Map<String, DataAccessor> mDataAccessorsMap = new UniqueMap<>();

    /**
     * Add a validator for the specified key.
     *
     * @param key          Key for the data
     * @param validator    Validator
     * @param errorLabelId string resource id for a user visible message
     */
    protected void addValidator(@NonNull final String key,
                                @NonNull final DataValidator validator,
                                @StringRes final int errorLabelId) {
        mValidatorsMap.put(key, validator);
        mValidatorErrorIdMap.put(key, errorLabelId);
    }

    /**
     * Add a cross validator.
     *
     * @param validator Validator
     */
    protected void addCrossValidator(@NonNull final DataCrossValidator validator) {
        mCrossValidators.add(validator);
    }

    /**
     * Add an {@link DataAccessor} for the specified key.
     * <p>
     * It's up to the Accessor to handle the actual key into the rawData.
     *
     * @param accessorKey Key to the data
     * @param accessor    Accessor
     */
    protected void addAccessor(@NonNull final String accessorKey,
                               @NonNull final DataAccessor accessor) {
        mDataAccessorsMap.put(accessorKey, accessor);
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
                putInt(key, (int) value);

            } else if (value instanceof Long) {
                putLong(key, (long) value);

            } else if (value instanceof Double) {
                putDouble(key, (double) value);

            } else if (value instanceof Float) {
                putFloat(key, (float) value);

            } else if (value instanceof Boolean) {
                putBoolean(key, (boolean) value);

            } else if ((value instanceof ArrayList)
                       && (!((ArrayList) value).isEmpty())
                       && ((ArrayList) value).get(0) instanceof Parcelable) {
                //noinspection unchecked
                putParcelableArrayList(key, (ArrayList<Parcelable>) value);

            } else if (value instanceof Serializable) {
                putSerializable(key, (Serializable) value);

            } else {
                // THIS IS NOT IDEAL! Keep checking the log if we ever get here.
                Logger.warnWithStackTrace(this, "putAll",
                                          "key=`" + key + '`',
                                          "value=" + value);
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
     * <ul>
     * <li>booleans -> long (0,1)</li>
     * <li>int -> long</li>
     * <li>float -> double</li>
     * <li>date -> string</li>
     * </ul>
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

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(name, cursor.getBlob(i));
                    break;

                case Cursor.FIELD_TYPE_NULL:
                    // discard any fields with null values.
                    break;

                default:
                    throw new UnexpectedValueException(cursor.getType(i));
            }
        }

    }

    /**
     * Store a Object value. The object will be casted to one of the supported types.
     * Does not cover {@link #putParcelableArrayList}
     * or {@link #putSerializable}
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @throws UnexpectedValueException if the type of the Object is not supported.
     */
    public void put(@NonNull final String key,
                    @NonNull final Object value) {
        if (value instanceof String) {
            putString(key, (String) value);
        } else if (value instanceof Boolean) {
            putBoolean(key, (boolean) value);
        } else if (value instanceof Double) {
            putDouble(key, (double) value);
        } else if (value instanceof Float) {
            putFloat(key, (float) value);
        } else if (value instanceof Long) {
            putLong(key, (long) value);
        } else if (value instanceof Integer) {
            putInt(key, (int) value);
        } else {
            throw new UnexpectedValueException(value.getClass().getName());
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
        Object o;
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            o = mDataAccessorsMap.get(key).get(mRawData);
        } else {
            o = mRawData.get(key);
        }
        return o;
    }

    /**
     * Get a boolean value.
     *
     * @param key Key of data object
     *
     * @return a boolean value.
     */
    public boolean getBoolean(@NonNull final String key) {
        return ParseUtils.toBoolean(get(key));
    }

    /**
     * Store a boolean value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putBoolean(key, value);
        }
    }

    /**
     * Get a double value.
     *
     * @param key Key of data object
     *
     * @return a double value.
     */
    public double getDouble(@NonNull final String key) {
        return ParseUtils.toDouble(get(key), null);
    }

    /**
     * Store a double value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putDouble(@NonNull final String key,
                          final double value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putDouble(key, value);
        }
    }

    /**
     * Get a float value.
     *
     * @param key Key of data object
     *
     * @return a float value.
     */
    float getFloat(@NonNull final String key) {
        return ParseUtils.toFloat(get(key), null);
    }

    /**
     * Store a float value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putFloat(@NonNull final String key,
                         final float value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putFloat(key, value);
        }
    }

    /**
     * Store an int value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putInt(key, value);
        }
    }

    /**
     * Get a long value.
     *
     * @param key Key of data object
     *
     * @return a long value.
     */
    public long getLong(@NonNull final String key) {
        return ParseUtils.toLong(get(key));
    }

    /**
     * Store a long value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putLong(key, value);
        }
    }

    /**
     * Get a String value.
     *
     * @param key Key of data object
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final String key) {
        Object o = get(key);
        if (o == null) {
            return "";
        } else {
            return o.toString().trim();
        }
    }

    /**
     * Store a String value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putString(@NonNull final String key,
                          @NonNull final String value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putString(key, value);
        }
    }

    /**
     * Get the Parcelable ArrayList from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        Object o;
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            o = mDataAccessorsMap.get(key).get(mRawData);
        } else {
            o = mRawData.get(key);
        }

        if (o == null) {
            return new ArrayList<>();
        }
        //noinspection unchecked
        return (ArrayList<T>) o;
    }

    /**
     * Set the Parcelable ArrayList in the collection.
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of objects in the list
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final String key,
                                                              @NonNull final ArrayList<T> value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putParcelableArrayList(key, value);
        }
    }

    /**
     * Get the serializable object from the collection.
     * Does not support a {@link DataAccessor}.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The data
     */
    @SuppressWarnings("unused")
    @Nullable
    protected <T extends Serializable> T getSerializable(@NonNull final String key) {
        //noinspection unchecked
        return (T) mRawData.getSerializable(key);
    }

    /**
     * Set the serializable object in the collection.
     * Does not support a {@link DataAccessor}.
     *
     * @param key   Key of data object
     * @param value to store
     */
    private void putSerializable(@NonNull final String key,
                                 @NonNull final Serializable value) {
        if (BuildConfig.DEBUG /* always */) {
            Logger.debugWithStackTrace(this, "putSerializable",
                                       "key=" + key,
                                       "type=" + value.getClass().getCanonicalName());
        }
        mRawData.putSerializable(key, value);
    }

    /**
     * Check if the underlying data contains the specified key.
     *
     * @param key Key of data object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean containsKey(@NonNull final String key) {
        return mDataAccessorsMap.containsKey(key) || mRawData.containsKey(key);
    }

    /**
     * Get all (real and virtual) keys for this data manager.
     *
     * @return the current set of data.
     */
    @NonNull
    public Set<String> keySet() {
        Set<String> allKeys = new HashSet<>(mRawData.keySet());
        // add virtual keys ('real' duplicates are simply overwritten which is fine).
        allKeys.addAll(mDataAccessorsMap.keySet());
        return allKeys;
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data object to remove.
     */
    public void remove(@NonNull final String key) {
        mDataAccessorsMap.remove(key);
        mRawData.remove(key);
    }

    /**
     * Erase everything in this instance.
     */
    public void clear() {
        mRawData.clear();
        mDataAccessorsMap.clear();
        mValidatorsMap.clear();
        mValidatorErrorIdMap.clear();
        mCrossValidators.clear();
        mValidationExceptions.clear();
    }

    /**
     * Loop through and apply validators.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     * Use {@link #getValidationExceptionMessage} for the results.
     *
     * @return {@code true} if all validation passed.
     */
    public boolean validate() {

        boolean isOk = true;
        mValidationExceptions.clear();

        for (Map.Entry<String, DataValidator> entry : mValidatorsMap.entrySet()) {
            try {
                String key = entry.getKey();
                //noinspection ConstantConditions
                entry.getValue().validate(this, key, mValidatorErrorIdMap.get(key));
            } catch (@NonNull final ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }

        for (DataCrossValidator crossValidator : mCrossValidators) {
            try {
                crossValidator.validate(this);
            } catch (@NonNull final ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }
        return isOk;
    }

    /**
     * Retrieve the text message associated with the validation exceptions (if any).
     *
     * @param context Current context
     *
     * @return a user displayable list of error messages, or {@code null} if none present
     */
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Context context) {
        if (mValidationExceptions.isEmpty()) {
            return null;
        } else {
            StringBuilder message = new StringBuilder();
            int i = 0;
            for (ValidatorException e : mValidationExceptions) {
                message.append(" (").append(++i).append(") ")
                       .append(e.getLocalizedMessage(context))
                       .append('\n');
            }
            return message.toString();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "DataManager{"
               + "mRawData=" + mRawData
               + ", mDataAccessorsMap=" + mDataAccessorsMap
               + ", mValidationExceptions=" + mValidationExceptions
               + ", mValidatorsMap=" + mValidatorsMap
               + ", mCrossValidators=" + mCrossValidators
               + '}';
    }
}
