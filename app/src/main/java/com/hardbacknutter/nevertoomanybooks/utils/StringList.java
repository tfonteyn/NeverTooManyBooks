/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @param <T> the type of the elements stored in the string
 */
public class StringList<T> {

    @NonNull
    private final Factory<T> mFactory;

    /**
     * Constructor.
     * <p>
     * The elements need to be able to be cast to a String for decoding,
     * and have a .toString() method for encoding.
     * <p>
     * When initialised with type {@code String},
     * provides a dummy Factory useful for giving access to the list encoder/decoder methods.
     */
    public StringList() {
        mFactory = new Factory<T>() {

            @NonNull
            @Override
            public T decode(@NonNull final String element) {
                //noinspection unchecked
                return (T) element;
            }

            @NonNull
            @Override
            public String encode(@NonNull final T obj) {
                return obj.toString();
            }
        };
    }

    /**
     * Constructor.
     *
     * @param factory that can encode/decode strings to objects and vice versa.
     */
    public StringList(@NonNull final Factory<T> factory) {
        mFactory = factory;
    }

    /**
     * Decode a string list according to {@link Factory} rules.
     *
     * @param stringList String representing the list
     *
     * @return Array of objects resulting from list
     */
    @NonNull
    public ArrayList<T> decodeList(@Nullable final String stringList) {
        return decodeList(stringList, mFactory.getListSeparator(), false);
    }

    /**
     * Decode a list of elements (fields) in a single list objects.
     *
     * @param stringList String representing the list of elements
     *
     * @return Array of objects resulting from list
     */
    @NonNull
    public ArrayList<T> decodeElementList(@Nullable final String stringList) {
        return decodeList(stringList, mFactory.getFieldSeparator(), false);
    }

    /**
     * Decode a string list separated by 'delimiter' and
     * encoded by {@link Factory#escapeListItem}.
     * i.e. this method allows overriding the default list separator char.
     *
     * @param stringList String representing the list
     * @param delimiter  delimiter to use
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return Array of objects resulting from list
     */
    @NonNull
    public ArrayList<T> decodeList(@Nullable final String stringList,
                                   final char delimiter,
                                   final boolean allowBlank) {
        StringBuilder sb = new StringBuilder();
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
                        if (c == delimiter) {
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
     * Encode a list of elements.
     *
     * @param list to encode
     *
     * @return Encoded string
     */
    @NonNull
    public String encodeList(@NonNull final Collection<T> list) {
        return Csv.join(String.valueOf(mFactory.getListSeparator()), list, mFactory::encode);
    }

    /**
     * Encode a single element.
     *
     * @param element to encode
     *
     * @return Encoded string
     */
    @NonNull
    public String encodeElement(@NonNull final T element) {
        return mFactory.encode(element);
    }

    public interface Factory<E> {

        /**
         * Decode a single element.
         *
         * @param element to decode
         *
         * @return the object
         */
        @NonNull
        E decode(@NonNull String element);

        /**
         * Encode an object to a String representation.
         *
         * @param obj Object to encode
         *
         * @return string
         */
        @NonNull
        String encode(@NonNull E obj);

        /**
         * the separator character used between list elements.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        default char getListSeparator() {
            return '|';
        }

        /**
         * the separator character used between fields in a single element.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        default char getFieldSeparator() {
            return '*';
        }

        /**
         * Convert a string by 'escaping' all instances of:
         * {@link #getListSeparator()}, '\', \'r', '\n', '\t' and any additional 'escapeChars'.
         * The escape char is '\'.
         *
         * @param source      String to escape
         * @param escapeChars additional characters to escape. Case sensitive.
         *
         * @return Converted string(trimmed)
         */
        default String escapeListItem(@NonNull final String source,
                                      final char... escapeChars) {

            char listSeparator = getListSeparator();
            char fieldSeparator = getFieldSeparator();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                switch (c) {
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
                        if (c == listSeparator) {
                            sb.append("\\").append(listSeparator);
                            break;
                        }
                        if (c == fieldSeparator) {
                            sb.append("\\").append(fieldSeparator);
                            break;
                        }
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

    }
}
