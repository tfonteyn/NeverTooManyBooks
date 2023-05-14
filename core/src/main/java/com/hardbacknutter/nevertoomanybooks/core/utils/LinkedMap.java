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

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.Map;

public final class LinkedMap {

    private LinkedMap() {
    }

    /**
     * Check the two maps for equality, including the order in which the elements appear.
     *
     * @param left  map1
     * @param right map2
     * @param <K>   type of key
     * @param <V>   type of value
     *
     * @return {@code true} if both the order and the elements are equal.
     */
    public static <K, V> boolean equals(@NonNull final Map<K, V> left,
                                        @NonNull final Map<K, V> right) {
        final Iterator<Map.Entry<K, V>> leftItr = left.entrySet().iterator();
        final Iterator<Map.Entry<K, V>> rightItr = right.entrySet().iterator();

        while (leftItr.hasNext() && rightItr.hasNext()) {
            final Map.Entry<K, V> leftEntry = leftItr.next();
            final Map.Entry<K, V> rightEntry = rightItr.next();

            // AbstractList does null checks here but for maps
            // we can assume you never get null entries
            if (!leftEntry.equals(rightEntry)) {
                return false;
            }
        }
        return !(leftItr.hasNext() || rightItr.hasNext());
    }
}
