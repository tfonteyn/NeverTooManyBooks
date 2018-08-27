package com.eleybourn.bookcatalogue.utils;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;

import java.util.ArrayList;
import java.util.Iterator;

public class Convert {
    private Convert() {
    }

    /**
     * Convert a string by 'escaping' all instances of: '|', '\', \r, \n. The
     * escape char is '\'.
     *
     * This is used to build text lists separated by the passed delimiter.
     *
     * @param s			String to convert
     * @param delim		The list delimiter to encode (if found).
     *
     * @return		Converted string
     */
    public static String encodeListItem(String s, char delim) {
        StringBuilder ns = new StringBuilder();
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    ns.append("\\\\");
                    break;
                case '\r':
                    ns.append("\\r");
                    break;
                case '\n':
                    ns.append("\\n");
                    break;
                default:
                    if (c == delim)
                        ns.append("\\");
                    ns.append(c);
            }
        }
        return ns.toString();
    }

    /**
     * Convert a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
     * escape char is '\'.
     *
     * This is used to build text lists separated by 'delim'.
     *
     * @param sa	String to convert
     * @return		Converted string
     */
    public static String encodeList(ArrayList sa, char delim) {
        Iterator si = sa.iterator();
        StringBuilder ns = new StringBuilder();
        if (si.hasNext()) {
            ns.append(encodeListItem(si.next().toString(), delim));
            while (si.hasNext()) {
                ns.append(delim);
                ns.append(encodeListItem(si.next().toString(), delim));
            }
        }
        return ns.toString();
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
     * @param s		String to convert
     * @param emptyIsFalse TODO
     *
     * @return		Boolean value
     */
    public static boolean toBoolean(String s, boolean emptyIsFalse) {
        boolean v;
        if (s == null || s.isEmpty())
            if (emptyIsFalse) {
                v = false;
            } else {
                throw new RuntimeException("Not a valid boolean value");
            }
        else if (s.equals("1"))
            v = true;
        else if (s.equals("0"))
            v = false;
        else {
            s = s.trim().toLowerCase();
            switch (s) {
                case "y":
                case "yes":
                case "t":
                case "true":
                    v = true;
                    break;
                case "n":
                case "no":
                case "f":
                case "false":
                    v = false;
                    break;
                default:
                    try {
                        return Integer.parseInt(s) != 0;
                    } catch (Exception e) {
                        throw new RuntimeException("Not a valid boolean value");
                    }
            }
        }
        return v;
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

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s		    String representing the list
     * @param delim     delimiter used in string s
     *
     * @return		    Array of strings resulting from list
     */
    public static ArrayList<String> decodeList(String s, char delim) {
        StringBuilder ns = new StringBuilder();
        ArrayList<String> list = new ArrayList<>();
        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if (inEsc) {
                switch(c) {
                    case '\\':
                        ns.append(c);
                        break;
                    case 'r':
                        ns.append('\r');
                        break;
                    case 't':
                        ns.append('\t');
                        break;
                    case 'n':
                        ns.append('\n');
                        break;
                    default:
                        ns.append(c);
                        break;
                }
                inEsc = false;
            } else {
                switch (c) {
                    case '\\':
                        inEsc = true;
                        break;
                    default:
                        if (c == delim) {
                            list.add(ns.toString());
                            ns.setLength(0);
                            break;
                        } else {
                            ns.append(c);
                            break;
                        }
                }
            }
        }
        // It's important to send back even an empty item.
        list.add(ns.toString());
        return list;
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s		String representing the list
     * @return		Array of strings resulting from list
     */
    public static ArrayList decodeList(String s, char delim, boolean allowBlank) {
        StringBuilder ns = new StringBuilder();
        ArrayList list = new ArrayList<>();
        if (s == null)
            return list;

        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if (inEsc) {
                switch(c) {
                    case '\\':
                        ns.append(c);
                        break;
                    case 'r':
                        ns.append('\r');
                        break;
                    case 't':
                        ns.append('\t');
                        break;
                    case 'n':
                        ns.append('\n');
                        break;
                    default:
                        ns.append(c);
                        break;
                }
                inEsc = false;
            } else {
                switch (c) {
                    case '\\':
                        inEsc = true;
                        break;
                    default:
                        if (c == delim) {
                            String source = ns.toString();
                            if (allowBlank || !source.isEmpty())
                                list.add(source);
                            ns.setLength(0);
                            break;
                        } else {
                            ns.append(c);
                            break;
                        }
                }
            }
        }
        // It's important to send back even an empty item.
        String source = ns.toString();
        if (allowBlank || !source.isEmpty())
            list.add(source);
        return list;
    }

    /**
     * Convert a array of objects to a string.
     *
     * @param a		Array
     * @return		Resulting string
     */
    public static <T> String toString(ArrayList<T> a) {
        StringBuilder details = new StringBuilder();

        for (T i : a) {
            if (details.length() > 0)
                details.append("|");
            details.append(Convert.encodeListItem(i.toString(), '|'));
        }
        return details.toString();
    }

    /**
     * Add the current text data to the collection if not present, otherwise
     * append the data as a list.
     *
     * @param key	Key for data to add
     */
    static public void appendOrAdd(Bundle values, String key, String value) {
        String s = encodeListItem(value, '|');
        if (!values.containsKey(key) || values.getString(key).isEmpty()) {
            values.putString(key, s);
        } else {
            String curr = values.getString(key);
            values.putString(key, curr + "|" + s);
        }
    }
}
