/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

/**
 * Wraps a HashMap with {@link #put(Object, Object)} method that ensures values are unique.
 *
 * @param <K> Type of Key values
 * @param <V> Type of data values
 */
public class UniqueMap<K, V>
        extends WrappedMap<K, V> {

    public UniqueMap() {
        super(new HashMap<>());
    }

    /**
     * Associates the specified value with the specified key in this map.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return always {@code null}
     *
     * @throws IllegalArgumentException if the key was already present
     */
    @Override
    @Nullable
    @CallSuper
    public V put(@NonNull final K key,
                 @NonNull final V value) {
        if (super.put(key, value) != null) {
            throw new IllegalArgumentException("Map already contains key value: " + key);
        }
        /*
         * collection contract says to return the previous value associated with the key,
         * or {@code null} if there was no mapping for that key.
         * Which in our case means always {@code null}
         */
        return null;
    }
}
