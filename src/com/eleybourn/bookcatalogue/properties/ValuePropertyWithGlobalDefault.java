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
import android.support.annotation.StringRes;

/**
 * Implements a property with a default value stored in preferences or provided locally.
 *
 * @param <T>
 *
 * @author Philip Warner
 */
public abstract class ValuePropertyWithGlobalDefault<T> extends Property {
    /** Underlying value */
    private T mValue = null;
    /** Key in preferences for default value */
    private String mDefaultPrefKey = null;
    /** Default value, for case when not in preferences, or no preferences given */
    private T mDefaultValue = null;
    /** Indicates that this instance is to only use the global default */
    private boolean mIsGlobal = false;

    /**
     * Constructor
     *
     * @param uniqueId       Unique property name; just needs to be unique for the collection it belongs to.
     *                       Simplest to use a pref name, if there is one.
     * @param group          PropertyGroup it belongs to
     * @param nameResourceId Resource ID for name string
     */
    ValuePropertyWithGlobalDefault(@NonNull final String uniqueId,
                                   @NonNull final PropertyGroup group,
                                   @StringRes final int nameResourceId) {
        super(uniqueId, group, nameResourceId);
    }

    /**
     * Constructor
     *
     * @param uniqueId       Unique property name; just needs to be unique for the collection it belongs to.
     *                       Simplest to use a pref name, if there is one.
     * @param group          PropertyGroup it belongs to
     * @param nameResourceId Resource ID for name string
     * @param defaultValue   Default value used in case preferences is null, or returns null
     */
    ValuePropertyWithGlobalDefault(@NonNull final String uniqueId,
                                   @NonNull final PropertyGroup group,
                                   @StringRes final int nameResourceId,
                                   @NonNull final T defaultValue) {
        super(uniqueId, group, nameResourceId);
        mDefaultValue = defaultValue;
    }

    /**
     * Constructor
     *
     * @param uniqueId       Unique property name; just needs to be unique for the collection it belongs to. Simplest to use a pref name, if there is one.
     * @param group          PropertyGroup it belongs to
     * @param nameResourceId Resource ID for name string
     * @param defaultValue   Default value used in case preferences is null, or returns null
     * @param value          Current value (may be null)
     */
    ValuePropertyWithGlobalDefault(@NonNull final String uniqueId,
                                   @NonNull final PropertyGroup group,
                                   @StringRes final int nameResourceId,
                                   @Nullable final T defaultValue,
                                   @Nullable final T value) {
        super(uniqueId, group, nameResourceId);
        mDefaultValue = defaultValue;
        mValue = value;
    }

    /** Children must implement accessor for global default */
    @Nullable
    protected abstract T getGlobalDefault();

    /** Children must implement accessor for global default */
    @SuppressWarnings("UnusedReturnValue")
    protected abstract ValuePropertyWithGlobalDefault<T> setGlobalDefault(@Nullable final T value);

    /** Accessor for underlying (or global) value */
    @Nullable
    public T get() {
        if (mIsGlobal) {
            return getGlobalDefault();
        } else {
            return mValue;
        }
    }

    /** Accessor for underlying (or global) value */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public ValuePropertyWithGlobalDefault<T> set(@Nullable final T value) {
        mValue = value;
        if (mIsGlobal) {
            setGlobalDefault(value);
        }
        return this;
    }

    /** Accessor for for fully resolved/defaulted value */
    @Nullable
    public T getResolvedValue() {
        if (mIsGlobal) {
            return getGlobalDefault();
        } else if (mValue == null) {
            if (hasGlobalDefault()) {
                return getGlobalDefault();
            } else {
                return mDefaultValue;
            }
        } else {
            return mValue;
        }
    }

    public boolean isGlobal() {
        return mIsGlobal;
    }

    @NonNull
    public ValuePropertyWithGlobalDefault<T> setGlobal(final boolean isGlobal) {
        if (isGlobal && !hasGlobalDefault()) {
            throw new IllegalStateException();
        }
        mIsGlobal = isGlobal;
        return this;
    }

    @Nullable
    public String toString() {
        return (mValue == null ? null : mValue.toString());
    }

    @Nullable
    T getDefaultValue() {
        return mDefaultValue;
    }

    @NonNull
    public ValuePropertyWithGlobalDefault<T> setDefaultValue(@Nullable final T value) {
        mDefaultValue = value;
        return this;
    }

    @NonNull
    String getPreferenceKey() {
        if (mDefaultPrefKey == null) {
            throw new IllegalStateException();
        }
        return mDefaultPrefKey;
    }

    @NonNull
    public ValuePropertyWithGlobalDefault<T> setPreferenceKey(@NonNull final String key) {
        mDefaultPrefKey = key;
        return this;
    }

    /** Utility to check if a global default is available */
    public boolean hasGlobalDefault() {
        return (mDefaultPrefKey != null && !mDefaultPrefKey.isEmpty());
    }

    /** Utility to check if the current value IS the default value */
    boolean isDefault(@Nullable final T value) {
        if (hasGlobalDefault() && !isGlobal()) {
            return (value == null);
        }

        // We have a default value, and no global prefs
        return value == null
                && mDefaultValue == null || value != null
                && value.equals(mDefaultValue);
    }
}
