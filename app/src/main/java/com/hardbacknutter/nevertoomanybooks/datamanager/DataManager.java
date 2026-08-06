/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.parsers.BooleanParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Class to manage a set of related data.
 * It's basically an extended Bundle with support for Money and Bit types,
 * parsing, easier list handling, nullability...
 * <p>
 * The actual data is stored in a single member variable {@link #rawData}.
 */
public class DataManager
        implements DataHolder, Parcelable {

    /** {@link Parcelable}. */
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
     * Constructor.
     */
    public DataManager() {
        this.rawData = new Bundle();
    }

    /**
     * Constructor. Loads the data <strong>without</strong> type checks.
     *
     * @param rawData to use as-is
     */
    public DataManager(@NonNull final Bundle rawData) {
        this.rawData = rawData;
    }

    protected DataManager(@NonNull final Parcel in) {
        //noinspection DataFlowIssue
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

    /**
     * Check if this instance is empty.
     *
     * @return flag
     */
    public boolean isEmpty() {
        return rawData.isEmpty();
    }

    /**
     * Get the size of the raw data bundle.
     *
     * @return number of keys
     */
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
     * <p>
     * Does a <strong>shallow</strong> copy.
     *
     * @param src              DataManager to copy from
     * @param realNumberParser to use for number parsing
     */
    protected void putAll(@NonNull final DataManager src,
                          @NonNull final RealNumberParser realNumberParser) {
        src.keySet().forEach(key -> put(key, src.get(key, realNumberParser)));
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
     *      <li>date -> String</li>
     *      <li>Money values -> BigDecimal as String</li>
     * </ul>
     *
     * @param cursor an already positioned Cursor to read from
     *
     * @throws IllegalArgumentException for unsupported types.
     */
    public void putAll(@NonNull final Cursor cursor) {
        final Set<String> moneyKeys = DBKey.getMoneyKeys();

        for (int i = 0; i < cursor.getColumnCount(); i++) {
            final String key = cursor.getColumnName(i);

            if (moneyKeys.contains(key)) {
                // Money is a BigDecimal, stored as a String
                rawData.putString(key, cursor.getString(i));
                continue;
            }

            switch (cursor.getType(i)) {
                case Cursor.FIELD_TYPE_STRING:
                    rawData.putString(key, cursor.getString(i));
                    break;

                case Cursor.FIELD_TYPE_INTEGER:
                    // a null becomes 0
                    rawData.putLong(key, cursor.getLong(i));
                    break;

                case Cursor.FIELD_TYPE_FLOAT:
                    // a null becomes 0.0
                    rawData.putDouble(key, cursor.getDouble(i));
                    break;

                case Cursor.FIELD_TYPE_BLOB:
                    putSerializable(key, cursor.getBlob(i));
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
     * Store an Object value. The object will be cast to one of the supported types.
     * <p>
     * This code is a subset of Bundle#putObject(String, Object)
     * which is not part of the public API.
     * <p>
     * In addition, this method supports:
     * <ul>
     *     <li>{@link Money} using {@link #putMoney(String, Money)}</li>
     *     <li>{@code BigDecimal} using {@link #putBigDecimal(String, BigDecimal)}</li>
     *     <li>{@code BigInteger} using {@link BigInteger#toString()} for future compatibility</li>
     * </ul>
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @throws IllegalArgumentException for unsupported types.
     */
    public void put(@NonNull final String key,
                    @Nullable final Object value) {

        if (value instanceof Money) {
            putMoney(key, (Money) value);
        } else if (value instanceof BigDecimal) {
            putBigDecimal(key, (BigDecimal) value);
        } else if (value instanceof BigInteger) {
            rawData.putString(key, value.toString());

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
                LoggerFactory.getLogger().d(TAG, "put|key=`" + key + "`|value=<NULL>");
            }
            putNull(key);

        } else {
            throw new IllegalArgumentException("put|key=`" + key
                                               + "|type=" + value.getClass().getName()
                                               + "`|value=" + value);
        }
    }

    /**
     * Get the unprotected, underlying Bundle with the raw data.
     * Use for testing only.
     *
     * @return data
     */
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.TESTS)
    @NonNull
    public Bundle getRawData() {
        return rawData;
    }

    /**
     * Get the raw data object specified by the passed key.
     * <p>
     * <strong>IMPORTANT: Does NOT Support returning a {@link Money} object</strong>
     * <p>
     * Use {@link #get(String, RealNumberParser)} for detection/return of {@link Money} objects.
     *
     * @param key Key of data object
     *
     * @return Object
     */
    @Nullable
    public Object get(@NonNull final String key) {
        return rawData.get(key);
    }

    /**
     * Get the data object specified by the passed key.
     * <p>
     * <strong>Supports returning a {@link Money} object</strong>
     *
     * @param key    Key of data object
     * @param parser to use for {@link Money} parsing
     *
     * @return Data object, or {@code null} when not present or the value is {@code null}
     */
    @Nullable
    public Object get(@NonNull final String key,
                      @NonNull final RealNumberParser parser) {
        if (DBKey.getMoneyKeys().contains(key)) {
            try {
                if (rawData.containsKey(key)) {
                    return getMoney(key, parser);
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
            // fall through and return the raw value
        }
        return rawData.get(key);
    }

    @Override
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

    @Override
    public double getDouble(@NonNull final String key,
                            @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        // always use a parser. The Bundle#getDouble/getFloat cannot cast between Double and Float.
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

    @NonNull
    @Override
    public BigDecimal getBigDecimal(@NonNull final String key,
                                    @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        // The type should be a String, as used by putBigDecimal;
        final Object source = rawData.get(key);
        if (source instanceof String) {
            try {
                return new BigDecimal((String) source);
            } catch (@NonNull final NumberFormatException e) {
                LoggerFactory.getLogger()
                             .e(TAG, "key='" + key + "' new BigDecimal() failed for: " + source);
            }
        }

        // fallback to parsing
        return parser.toBigDecimal(source);
    }

    /**
     * Store a BigDecimal value. It is written as a String with '.' as the decimal separator.
     *
     * @param key   Key of data object
     * @param value to store
     *
     * @see #getBigDecimal(String, RealNumberParser)
     */
    public void putBigDecimal(@NonNull final String key,
                              @NonNull final BigDecimal value) {
        //noinspection CallToNumericToString
        rawData.putString(key, value.toString());
    }

    @Override
    public float getFloat(@NonNull final String key,
                          @NonNull final RealNumberParser parser)
            throws NumberFormatException {
        // always use a parser. The Bundle#getDouble/getFloat cannot cast between Double and Float.
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
            return o.toString().strip();
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
     * Returns the value associated with the given key.
     * <p>
     * <strong>THIS IS A PRIVATE METHOD AND SHOULD STAY PRIVATE</strong>
     *
     * @param key    Key of data object
     * @param parser to use for number parsing
     *
     * @return value; {@code null} becomes a Money object with value {@code BigDecimal.ZERO}.
     *         The returned value may or may not have a Currency set.
     *
     * @throws NumberFormatException if the source was not compatible.
     * @see #get(String, RealNumberParser)
     */
    @NonNull
    private Money getMoney(@NonNull final String key,
                           @NonNull final RealNumberParser parser)
        throws NumberFormatException {

        return MoneyParser.parse(getBigDecimal(key, parser),
                                 getString(key + DBKey.CURRENCY_SUFFIX));
    }

    /**
     * Store a {@link Money} value as a {@code BigDecimal} for the value,
     * and a {@code String} for the currency.
     *
     * @param key   Key of data object
     * @param money to store
     */
    public void putMoney(@NonNull final String key,
                         @NonNull final Money money) {

        putBigDecimal(key, money.getValue());

        final Currency currency = money.getCurrency();
        if (currency != null) {
            rawData.putString(key + DBKey.CURRENCY_SUFFIX, currency.getCurrencyCode());
        } else {
            // Explicitly remove in case we're replace a Money object with a new one
            rawData.remove(key + DBKey.CURRENCY_SUFFIX);
        }
    }

    /**
     * Get a {@link LocalDateTime} value.
     *
     * @param key        Key of data object
     * @param dateParser to use for date parsing
     *
     * @return value or {@code null} if parsing did not produce a {@link LocalDateTime} object
     */
    @NonNull
    protected Optional<LocalDateTime> getLocalDateTime(
            @SuppressWarnings("SameParameterValue")
            @NonNull final String key,
            @NonNull final DateParser<LocalDateTime> dateParser) {
        if (rawData.containsKey(key)) {
            return dateParser.parse(rawData.getString(key));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Store a {@link LocalDateTime} value.
     *
     * @param key      Key of data object
     * @param dateTime to store
     */
    public void putLocalDateTime(@SuppressWarnings("SameParameterValue")
                                 @NonNull final String key,
                                 @NonNull final LocalDateTime dateTime) {
        rawData.putString(key, SqlEncode.dateTime(dateTime));
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
    public List<String> getStringArrayList(@NonNull final String key) {
        Object o = rawData.get(key);
        if (o == null) {
            o = new ArrayList<>();
            //noinspection unchecked
            rawData.putStringArrayList(key, (ArrayList<String>) o);
        }
        //noinspection unchecked
        return (List<String>) o;
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
     * Get a {@link Parcelable} {@link ArrayList} from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty
     */
    @NonNull
    public <T extends Parcelable> Optional<ArrayList<T>> optParcelableArrayList(
            @NonNull final String key) {
        if (rawData.containsKey(key)) {
            final ArrayList<T> list = rawData.getParcelableArrayList(key);
            // paranoia
            if (list != null) {
                return Optional.of(list);
            }
            // the key was present but its value was null
            rawData.remove(key);
        }
        return Optional.empty();
    }

    /**
     * Store a {@link Parcelable} {@link Collection}.
     * <p>
     * An {@link ArrayList} value is stored as-is,
     * while other types of {@link Collection}s are wrapped in a new {@link ArrayList}.
     * <p>
     * <strong>If possible, AVOID using this method directly.</strong>
     *
     * @param key   Key of data object
     * @param value to store
     * @param <T>   type of objects in the list
     *
     * @see #getParcelableArrayList(String)
     */
    public <T extends Parcelable> void putParcelableCollection(@NonNull final String key,
                                                               @NonNull final Collection<T> value) {
        if (value instanceof ArrayList) {
            rawData.putParcelableArrayList(key, (ArrayList<T>) value);
        } else {
            rawData.putParcelableArrayList(key, new ArrayList<>(value));
        }
    }

    /**
     * Get a {@link Parcelable} object from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The data
     */
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
    @SuppressWarnings("WeakerAccess")
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

    /**
     * Full equality! This includes equality of the DBKey.PK_ID when present.
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataManager that = (DataManager) o;

        return equalBundles(rawData, that.rawData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rawData);
    }

    private boolean equalBundles(@NonNull final Bundle b1,
                                 @NonNull final Bundle b2) {

        if (b1.size() != b2.size()) {
            return false;
        }

        final Set<String> allKeys = new HashSet<>(b1.keySet());
        allKeys.addAll(b2.keySet());

        @Nullable
        Object valueOne;
        @Nullable
        Object valueTwo;

        for (final String key : allKeys) {
            if (!b1.containsKey(key) || !b2.containsKey(key)) {
                return false;
            }

            valueOne = b1.get(key);
            valueTwo = b2.get(key);
            if (valueOne instanceof Bundle && valueTwo instanceof Bundle
                && !equalBundles((Bundle) valueOne, (Bundle) valueTwo)) {
                return false;
            } else if (valueOne == null) {
                if (valueTwo != null) {
                    return false;
                }
            } else if (!valueOne.equals(valueTwo)) {
                return false;
            }
        }

        return true;
    }
}
