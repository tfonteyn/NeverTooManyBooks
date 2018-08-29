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

import android.content.Intent;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.Series;

import java.util.ArrayList;
import java.util.Iterator;

public class ArrayUtils<T> {

    private static ArrayUtils<Author> mAuthorUtils = null;
    private static ArrayUtils<Series> mSeriesUtils = null;
    private Factory<T> mFactory;

    private ArrayUtils() {
    }

    private ArrayUtils(Factory<T> factory) {
        mFactory = factory;
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s     String representing the list
     * @param delim delimiter used in string s
     *
     * @return Array of strings resulting from list
     */
    public static ArrayList<String> decodeList(String s, char delim) {
        StringBuilder ns = new StringBuilder();
        ArrayList<String> list = new ArrayList<>();
        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inEsc) {
                switch (c) {
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
     * Convert a string by 'escaping' all instances of: '|', '\', \r, \n. The
     * escape char is '\'.
     * <p>
     * This is used to build text lists separated by the passed delimiter.
     *
     * @param s     String to convert
     * @param delim The list delimiter to encode (if found).
     *
     * @return Converted string
     */
    public static String encodeListItem(String s, char delim) {
        StringBuilder ns = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
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
     * Convert a array of objects to a string.
     *
     * @param a Array
     *
     * @return Resulting string
     */
    public static <T> String toString(ArrayList<T> a) {
        StringBuilder details = new StringBuilder();

        for (T i : a) {
            if (details.length() > 0)
                details.append("|");
            details.append(encodeListItem(i.toString(), '|'));
        }
        return details.toString();
    }

    /**
     * Add the current text data to the collection if not present, otherwise
     * append the data as a list.
     *
     * @param key Key for data to add
     */
    public static void appendOrAdd(Bundle values, String key, String value) {
        String s = encodeListItem(value, '|');
        if (!values.containsKey(key) || values.getString(key).isEmpty()) {
            values.putString(key, s);
        } else {
            String curr = values.getString(key);
            values.putString(key, curr + "|" + s);
        }
    }

    static public ArrayUtils<Author> getAuthorUtils() {
        if (mAuthorUtils == null) {
            mAuthorUtils = new ArrayUtils<>(new Factory<Author>() {
                @Override
                public Author get(String source) {
                    return new Author(source);
                }
            });
        }
        return mAuthorUtils;
    }

    static public ArrayUtils<Series> getSeriesUtils() {
        if (mSeriesUtils == null) {
            mSeriesUtils = new ArrayUtils<>(new Factory<Series>() {
                @Override
                public Series get(String source) {
                    return new Series(source);
                }
            });
        }
        return mSeriesUtils;
    }
    /**
     * Utility routine to get an author list from the intent extras
     *
     * @param b		Bundle with author list
     * @return		List of authors
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Author> getAuthorsFromBundle(Bundle b) {
        return (ArrayList<Author>) b.getSerializable(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
    }

    /**
     * Utility routine to get a series list from the intent extras
     *
     * @param b		Bundle with series list

     * @return		List of series
     */
    @SuppressWarnings("unchecked")
    public
    static ArrayList<Series> getSeriesFromBundle(Bundle b) {
        return (ArrayList<Series>) b.getSerializable(CatalogueDBAdapter.KEY_SERIES_ARRAY);
    }

    /**
     * Utility routine to get the list from the passed bundle. Added to reduce lint warnings...
     *
     * @param b		Bundle containing list
     * @param key   element to get
     *
     * @return		List
     */
    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> getListFromBundle(Bundle b, String key) {
        return (ArrayList<T>) b.getSerializable(key);
    }

    /**
     * Utility routine to get the series from the passed intent. Added to reduce lint warnings...
     *
     * @param i		Intent containing list
     *
     * @return		List
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Author> getAuthorFromIntentExtras(Intent i) {
        return (ArrayList<Author>) i.getSerializableExtra(CatalogueDBAdapter.KEY_AUTHOR_ARRAY);
    }

    /**
     * Utility routine to get the series from the passed intent. Added to reduce lint warnings...
     *
     * @param i		Intent containing list
     *
     * @return		List
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Series> getSeriesFromIntentExtras(Intent i) {
        return (ArrayList<Series>) i.getSerializableExtra(CatalogueDBAdapter.KEY_SERIES_ARRAY);
    }

    /**
     * Utility routine to get the list from the passed intent. Added to reduce lint warnings...
     *
     * @param i		Intent containing list
     * @param key   element to get
     *
     * @return		List
     */
    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> getListFromIntentExtras(Intent i, String key) {
        return (ArrayList<T>) i.getSerializableExtra(key);
    }

    private T get(String source) {
        return mFactory.get(source);
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
     * escape char is '\'.
     * <p>
     * This is used to build text lists separated by 'delim'.
     *
     * @param sa String to convert
     *
     * @return Converted string
     */
    public String encodeList(ArrayList<T> sa, char delim) {
        Iterator<T> si = sa.iterator();
        return encodeList(si, delim);
    }

    private String encodeList(Iterator<T> si, char delim) {
        StringBuilder ns = new StringBuilder();
        if (si.hasNext()) {
            ns.append(
                    encodeListItem(si.next().toString(), delim));
            while (si.hasNext()) {
                ns.append(delim);
                ns.append(encodeListItem(si.next().toString(), delim));
            }
        }
        return ns.toString();
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s String representing the list
     *
     * @return Array of strings resulting from list
     */
    public ArrayList<T> decodeList(String s, char delim, boolean allowBlank) {
        StringBuilder ns = new StringBuilder();
        ArrayList<T> list = new ArrayList<>();
        if (s == null)
            return list;

        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inEsc) {
                switch (c) {
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
                            if (allowBlank || source.length() > 0)
                                list.add(get(source));
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
        if (allowBlank || source.length() > 0)
            list.add(get(source));
        return list;
    }

    public interface Factory<T> {
        T get(String source);
    }
}
