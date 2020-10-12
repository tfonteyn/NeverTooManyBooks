/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.util.SparseArray;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bitmask converter routines.
 */
public final class BitUtils {

    private static final String BITMASK = "bitmask=";

    private BitUtils() {
    }

    /**
     * Convert a set where each <strong>String</strong> element represents one bit to a bitmask.
     *
     * @param set the set
     *
     * @return the value
     */
    @NonNull
    public static Integer from(@SuppressWarnings("TypeMayBeWeakened")
                               @NonNull final Set<String> set) {
        int tmp = 0;
        for (final String s : set) {
            tmp |= Integer.parseInt(s);
        }
        return tmp;
    }

    /**
     * Convert a set where each element represents one bit to a bitmask.
     *
     * @param set the set
     *
     * @return the value
     */
    @NonNull
    public static Integer from(@SuppressWarnings("TypeMayBeWeakened")
                               @NonNull final List<Integer> set) {
        int tmp = 0;
        for (final Integer value : set) {
            tmp |= value;
        }
        return tmp;
    }

    /**
     * Convert a bitmask.
     *
     * @param bitmask the value
     *
     * @return the set
     */
    @NonNull
    public static Set<String> toStringSet(@IntRange(from = 0, to = 0xFFFF)
                                          @NonNull final Integer bitmask) {
        if (bitmask < 0) {
            throw new IllegalArgumentException(BITMASK + bitmask);
        }

        final Set<String> set = new HashSet<>();
        int tmp = bitmask;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                set.add(String.valueOf(bit));
            }
            bit *= 2;
            // unsigned shift
            tmp = tmp >>> 1;
        }
        return set;
    }

    /**
     * Convert a bitmask.
     *
     * @param context Current context
     * @param map     Collection with bits mapped to resource IDs
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> toListOfStrings(@NonNull final Context context,
                                               @NonNull final Map<Integer, Integer> map,
                                               final long bitmask) {
        return map.entrySet()
                  .stream()
                  .filter(entry -> (entry.getKey() & bitmask) != 0)
                  .map(entry -> context.getString(entry.getValue()))
                  .collect(Collectors.toList());
    }

    /**
     * Convert a bitmask.
     *
     * @param map     Collection with bits mapped to strings
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> toListOfStrings(@NonNull final Map<Integer, String> map,
                                               final long bitmask) {
        return map.entrySet()
                  .stream()
                  .filter(entry -> (entry.getKey() & bitmask) != 0)
                  .map(Map.Entry::getValue)
                  .collect(Collectors.toList());
    }

    /**
     * @param context Current context
     * @param map     Collection with bits mapped to strings
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> toListOfStrings(@NonNull final Context context,
                                               @NonNull final SparseArray<Integer> map,
                                               final long bitmask) {
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < map.size(); i++) {
            if ((map.keyAt(i) & bitmask) != 0) {
                list.add(context.getString(map.valueAt(i)));
            }
        }
        return list;
    }

    /**
     * Convert a bitmask.
     *
     * @param map     Collection with bits mapped to strings
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> toListOfStrings(@NonNull final SparseArray<String> map,
                                               final long bitmask) {
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < map.size(); i++) {
            if ((map.keyAt(i) & bitmask) != 0) {
                list.add(map.valueAt(i));
            }
        }
        return list;
    }

    /**
     * Convert a bitmask to a list where each element represents one bit.
     *
     * @param bitmask the value
     *
     * @return the list
     */
    @NonNull
    public static List<Integer> toListOfIntegers(@IntRange(from = 0, to = 0xFFFF)
                                                 @NonNull final Integer bitmask) {
        if (bitmask < 0) {
            throw new IllegalArgumentException(BITMASK + bitmask);
        }

        final List<Integer> set = new LinkedList<>();
        int tmp = bitmask;
        int bit = 1;
        while (tmp != 0) {
            if ((tmp & 1) == 1) {
                set.add(bit);
            }
            bit *= 2;
            // unsigned shift
            tmp = tmp >>> 1;
        }
        return set;
    }
}
