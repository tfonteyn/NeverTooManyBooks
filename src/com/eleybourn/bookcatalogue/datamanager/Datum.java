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

import com.eleybourn.bookcatalogue.datamanager.datavalidators.DataValidator;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class to manage storage and retrieval of a piece of data from a bundle as well as
 * ancillary details such as visibility.
 * <p>
 * Note: In theory, all strings PUT into the collection will get automatically trimmed.
 *
 * @author pjw
 */
public class Datum {

    /** Key of this datum. */
    @NonNull
    private final String mKey;
    /** <tt>true</tt> if data should be visible. */
    private final boolean mIsVisible;
    /** Validator for this Datum. */
    @Nullable
    private DataValidator mValidator;
    /**
     * Accessor for this Datum (eg. the datum might be a bit in a bitmask field,
     * or a composite read-only value.
     */
    private DataAccessor mAccessor;

    /**
     * Constructor.
     *
     * @param key     Key of this datum
     * @param visible <tt>true</tt> if data should be visible
     */
    public Datum(@NonNull final String key,
                 final boolean visible) {
        mKey = key;
        mIsVisible = visible;
    }

    /**
     * Translate the passed object to a Long value.
     *
     * @param o Object
     *
     * @return Resulting value (null or empty becomes 0)
     */
    @SuppressWarnings("WeakerAccess")
    public static long toLong(@Nullable final Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return (Long) o;
        } catch (ClassCastException e) {
            final String s = o.toString();
            if (s.isEmpty()) {
                return 0;
            }
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e1) {
                return toBoolean(o) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed object to a Double value.
     *
     * @param o Object
     *
     * @return Resulting value (null or empty becomes 0)
     */
    private static double toDouble(@Nullable final Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return (Double) o;
        } catch (ClassCastException e) {
            final String s = o.toString();
            if (s.isEmpty()) {
                return 0;
            } else {
                return Double.parseDouble(s);
            }
        }
    }

