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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.validators.DataValidator;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class to manage storage and retrieval of a piece of data from a bundle as well as
 * ancillary details such as visibility.
 *
 * Note: In theory, all strings PUT into the collection will get automatically trimmed.
 *
 * @author pjw
 */
public class Datum {
    /** Key of this datum */
    @NonNull
    private final String mKey;
    /** True if data should be visible */
    private final boolean mIsVisible;
    /** Validator for this Datum */
    @Nullable
    private DataValidator mValidator;
    /** Accessor for this Datum (eg. the datum might be a bit in a mask field, or a composite read-only value */
    private DataAccessor mAccessor;

    /**
     * Constructor
     *
     * @param key       Key of this datum
     * @param visible   True if data should be visible
     */
    public Datum(final @NonNull String key, final boolean visible) {
        mKey = key;
        mIsVisible = visible;
    }

    /**
     * Translate the passed object to a Long value
     *
     * @param o Object
     *
     * @return Resulting value (null or empty becomes 0)
     */
    public static long toLong(final @Nullable Object o) {
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
     * Translate the passed object to a Double value
     *
     * @param o Object
     *
     * @return Resulting value (null or empty becomes 0)
     */
    private static double toDouble(final @Nullable Object o) {
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
     * Format the passed bundle in a way that is convenient for display
     *
     * @param bundle Bundle to format, strings will be trimmed before adding
     *
     * @return Formatted string
     */
    @NonNull
    public static String toString(final @NonNull Bundle bundle) {
        StringBuilder sb = new StringBuilder();
        for (String k : bundle.keySet()) {
            sb.append(k).append("->");
            try {
                Object o = bundle.get(k);
                if (o != null) {
                    sb.append(String.valueOf(o).trim());
                }
            } catch (Exception e) {
                sb.append("<<").append(R.string.unknown).append(">>");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Translate the passed object to a String value
     *
     * @param o Object
     *
     * @return Resulting value (null becomes empty)
     */
    @NonNull
    public static String toString(final @Nullable Object o) {
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Utility function to convert string to boolean
     *
     * @param s            String to convert
     * @param emptyIsFalse if true, null and empty string is handled as false
     *
     * @return Boolean value
     */
    public static boolean toBoolean(final @Nullable String s, final boolean emptyIsFalse) {
        if (s == null || s.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new RTE.IllegalTypeException("Not a valid boolean value, s=`" + s + "`");
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
                        throw new RTE.IllegalTypeException("Not a valid boolean value, s=`" + s + "`");
                    }
            }
        }
    }

    /**
     * Translate the passed object to a boolean value
     *
     * @param o Object
     *
     * @return Resulting value
     */
    public static boolean toBoolean(final @NonNull Object o) {
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
    public Datum addValidator(final @NonNull DataValidator validator) {
        if (mValidator != null && validator != mValidator) {
            throw new IllegalStateException("Datum '" + mKey + "' already has a validator");
        }
        mValidator = validator;
        return this;
    }

    boolean hasValidator() {
        return mValidator != null;
    }

    /** Accessor */
    @Nullable
    public DataAccessor getAccessor() {
        return mAccessor;
    }

    /**
     * Accessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum addAccessor(final @NonNull DataAccessor accessor) {
        if (mAccessor != null && accessor != mAccessor) {
            throw new IllegalStateException("Datum '" + mKey + "' already has an Accessor");
        }
        mAccessor = accessor;
        return this;
    }

    /**
     * Get the raw Object for this Datum
     *
     * @param data   Parent DataManager
     * @param bundle Raw data bundle
     *
     * @return The object data
     */
    @Nullable
    public Object get(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    public boolean getBoolean(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    public Datum putBoolean(final @NonNull DataManager data, final @NonNull Bundle bundle, final boolean value) {
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
    public int getInt(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    public Datum putInt(final @NonNull DataManager data, final @NonNull Bundle bundle, final int value) {
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
    public long getLong(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    public Datum putLong(final @NonNull DataManager data, final @NonNull Bundle bundle, final long value) {
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
    double getDouble(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    Datum putDouble(final @NonNull DataManager data, final @NonNull Bundle bundle, final double value) {
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
    float getFloat(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    Datum putFloat(final @NonNull DataManager data, final @NonNull Bundle bundle, final float value) {
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
    public String getString(final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    public Datum putString(final @NonNull DataManager data, final @NonNull Bundle bundle, final @NonNull String value) {
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
    <T extends Serializable> T getSerializable(@SuppressWarnings("unused") final @NonNull DataManager data, final @NonNull Bundle bundle) {
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
    Datum putSerializable(final @NonNull Bundle bundle, final @NonNull Serializable value) {
        if (mAccessor == null) {
            bundle.putSerializable(mKey, value);
        } else {
            throw new AccessorNotSupportedException("Serializable");
        }
        return this;
    }

    /**
     * Get the ArrayList<String> object from the collection.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param data   Parent DataManager
     * @param bundle Raw data Bundle
     *
     * @return The data
     */
    @SuppressWarnings("unused")
    @Nullable
    ArrayList<String> getStringArrayList(@SuppressWarnings("unused") final @NonNull DataManager data, final @NonNull Bundle bundle) {
        if (mAccessor == null) {
            return bundle.getStringArrayList(mKey);
        } else {
            throw new AccessorNotSupportedException("ArrayList<String>");
        }
    }

    /**
     * Set the ArrayList<String> object in the collection.
     * We currently do not use a Datum for special access.
     * TODO: Consider how to use an accessor
     *
     * @param bundle Raw data Bundle
     * @param value  The ArrayList<String> object
     *
     * @return The data manager for chaining
     */
    @SuppressWarnings("unused")
    @NonNull
    Datum putStringArrayList(final @NonNull Bundle bundle, final @NonNull ArrayList<String> value) {
        if (mAccessor == null) {
            bundle.putStringArrayList(mKey, value);
        } else {
            throw new AccessorNotSupportedException("ArrayList<String>");
        }
        return this;
    }

    private class AccessorNotSupportedException extends IllegalStateException {

        AccessorNotSupportedException(final String typeDescription) {
            super("Accessor not supported for " + typeDescription + " objects");
        }
    }
}
