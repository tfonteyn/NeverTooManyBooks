/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * Read-only interface that allows a method to take both a custom {@link CursorRow}
 * and a {@link DataManager} as an argument without having to distinguish between them.
 * <p>
 * <strong>Important</strong>:
 * {@link DataManager} will return default values if the key is not found.
 * {@link CursorRow} will throw an exception instead.
 */
public interface DataHolder {

    /**
     * Returns a Set containing the Strings used as keys in this DataHolder.
     * Note: this method is not strictly needed.
     *
     * @return an immutable Set with the keys
     */
    @NonNull
    Set<String> keySet();

    /**
     * Returns {@code true} if the given key is contained in the mapping
     * of this DataHolder.
     *
     * @param key Key of data object
     *
     * @return {@code true} if the key is part of the mapping, {@code false} otherwise
     */
    boolean contains(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     *
     * @param key Key of data object
     *
     * @return an int value
     */
    int getInt(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     *
     * @param key Key of data object
     *
     * @return a long value
     */
    long getLong(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     *
     * @param key Key of data object
     *
     * @return a double value
     */
    double getDouble(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     *
     * @param key Key of data object
     *
     * @return a float value
     */
    float getFloat(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     *
     * @param key Key of data object
     *
     * @return a boolean value
     */
    boolean getBoolean(@NonNull String key);

    /**
     * Returns the value associated with the given key.
     * If the key is not present, or the value was {@code null}, an empty String will be returned.
     * <p>
     * If the value was not a String, implementations <strong>must</strong> stringify it.
     *
     * @param key Key of data object
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
     * If the key is not present, or the value was {@code null}, the 'defValue' will be returned.
     * <p>
     * If the value was not a String, implementations <strong>must</strong> stringify it.
     *
     * @param key      Key of data object
     * @param defValue (optional) default value
     *
     * @return Value of the data, or the 'defValue'
     */
    @Nullable
    String getString(@NonNull String key,
                     @Nullable String defValue);

    /**
     * Get a {@link Parcelable} {@link ArrayList} from the collection.
     *
     * @param key Key of data object
     * @param <T> type of objects in the list
     *
     * @return The list, can be empty, but never {@code null}
     */
    @NonNull
    <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull String key);
}
