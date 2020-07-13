/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Read-only interface that allows a method to take both a custom {@link Cursor}
 * and a {@link DataManager} as an argument without having to distinguish between them.
 */
public interface DataHolder {

    /**
     * Returns a Set containing the Strings used as keys in this DataHolder.
     * Note: this method is not strictly needed.
     *
     * @return a Set of String keys
     */
    @NonNull
    Set<String> keySet();

    /**
     * Returns {@code true} if the given key is contained in the mapping
     * of this DataHolder.
     *
     * @param key a String key
     *
     * @return {@code true} if the key is part of the mapping, {@code false} otherwise
     */
    boolean contains(@NonNull String key);

    /**
     * Returns the value associated with the given key, or {@code 0} if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     *
     * @return an int value
     */
    int getInt(@NonNull String key);

    /**
     * Returns the value associated with the given key, or {@code 0L} if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     *
     * @return a long value
     */
    long getLong(@NonNull String key);

    /**
     * Returns the value associated with the given key, or {@code 0.0} if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     *
     * @return a double value
     */
    double getDouble(@NonNull String key);

    /**
     * Returns the value associated with the given key, or {@code false} if
     * no mapping of the desired type exists for the given key.
     *
     * @param key a String
     *
     * @return a boolean value
     */
    boolean getBoolean(@NonNull String key);

    /**
     * Returns the value associated with the given key, or {@code null} if
     * no mapping of the desired type exists for the given key or a {@code null}
     * value is explicitly associated with the key.
     *
     * @param key a String, or {@code null}
     *
     * @return a String value, or {@code null}
     */
    @NonNull
    String getString(@NonNull String key);
}
