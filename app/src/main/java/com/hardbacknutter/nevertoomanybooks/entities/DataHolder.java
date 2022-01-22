/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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
     * Returns the value associated with the given key.
     * A {@code null} value will be returned as an empty String.
     * <p>
     * If the value was not a String, implementations <strong>must</strong> stringify it.
     *
     * @param key a String
     *
     * @return Value of the data, can be empty, but never {@code null}
     */
    @NonNull
    default String getString(@NonNull final String key) {
        //noinspection ConstantConditions
        return getString(key, "");
    }

    /**
     * Returns the value associated with the given key.
     * A {@code null} value will be substituted with the 'defValue'.
     * <p>
     * If the value was not a String, implementations <strong>must</strong> stringify it.
     *
     * @param key a String
     *
     * @return Value of the data, or the 'defValue'
     */
    @Nullable
    String getString(@NonNull String key,
                     @Nullable String defValue);

    /**
     * Get a {@link Parcelable} {@link ArrayList} from the collection.
     * <p>
     * This default implementation simply returns an empty list.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    default <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key) {
        return new ArrayList<>();
    }
}
