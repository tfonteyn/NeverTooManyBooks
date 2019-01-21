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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Provides a number of static methods to manipulate string lists.
 * <p>
 * Can also be instantiated to allow the use of generic elements in the lists.
 * Contains some pre-defined static method for specific types; e.g. {@link Author} etc...
 *
 * @param <T>
 */
public class StringList<T> {

    private static final char MULTI_STRING_SEPARATOR = '|';
    @Nullable
    private static StringList<Bookshelf> mBookshelfUtils;
    @Nullable
    private static StringList<Author> mAuthorUtils;
    @Nullable
    private static StringList<Series> mSeriesUtils;
    @Nullable
    private static StringList<TOCEntry> mTOCUtils;
    private Factory<T> mFactory;

    /**
     * Public constructor to roll your own.
     */
    public StringList() {
    }

    /**
     * Private constructor to support pre-defined types.
     *
     * @param factory that can produce an object based on a single string value.
     */
    private StringList(@NonNull final Factory<T> factory) {
        mFactory = factory;
    }

    @NonNull
    public static StringList<Bookshelf> getBookshelfUtils() {
        if (mBookshelfUtils == null) {
            mBookshelfUtils = new StringList<>(new Factory<Bookshelf>() {
                @Override
                @NonNull
                public Bookshelf get(@NonNull final String stringList) {
                    return new Bookshelf(stringList);
                }
            });
        }
        return mBookshelfUtils;
    }

    @NonNull
    public static StringList<Author> getAuthorUtils() {
        if (mAuthorUtils == null) {
            mAuthorUtils = new StringList<>(new Factory<Author>() {
                @Override
                @NonNull
                public Author get(@NonNull final String stringList) {
                    return new Author(stringList);
                }
            });
        }
        return mAuthorUtils;
    }

    @NonNull
    public static StringList<Series> getSeriesUtils() {
        if (mSeriesUtils == null) {
            mSeriesUtils = new StringList<>(new Factory<Series>() {
                @Override
                @NonNull
                public Series get(@NonNull final String stringList) {
                    return new Series(stringList);
                }
            });
        }
        return mSeriesUtils;
    }

    @NonNull
    public static StringList<TOCEntry> getTOCUtils() {
        if (mTOCUtils == null) {
            mTOCUtils = new StringList<>(new Factory<TOCEntry>() {
                @Override
                @NonNull
                public TOCEntry get(@NonNull final String stringList) {
                    return new TOCEntry(stringList);
                }
            });
        }
        return mTOCUtils;
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * This is a static convenience method, to hand <String> as the type,
     * avoiding to have a {@link Factory} produce a String.
     * <p>
     * Decode a text list separated by '|'
     *
     * @param stringList String representing the list
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public static ArrayList<String> decode(@NonNull final String stringList) {
        return decode(MULTI_STRING_SEPARATOR, stringList);
    }

    /**
     * This is a static convenience method, to hand <String> as the type.
     * avoiding to have a {@link Factory} produce a String.
     * <p>
     * Decode a text list separated by 'delim'
     *
     * @param delim      delimiter used in stringList
     * @param stringList String representing the list
     *
     * @return Array of strings(trimmed) resulting from list
     */
    @NonNull
    public static ArrayList<String> decode(final char delim,
                                           @Nullable final String stringList) {
        StringBuilder ns = new StringBuilder();
        ArrayList<String> list = new ArrayList<>();
        if (stringList == null) {
            return list;
        }

        boolean inEsc = false;
        for (int i = 0; i < stringList.length(); i++) {
            char c = stringList.charAt(i);
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
                            String source = ns.toString().trim();
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
        String source = ns.toString().trim();
        list.add(source);
        return list;
    }



    /**
     * Decode a string list separated by '|' and encoded by {@link #escapeListItem}.
     *
     * @param stringList String representing the list
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public ArrayList<T> decode(@Nullable final String stringList,
                               final boolean allowBlank) {
        return decode(MULTI_STRING_SEPARATOR, stringList, allowBlank);
    }

    /**
     * Decode a string list separated by 'delim' and
     * encoded by {@link #escapeListItem(char, String)}.
     *
     * @param delim      delimiter to use
     * @param stringList String representing the list
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return Array of strings resulting from list
     */
    @SuppressWarnings("SameParameterValue")
    @NonNull
    public ArrayList<T> decode(final char delim,
                               @Nullable final String stringList,
                               final boolean allowBlank) {
        StringBuilder ns = new StringBuilder();
        ArrayList<T> list = new ArrayList<>();
        if (stringList == null) {
            return list;
        }

        boolean inEsc = false;
        for (int i = 0; i < stringList.length(); i++) {
            char c = stringList.charAt(i);
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
                            String source = ns.toString().trim();
                            if (allowBlank || !source.isEmpty()) {
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
        String source = ns.toString().trim();
        if (allowBlank || !source.isEmpty()) {
            list.add(get(source));
        }
        return list;
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n.
     * The escape char is '\'.
     * <p>
     * This is used to build text lists separated by 'delim'.
     *
     * @param list String to convert
     *
     * @return Converted string
     */
    @NonNull
    public String encode(@NonNull final List<T> list) {
        return encode(MULTI_STRING_SEPARATOR, list);
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n.
     * The escape char is '\'.
     * <p>
     * This is used to build text lists separated by 'delim'.
     * <p>
     * Note: if you don't need escaping, then use {@link android.text.TextUtils#join}
     *
     * @param list to convert, objects are converted to String with their toString() method.
     *
     * @return Converted string
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    @NonNull
    public String encode(final char delim,
                         @NonNull final List<T> list) {
        StringBuilder ns = new StringBuilder();
        Iterator<T> si = list.iterator();
        if (si.hasNext()) {
            ns.append(escapeListItem(delim, si.next().toString()));
            while (si.hasNext()) {
                ns.append(delim);
                ns.append(escapeListItem(delim, si.next().toString()));
            }
        }
        return ns.toString();
    }

    /**
     * Convert a string by 'escaping' all instances of: '|', '\', \r, \n.
     * The escape char is '\'.
     * <p>
     * This is used to build text lists separated by the passed delimiter.
     *
     * @param delim The list delimiter to encode (if found).
     * @param s     String to convert
     *
     * @return Converted string(trimmed)
     */
    @NonNull
    public static String escapeListItem(final char delim,
                                        @NonNull final String s) {
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
                        ns.append('\\');
                    }
                    ns.append(c);
            }
        }
        return ns.toString().trim();
    }

    @NonNull
    private T get(@NonNull final String stringList) {
        return mFactory.get(stringList);
    }

    public interface Factory<T> {

        @NonNull
        T get(@NonNull String stringList);
    }
}
