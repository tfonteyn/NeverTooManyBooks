/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
import java.util.HashMap;
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
import com.hardbacknutter.nevertoomanybooks.utils.IllegalTypeException;
import com.hardbacknutter.nevertoomanybooks.utils.UniqueMap;

/**
 * Class to manage a version of a set of related data.
 * <p>
 * Not performance tested, but this class and {@link Datum} have an efficiency problem:
 * A Datum is only needed if there is a DataAccessor and/or DataValidator for that
 * particular piece of information.
 * If neither is present, then a Datum is just a pass-through layer.
 * This would be fine if the majority of info fields HAD a DataAccessor and/or DataValidator.
 * But (2019-06-11) right now there are only 4 accessors and 6 validators. The majority of
 * the fields have neither.
 */
public class DataManager {

    // Pre-defined generic validators; if field-specific defaults are needed, create a new one.
    /** re-usable validator. */
    protected static final DataValidator INTEGER_VALIDATOR = new IntegerValidator(0);
    /** re-usable validator. */
    protected static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();
//    /** re-usable validator. */
//    protected static final DataValidator BLANK_OR_INTEGER_VALIDATOR = new OrValidator(
//            new BlankValidator(),
//            new IntegerValidator(0));
    /** re-usable validator. */
    protected static final DataValidator BLANK_OR_FLOAT_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new FloatValidator(0f));

    // DataValidator blankOrDateValidator =
    //     new OrValidator(new BlankValidator(), new DateValidator());

    /** A list of cross-validators to apply if all fields pass simple validation. */
    private final List<DataCrossValidator> mCrossValidators = new ArrayList<>();

    /** Datum Validators. Key is the same as used for mRawData access. */
    private final Map<String, DataValidator> mValidatorsMap = new UniqueMap<>();

    /** The last validator exception caught by this object. */
    private final List<ValidatorException> mValidationExceptions = new ArrayList<>();

    /** Raw data storage. */
    private final Bundle mRawData = new Bundle();

    /** Storage for the {@link Datum} objects; the data-related code. */
    private final DatumMap mDatumMap = new DatumMap();


    /**
     * Add a validator for the specified key.
     *
     * @param key       Key for the {@link Datum}
     * @param validator Validator
     */
    protected void addValidator(@NonNull final String key,
                                @NonNull final DataValidator validator) {
        mValidatorsMap.put(key, validator);
    }

    /**
     * Add an {@link DataAccessor} for the specified key.
     * <p>
     * It's up to the Accessor to handle the actual key into the rawData.
     *
     * @param accessorKey Key to the {@link Datum}
     * @param accessor    Accessor
     */
    protected void addAccessor(@NonNull final String accessorKey,
                               @NonNull final DataAccessor accessor) {
        mDatumMap.getOrNew(accessorKey).setAccessor(accessor);
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
                //TODO: use putLong?
                putInt(key, (Integer) value);

            } else if (value instanceof Long) {
                putLong(key, (Long) value);

            } else if (value instanceof Double) {
                putDouble(key, (Double) value);

            } else if (value instanceof Float) {
                //TODO: use putDouble?
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
                    throw new IllegalTypeException(String.valueOf(cursor.getType(i)));
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
        return get(mDatumMap.getOrNew(key));
    }

    /**
     * Get the data object specified by the passed {@link Datum}.
     *
     * @return Data object, or {@code null} when not present
     */
    @Nullable
    public Object get(@NonNull final Datum datum) {
        return datum.get(mRawData);
    }

    /**
     * @return a boolean value.
     */
    public boolean getBoolean(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getBoolean(mRawData);
    }

    /**
     * Store a boolean value.
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        mDatumMap.getOrNew(key).putBoolean(mRawData, value);
    }

    /**
     * Store a boolean value.
     * Shortcut for {@link #putBoolean(String, boolean)}.
     * Mainly (only?) used by {@link DataValidator} classes.
     */
    public void putBoolean(@NonNull final Datum datum,
                           final boolean value) {
        datum.putBoolean(mRawData, value);
    }

    /**
     * @return a double value.
     */
    public double getDouble(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getDouble(mRawData);
    }

    /**
     * Store a double value.
     */
    @SuppressWarnings("WeakerAccess")
    public void putDouble(@NonNull final String key,
                          final double value) {
        mDatumMap.getOrNew(key).putDouble(mRawData, value);
    }

    /**
     * Store a double value.
     */
    @SuppressWarnings("unused")
    public void putDouble(@NonNull final Datum datum,
                          final double value) {
        datum.putDouble(mRawData, value);
    }

    /** @return a float value. */
    @SuppressWarnings("unused")
    public float getFloat(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getFloat(mRawData);
    }

    /**
     * Store a float value.
     */
    @SuppressWarnings("WeakerAccess")
    public void putFloat(@NonNull final String key,
                         final float value) {
        mDatumMap.getOrNew(key).putFloat(mRawData, value);
    }

    /**
     * Store a float value.
     */
    public void putFloat(@NonNull final Datum datum,
                         final float value) {
        datum.putFloat(mRawData, value);
    }

    /**
     * @return an int value.
     */
    public int getInt(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getInt(mRawData);
    }

    /**
     * Store an int value.
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        mDatumMap.getOrNew(key).putInt(mRawData, value);
    }

    /**
     * Store an int value.
     */
    public void putInt(@NonNull final Datum datum,
                       final int value) {
        datum.putInt(mRawData, value);
    }

    /**
     * @return a long value.
     */
    public long getLong(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getLong(mRawData);
    }

    /**
     * Store a long value.
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        mDatumMap.getOrNew(key).putLong(mRawData, value);
    }

    /**
     * Store a long value.
     */
    public void putLong(@NonNull final Datum datum,
                        final long value) {
        datum.putLong(mRawData, value);
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final String key) {
        return mDatumMap.getOrNew(key).getString(mRawData);
    }

    /**
     * Get a String value.
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final Datum datum) {
        return datum.getString(mRawData);
    }

    /**
     * Store a String value.
     */
    public void putString(@NonNull final String key,
                          @NonNull final String value) {
        mDatumMap.getOrNew(key).putString(mRawData, value);
    }

    /**
     * Store a String value.
     */
    public void putString(@NonNull final Datum datum,
                          @NonNull final String value) {
        datum.putString(mRawData, value);
    }

    /**
     * Get the Parcelable ArrayList from the collection.
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final Datum datum) {
        return datum.getParcelableArrayList(mRawData);
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
        return mDatumMap.getOrNew(key).getParcelableArrayList(mRawData);
    }

    /**
     * Set the Parcelable ArrayList in the collection.
     *
     * @param key   Key of object
     * @param value The Parcelable ArrayList
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final String key,
                                                              @NonNull final ArrayList<T> value) {
        mDatumMap.getOrNew(key).putParcelableArrayList(mRawData, value);
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
        //noinspection unchecked
        return (T) mRawData.getSerializable(key);
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
        mRawData.putSerializable(key, value);
    }

    /**
     * @param key Key of object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean containsKey(@NonNull final String key) {
        return mDatumMap.getOrNew(key).isPresent(mRawData);
    }

    /**
     * @return the current set of data.
     */
    @NonNull
    public Set<String> keySet() {
        return mDatumMap.keySet();
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data to remove.
     */
    public void remove(@NonNull final String key) {
        mDatumMap.remove(key);
        mRawData.remove(key);
    }

    /**
     * Erase everything in this instance.
     */
    public void clear() {
        mRawData.clear();
        mDatumMap.clear();
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
     * @param crossValidating Options indicating if this is a cross validation pass.
     *
     * @return {@code true} if all validations passed.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validate(final boolean crossValidating) {
        boolean isOk = true;

        for (final Map.Entry<String, DataValidator> entry : mValidatorsMap.entrySet()) {
            Datum datum = mDatumMap.get(entry.getKey());
            if (datum != null) {
                try {
                    entry.getValue().validate(this, datum, crossValidating);
                } catch (@NonNull final ValidatorException e) {
                    mValidationExceptions.add(e);
                    isOk = false;
                }
            }
        }

//        for (Datum datum : mDatumMap.values()) {
//            validator = mValidatorsMap.get(datum.getKey());
//            if (validator != null) {
//                try {
//                    validator.validate(this, datum, crossValidating);
//                } catch (@NonNull final ValidatorException e) {
//                    mValidationExceptions.add(e);
//                    isOk = false;
//                }
//            }
//        }

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
               + ", mDatumMap=" + mDatumMap
               + ", mValidationExceptions=" + mValidationExceptions
               + ", mValidatorsMap=" + mValidatorsMap
               + ", mCrossValidators=" + mCrossValidators
               + '}';
    }

    /**
     * Class to manage the collection of {@link Datum} objects for this DataManager.
     */
    private static class DatumMap
            extends HashMap<String, Datum> {

        private static final long serialVersionUID = 455375570133391482L;

        /**
         * Get the specified {@link Datum}, or create a new Datum if not present.
         * The new Datum is stored into the map before returning.
         *
         * @param key for the Datum to get
         *
         * @return the Datum
         */
        @NonNull
        Datum getOrNew(@NonNull final String key) {
            Datum datum = super.get(key);
            if (datum == null) {
                datum = new Datum(key);
                put(key, datum);
            }
            return datum;
        }
    }
}
