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
package com.hardbacknutter.nevertoomanybooks.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Interface implemented for custom data access.
 * <p>
 * Usage:
 * Extends your object from {@link DataManager}.
 * During initialisation, call setAccessor(key, new DataAccessor(dataKey...))
 * The 'key' is the key to define the DataAccessor.
 * The 'dataKey' is the actual key for the data.
 * These will often be identical, but will be different for virtual fields.
 * <p>
 * Access your object with {@link DataManager#get(String)}.
 */
public interface DataAccessor {

    /*
    // example code:
    String mDataKey;

    DataAccessor(String dataKey) {
        mDataKey = dataKey;
    }
    */

    /**
     * Get the value.
     *
     * @param source the bundle from which the value should be read.
     *
     * @return the raw value, or {@code null} if not present
     */
    @Nullable
    Object get(@NonNull Bundle source);

    /**
     * Set the specified value.
     *
     * @param target the bundle into which the value should be stored.
     * @param value  the actual value to set
     */
    void put(@NonNull Bundle target,
             @NonNull Object value);
}