    /**
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
     * Translate the passed Object to a String value.
     *
     * @param o Object
     *
     * @return Resulting value (null becomes empty)
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static String toString(@Nullable final Object o) {
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Converts a String to a boolean value.
     *
     * @param s            String to convert
     * @param emptyIsFalse if true, null and empty string is handled as false
     *
     * @return Boolean value
     *
     * @throws IllegalArgumentException when the string does not contain a valid boolean.
     */
    public static boolean toBoolean(@Nullable final String s,
                                    final boolean emptyIsFalse)
            throws IllegalArgumentException {
        if (s == null || s.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new IllegalArgumentException("Invalid boolean, s=`" + s + '`');
            }
        } else {
            switch (s.trim().toLowerCase()) {
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
                        throw new IllegalArgumentException("Invalid boolean, s=`" + s + '`');
                    }
            }
        }
    }

    /**
     * Translate the passed Object to a boolean value.
     *
     * @param o Object
     *
     * @return Resulting value
     */
    public static boolean toBoolean(@NonNull final Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Integer) {
            return (Integer) o != 0;
        }
        if (o instanceof Long) {
            return (Long) o != 0;
        }
        // lets see if its a String then
        return toBoolean(o.toString(), true);
    }

    @NonNull
    public String getKey() {
        return mKey;
    }

    public boolean isHidden() {
        return !mIsVisible;
    }

    @Nullable
    public DataValidator getValidator() {
        return mValidator;
    }

    /**
     * Accessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum addValidator(@NonNull final DataValidator validator) {
        if (mValidator != null && validator != mValidator) {
            throw new IllegalStateException("Datum '" + mKey + "' already has a validator");
        }
        mValidator = validator;
        return this;
    }

    boolean hasValidator() {
        return mValidator != null;
    }

    /** Accessor. */
    @Nullable
    public DataAccessor getAccessor() {
        return mAccessor;
    }

    /**
     * Accessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum addAccessor(@NonNull final DataAccessor accessor) {
        if (mAccessor != null && accessor != mAccessor) {
            throw new IllegalStateException("Datum '" + mKey + "' already has an Accessor");
        }
        mAccessor = accessor;
        return this;
    }

    /**
     * Get the raw Object for this Datum.
     *
     * @param data   Parent DataManager
     * @param bundle Raw data bundle
     *
     * @return The object data
     */
    @Nullable
    public Object get(@NonNull final DataManager data,
                      @NonNull final Bundle bundle) {
        if (mAccessor == null) {
            return bundle.get(mKey);
        } else {
            return mAccessor.get(data, this, bundle);
        }
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data
     */
    public boolean getBoolean(@NonNull final DataManager data,
                              @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.getBoolean(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return toBoolean(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum putBoolean(@NonNull final DataManager data,
                            @NonNull final Bundle bundle,
                            final boolean value) {
        if (mAccessor == null) {
            bundle.putBoolean(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data
     */
    public int getInt(@NonNull final DataManager data,
                      @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return (int) toLong(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum putInt(@NonNull final DataManager data,
                        @NonNull final Bundle bundle,
                        final int value) {
        if (mAccessor == null) {
            bundle.putInt(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data
     */
    public long getLong(@NonNull final DataManager data,
                        @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return toLong(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum putLong(@NonNull final DataManager data,
                         @NonNull final Bundle bundle,
                         final long value) {
        if (mAccessor == null) {
            bundle.putLong(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data
     */
    double getDouble(@NonNull final DataManager data,
                     @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return toDouble(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    Datum putDouble(@NonNull final DataManager data,
                    @NonNull final Bundle bundle,
                    final double value) {
        if (mAccessor == null) {
            bundle.putDouble(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data
     */
    float getFloat(@NonNull final DataManager data,
                   @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return (float) toDouble(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    Datum putFloat(@NonNull final DataManager data,
                   @NonNull final Bundle bundle,
                   final float value) {
        if (mAccessor == null) {
            bundle.putFloat(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Retrieve the data from the DataManager, translating and using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     *
     * @return Value of the data, can be empty, but never null
     */
    @NonNull
    public String getString(@NonNull final DataManager data,
                            @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        // any null gets turned into ""
        return toString(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     * @param value  string will be trimmed before storing.
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum putString(@NonNull final DataManager data,
                           @NonNull final Bundle bundle,
                           @NonNull final String value) {
        if (mAccessor == null) {
            bundle.putString(mKey, value.trim());
        } else {
            mAccessor.set(data, this, bundle, value.trim());
        }
        return this;
    }

    /**
     * Get the serializable object from the collection.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param data   Parent DataManager
     * @param bundle Raw data Bundle
     *
     * @return The data
     */
    @SuppressWarnings("unchecked")
    @Nullable
    <T extends Serializable> T getSerializable(@SuppressWarnings("unused")
                                               @NonNull final DataManager data,
                                               @NonNull final Bundle bundle) {
        if (mAccessor == null) {
            return (T) bundle.getSerializable(mKey);
        } else {
            throw new AccessorNotSupportedException("Serializable");
        }
    }

    /**
     * Set the serializable object in the collection.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param bundle Raw data Bundle
     * @param value  The serializable object
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    Datum putSerializable(@NonNull final Bundle bundle,
                          @NonNull final Serializable value) {
        if (mAccessor == null) {
            bundle.putSerializable(mKey, value);
        } else {
            throw new AccessorNotSupportedException("Serializable");
        }
        return this;
    }

    /**
     * Get the ArrayList<Parcelable> object from the collection.
     *
     * @param data   Parent DataManager
     * @param bundle Raw data Bundle
     *
     * @return The list, can be empty but never null
     */
    @NonNull
    <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final DataManager data,
                                                               @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }

        if (o == null) {
            return new ArrayList<>();
        }
        //noinspection unchecked
        return (ArrayList<T>) o;
    }

    /**
     * Set the ArrayList<Parcelable> object in the collection.
     *
     * @param bundle Raw data Bundle
     * @param value  The ArrayList<Parcelable> object
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    <T extends Parcelable> Datum putParcelableArrayList(@NonNull final DataManager data,
                                                        @NonNull final Bundle bundle,
                                                        @NonNull final ArrayList<T> value) {
        if (mAccessor == null) {
            bundle.putParcelableArrayList(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    /**
     * Get the ArrayList<String> object from the collection.
     *
     * @param data   Parent DataManager
     * @param bundle Raw data Bundle
     *
     * @return The list, can be empty but never null
     */
    @NonNull
    ArrayList<String> getStringArrayList(@NonNull final DataManager data,
                                         @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }

        if (o == null) {
            return new ArrayList<>();
        }
        //noinspection unchecked
        return (ArrayList<String>) o;
    }

    /**
     * Set the ArrayList<String> object in the collection.
     *
     * @param bundle Raw data Bundle
     * @param value  The ArrayList<String> object
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    Datum putStringArrayList(@NonNull final DataManager data,
                             @NonNull final Bundle bundle,
                             @NonNull final ArrayList<String> value) {
        if (mAccessor == null) {
            bundle.putStringArrayList(mKey, value);
        } else {
            mAccessor.set(data, this, bundle, value);
        }
        return this;
    }

    private static class AccessorNotSupportedException
            extends IllegalStateException {

        private static final long serialVersionUID = 2561013689178409200L;

        AccessorNotSupportedException(@NonNull final String typeDescription) {
            super("Accessor not supported for " + typeDescription + " objects");
        }
    }
}
