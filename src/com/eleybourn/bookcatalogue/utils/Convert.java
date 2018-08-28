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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

public class Convert {
    private Convert() {
    }

    /**
     * Format the passed bundle in a way that is convenient for display
     *
     * @param b		Bundle to format
     *
     * @return		Formatted string
     */
    public static String toString(Bundle b) {
        StringBuilder sb = new StringBuilder();
        for(String k: b.keySet()) {
            sb.append(k);
            sb.append("->");
            try {
                sb.append(b.get(k).toString());
            } catch (Exception e) {
                sb.append("<<Unknown>>");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     *
     * @return		Formatted date, eg. '2011-11-01' or '2011-11'
     */
    public static String buildPartialDate(Integer year, Integer month, Integer day) {
        String value;
        if (year == null) {
            value = "";
        } else {
            value = String.format("%04d", year);
            if (month != null && month > 0) {
                String mm = month.toString();
                if (mm.length() == 1) {
                    mm = "0" + mm;
                }

                value += "-" + mm;

                if (day != null && day > 0) {
                    String dd = day.toString();
                    if (dd.length() == 1) {
                        dd = "0" + dd;
                    }
                    value += "-" + dd;
                }
            }
        }
        return value;
    }
    /**
     * Join the passed array of strings, with 'delim' between them.
     *
     * @param sa		Array of strings to join
     * @param delim		Delimiter to place between entries
     *
     * @return			The joined strings
     */
    public static String join(String[] sa, String delim) {
        // Simple case, return empty string
        if (sa.length <= 0)
            return "";

        // Initialize with first
        StringBuilder buf = new StringBuilder(sa[0]);

        if (sa.length > 1) {
            // If more than one, loop appending delim then string.
            for(int i = 1; i < sa.length; i++) {
                buf.append(delim);
                buf.append(sa[i]);
            }
        }
        // Return result
        return buf.toString();
    }

    /**
     * Get a value from a bundle and convert to a long.
     *
     * @param b		Bundle
     * @param key	Key in bundle
     *
     * @return		Result
     */
    public static String getAsString(Bundle b, String key) {
        Object o = b.get(key);
        return o.toString();
    }

    /**
     * Get a value from a bundle and convert to a long.
     *
     * @param b		Bundle
     * @param key	Key in bundle
     *
     * @return		Result
     */
    public static long getAsLong(Bundle b, String key) {
        Object o = b.get(key);
        if (o instanceof Long) {
            return (Long) o;
        } else if (o instanceof String) {
            return Long.parseLong((String)o);
        } else if (o instanceof Integer) {
            return ((Integer)o).longValue();
        } else {
            throw new RuntimeException("Not a long value");
        }
    }

    /**
     * Format a number of bytes in a human readable form
     */
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
        return String.format(sizeFmt,space);
    }

    /**
     * Utility function to convert string to boolean
     *
     * @param s		        String to convert
     * @param emptyIsFalse  if true, empty string is handled as false
     *
     * @return		Boolean value
     */
    public static boolean toBoolean(String s, boolean emptyIsFalse) {
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

    public static boolean toBoolean(Object o) {
        if (o instanceof Boolean) {
            return (Boolean)o;
        }
        if (o instanceof Integer || o instanceof Long) {
            return (Long)o != 0;
        }
        try {
            return (Boolean)o;
        } catch (ClassCastException e) {
            return toBoolean(o.toString(), true);
        }
    }

}
