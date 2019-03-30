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
package com.eleybourn.bookcatalogue.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * Interface implemented for custom data access.
 * <p>
 * <p>
 * Usage:
 * Extends your object from DataManager.
 * During initialisation, call setAccessor(key, new DataAccessor(...))
 * The Accessor needs to be initialised with the ACTUAL key into the rawData. See example below.
 * <p>
 * Access your object with get(key); i.e. same key used in "setAccessor(key, accessor)"
 * The dataManager will use that key to get a Datum (still with that key)
 * The Datum will call the Accessor (this class).
 * This class.get can then use the mRawDataKey to access the rawData
 * <p>
 * pro: very nice and clean way to add artificial data (a bit in a bitmask, a composite object, ...)
 * con: lots of layers on layers bring a performance penalty.
 * <p>
 * Currently used for:
 * - bitmask access to a single bit
 * - boolean/int transformation
 * <p>
 * TODO: speed this up
 *
 * @author pjw
 */
public interface DataAccessor {

    /*
    // example code:
    String mRawDataKey;

    DataAccessor(String key) {
        mRawDataKey = key;
    }
    */

    /**
     * Get the value.
     *
     * @param dataManager the parent collection
     * @param rawData     the bundle into which the value should be stored
     * @param datum       info about the data we're handling
     *
     * @return the raw value, or null if not present
     */
    @Nullable
    Object get(@NonNull DataManager dataManager,
               @NonNull Bundle rawData,
               @NonNull Datum datum);

    /**
     * Set the specified value.
     *
     * @param dataManager the parent collection
     * @param rawData     the bundle into which the value should be stored
     * @param datum       info about the data we're handling
     * @param value       the actual value to set
     */
    void put(@NonNull DataManager dataManager,
             @NonNull Bundle rawData,
             @NonNull Datum datum,
             @NonNull Object value);

    /**
     * Check if the key is present.
     */
    boolean isPresent(@NonNull Bundle rawData);
}