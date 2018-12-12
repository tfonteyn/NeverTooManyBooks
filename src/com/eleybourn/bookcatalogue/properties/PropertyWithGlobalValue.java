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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Objects;

/**
 * Implements a property with an optional global value stored in preferences.
 *
 * Normally the LOCAL value is used, unless:
 * - we force to use the global only, see {@link #setIsGlobal}
 * - we don't have a local value set, and there is a global. See {@link #hasGlobal()}
 *
 * @param <T> type of underlying Value
 *
 * @author Philip Warner
 */
public abstract class PropertyWithGlobalValue<T> extends Property<T> {

    /** Key in preferences for optional global persistence */
    @Nullable
    private String mPreferenceKey = null;

    /** Indicates that this instance is to use the global value instead of the local */
    private boolean mIsGlobal = false;

    PropertyWithGlobalValue(final @NonNull PropertyGroup group,
                            final @StringRes int nameResourceId,
                            final @NonNull T defaultValue) {
        super(nameResourceId, group, defaultValue);
    }

    /** Children must implement accessor for the global value */
    @NonNull
    protected abstract T getGlobalValue();

    /** Children must implement accessor for the global value */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    protected abstract PropertyWithGlobalValue<T> setGlobalValue(final @NonNull T value);

    /**
     * Accessor for underlying value
     *
     * Always think twice when using this method.
     * It should only be used for *editing* the value.
     *
     * If you want to *use* the value, use {@link #getResolvedValue()}
     */
    @Nullable
    public T getValue() {
        if (mIsGlobal) {
            return getGlobalValue();
        }
        return super.getValue();
    }

    /** Accessor for for fully resolved/defaulted value */
    @NonNull
    public T getResolvedValue() {
        if (mIsGlobal) {
            return getGlobalValue();
        }

        if (mValue == null) {
            if (hasGlobal()) {
                // we did not have a value, but we have a global one.
                return getGlobalValue();
            } else {
                // we did not have a value, nor a global one in prefs yet, so use the default.
                return getDefaultValue();
            }
        } else {
            // NonNull
            return mValue;
        }
    }

    /**
     * Set the underlying value.
     * If this property is declared a global, also set the global value
     */
    @NonNull
    public PropertyWithGlobalValue<T> setValue(final @Nullable T value) {
        if (mIsGlobal) {
            Objects.requireNonNull(value);
            setGlobalValue(value);
        }
        super.setValue(value);
        return this;
    }

    boolean isGlobal() {
        return mIsGlobal;
    }

    /**
     * Declare this property to be a global.
     * A preference key MUST be set prior.
     */
    @NonNull
    public PropertyWithGlobalValue<T> setIsGlobal(final boolean isGlobal) {
        // to be a global, a preference key MUST be set prior.
        if (isGlobal && !hasGlobal()) {
            throw new IllegalStateException();
        }

        mIsGlobal = isGlobal;
        return this;
    }

    @NonNull
    String getPreferenceKey() {
        return Objects.requireNonNull(mPreferenceKey);
    }

    @NonNull
    public Property<T> setPreferenceKey(final @NonNull String key) {
        mPreferenceKey = key;
        return this;
    }
    /** check if there is a preference key for persisting the value */
    public boolean hasGlobal() {
        return (mPreferenceKey != null && !mPreferenceKey.isEmpty());
    }

    /**
     * Utility to check if the passed value == the default value
     */
    boolean isDefault(final @Nullable T value) {
        if (hasGlobal() && !mIsGlobal)
            return (value == null);

        // We have a default value, and no global prefs
        return super.isDefault(value);
    }
}
