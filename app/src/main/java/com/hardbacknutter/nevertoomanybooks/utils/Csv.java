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
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

/**
 * CSV & list formatting utilities.
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
     * Uses ", " as delimiter.
     * A {@code null} element is morphed into "".
     * This can be avoided by providing a {@link Formatter}.
     * Either way, empty elements <strong>are included</strong>.
     *
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or {@code null} for none.
     * @param <E>        type of elements
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final Iterable<E> collection,
                                  @Nullable final Formatter<E> formatter) {
        return join(", ", collection, false, null, formatter);
    }

    /**
     * Create a CSV list String from the passed collection.
     * A {@code null} element is morphed into "".
     * This can be avoided by providing a {@link Formatter}.
     * Either way, empty elements <strong>are included</strong>.
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
     * @param delimiter         e.g. "," or ", ", "<br>", etc...
     * @param collection        collection
     * @param skipEmptyElements Flag skip null/empty values.
     * @param lineFormat        (optional) format string like "abc %s xyz"
     * @param valueFormatter    (optional) formatter to use on each element,
     *                          or {@code null} for none.
     * @param <E>               type of elements
     *
     * @return csv string, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delimiter,
                                  @NonNull final Iterable<E> collection,
                                  final boolean skipEmptyElements,
                                  @Nullable final String lineFormat,
                                  @Nullable final Formatter<E> valueFormatter) {

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (E element : collection) {
            String value;
            if (valueFormatter == null) {
                if (element != null) {
                    value = String.valueOf(element).trim();
                } else {
                    value = "";
                }
            } else {
                value = valueFormatter.format(element);
            }

            if ((value != null && !value.isEmpty()) || !skipEmptyElements) {
                if (first) {
                    first = false;
                } else {
                    result.append(delimiter);
                }

                if (lineFormat == null) {
                    result.append(value);
                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        if (!lineFormat.contains("%s")) {
                            throw new UnexpectedValueException("lineFormat without %s");
                        }
                    }
                    result.append(String.format(lineFormat, value));
                }
            }
        }
        return result.toString();
    }

    /**
     * Construct a multi-line list using text (i.e. no html).
     *
     * @param context        Current context
     * @param collection     collection
     * @param valueFormatter (optional) formatter to use on each element,
     *                       or {@code null} for none.
     * @param <E>            type of elements
     *
     * @return formatted list, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String textList(@NonNull final Context context,
                                      @NonNull final Iterable<E> collection,
                                      @Nullable final Formatter<E> valueFormatter) {

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (E element : collection) {
            String value;
            if (valueFormatter == null) {
                if (element != null) {
                    value = String.valueOf(element).trim();
                } else {
                    value = "";
                }
            } else {
                value = valueFormatter.format(element);
            }

            if (value != null && !value.isEmpty()) {
                if (first) {
                    first = false;
                } else {
                    result.append('\n');
                }

                result.append(context.getString(R.string.list_element, value));
            }
        }
        return result.toString();
    }

    /**
     * Construct a multi-line list using html.
     * <p>
     * Uses html list element on SDK 25 and up; uses br and bullet character on 23.
     *
     * @param context        Current context
     * @param collection     collection
     * @param valueFormatter (optional) formatter to use on each element,
     *                       or {@code null} for none.
     * @param <E>            type of elements
     *
     * @return formatted list, can be empty, but never {@code null}.
     */
    @NonNull
    public static <E> String htmlList(@NonNull final Context context,
                                      @NonNull final Iterable<E> collection,
                                      @Nullable final Formatter<E> valueFormatter) {
        if (Build.VERSION.SDK_INT < 24) {
            return Csv.join("<br>", collection, true,
                            context.getString(R.string.list_element), valueFormatter);
        }

        StringBuilder result = new StringBuilder();
        for (E element : collection) {
            String value;
            if (valueFormatter == null) {
                if (element != null) {
                    value = String.valueOf(element).trim();
                } else {
                    value = "";
                }
            } else {
                value = valueFormatter.format(element);
            }

            if (value != null && !value.isEmpty()) {
                result.append("<li>").append(value).append("</li>");
            }
        }
        if (result.length() > 0) {
            return "<ul>" + result + "</ul>";
        } else {
            return "";
        }
    }

    public interface Formatter<E> {

        @Nullable
        String format(@NonNull E element);
    }
}
