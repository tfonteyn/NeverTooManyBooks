/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.datamanager.accessors;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.datamanager.Datum;

/**
 * A bitmask is read/written to the database as a long.
 * We need it as a boolean.
 * <p>
 * Transform setting/resetting a single bit.
 * A 'get' returns a Boolean
 * A 'set will store it back as a long with the bit set/reset.
 */
public class BitmaskDataAccessor
        implements DataAccessor {

    /** this is the ACTUAL key into the 'rawData' object. */
    private final String mKey;
    /** the bit we're handling in this object. */
    private final long mBitmask;

    /**
     * Constructor.
     *
     * @param key     The key for the actual data
     * @param bitmask to manage
     */
    public BitmaskDataAccessor(@NonNull final String key,
                               final long bitmask) {
        mKey = key;
        mBitmask = bitmask;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final Bundle rawData) {
        return (Datum.toLong(rawData.get(mKey)) & mBitmask) != 0;
    }

    @Override
    public void put(@NonNull final Bundle rawData,
                    @NonNull final Object value) {

        long bits = Datum.toLong(rawData.get(mKey));

        if (Datum.toBoolean(value)) {
            // set the bit
            bits |= mBitmask;
        } else {
            // or reset the bit
            bits &= ~mBitmask;
        }

        rawData.putLong(mKey, bits);
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}
