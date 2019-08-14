/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
import com.hardbacknutter.nevertoomanybooks.datamanager.Datum;

/**
 * Interface implemented for custom data access.
 * <p>
 * Usage:
 * Extends your object from {@link DataManager}.
 * During initialisation, call setAccessor(key, new DataAccessor(dataKey...))
 * The 'key' is the artificial key to define the DataAccessor.
 * The 'dataKey' is the ACTUAL key into the rawData.
 * <p>
 * Access your object with {@link DataManager#get(String)}.
 * The dataManager will use that key to get a {@link Datum} (still with that key)
 * The Datum will call the Accessor (this class).
 * This class get can then use the dataKey to access the rawData
 * <p>
 * pro: very nice and clean way to add artificial data (a bit in a bitmask, a composite object, ...)
 * con: lots of layers on layers bring a performance penalty.
 * <p>
 * Currently only used for:
 * - bitmask access to a single bit (set and get) (2x)
 * - boolean automatic-transformation when loading values from the database (2x)
 */
public interface DataAccessor<TIN, TOUT> {

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
     * @param rawData the bundle into which the value is stored
     *
     * @return the raw value, or {@code null} if not present
     */
    @Nullable
    TOUT get(@NonNull Bundle rawData);

    /**
     * Set the specified value.
     *
     * @param rawData the bundle into which the value should be stored
     * @param value   the actual value to set
     */
    void put(@NonNull Bundle rawData,
             @NonNull TIN value);

    /**
     * Check if the key is present.
     */
    boolean isPresent(@NonNull Bundle rawData);
//    boolean isPresent(@NonNull Bundle rawData) {
//        return rawData.containsKey(mKey);
//    }
}
