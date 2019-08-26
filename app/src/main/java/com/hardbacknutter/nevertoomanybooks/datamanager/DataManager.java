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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
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
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

/**
 * Class to manage a version of a set of related data.
 * <ul>
 *     <li>mRawData: stores the actual data</li>
 *     <li>mDataAccessorsMap: accessors which can 'translate' data</li>
 *     <li>mValidatorsMap: validators applied at 'save' time</li>
 *     <li>mCrossValidators: cross-validators applied at 'save' time</li>
 *     <li></li>
 * </ul>
 */
public class DataManager {

    /** re-usable validator. */
    protected static final DataValidator INTEGER_VALIDATOR = new IntegerValidator(0);
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_FLOAT_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new FloatValidator(0f));

    /** log error string. */
    private static final String ERROR_INVALID_BOOLEAN_S = "Invalid boolean, s=`";

    /** DataValidators. */
    private final Map<String, DataValidator> mValidatorsMap = new UniqueMap<>();
    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<DataCrossValidator> mCrossValidators = new ArrayList<>();
    /** The last validator exception caught by this object. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();

    /** Raw data storage. */
    private final Bundle mRawData = new Bundle();
    /** DataAccessors. */
    private final Map<String, DataAccessor> mDataAccessorsMap = new UniqueMap<>();

    /**
     * Translate the passed object to a Long value.
     *
     * @param o Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    public static long toLong(@Nullable final Object o) {
        if (o == null) {
            return 0;
        } else if (o instanceof Long) {
            return (Long) o;
        } else if (o instanceof Integer) {
            return ((Integer) o).longValue();
        } else if (o.toString().trim().isEmpty()) {
            return 0;
        } else {
            try {
                return Long.parseLong(o.toString());
            } catch (@NonNull final NumberFormatException e1) {
                // desperate ?
                return toBoolean(o) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed object to a double value.
     *
     * @param o Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    private static double toDouble(@Nullable final Object o) {
        if (o == null) {
            return 0;
        } else if (o instanceof Double) {
            return (Double) o;
        } else if (o instanceof Float) {
            return ((Float) o).doubleValue();
        } else if (o.toString().trim().isEmpty()) {
            return 0;
        } else {
            try {
                return Double.parseDouble(o.toString());
            } catch (@NonNull final NumberFormatException e1) {
                // desperate ?
                return toBoolean(o) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed object to a float value.
     *
     * @param o Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    private static float toFloat(@Nullable final Object o) {
        if (o == null) {
            return 0;
        } else if (o instanceof Float) {
            return (float) o;
        } else if (o instanceof Double) {
            return ((Double) o).floatValue();
        } else if (o.toString().trim().isEmpty()) {
            return 0;
        } else {
            try {
                return Float.parseFloat(o.toString());
            } catch (@NonNull final NumberFormatException e1) {
                // desperate ?
                return toBoolean(o) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed Object to a boolean value.
     *
     * @param o Object
     *
     * @return Resulting value
     *
     * @throws NumberFormatException if the Object was not boolean compatible.
     */
    public static boolean toBoolean(@Nullable final Object o)
            throws NumberFormatException {
        if (o == null) {
            return false;
        } else if (o instanceof Boolean) {
            return (Boolean) o;
        } else if (o instanceof Integer) {
            return (Integer) o != 0;
        } else if (o instanceof Long) {
            return (Long) o != 0;
        }
        // lets see if its a String then
        return toBoolean(o.toString(), true);
    }

    /**
     * Translate a String to a boolean value.
     *
     * @param s            String to convert
     * @param emptyIsFalse if {@code true}, {@code null} and empty string
     *                     are handled as {@code false}
     *
     * @return boolean value
     *
     * @throws NumberFormatException if the string does not contain a valid boolean.
     */
    public static boolean toBoolean(@Nullable final String s,
                                    final boolean emptyIsFalse)
            throws NumberFormatException {
        if (s == null || s.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new NumberFormatException(ERROR_INVALID_BOOLEAN_S + s + '`');
            }
        } else {
            switch (s.trim().toLowerCase(App.getSystemLocale())) {
                case "1":
                case "y":
                case "yes":
                case "t":
                case "true":
                    return true;
                case "0":
                case "n":
                case "no":
                case "f":
                case "false":
                    return false;
                default:
                    try {
                        return Integer.parseInt(s) != 0;
                    } catch (@NonNull final NumberFormatException e) {
                        Logger.error(DataManager.class, e, ERROR_INVALID_BOOLEAN_S + s + '`');
                        throw e;
                    }
            }
        }
    }

    /**
     * Add a validator for the specified key.
     *
     * @param key       Key for the data
     * @param validator Validator
     */
    protected void addValidator(@NonNull final String key,
                                @NonNull final DataValidator validator) {
        mValidatorsMap.put(key, validator);
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
                putInt(key, (Integer) value);

            } else if (value instanceof Long) {
                putLong(key, (Long) value);

            } else if (value instanceof Double) {
                putDouble(key, (Double) value);

            } else if (value instanceof Float) {
                putFloat(key, (Float) value);

            } else if (value instanceof Boolean) {
                putBoolean(key, (Boolean) value);

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
                    throw new IllegalStateException(String.valueOf(cursor.getType(i)));
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
     * @return a boolean value.
     */
    public boolean getBoolean(@NonNull final String key) {
        return toBoolean(get(key));
    }

    /**
     * Store a boolean value.
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
     * @return a double value.
     */
    public double getDouble(@NonNull final String key) {
        return toDouble(get(key));
    }

    /**
     * Store a double value.
     */
    private void putDouble(@NonNull final String key,
                           final double value) {
        if (mDataAccessorsMap.containsKey(key)) {
            //noinspection ConstantConditions
            mDataAccessorsMap.get(key).put(mRawData, value);
        } else {
            mRawData.putDouble(key, value);
        }
    }

    /**
     * @return a float value.
     */
    float getFloat(@NonNull final String key) {
        return toFloat(get(key));
    }

    /**
     * Store a float value.
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
     * @return an int value.
     */
    public long getInt(@NonNull final String key) {
        return toLong(get(key));
    }

    /**
     * Store an int value.
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
     * @return a long value.
     */
    public long getLong(@NonNull final String key) {
        return toLong(get(key));
    }

    /**
     * Store a long value.
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
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final String key) {
        Object o = get(key);
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Store a String value.
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
     * @param key Key of object
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
     * @param key   Key of object
     * @param value The Parcelable ArrayList
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
     * @param key Key of object
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
     * @param key   Key of object
     * @param value The serializable object
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
     * @param key Key of object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean containsKey(@NonNull final String key) {
        return mDataAccessorsMap.containsKey(key) || mRawData.containsKey(key);
    }

    /**
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
     * @param key Key of data to remove.
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

        for (final Map.Entry<String, DataValidator> entry : mValidatorsMap.entrySet()) {
            try {
                entry.getValue().validate(this, entry.getKey());
            } catch (@NonNull final ValidatorException e) {
                mValidationExceptions.add(e);
                isOk = false;
            }
        }

        for (DataCrossValidator v : mCrossValidators) {
            try {
                v.validate(this);
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
            int cnt = 0;
            for (ValidatorException e : mValidationExceptions) {
                message.append(" (").append(++cnt).append(") ")
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
