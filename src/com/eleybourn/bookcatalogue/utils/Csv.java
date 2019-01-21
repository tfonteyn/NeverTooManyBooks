package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * CSV utilities.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Convert a array of objects to a csv string fit for user displaying.
     *
     * @param list with items. toString() will be used to make the item displayable.
     *
     * @return Resulting string
     */
    @NonNull
    public static <T> String toDisplayString(@NonNull final List<T> list) {
        return csv(", ", list);
    }

    public static <E> String csv(@NonNull final String delim,
                                 @NonNull final E[] collection) {
        return csv(delim, Arrays.asList(collection), null);
    }

    public static <E> String csv(@NonNull final String delim,
                                 @NonNull final Collection<E> collection) {
        return csv(delim, collection, null);
    }

    /**
     * Create a CSV list String from the passed collection.
     * Uses String.valueOf(element).trim()
     * This means that the "null" string is used for null elements.
     * (but no exceptions thrown)
     *
     * @param delim      delimiter, e.g. "," or ", " etc...
     * @param collection collection
     * @param formatter  (optional) formatter to use on each element, or null for none.
     *
     * @return csv string
     */
    public static <E> String csv(@NonNull final String delim,
                                 @NonNull final Collection<E> collection,
                                 @Nullable final Formatter<E> formatter) {
        if (collection.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (E element : collection) {
            if (first) {
                first = false;
            } else {
                result.append(delim);
            }
            if (formatter == null) {
                result.append(String.valueOf(element).trim());
            } else {
                result.append(formatter.format(element));
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
    public static String csv(final String delim,
                             final int length,
                             final String element) {
        StringBuilder sb = new StringBuilder(element);
        for (int i = 1; i < length; i++) {
            sb.append(delim).append(element);
        }
        return sb.toString();
    }

    public interface Formatter<E> {

        String format(@NonNull final E element);
    }
}
