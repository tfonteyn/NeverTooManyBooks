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
 * Implements a property with an optional global value stored in preferences.
 *
 * Normally the LOCAL value is used, unless:
 * - we force to use the global only, see {@link #mIsGlobal}
 * - we don't have a local value set, and there is a global.
 *
 * @param <T> type of underlying Value
 *
 * @author Philip Warner
 */
public abstract class PropertyWithGlobalValue<T> extends Property<T> {

    /** Indicates that this instance is to use the global value instead of the local */
    private boolean mIsGlobal = false;

    PropertyWithGlobalValue(final @NonNull String uniqueId,
                            final @NonNull PropertyGroup group,
                            final @StringRes int nameResourceId) {
        super(uniqueId, group, nameResourceId);
    }

    PropertyWithGlobalValue(final @NonNull String uniqueId,
                    final @NonNull PropertyGroup group,
                    final @StringRes int nameResourceId,
                    final @Nullable T defaultValue) {
        super(uniqueId, group, nameResourceId, defaultValue);

    }

    /** Children must implement accessor for the global value */
    @Nullable
    protected abstract T getGlobalValue();

    /** Children must implement accessor for the global value */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    protected abstract PropertyWithGlobalValue<T> setGlobalValue(final @Nullable T value);

    /** Accessor for underlying (or global) value */
    @Nullable
    public T getValue() {
        if (mIsGlobal) {
            return getGlobalValue();
        }
        return super.getValue();
    }

    /** Accessor for for fully resolved/defaulted value */
    @Nullable
    public T getResolvedValue() {
        if (mIsGlobal) {
            return getGlobalValue();
        }

        if (mValue == null) {
            if (hasPreferenceKey()) {
                // we did not have a value, but we have a global one.
                return getGlobalValue();
            } else {
                // we did not have a value, nor a global one, so use the in-memory default.
                return getDefaultValue();
            }
        } else {
            // we had a non-null value
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
            setGlobalValue(value);
        }
        super.setValue(value);
        return this;
    }

    boolean isGlobal() {
        return mIsGlobal;
    }

    /**
     * declare this property to be a global.
     */
    @NonNull
    public PropertyWithGlobalValue<T> setIsGlobal(final boolean isGlobal) {
        // to be a global, a preference key MUST be set prior.
        if (isGlobal && !hasPreferenceKey()) {
            throw new IllegalStateException();
        }

        mIsGlobal = isGlobal;
        return this;
    }


    /**
     * Utility to check if the passed value == the default value
     */
    boolean isDefault(final @Nullable T value) {
        if (hasPreferenceKey() && !isGlobal())
            return (value == null);

        // We have a default value, and no global prefs
        return super.isDefault(value);
    }
}
