/*
 * @Copyright 2020 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.BlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataCrossValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DoubleValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.LongValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.NonBlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.OrValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

/**
 * Class to manage a version of a set of related data.
 * It's basically an extended Bundle.
 *
 * <ul>
 * <li>mRawData: stores the actual data</li>
 * <li>mValidatorsMap: validators applied at 'save' time</li>
 * <li>mCrossValidators: cross-validators applied at 'save' time</li>
 * </ul>
 */
public class DataManager
        implements RowDataHolder {

    /** re-usable validator. */
    protected static final DataValidator LONG_VALIDATOR = new LongValidator();
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_DOUBLE_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new DoubleValidator());

    /** Log tag. */
    private static final String TAG = "DataManager";
    /** DataValidators. */
    private final Map<String, DataValidator> mValidatorsMap = new UniqueMap<>();
    /** DataValidators. Sake key as mValidatorsMap; value: @StringRes. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Map<String, Integer> mValidatorErrorIdMap = new UniqueMap<>();

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final Collection<DataCrossValidator> mCrossValidators = new ArrayList<>();
    /** The last validator exception caught by this object. */
    private final Collection<ValidatorException> mValidationExceptions = new ArrayList<>();

    /** Raw data storage. */
    private final Bundle mRawData;

    protected DataManager() {
        mRawData = new Bundle();
    }

    @VisibleForTesting
    protected DataManager(@NonNull final Bundle rawData) {
        mRawData = rawData;
    }

    /**
     * Erase everything in this instance.
     */
    public void clear() {
        mRawData.clear();
        mValidatorsMap.clear();
        mValidatorErrorIdMap.clear();
        mCrossValidators.clear();
        mValidationExceptions.clear();
    }

    /**
     * Store all passed values in our collection.
     *
     * @param src bundle to copy from
     *
     * @throws UnexpectedValueException if the type of the Object is not supported.
     */
    public void putAll(@NonNull final Bundle src)
            throws UnexpectedValueException {
        for (String key : src.keySet()) {
            put(key, src.get(key));
        }
    }

    /**
     * Store all passed values in our collection.
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
                    mRawData.putString(name, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    // a null becomes 0
                    mRawData.putLong(name, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    // a null becomes 0.0
                    mRawData.putDouble(name, cursor.getDouble(i));
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
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @throws UnexpectedValueException if the type of the Object is not supported.
     */
    public void put(@NonNull final String key,
                    @Nullable final Object value)
            throws UnexpectedValueException {

        if (value instanceof String) {
            mRawData.putString(key, (String) value);
        } else if (value instanceof Integer) {
            mRawData.putInt(key, (int) value);
        } else if (value instanceof Long) {
            mRawData.putLong(key, (long) value);
        } else if (value instanceof Double) {
            mRawData.putDouble(key, (double) value);
        } else if (value instanceof Float) {
            mRawData.putFloat(key, (float) value);
        } else if (value instanceof Boolean) {
            mRawData.putBoolean(key, (boolean) value);

//        } else if ((value instanceof ArrayList)
//                   && (!((ArrayList) value).isEmpty())
//                   && ((ArrayList) value).get(0) instanceof Parcelable) {
//            //noinspection unchecked
//            putParcelableArrayList(key, (ArrayList<Parcelable>) value);

        } else if ((value instanceof ArrayList)) {
            //noinspection unchecked
            putParcelableArrayList(key, (ArrayList<Parcelable>) value);

        } else if (value instanceof Money) {
            putMoney(key, (Money) value);

        } else if (value instanceof Serializable) {
            putSerializable(key, (Serializable) value);

        } else if (value == null) {
            Logger.w(TAG, "put|key=`" + key + "`|value=<NULL>");
            remove(key);

        } else {
            throw new UnexpectedValueException("put|key=`" + key + "`|value=" + value);
        }
    }

    /**
     * Get the data object specified by the passed key.
     *
     * @param key Key of data object
     *
     * @return Data object, or {@code null} when not present or the value is {@code null}
     */
    @Nullable
    public Object get(@NonNull final String key) {
        if (DBDefinitions.MONEY_KEYS.contains(key)) {
            return getMoney(key);
        }
        return mRawData.get(key);
    }

    /**
     * Check if the underlying data contains the specified key.
     *
     * @param key Key of data object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean contains(@NonNull final String key) {
        return mRawData.containsKey(key);
    }

    /**
     * Get a boolean value.
     *
     * @param key Key of data object
     *
     * @return a boolean value.
     */
    public boolean getBoolean(@NonNull final String key) {
        return ParseUtils.toBoolean(mRawData.get(key));
    }

    /**
     * Store a boolean value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        mRawData.putBoolean(key, value);
    }

    public boolean isBitSet(@NonNull final String key,
                            final int bit) {
        return (ParseUtils.toLong(mRawData.get(key)) & bit) != 0;
    }

    public void setBit(@NonNull final String key,
                       final int bit,
                       final boolean checked) {

        long bits = ParseUtils.toLong(mRawData.get(key));

        if (checked) {
            // set the bit
            bits |= bit;
        } else {
            // or reset the bit
            bits &= ~bit;
        }

        mRawData.putLong(key, bits);
    }

    /**
     * Get a Money value.
     *
     * @param key Key of data object
     *
     * @return a Money value.
     */
    @Nullable
    private Money getMoney(@NonNull final String key) {
        if (mRawData.containsKey(key)) {
            return new Money(getDouble(key),
                             getString(key + DBDefinitions.SUFFIX_KEY_CURRENCY));
        } else {
            return null;
        }
    }

    /**
     * Store a Money value.
     *
     * @param key   Key of data object
     * @param money to store
     */
    private void putMoney(@NonNull final String key,
                          @NonNull final Money money) {
        mRawData.putDouble(key, money.doubleValue());
        if (money.getCurrency() != null) {
            mRawData.putString(key + DBDefinitions.SUFFIX_KEY_CURRENCY, money.getCurrency());
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
        return ParseUtils.toDouble(mRawData.get(key), null);
    }

    /**
     * Store a double value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putDouble(@NonNull final String key,
                          final double value) {
        mRawData.putDouble(key, value);
    }

    /**
     * Get a float value.
     *
     * @param key Key of data object
     *
     * @return a float value.
     */
    public float getFloat(@NonNull final String key) {
        return ParseUtils.toFloat(mRawData.get(key), null);
    }

    /**
     * Store a float value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putFloat(@NonNull final String key,
                         final float value) {
        mRawData.putFloat(key, value);
    }

    /**
     * Get an int value.
     *
     * @param key Key of data object
     *
     * @return an int value; {@code null} or empty becomes 0
     */
    @Override
    public int getInt(@NonNull final String key) {
        return (int) ParseUtils.toLong(mRawData.get(key));
    }

    /**
     * Store an int value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        mRawData.putInt(key, value);
    }

    /**
     * Get a long value.
     *
     * @param key Key of data object
     *
     * @return a long value; {@code null} or empty becomes 0
     */
    @Override
    public long getLong(@NonNull final String key) {
        return ParseUtils.toLong(mRawData.get(key));
    }

    /**
     * Store a long value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        mRawData.putLong(key, value);
    }

    /**
     * Get a String value. Non-String values will be casted using toString().
     *
     * @param key Key of data object
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    @Override
    public String getString(@NonNull final String key) {
        Object o = mRawData.get(key);
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
        mRawData.putString(key, value);
    }

    /**
     * Store a {@code null} value.
     *
     * @param key Key of data object
     */
    protected void putNull(@NonNull final String key) {
        mRawData.putString(key, null);
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
        Object o = mRawData.get(key);
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
        mRawData.putParcelableArrayList(key, value);
    }

    /**
     * Get the serializable object from the collection.
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
     *
     * @param key   Key of data object
     * @param value to store
     */
    private void putSerializable(@NonNull final String key,
                                 @NonNull final Serializable value) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "putSerializable|key=" + key
                       + "|type=" + value.getClass().getCanonicalName(), new Throwable());
        }
        mRawData.putSerializable(key, value);
    }


    /**
     * Get all (real and virtual) keys for this data manager.
     *
     * @return the current set of data.
     */
    @NonNull
    public Set<String> keySet() {
        return new HashSet<>(mRawData.keySet());
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data object to remove.
     */
    public void remove(@NonNull final String key) {
        mRawData.remove(key);
    }

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
     * Loop through and apply validators.
     * <p>
     * {@link ValidatorException} are added to {@link #mValidationExceptions}
     * Use {@link #getValidationExceptionMessage} for the results.
     *
     * @param context Current context
     *
     * @return {@code true} if all validation passed.
     */
    public boolean validate(@NonNull final Context context) {

        boolean isOk = true;
        mValidationExceptions.clear();

        for (Map.Entry<String, DataValidator> entry : mValidatorsMap.entrySet()) {
            try {
                String key = entry.getKey();
                entry.getValue().validate(context, this, key,
                                          Objects.requireNonNull(mValidatorErrorIdMap.get(key)));
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
               + ", mValidationExceptions=" + mValidationExceptions
               + ", mValidatorsMap=" + mValidatorsMap
               + ", mCrossValidators=" + mCrossValidators
               + '}';
    }
}
