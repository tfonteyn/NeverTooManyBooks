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

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.datamanager.accessors.DataAccessor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Class to manage storage/retrieval of a piece of data from a bundle.
 * <ul>
 * <li>DataManager, e.g. a Book.</li>
 * <li>Datum, describes a single piece of data.</li>
 * <li>rawData, where the actual values are stored using the 'key' from the Datum.</li>
 * </ul>
 * <ul>A Datum has:
 * <li>key: the name/id of this Datum:
 * <ul>
 * <li>without DataAccessor: the key is used directly to access the 'rawData'</li>
 * <li>WITH DataAccessor: access to 'rawData' is handled by the private key of the DataAccessor</li>
 * </ul>
 * </li>
 * <li>DataAccessor: optional layer to 'translate' the data between the value in the bundle
 * and the value that comes in / goes out</li>
 * </ul>
 * What a Datum is <strong>NOT: a value</strong>
 */
public class Datum {

    @NonNull
    private final String mKey;

    /**
     * Accessor for this Datum (e.g. the datum might be a bit in a bitmask field,
     * or a composite read-only value.
     */
    private DataAccessor mAccessor;

    /**
     * Constructor.
     *
     * @param key Key of this datum
     */
    public Datum(@NonNull final String key) {
        mKey = key;
    }

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
     * Translate the passed object to a Double value.
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
     * Converts a String to a boolean value.
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
                throw new NumberFormatException("Invalid boolean, s=`" + s + '`');
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
                        Logger.error(Datum.class, e, "Invalid boolean, s=`" + s + '`');
                        throw e;
                    }
            }
        }
    }

    /** @return the key. */
    @NonNull
    public String getKey() {
        return mKey;
    }

    /**
     * @param rawData Raw data
     *
     * @return {@code true} if the underlying data contains the specified key.
     */
    boolean isPresent(@NonNull final Bundle rawData) {
        if (mAccessor == null) {
            return rawData.containsKey(mKey);
        } else {
            return mAccessor.isPresent(rawData);
        }
    }

    /**
     * Set the DataAccessor. Protected against being set twice.
     */
    public void setAccessor(@NonNull final DataAccessor accessor) {
        if (mAccessor != null && accessor != mAccessor) {
            throw new IllegalStateException("Datum '" + mKey + "' already has an Accessor");
        }
        mAccessor = accessor;
    }

    /**
     * Retrieve the raw Object from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data bundle
     *
     * @return The object data, or {@code null} if not found
     */
    @Nullable
    public Object get(@NonNull final Bundle rawData) {
        if (mAccessor == null) {
            return rawData.get(mKey);
        } else {
            return mAccessor.get(rawData);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, a {@code null} is returned as {@code false}
     */
    public boolean getBoolean(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        return toBoolean(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     * @param value   The boolean (primitive) value to store
     */
    public void putBoolean(@NonNull final Bundle rawData,
                           final boolean value) {
        if (mAccessor == null) {
            rawData.putBoolean(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, a {@code null} is returned as 0
     */
    public int getInt(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        return (int) toLong(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     * @param value   The int value to store
     */
    public void putInt(@NonNull final Bundle rawData,
                       final int value) {
        if (mAccessor == null) {
            rawData.putInt(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, a {@code null} is returned as 0
     */
    public long getLong(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        return toLong(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     * @param value   The long value to store
     */
    public void putLong(@NonNull final Bundle rawData,
                        final long value) {
        if (mAccessor == null) {
            rawData.putLong(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, a {@code null} is returned as 0
     */
    public double getDouble(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        return toDouble(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     * @param value   The double value to store
     */
    void putDouble(@NonNull final Bundle rawData,
                   final double value) {
        if (mAccessor == null) {
            rawData.putDouble(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, a {@code null} is returned as 0
     */
    @SuppressWarnings("WeakerAccess")
    public float getFloat(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        return (float) toDouble(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     */
    void putFloat(@NonNull final Bundle rawData,
                  final float value) {
        if (mAccessor == null) {
            rawData.putFloat(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }

    /**
     * Retrieve the data from the rawData, translating and using Accessor as necessary.
     *
     * @param rawData Raw data
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }
        // any null gets turned into ""
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data
     * @param value   the String object to store, will be trimmed before storing.
     */
    public void putString(@NonNull final Bundle rawData,
                          @NonNull final String value) {
        if (mAccessor == null) {
            rawData.putString(mKey, value.trim());
        } else {
            mAccessor.put(rawData, value.trim());
        }
    }

    /**
     * Get the {@code ArrayList<Parcelable>} object from the collection.
     *
     * @param rawData Raw data Bundle
     * @param <T>     the type of the list elements
     *
     * @return The list, can be empty but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(
            @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(rawData);
        }

        if (o == null) {
            return new ArrayList<>();
        }
        //noinspection unchecked
        return (ArrayList<T>) o;
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data Bundle
     * @param value   The {@code ArrayList<Parcelable>} object to store
     * @param <T>     the type of the list elements
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final Bundle rawData,
                                                              @NonNull final ArrayList<T> value) {
        if (mAccessor == null) {
            rawData.putParcelableArrayList(mKey, value);
        } else {
            mAccessor.put(rawData, value);
        }
    }
}
