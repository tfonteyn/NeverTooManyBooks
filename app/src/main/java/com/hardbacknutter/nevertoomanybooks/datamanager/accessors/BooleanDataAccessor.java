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

import com.hardbacknutter.nevertoomanybooks.datamanager.Datum;

/**
 * The database value is stored as an int (0 or 1). Transform to/from boolean
 */
public class BooleanDataAccessor
        implements DataAccessor {

    /** this is the ACTUAL key into the 'rawData' object. */
    private final String mKey;

    /**
     * Constructor.
     *
     * @param key The key for the actual data
     */
    public BooleanDataAccessor(@NonNull final String key) {
        mKey = key;
    }

    @NonNull
    @Override
    public Boolean get(@NonNull final Bundle rawData) {
        return Datum.toBoolean(rawData.get(mKey));
    }

    @Override
    public void put(@NonNull final Bundle rawData,
                    @NonNull final Object value) {
        rawData.putBoolean(mKey, Datum.toBoolean(value));
    }

    @Override
    public boolean isPresent(@NonNull final Bundle rawData) {
        return rawData.containsKey(mKey);
    }
}
