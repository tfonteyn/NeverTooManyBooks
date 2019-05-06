package com.eleybourn.bookcatalogue.utils;

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
     * Convert a Collection of objects to a csv string fit for user displaying.
     *
     * @param collection with items. toString() will be used to make the item displayable,
     *                   or you can provide a {@link Formatter}.
     * @param formatter  (optional) formatter to use on each element, or null for none.
     *
     * @return Resulting string
     */
    @NonNull
    public static <E> String toDisplayString(@NonNull final Collection<E> collection,
                                             @Nullable final Formatter<E> formatter) {
        return join(", ", collection, formatter);
    }

    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection) {
        return join(delim, collection, null);
    }

    /**
     * Create a CSV list String from the passed collection.
     * A null element is morphed into "".
     * This can be avoided by providing a {@link Formatter}.
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or null for none.
     *
     * @return csv string
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection,
                                  @Nullable final Formatter<E> formatter) {
        if (collection.isEmpty()) {
            return "";
        }
        return join(delim, collection,true,formatter);
    }

    /**
     * Create a CSV list String from the passed collection.
     * Uses String.valueOf(element).trim()
     * This means that the "null" string is used for null elements.
     * (but no exceptions thrown). This can be avoided by providing a {@link Formatter}.
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or null for none.
     *
     * @return csv string, can be empty, never null.
     */
    @NonNull
    public static <E> String join(@NonNull final CharSequence delim,
                                  @NonNull final Collection<E> collection,
                                  final boolean allowEmpties,
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
                result.append(value);
            }
        }
        return result.toString();
    }

    /**
     * Create a CSV List String by replicating the 'element' length'd times.
     *
     * @param delim   delimiter, e.g. "," or ", " etc...
     * @param length  nr of elements to generate
     * @param element for the list
     *
     * @return csv string
     */
    public static String join(final CharSequence delim,
                              final int length,
                              final String element) {
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
