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

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.datamanager.accessors.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.validators.DataValidator;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * Class to manage storage and retrieval of a piece of data from a bundle as well as
 * ancillary details such as visibility.
 * <p>
 * DataManager, e.g. a Book.
 * Datum, describes a single piece of data. Has a 'key' identifier.
 * rawData, where the actual values are stored using the 'key' from the Datum.
 *
 * @author pjw
 */
public class Datum {

    @NonNull
    private final String mKey;
    /** {@code true} if data should be visible. */
    private final boolean mIsVisible;
    /** Validator for this Datum. */
    @Nullable
    private DataValidator mValidator;
    /**
     * Accessor for this Datum (e.g. the datum might be a bit in a bitmask field,
     * or a composite read-only value.
     */
    private DataAccessor mAccessor;

    /**
     * Constructor.
     *
     * @param datumKey Key of this datum
     * @param visible  {@code true} if data should be visible
     */
    public Datum(@NonNull final String datumKey,
                 final boolean visible) {
        mKey = datumKey;
        mIsVisible = visible;
    }

    /**
     * Translate the passed object to a Long value.
     *
     * @param o Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    @SuppressWarnings("WeakerAccess")
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
            } catch (NumberFormatException e1) {
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
            } catch (NumberFormatException e1) {
                // desperate ?
                return toBoolean(o) ? 1 : 0;
            }
        }
    }

    /**
     * DEBUG
     * <p>
     * Format the passed bundle in a way that is convenient for display.
     *
     * @param bundle Bundle to format, strings will be trimmed before adding
     *
     * @return Formatted string
     */
    @NonNull
    public static String toString(@NonNull final Bundle bundle) {
        StringBuilder sb = new StringBuilder();
        for (String k : bundle.keySet()) {
            sb.append(k).append("->");
            Object o = bundle.get(k);
            if (o != null) {
                sb.append(o.toString().trim());
            }
            sb.append('\n');
        }
        return sb.toString();
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
            switch (s.trim().toLowerCase(LocaleUtils.getSystemLocale())) {
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
                    } catch (NumberFormatException e) {
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isVisible() {
        return mIsVisible;
    }

    /** @return the DataValidator. */
    @Nullable
    public DataValidator getValidator() {
        return mValidator;
    }

    /**
     * Set the DataValidator. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum setValidator(@NonNull final DataValidator validator) {
        if (mValidator != null && validator != mValidator) {
            throw new IllegalStateException("Datum '" + mKey + "' already has a validator");
        }
        mValidator = validator;
        return this;
    }

    /** @return the DataAccessor. */
    @Nullable
    public DataAccessor getAccessor() {
        return mAccessor;
    }

    /**
     * Set the DataAccessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum setAccessor(@NonNull final DataAccessor accessor) {
        if (mAccessor != null && accessor != mAccessor) {
            throw new IllegalStateException("Datum '" + mKey + "' already has an Accessor");
        }
        mAccessor = accessor;
        return this;
    }

    /**
     * Retrieve the raw Object from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent DataManager
     * @param rawData Raw data bundle
     *
     * @return The object data, or {@code null} if not found
     */
    @Nullable
    public Object get(@NonNull final DataManager data,
                      @NonNull final Bundle rawData) {
        if (mAccessor == null) {
            return rawData.get(mKey);
        } else {
            return mAccessor.get(data, rawData, this);
        }
    }

    /**
     * Retrieve the value from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data
     */
    public boolean getBoolean(@NonNull final DataManager data,
                              @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        return toBoolean(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     * @param value   The boolean (primitive) value to store
     */
    public void putBoolean(@NonNull final DataManager data,
                           @NonNull final Bundle rawData,
                           final boolean value) {
        if (mAccessor == null) {
            rawData.putBoolean(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data
     */
    public int getInt(@NonNull final DataManager data,
                      @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        return (int) toLong(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     * @param value   The int value to store
     */
    public void putInt(@NonNull final DataManager data,
                       @NonNull final Bundle rawData,
                       final int value) {
        if (mAccessor == null) {
            rawData.putInt(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data
     */
    public long getLong(@NonNull final DataManager data,
                        @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        return toLong(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     * @param value   The long value to store
     */
    public void putLong(@NonNull final DataManager data,
                        @NonNull final Bundle rawData,
                        final long value) {
        if (mAccessor == null) {
            rawData.putLong(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data
     */
    @SuppressWarnings("WeakerAccess")
    public double getDouble(@NonNull final DataManager data,
                            @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        return toDouble(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     * @param value   The double value to store
     */
    void putDouble(@NonNull final DataManager data,
                   @NonNull final Bundle rawData,
                   final double value) {
        if (mAccessor == null) {
            rawData.putDouble(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data
     */
    @SuppressWarnings("WeakerAccess")
    public float getFloat(@NonNull final DataManager data,
                          @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        return (float) toDouble(o);
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     */
    void putFloat(@NonNull final DataManager data,
                  @NonNull final Bundle rawData,
                  final float value) {
        if (mAccessor == null) {
            rawData.putFloat(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    public String getString(@NonNull final DataManager data,
                            @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }
        // any null gets turned into ""
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param data    Parent collection
     * @param rawData Raw data
     * @param value   the String object to store, will be trimmed before storing.
     */
    public void putString(@NonNull final DataManager data,
                          @NonNull final Bundle rawData,
                          @NonNull final String value) {
        if (mAccessor == null) {
            rawData.putString(mKey, value.trim());
        } else {
            mAccessor.put(data, rawData, this, value.trim());
        }
    }

    /**
     * Get the serializable object from the collection.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param data    Parent collection
     * @param rawData Raw data Bundle
     *
     * @return The data
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    @Nullable
    public <T extends Serializable> T getSerializable(@SuppressWarnings("unused")
                                                      @NonNull final DataManager data,
                                                      @NonNull final Bundle rawData) {
        if (mAccessor == null) {
            return (T) rawData.getSerializable(mKey);
        } else {
            throw new AccessorNotSupportedException("Serializable");
        }
    }

    /**
     * Store the value in the rawData.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param rawData Raw data Bundle
     * @param value   The Serializable object to store
     */
    void putSerializable(@NonNull final Bundle rawData,
                         @NonNull final Serializable value) {
        if (mAccessor == null) {
            rawData.putSerializable(mKey, value);
        } else {
            throw new AccessorNotSupportedException("Serializable");
        }
    }

    /**
     * Get the ArrayList<Parcelable> object from the collection.
     *
     * @param data    Parent DataManager
     * @param rawData Raw data Bundle
     *
     * @return The list, can be empty but never {@code null}
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final DataManager data,
                                                                      @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
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
     * @param value   The ArrayList<Parcelable> object to store
     */
    public <T extends Parcelable> void putParcelableArrayList(@NonNull final DataManager data,
                                                              @NonNull final Bundle rawData,
                                                              @NonNull final ArrayList<T> value) {
        if (mAccessor == null) {
            rawData.putParcelableArrayList(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    /**
     * Get the ArrayList<String> object from the collection.
     *
     * @param data    Parent DataManager
     * @param rawData Raw data Bundle
     *
     * @return The list, can be empty but never {@code null}
     */
    @NonNull
    public ArrayList<String> getStringArrayList(@NonNull final DataManager data,
                                                @NonNull final Bundle rawData) {
        Object o;
        if (mAccessor == null) {
            o = rawData.get(mKey);
        } else {
            o = mAccessor.get(data, rawData, this);
        }

        if (o == null) {
            return new ArrayList<>();
        }
        //noinspection unchecked
        return (ArrayList<String>) o;
    }

    /**
     * Store the value in the rawData, or using Accessor as necessary.
     *
     * @param rawData Raw data Bundle
     * @param value   The ArrayList<String> object to store
     */
    void putStringArrayList(@NonNull final DataManager data,
                            @NonNull final Bundle rawData,
                            @NonNull final ArrayList<String> value) {
        if (mAccessor == null) {
            rawData.putStringArrayList(mKey, value);
        } else {
            mAccessor.put(data, rawData, this, value);
        }
    }

    private static class AccessorNotSupportedException
            extends IllegalStateException {

        private static final long serialVersionUID = 2561013689178409200L;

        @SuppressWarnings("SameParameterValue")
        AccessorNotSupportedException(@NonNull final String typeDescription) {
            super("Accessor not supported for " + typeDescription + " objects");
        }
    }
}
