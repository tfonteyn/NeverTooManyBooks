/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

public class Convert {
    private Convert() {
    }

    /**
     * Format the passed bundle in a way that is convenient for display
     *
     * @param b Bundle to format
     *
     * @return Formatted string
     */
    @NonNull
    public static String toString(@NonNull final Bundle b) {
        StringBuilder sb = new StringBuilder();
        for (String k : b.keySet()) {
            sb.append(k).append("->");
            try {
                Object o = b.get(k);
                if (o != null) {
                    sb.append(o.toString());
                }
            } catch (Exception e) {
                sb.append("<<Unknown>>");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Join the passed array of strings, with 'delim' between them.
     *
     * API 26 needed for {@link String#join(CharSequence, Iterable)} }
     *
     * @param delim Delimiter to place between entries
     * @param sa    Array of strings to join
     *
     * @return The joined strings
     */
    @NonNull
    public static String join(@NonNull final String delim, @NonNull final String[] sa) {
        // Simple case, return empty string
        if (sa.length <= 0)
            return "";

        // Initialize with first
        StringBuilder buf = new StringBuilder(sa[0]);

        if (sa.length > 1) {
            // If more than one, loop appending delim then string.
            for (int i = 1; i < sa.length; i++) {
                buf.append(delim);
                buf.append(sa[i]);
            }
        }
        // Return result
        return buf.toString();
    }

    /**
     * Get a value from a bundle and convert to a String.
     *
     * @param b   Bundle
     * @param key Key in bundle
     *
     * @return Result
     */
    @Nullable
    public static String getAsString(@NonNull final Bundle b, @Nullable final String key) {
        Object o = b.get(key);
        if (o != null)
            return o.toString();
        return null;
    }

    /**
     * Get a value from a bundle and convert to a long.
     *
     * @param b   Bundle
     * @param key Key in bundle
     *
     * @return Result
     */
    public static long getAsLong(@NonNull final Bundle b, @Nullable final String key) {
        Object o = b.get(key);
        if (o instanceof Long) {
            return (Long) o;
        } else if (o instanceof String) {
            return Long.parseLong((String) o);
        } else if (o instanceof Integer) {
            return ((Integer) o).longValue();
        } else {
            throw new RuntimeException("Not a long value");
        }
    }

    /**
     * Format a number of bytes in a human readable form
     */
    @NonNull
    public static String formatFileSize(float space) {
        String sizeFmt;
        if (space < 3072) { // Show 'bytes' if < 3k
            sizeFmt = BookCatalogueApp.getResourceString(R.string.bytes);
        } else if (space < 250 * 1024) { // Show Kb if less than 250kB
            sizeFmt = BookCatalogueApp.getResourceString(R.string.kilobytes);
            space = space / 1024;
        } else { // Show MB otherwise...
            sizeFmt = BookCatalogueApp.getResourceString(R.string.megabytes);
            space = space / (1024 * 1024);
        }
        return String.format(sizeFmt, space);
    }

    /**
     * Utility function to convert string to boolean
     *
     * @param s            String to convert
     * @param emptyIsFalse if true, empty string is handled as false
     *
     * @return Boolean value
     */
    public static boolean toBoolean(@Nullable final String s, boolean emptyIsFalse) {
        if (s == null || s.isEmpty())
            if (emptyIsFalse) {
                return false;
            } else {
                throw new RuntimeException("Not a valid boolean value");
            }
        else {
            switch (s.trim().toLowerCase()) {
                case "1":
                case "y":
                case "yes":
                case "t":
                case "true":
                    return true;
                case "0":
                case "n":
                case "no":
                case "f":
                case "false":
                    return false;
                default:
                    try {
                        return Integer.parseInt(s) != 0;
                    } catch (Exception e) {
                        throw new RuntimeException("Not a valid boolean value");
                    }
            }
        }
    }

    public static boolean toBoolean(@NonNull final Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        try {
            if (o instanceof Integer || o instanceof Long) {
                return (Long) o != 0;
            }

            return (Boolean) o;
        } catch (ClassCastException e) {
            return toBoolean(o.toString(), true);
        }
    }
}
