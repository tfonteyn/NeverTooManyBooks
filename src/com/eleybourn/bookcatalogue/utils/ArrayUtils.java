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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArrayUtils<T> {

    public static final char MULTI_STRING_SEPARATOR = '|';

    private static ArrayUtils<Author> mAuthorUtils = null;
    private static ArrayUtils<Series> mSeriesUtils = null;

    private Factory<T> mFactory;

    @SuppressWarnings("unused")
    private ArrayUtils() {
    }

    private ArrayUtils(Factory<T> factory) {
        mFactory = factory;
    }


    //<editor-fold desc="Author">
    @NonNull
    public static ArrayUtils<Author> getAuthorUtils() {
        if (mAuthorUtils == null) {
            mAuthorUtils = new ArrayUtils<>(new Factory<Author>() {
                @Override
                @NonNull
                public Author get(@NonNull final String source) {
                    return new Author(source);
                }
            });
        }
        return mAuthorUtils;
    }

    /**
     * Utility routine to get an author list from the intent extras
     *
     * @param b Bundle with author list
     *
     * @return List of authors
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static ArrayList<Author> getAuthorsFromBundle(@NonNull final Bundle b) {
        return (ArrayList<Author>) b.getSerializable(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    /**
     * Utility routine to get the series from the passed intent. Added to reduce lint warnings...
     *
     * @param i Intent containing list
     *
     * @return List
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static ArrayList<Author> getAuthorFromIntentExtras(@NonNull final Intent i) {
        ArrayList<Author> list = (ArrayList<Author>) i.getSerializableExtra(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list != null) {
            return list;
        } else {
            return new ArrayList<>();
        }
    }
    //</editor-fold>


    //<editor-fold desc="Series">
    @NonNull
    public static ArrayUtils<Series> getSeriesUtils() {
        if (mSeriesUtils == null) {
            mSeriesUtils = new ArrayUtils<>(new Factory<Series>() {
                @Override
                @NonNull
                public Series get(@NonNull final String source) {
                    return new Series(source);
                }
            });
        }
        return mSeriesUtils;
    }

    /**
     * Utility routine to get a series list from the intent extras
     *
     * @param b Bundle with series list
     *
     * @return List of series
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static ArrayList<Series> getSeriesFromBundle(@NonNull final Bundle b) {
        return (ArrayList<Series>) b.getSerializable(UniqueId.BKEY_SERIES_ARRAY);
    }

    /**
     * Utility routine to get the series from the passed intent. Added to reduce lint warnings...
     *
     * @param i Intent containing list
     *
     * @return List
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static ArrayList<Series> getSeriesFromIntentExtras(@NonNull final Intent i) {
        ArrayList<Series> list = (ArrayList<Series>) i.getSerializableExtra(UniqueId.BKEY_SERIES_ARRAY);
        if (list != null) {
            return list;
        } else {
            return new ArrayList<>();
        }
    }
    //</editor-fold>

    /**
     * Utility routine to get the list from the passed bundle. Added to reduce lint warnings...
     *
     * @param b   Bundle containing list
     * @param key element to get
     *
     * @return List
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> ArrayList<T> getListFromBundle(@NonNull final Bundle b, @Nullable final String key) {
        return (ArrayList<T>) b.getSerializable(key);
    }

    /**
     * Utility routine to get the list from the passed intent. Added to reduce lint warnings...
     *
     * @param i   Intent containing list
     * @param key element to get
     *
     * @return List, or null when not present in the intent extras
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> ArrayList<T> getListFromIntentExtras(@NonNull final Intent i, @Nullable final String key) {
        return (ArrayList<T>) i.getSerializableExtra(key);
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s     String representing the list
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public static ArrayList<String> decodeList(@NonNull final String s) {
        return decodeList(MULTI_STRING_SEPARATOR, s);
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param delim delimiter used in string s
     * @param s     String representing the list
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public static ArrayList<String> decodeList(final char delim, @NonNull final String s) {
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
     * @see #encodeListItem(char, String)
     */
    @NonNull
    public static String encodeListItem(@NonNull final String s) {
        return encodeListItem(MULTI_STRING_SEPARATOR, s);
    }

    /**
     * Convert a string by 'escaping' all instances of: '|', '\', \r, \n. The
     * escape char is '\'.
     *
     * This is used to build text lists separated by the passed delimiter.
     *
     * @param delim The list delimiter to encode (if found).
     * @param s     String to convert
     *
     * @return Converted string
     */
    @NonNull
    public static String encodeListItem(final char delim, @NonNull final String s) {
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
                    if (c == delim) {
                        ns.append("\\");
                    }
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
    @NonNull
    public static <T> String toString(@NonNull final ArrayList<T> a) {
        StringBuilder details = new StringBuilder();

        for (T i : a) {
            if (details.length() > 0) {
                details.append(MULTI_STRING_SEPARATOR);
            }
            details.append(encodeListItem(MULTI_STRING_SEPARATOR, i.toString()));
        }
        return details.toString();
    }

    /**
     * Add the current text data to the collection if not present, otherwise
     * append the data as a list.
     *
     * @param key   Key for data to add
     * @param value Data to add
     */
    public static void appendOrAdd(@NonNull final Bundle values, @Nullable final String key, @NonNull final String value) {
        String s = encodeListItem('|', value);
        if (!values.containsKey(key) || values.getString(key).isEmpty()) {
            values.putString(key, s);
        } else {
            String curr = values.getString(key);
            values.putString(key, curr + MULTI_STRING_SEPARATOR + s);
        }
    }

    @NonNull
    private T get(@NonNull final String source) {
        return mFactory.get(source);
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
     * escape char is '\'.
     *
     * This is used to build text lists separated by 'delim'.
     *
     * @param sa String to convert
     *
     * @return Converted string
     */
    @NonNull
    public String encodeList(@NonNull final List<T> sa) {
        return encodeList(MULTI_STRING_SEPARATOR, sa);
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n.
     * The escape char is '\'.
     *
     * This is used to build text lists separated by 'delim'.
     *
     * @param sa String to convert
     *
     * @return Converted string
     */
    @NonNull
    public String encodeList(final char delim, @NonNull final List<T> sa) {
        StringBuilder ns = new StringBuilder();
        Iterator<T> si = sa.iterator();
        if (si.hasNext()) {
            ns.append(encodeListItem(delim, si.next().toString()));
            while (si.hasNext()) {
                ns.append(delim);
                ns.append(encodeListItem(delim, si.next().toString()));
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
    @NonNull
    public ArrayList<T> decodeList(@Nullable final String s, final boolean allowBlank) {
        return decodeList(MULTI_STRING_SEPARATOR, s, allowBlank);
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s String representing the list
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public ArrayList<T> decodeList(final char delim, @Nullable final String s, final boolean allowBlank) {
        StringBuilder ns = new StringBuilder();
        ArrayList<T> list = new ArrayList<>();
        if (s == null) {
            return list;
        }

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
                            if (allowBlank || source.length() > 0) {
                                list.add(get(source));
                            }
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
        if (allowBlank || source.length() > 0) {
            list.add(get(source));
        }
        return list;
    }

    public interface Factory<T> {
        T get(@NonNull final String source);
    }
}
