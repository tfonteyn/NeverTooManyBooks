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
package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * Subclass of HashMap with an add(...) method that ensures values are unique.
 *
 * @param <K> Type of Key values
 * @param <V> Type of data values
 */
public class UniqueMap<K, V>
        extends HashMap<K, V> {

    private static final long serialVersionUID = 425468000396955263L;

    /**
     * @param key   Key for new value
     * @param value Data for new value
     */
    @Override
    @NonNull
    @CallSuper
    public V put(@NonNull final K key,
                 @NonNull final V value) {
        if (super.put(key, value) != null) {
            throw new IllegalArgumentException("Map already contains key value: " + key);
        }
        /*
         * collection contract says to return the previous value associated with the key,
         * or {@code null} if there was no mapping for that key.
         */
        return value;
    }
}
