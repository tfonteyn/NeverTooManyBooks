/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Class to manage a set of related data.
 * It's basically an extended Bundle with support for Money and Bit types,
 * parsing, easier list handling, nullability...
 * <p>
 * The actual data is stored in a single member variable {@link #rawData}.
 */
public class DataManager
        implements DataHolder, Parcelable {

    public static final Creator<DataManager> CREATOR = new Creator<>() {

        @Override
        @NonNull
        public DataManager createFromParcel(@NonNull final Parcel in) {
            return new DataManager(in);
        }

        @Override
        @NonNull
        public DataManager[] newArray(final int size) {
            return new DataManager[size];
        }
    };


    /** Log tag. */
    private static final String TAG = "DataManager";

    /** Raw data storage. */
    @NonNull
    private final Bundle rawData;

    /**
     * Constructor. Loads the data <strong>without</strong> type checks.
     */
    protected DataManager(@NonNull final Bundle rawData) {
        this.rawData = rawData;
    }

    protected DataManager(@NonNull final Parcel in) {
        rawData = in.readBundle(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeBundle(rawData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Clear all data in this instance.
     */
    public void clearData() {
        rawData.clear();
    }

    public boolean isEmpty() {
        return rawData.isEmpty();
    }

    public int size() {
        return rawData.size();
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return Set.copyOf(rawData.keySet());
    }

    /**
     * Remove the specified key from this collection.
     *
     * @param key Key of data object to remove.
     */
    public void remove(@NonNull final String key) {
        rawData.remove(key);
    }

    /**
     * Check if the underlying data contains the specified key.
     *
     * @param key Key of data object
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    public boolean contains(@NonNull final String key) {
        return rawData.containsKey(key);
    }

    /**
     * Store all passed values in our collection (with type checking).
     *
     * @param context Current context
     * @param src     DataManager to copy from
     */
    protected void putAll(final Context context,
                          @NonNull final DataManager src) {
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        for (final String key : src.keySet()) {
            put(key, src.get(key, realNumberParser));
        }
    }

    /**
     * Store all passed values in our collection.
     * <p>
     * See the comments on methods in {@link android.database.CursorWindow}
     * for info on type conversions which explains our use of getLong/getDouble.
     * <ul>
     *      <li>booleans -> long (0,1)</li>
     *      <li>int -> long</li>
     *      <li>float -> double</li>
     *      <li>date -> string</li>
     * </ul>
     *
     * @param cursor an already positioned Cursor to read from
     */
    protected void putAll(@NonNull final Cursor cursor) {
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            final String name = cursor.getColumnName(i);
            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_STRING:
                    rawData.putString(name, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    // a null becomes 0
                    rawData.putLong(name, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    // a null becomes 0.0
                    rawData.putDouble(name, cursor.getDouble(i));
                    break;

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(name, cursor.getBlob(i));
                    break;

                case Cursor.FIELD_TYPE_NULL:
                    // discard any fields with null values.
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(cursor.getType(i)));
            }
        }
    }

    /**
     * Store a Object value. The object will be cast to one of the supported types.
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @throws IllegalArgumentException for unsupported types.
     */
    public void put(@NonNull final String key,
                    @Nullable final Object value) {
        // This code is a subset of Bundle#putObject(String, Object)
        // which is not part of the public API.

        if (value instanceof Money) {
            // special handling
            putMoney(key, (Money) value);

        } else if (value instanceof CharSequence) {
            rawData.putCharSequence(key, (CharSequence) value);
        } else if (value instanceof Integer) {
            rawData.putInt(key, (int) value);
        } else if (value instanceof Long) {
            rawData.putLong(key, (long) value);
        } else if (value instanceof Double) {
            rawData.putDouble(key, (double) value);
        } else if (value instanceof Float) {
            rawData.putFloat(key, (float) value);
        } else if (value instanceof Boolean) {
            rawData.putBoolean(key, (boolean) value);

        } else if (value instanceof Parcelable) {
            rawData.putParcelable(key, (Parcelable) value);
        } else if (value instanceof Parcelable[]) {
            rawData.putParcelableArray(key, (Parcelable[]) value);
        } else if (value instanceof ArrayList) {
            //noinspection unchecked,rawtypes
            rawData.putParcelableArrayList(key, (ArrayList) value);

        } else if (value instanceof Serializable) {
            putSerializable(key, (Serializable) value);

        } else if (value == null) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger()
                             .w(TAG, "put|key=`" + key + "`|value=<NULL>");
            }
            putNull(key);

        } else {
            throw new IllegalArgumentException("put|key=`" + key + "`|value=" + value);
        }
    }

    @VisibleForTesting
    @NonNull
    public Bundle getRawData() {
        return rawData;
    }

    /**
     * Get the data object specified by the passed key.
     *
     * @param key Key of data object
     *
     * @return Data object, or {@code null} when not present or the value is {@code null}
     */
    @Nullable
    public Object get(@NonNull final String key,
                      @NonNull final RealNumberParser parser) {
        if (DBKey.MONEY_KEYS.contains(key)) {
            // try to combine the keys
            try {
                final Money money = getMoney(key, parser);
                if (money != null) {
                    return money;
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
            // fall through and return the raw value
        }
        return rawData.get(key);
    }

    /**
     * Get a boolean value.
     *
     * @param key Key of data object
     *
     * @return a boolean value {@code null} or empty becomes {@code false}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public boolean getBoolean(@NonNull final String key)
            throws NumberFormatException {
        return BooleanParser.toBoolean(rawData.get(key));
    }

    /**
     * Store a boolean value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        rawData.putBoolean(key, value);
    }

    /**
     * Get an int value.
     *
     * @param key Key of data object
     *
     * @return an int value; {@code null} or empty becomes {@code 0}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public int getInt(@NonNull final String key)
            throws NumberFormatException {
        return (int) NumberParser.toLong(rawData.get(key));
    }

    /**
     * Store an int value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putInt(@NonNull final String key,
                       final int value) {
        rawData.putInt(key, value);
    }

    /**
     * Get a long value.
     *
     * @param key Key of data object
     *
     * @return a long value; {@code null} or empty becomes {@code 0}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public long getLong(@NonNull final String key)
            throws NumberFormatException {
        return NumberParser.toLong(rawData.get(key));
    }

    /**
     * Store a long value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putLong(@NonNull final String key,
                        final long value) {
        rawData.putLong(key, value);
    }

    /**
     * Get a double value.
     *
     * @param key Key of data object
     *
     * @return a double value; {@code null} or empty becomes {@code 0}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public double getDouble(@NonNull final String key,
                            @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        return parser.toDouble(rawData.get(key));
    }

    /**
     * Store a double value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putDouble(@NonNull final String key,
                          final double value) {
        rawData.putDouble(key, value);
    }

    /**
     * Get a float value.
     *
     * @param key    Key of data object
     * @param parser to use for number parsing
     *
     * @return a float value {@code null} or empty becomes {@code 0}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Override
    public float getFloat(@NonNull final String key,
                          @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        return parser.toFloat(rawData.get(key));
    }

    /**
     * Store a float value.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putFloat(@NonNull final String key,
                         final float value) {
        rawData.putFloat(key, value);
    }

    @Nullable
    @Override
    public String getString(@NonNull final String key,
                            @Nullable final String defaultValue) {
        final Object o = rawData.get(key);
        if (o == null) {
            return defaultValue;
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
        rawData.putString(key, value);
    }

    /**
     * Get a {@link Money} value.
     * <p>
     * <strong>NOT for normal use; it's to easy to get this wrong.
     * Should only used by {@link #get(String, RealNumberParser)} or in tests.</strong>
     * <p>
     * This method should really return an "Either". i.e.
     * Either return the Money object, or return a String with the raw value.
     * This is exactly what is done in {@link #get(String, RealNumberParser)}
     * where we return an Object.
     *
     * @param key Key of data object
     *
     * @return value or {@code null} if parsing did not produce a Money object
     *
     * @throws NumberFormatException if parsing the value itself failed.
     */
    @VisibleForTesting
    @Nullable
    public Money getMoney(@NonNull final String key,
                          @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        if (rawData.containsKey(key)) {
            return MoneyParser.parse(BigDecimal.valueOf(getDouble(key, parser)),
                                     getString(key + DBKey.CURRENCY_SUFFIX));
        } else {
            return null;
        }
    }

    /**
     * Store a {@link Money} value.
     *
     * @param key   Key of data object
     * @param money to store
     */
    public void putMoney(@NonNull final String key,
                         @NonNull final Money money) {
        rawData.putDouble(key, money.getValue().doubleValue());
        if (money.getCurrency() != null) {
            rawData.putString(key + DBKey.CURRENCY_SUFFIX, money.getCurrencyCode());
        }
    }


    /**
     * Get a {@link String} {@link ArrayList} from the collection.
     * <p>
     * <strong>Important: </strong>
     * If the key is not present or {@code null}, a new list will be created/stored and returned.
     *
     * @param key Key of data object
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public ArrayList<String> getStringArrayList(@NonNull final String key) {
        Object o = rawData.get(key);
        if (o == null) {
            o = new ArrayList<>();
            //noinspection unchecked
            rawData.putStringArrayList(key, (ArrayList<String>) o);
        }
        //noinspection unchecked
        return (ArrayList<String>) o;
    }

    /**
     * Store a {@link String} {@link ArrayList} in the collection.
     *
     * @param key   Key of data object
     * @param value to store
     */
    public void putStringArrayList(@NonNull final String key,
                                   @NonNull final ArrayList<String> value) {
        rawData.putStringArrayList(key, value);
    }

    /**
     * Get a {@link Parcelable} {@link ArrayList} from the collection.
     * <p>
     * <strong>Important: </strong>
     * If the key is not present or {@code null}, a new list will be created/stored and returned.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        Object o = rawData.get(key);
        if (o == null) {
            o = new ArrayList<>();
            //noinspection unchecked
            rawData.putParcelableArrayList(key, (ArrayList<T>) o);
        }
        //noinspection unchecked
        return (ArrayList<T>) o;
    }

    /**
     * Store a {@link Parcelable} {@link ArrayList} in the collection.
     * <p>
     * If possible, AVOID using this method directly.
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of objects in the list
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final String key,
                                                              @NonNull final ArrayList<T> value) {
        rawData.putParcelableArrayList(key, value);
    }

    @Nullable
    public <T extends Parcelable> T getParcelable(@NonNull final String key) {
        return rawData.getParcelable(key);
    }

    /**
     * Store a {@link Parcelable} in the collection.
     * <p>
     * If possible, AVOID using this method directly.
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of object
     */
    public <T extends Parcelable> void putParcelable(@NonNull final String key,
                                                     @NonNull final T value) {
        rawData.putParcelable(key, value);
    }

    /**
     * Get a {@link Serializable} object from the collection.
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
        return (T) rawData.getSerializable(key);
    }

    /**
     * Store a {@link Serializable} object in the collection.
     *
     * @param key   Key of data object
     * @param value to store
     */
    private void putSerializable(@NonNull final String key,
                                 @NonNull final Serializable value) {
        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger()
                         .e(TAG, new Throwable("putSerializable"),
                            "putSerializable|key=" + key
                            + "|type=" + value.getClass().getCanonicalName());
        }
        rawData.putSerializable(key, value);
    }

    /**
     * Store a {@code null} value.
     *
     * @param key Key of data object
     */
    public void putNull(@NonNull final String key) {
        rawData.putString(key, null);
    }

    @Override
    @NonNull
    public String toString() {
        return "DataManager{"
               + "rawData=" + rawData
               + '}';
    }
}
