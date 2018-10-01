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

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class to manage storage and retrieval of a piece of data from a bundle as well as
 * ancillary details such as visibility.
 *
 * In theory, all strings PUT into the collection will get automatically trimmed.
 *
 * @author pjw
 */
public class Datum {
    /** Key of this datum */
    private final String mKey;
    /** True if data should be visible */
    private final boolean mIsVisible;
    /** Validator for this Datum */
    private DataValidator mValidator;
    /** Accessor for this Datum (eg. the datum might be a bit in a mask field, or a composite read-only value */
    private DataAccessor mAccessor;

    /**
     * Constructor
     *
     * @param key       Key of this datum
     * @param validator Validator for this Datum
     * @param visible   True if data should be visible
     */
    public Datum(@NonNull final String key, @Nullable final DataValidator validator, final boolean visible) {
        mKey = key;
        mValidator = validator;
        mIsVisible = visible;
    }

    /**
     * Translate the passed object to a Long value
     *
     * @param o Object
     *
     * @return Resulting value
     */
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
     * Translate the passed object to a Double value
     *
     * @param o Object
     *
     * @return Resulting value
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
     * Format the passed bundle in a way that is convenient for display
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
     * @return Resulting value
     */
    @NonNull
    private static String toString(@Nullable final Object o) {
        return o == null ? "" : o.toString().trim();
    }

    /**
     * Utility function to convert string to boolean
     *
     * @param s            String to convert
     * @param emptyIsFalse if true, empty string is handled as false
     *
     * @return Boolean value
     */
    public static boolean toBoolean(@Nullable final String s, final boolean emptyIsFalse) {
        if (s == null || s.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new RuntimeException("Not a valid boolean value");
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
                    } catch (Exception e) {
                        throw new RuntimeException("Not a valid boolean value");
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

    public String getKey() {
        return mKey;
    }

    public boolean isHidden() {
        return !mIsVisible;
    }

    public DataValidator getValidator() {
        return mValidator;
    }

    /**
     * Accessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Datum setValidator(@NonNull final DataValidator validator) {
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
    public DataAccessor getAccessor() {
        return mAccessor;
    }

    /**
     * Accessor. Protected against being set twice.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Datum setAccessor(@NonNull final DataAccessor accessor) {
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
    public Object get(@NonNull final DataManager data, @NonNull final Bundle bundle) {
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
    public boolean getBoolean(@NonNull final DataManager data, @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.getBoolean(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        try {
            return (Boolean) o;
        } catch (ClassCastException e) {
            return toBoolean(o);
        }
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
    public Datum putBoolean(@NonNull final DataManager data, @NonNull final Bundle bundle, final boolean value) {
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
    public int getInt(@NonNull final DataManager data, @NonNull final Bundle bundle) {
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
    public Datum putInt(@NonNull final DataManager data, @NonNull final Bundle bundle, final int value) {
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
    public long getLong(@NonNull final DataManager data, @NonNull final Bundle bundle) {
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
    public Datum putLong(@NonNull final DataManager data, @NonNull final Bundle bundle, final long value) {
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
    double getDouble(@NonNull final DataManager data, @NonNull final Bundle bundle) {
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
    Datum putDouble(@NonNull final DataManager data, @NonNull final Bundle bundle, final double value) {
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
    float getFloat(@NonNull final DataManager data, @NonNull final Bundle bundle) {
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
    Datum putFloat(@NonNull final DataManager data, @NonNull final Bundle bundle, final float value) {
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
     * @return Value of the data
     */
    public String getString(@NonNull final DataManager data, @NonNull final Bundle bundle) {
        Object o;
        if (mAccessor == null) {
            o = bundle.get(mKey);
        } else {
            o = mAccessor.get(data, this, bundle);
        }
        return toString(o);
    }

    /**
     * Store the data in the DataManager, using Accessor as necessary.
     *
     * @param data   Parent collection
     * @param bundle Raw data
     * @param value string will be trimmed before storing.
     *
     * @return This Datum, for chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Datum putString(@NonNull final DataManager data, @NonNull final Bundle bundle, @NonNull final String value) {
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
    @Nullable
    Serializable getSerializable(@SuppressWarnings("unused") @NonNull final DataManager data, @NonNull final Bundle bundle) {
        if (mAccessor == null) {
            return bundle.getSerializable(mKey);
        } else {
            throw new RuntimeException("Accessor not supported for serializable objects");
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
    public Datum putSerializable(@NonNull final Bundle bundle, @NonNull final Serializable value) {
        if (mAccessor == null) {
            bundle.putSerializable(mKey, value);
        } else {
            throw new RuntimeException("Accessor not supported for serializable objects");
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
    @Nullable
    ArrayList<String> getStringArrayList(@SuppressWarnings("unused") @NonNull final DataManager data, @NonNull final Bundle bundle) {
        if (mAccessor == null) {
            return bundle.getStringArrayList(mKey);
        } else {
            throw new RuntimeException("Accessor not supported for ArrayList<String> objects");
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
    @SuppressWarnings("UnusedReturnValue")
    Datum putStringArrayList(@NonNull final Bundle bundle, @NonNull final ArrayList<String> value) {
        if (mAccessor == null) {
            bundle.putStringArrayList(mKey, value);
        } else {
            throw new RuntimeException("Accessor not supported for ArrayList<String> objects");
        }
        return this;
    }
}
