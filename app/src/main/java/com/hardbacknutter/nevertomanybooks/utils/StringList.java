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
package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.entities.TocEntry;

/**
 * Provides a number of static methods to manipulate string lists.
 * <p>
 * Can also be instantiated to allow the use of generic elements in the lists.
 * Contains some pre-defined static method for specific types; e.g. {@link Author} etc...
 *
 * @param <E> the type of the elements stored in the string
 */
public class StringList<E> {

    /** the default delimiter to use when concatenating elements. */
    private static final char MULTI_STRING_SEPARATOR = '|';
    /** pre-configured  coder/decoder for Bookshelf elements. */
    @Nullable
    private static StringList<Bookshelf> sBookshelfUtils;
    /** pre-configured  coder/decoder for Author elements. */
    @Nullable
    private static StringList<Author> sAuthorUtils;
    /** pre-configured  coder/decoder for Series elements. */
    @Nullable
    private static StringList<Series> sSeriesUtils;
    /** pre-configured  coder/decoder for TocEntry elements. */
    @Nullable
    private static StringList<TocEntry> sTocUtils;

    @NonNull
    private final Factory<E> mFactory;

    /**
     * Constructor.
     * <p>
     * The elements need to be able to be cast to a String for decoding,
     * and have a .toString() method for encoding.
     */
    public StringList() {
        mFactory = new Factory<E>() {
            @NonNull
            @Override
            public E decode(@NonNull final String element) {
                //noinspection unchecked
                return (E) element;
            }

            @NonNull
            @Override
            public String encode(@NonNull final E element) {
                return element.toString();
            }
        };
    }

    /**
     * Constructor.
     *
     * @param factory that can encode/decode strings to objects and vice versa.
     */
    public StringList(@NonNull final Factory<E> factory) {
        mFactory = factory;
    }

    /**
     * @return StringList with Bookshelf factory.
     */
    @NonNull
    public static StringList<Bookshelf> getBookshelfCoder() {
        if (sBookshelfUtils == null) {
            sBookshelfUtils = new StringList<>(new Factory<Bookshelf>() {
                @Override
                @NonNull
                public Bookshelf decode(@NonNull final String element) {
                    return Bookshelf.fromString(element);
                }

                @NonNull
                @Override
                public String encode(@NonNull final Bookshelf element) {
                    return element.stringEncoded();
                }
            });
        }
        return sBookshelfUtils;
    }

    /**
     * @return StringList with Author factory.
     */
    @NonNull
    public static StringList<Author> getAuthorCoder() {
        if (sAuthorUtils == null) {
            sAuthorUtils = new StringList<>(new Factory<Author>() {
                @Override
                @NonNull
                public Author decode(@NonNull final String element) {
                    return Author.fromString(element);
                }

                @NonNull
                @Override
                public String encode(@NonNull final Author element) {
                    return element.stringEncoded();
                }
            });
        }
        return sAuthorUtils;
    }

    /**
     * @return StringList with Series factory.
     */
    @NonNull
    public static StringList<Series> getSeriesCoder() {
        if (sSeriesUtils == null) {
            sSeriesUtils = new StringList<>(new Factory<Series>() {
                @Override
                @NonNull
                public Series decode(@NonNull final String element) {
                    return Series.fromString(element);
                }

                @NonNull
                @Override
                public String encode(@NonNull final Series element) {
                    return element.stringEncoded();
                }
            });
        }
        return sSeriesUtils;
    }

    /**
     * @return StringList with TocEntry factory.
     */
    @NonNull
    public static StringList<TocEntry> getTocCoder() {
        if (sTocUtils == null) {
            sTocUtils = new StringList<>(new Factory<TocEntry>() {
                @Override
                @NonNull
                public TocEntry decode(@NonNull final String element) {
                    return TocEntry.fromString(element);
                }

                @NonNull
                @Override
                public String encode(@NonNull final TocEntry element) {
                    return element.stringEncoded();
                }
            });
        }
        return sTocUtils;
    }

    /**
     * Convert a string by 'escaping' all instances of:
     * {@link #MULTI_STRING_SEPARATOR}, '\', \'r', '\n', '\t' and any additional 'escapeChars'.
     * The escape char is '\'.
     *
     * @param source      String to escape
     * @param escapeChars additional characters to escape. Case sensitive!
     *
     * @return Converted string(trimmed)
     */
    @NonNull
    public static String escapeListItem(@NonNull final String source,
                                        final char... escapeChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case MULTI_STRING_SEPARATOR:
                    sb.append("\\" + MULTI_STRING_SEPARATOR);
                    break;

                case '\\':
                    sb.append("\\\\");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                default:
                    for (char e : escapeChars) {
                        if (c == e) {
                            sb.append('\\');
                            // break from for (char e : escapeChars)
                            break;
                        }
                    }
                    sb.append(c);
                    break;
            }
        }
        return sb.toString().trim();
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
    public ArrayList<E> decode(@Nullable final String stringList,
                               final boolean allowBlank) {
        return decode(stringList, allowBlank, MULTI_STRING_SEPARATOR);
    }

    /**
     * Decode a string list separated by 'delim' and
     * encoded by {@link #escapeListItem}.
     *
     * @param stringList String representing the list
     * @param allowBlank Flag to allow adding empty (non-null) strings
     * @param delim      delimiter to use
     *
     * @return Array of strings resulting from list
     */
    @NonNull
    public ArrayList<E> decode(@Nullable final String stringList,
                               final boolean allowBlank,
                               final char delim) {
        StringBuilder sb = new StringBuilder();
        ArrayList<E> list = new ArrayList<>();
        if (stringList == null) {
            return list;
        }

        boolean inEsc = false;
        for (int i = 0; i < stringList.length(); i++) {
            char c = stringList.charAt(i);
            if (inEsc) {
                switch (c) {
                    case '\\':
                        sb.append('\\');
                        break;

                    case 'r':
                        sb.append('\r');
                        break;

                    case 't':
                        sb.append('\t');
                        break;

                    case 'n':
                        sb.append('\n');
                        break;

                    default:
                        // covers MULTI_STRING_SEPARATOR
                        sb.append(c);
                        break;
                }
                inEsc = false;
            } else {
                //noinspection SwitchStatementWithTooFewBranches
                switch (c) {
                    case '\\':
                        inEsc = true;
                        break;

                    default:
                        if (c == delim) {
                            String source = sb.toString().trim();
                            if (allowBlank || !source.isEmpty()) {
                                list.add(mFactory.decode(source));
                            }
                            sb.setLength(0);
                        } else {
                            sb.append(c);
                        }
                        break;
                }
            }
        }

        // It's important to send back even an empty item.
        String source = sb.toString().trim();
        if (allowBlank || !source.isEmpty()) {
            list.add(mFactory.decode(source));
        }
        return list;
    }

    /**
     * Encode using the standard multi-string delimiter, '|'.
     *
     * @param list to convert
     *
     * @return Converted string
     */
    @NonNull
    public String encode(@NonNull final Collection<E> list) {
        return encode(MULTI_STRING_SEPARATOR, list);
    }

    /**
     * This is used to build text lists separated by 'delim'.
     *
     * @param delim delimiter to use.
     * @param list  to convert
     *
     * @return Converted string
     */
    @NonNull
    public String encode(final char delim,
                         @NonNull final Collection<E> list) {

        return Csv.join(String.valueOf(delim), list, mFactory::encode);
    }

    public interface Factory<E> {

        @NonNull
        E decode(@NonNull String element);

        @NonNull
        String encode(@NonNull E element);
    }
}
