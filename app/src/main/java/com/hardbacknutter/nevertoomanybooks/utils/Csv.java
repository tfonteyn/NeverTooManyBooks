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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CSV utilities.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Create a CSV list String from the passed collection.
     * A {@code null} element is morphed into "".
     * This can be avoided by using {@link #join(CharSequence, Iterable, Formatter)} and
     * providing a {@link Formatter}.
     *
     * @param delimiter  e.g. "," or ", " etc...
     * @param collection collection
     * @param <E>        type of elements
     *
     * @return csv string, can be empty, but never {@code null}.
     *
     * @deprecated use {@link TextUtils#join(CharSequence, Iterable)} instead.
     */
    @Deprecated
    @NonNull
    public static <E> String join(@NonNull final CharSequence delimiter,
                                  @NonNull final Iterable<E> collection) {
        return TextUtils.join(delimiter, collection);
    }

    /**
     * Create a CSV list String from the passed collection.
     * A {@code null} element is morphed into "".
     * This can be avoided by providing a {@link Formatter}.
     *
     * @param delimiter  e.g. "," or ", " etc...
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or {@code null} for none.
     * @param <E>        type of elements
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delimiter,
                                  @NonNull final Iterable<E> collection,
                                  @Nullable final Formatter<E> formatter) {
        return join(delimiter, collection, false, null, formatter);
    }

    /**
     * Create a CSV list String from the passed collection.
     * Uses String.valueOf(element).trim()
     * This means that the "null" string is used for {@code null} elements.
     * (but no exceptions thrown).
     * This can be avoided by providing a {@link Formatter}.
     *
     * @param delimiter        e.g. "," or ", " etc...
     * @param collection       collection
     * @param skipEmptyStrings Flag skip null/empty values.
     * @param prefix           (optional) prefix that will be added to each element.
     *                         Caller is responsible to add spaces if desired.
     * @param formatter        (optional) formatter to use on each element,
     *                         or {@code null} for none.
     * @param <E>              type of elements
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delimiter,
                                  @NonNull final Iterable<E> collection,
                                  final boolean skipEmptyStrings,
                                  @Nullable final String prefix,
                                  @Nullable final Formatter<E> formatter) {
//        if (collection.isEmpty()) {
//            return "";
//        }

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (E element : collection) {
            String value;
            if (formatter == null) {
                if (element != null) {
                    value = String.valueOf(element).trim();
                } else {
                    value = "";
                }
            } else {
                value = formatter.format(element);
            }

            if ((value != null && !value.isEmpty()) || !skipEmptyStrings) {
                if (first) {
                    first = false;
                } else {
                    result.append(delimiter);
                }

                if (prefix == null) {
                    result.append(value);
                } else {
                    result.append(prefix).append(value);
                }
            }
        }
        return result.toString();
    }

    /**
     * Not strictly a Csv method, but closely related as it's usually (always?) followed
     * by sending the list to a join method.
     *
     * @param context Current context
     * @param map     Map with bits mapped to resource IDs
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> bitmaskToList(@NonNull final Context context,
                                             @NonNull final Map<Integer, Integer> map,
                                             final long bitmask) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if ((entry.getKey() & bitmask) != 0) {
                list.add(context.getString(entry.getValue()));
            }
        }
        return list;
    }

    public static List<String> bitmaskToList(@NonNull final Context context,
                                             @NonNull final SparseArray<Integer> map,
                                             final long bitmask) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < map.size(); i++) {
            if ((map.keyAt(i) & bitmask) != 0) {
                list.add(context.getString(map.valueAt(i)));
            }
        }
        return list;
    }

    /**
     * Not strictly a Csv method, but closely related as it's usually (always?) followed
     * by sending the list to a join method.
     *
     * @param map     Map with bits mapped to strings
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> bitmaskToList(@NonNull final Map<Integer, String> map,
                                             final long bitmask) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if ((entry.getKey() & bitmask) != 0) {
                list.add(entry.getValue());
            }
        }
        return list;
    }

    /**
     * Not strictly a Csv method, but closely related as it's usually (always?) followed
     * by sending the list to a join method.
     *
     * @param map     Map with bits mapped to strings
     * @param bitmask to turn into strings
     *
     * @return list of Strings with the names for each bit.
     */
    public static List<String> bitmaskToList(@NonNull final SparseArray<String> map,
                                             final long bitmask) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < map.size(); i++) {
            if ((map.keyAt(i) & bitmask) != 0) {
                list.add(map.valueAt(i));
            }
        }
        return list;
    }

    public interface Formatter<E> {

        @Nullable
        String format(@NonNull E element);
    }
}
