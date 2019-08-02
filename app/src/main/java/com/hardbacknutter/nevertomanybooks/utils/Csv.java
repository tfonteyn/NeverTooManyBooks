package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * CSV utilities.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Create a CSV list String from the passed collection.
     * A {@code null} element is morphed into "".
     * This can be avoided by using {@link #join(CharSequence, Collection, Formatter)} and
     * providing a {@link Formatter}.
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     *
     * @return csv string, can be empty, never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection) {
        if (collection.isEmpty()) {
            return "";
        }
        return join(delim, collection, true, null, null);
    }

    /**
     * Create a CSV list String from the passed collection.
     * A {@code null} element is morphed into "".
     * This can be avoided by providing a {@link Formatter}.
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or {@code null} for none.
     *
     * @return csv string, can be empty, never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection,
                                  @Nullable final Formatter<E> formatter) {
        if (collection.isEmpty()) {
            return "";
        }
        return join(delim, collection, true, null, formatter);
    }

    /**
     * Create a CSV list String from the passed collection.
     * Uses String.valueOf(element).trim()
     * This means that the "null" string is used for {@code null} elements.
     * (but no exceptions thrown).
     * This can be avoided by providing a {@link Formatter}.
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     * @param prefix     (optional) prefix that will be added to each element.
     *                   Caller is responsible to add spaces if desired.
     * @param formatter  (optional) formatter to use on each element, or {@code null} for none.
     *
     * @return csv string, can be empty, never {@code null}.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection,
                                  final boolean allowEmpties,
                                  @Nullable final String prefix,
                                  @Nullable final Formatter<E> formatter) {
        if (collection.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (E element : collection) {
            String value;
            if (formatter == null) {
                value = element != null ? String.valueOf(element).trim() : "";
            } else {
                value = formatter.format(element);
            }

            if ((value != null && !value.isEmpty()) || allowEmpties) {
                if (first) {
                    first = false;
                } else {
                    result.append(delim);
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
     * Create a CSV List String by replicating the 'element' length'd times.
     *
     * @param delim   delimiter, e.g. "," or ", " etc...
     * @param element for the list
     * @param length  nr of elements to generate
     *
     * @return csv string, can be empty, never {@code null}.
     */
    public static String join(@NonNull final CharSequence delim,
                              @NonNull final String element,
                              final int length) {
        StringBuilder sb = new StringBuilder(element);
        for (int i = 1; i < length; i++) {
            sb.append(delim).append(element);
        }
        return sb.toString();
    }

    public interface Formatter<E> {

        @Nullable
        String format(@NonNull E element);
    }
}
