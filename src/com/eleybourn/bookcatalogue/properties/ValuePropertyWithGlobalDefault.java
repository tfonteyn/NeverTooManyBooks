/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.properties;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Implements a property with a default value stored in preferences or provided locally.
 *
 * @param <T>
 *
 * @author Philip Warner
 */
public abstract class ValuePropertyWithGlobalDefault<T> extends Property {
    /** Underlying value */
    private T mValue;
    /** Key in preferences for default value */
    private String mDefaultPrefKey;
    /** Default value, for case when not in preferences, or no preferences given */
    private T mDefaultValue;
    /** Indicates that this instance is to only use the global default */
    private boolean mIsGlobal = false;

    /**
     * Constructor
     *
     * @param uniqueId       Unique property name; just needs to be unique for the collection it belongs to. Simplest to use a pref name, if there is one.
     * @param group          PropertyGroup it belongs to
     * @param nameResourceId Resource ID for name string
     *
     * @param defaultValue   Default value used in case preferences is null, or returns null
     * @param preferenceKey  Key into Preferences for default value (may be null)
     * @param value          Current value (may be null)
     */
    ValuePropertyWithGlobalDefault(@NonNull final String uniqueId,
                                   @NonNull final PropertyGroup group,
                                   final int nameResourceId,
                                   @Nullable final T defaultValue, @Nullable final String preferenceKey, @Nullable final T value) {
        super(uniqueId, group, nameResourceId);
        mDefaultValue = defaultValue;
        mDefaultPrefKey = preferenceKey;
        mValue = value;
    }

    /**
     * Constructor
     *
     * @param uniqueId       Unique property name; just needs to be unique for the collection it belongs to. Simplest to use a pref name, if there is one.
     * @param group          PropertyGroup it belongs to
     * @param nameResourceId Resource ID for name string
     */
    ValuePropertyWithGlobalDefault(@NonNull final String uniqueId,
                                   @NonNull final PropertyGroup group,
                                   final int nameResourceId) {
        super(uniqueId, group, nameResourceId);
    }

    /** Children must implement accessor for global default */
    protected abstract T getGlobalDefault();

    /** Children must implement accessor for global default */
    @SuppressWarnings("UnusedReturnValue")
    protected abstract ValuePropertyWithGlobalDefault<T> setGlobalDefault(T value);

    /** Accessor for underlying (or global) value */
    public T get() {
        if (mIsGlobal)
            return getGlobalDefault();
        else
            return mValue;
    }

    /** Accessor for underlying (or global) value */
    public ValuePropertyWithGlobalDefault<T> set(T value) {
        mValue = value;
        if (mIsGlobal)
            setGlobalDefault(value);
        return this;
    }

    /** Accessor for for fully resolved/defaulted value */
    public T getResolvedValue() {
        if (mIsGlobal)
            return getGlobalDefault();
        else if (mValue == null) {
            if (hasGlobalDefault())
                return getGlobalDefault();
            else
                return mDefaultValue;
        } else
            return mValue;
    }

    public boolean isGlobal() {
        return mIsGlobal;
    }

    public ValuePropertyWithGlobalDefault<T> setGlobal(boolean isGlobal) {
        if (isGlobal && !hasGlobalDefault()) {
            throw new RuntimeException("Can not set a parameter to global if preference value has not been specified");
        }
        mIsGlobal = isGlobal;
        return this;
    }

    public String toString() {
        return (mValue == null ? null : mValue.toString());
    }

    T getDefaultValue() {
        return mDefaultValue;
    }

    String getPreferenceKey() {
        return mDefaultPrefKey;
    }

    public ValuePropertyWithGlobalDefault<T> setDefaultValue(T value) {
        mDefaultValue = value;
        return this;
    }

    public ValuePropertyWithGlobalDefault<T> setPreferenceKey(String key) {
        mDefaultPrefKey = key;
        return this;
    }

    /** Utility to check if a global default is available */
    public boolean hasGlobalDefault() {
        return (mDefaultPrefKey != null && !mDefaultPrefKey.isEmpty());
    }

    /** Utility to check if the current value IS the default value */
    boolean isDefault(T value) {
        if (hasGlobalDefault() && !isGlobal())
            return (value == null);

        // We have a default value, and no global prefs
        return value == null
                && mDefaultValue == null || value != null
                && mDefaultValue != null
                && value.equals(mDefaultValue);
    }
}
