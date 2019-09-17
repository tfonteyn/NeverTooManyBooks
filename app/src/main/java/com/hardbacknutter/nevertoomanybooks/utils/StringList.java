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
 * A StringList contains a list of elements, separated by the {@link Factory#getElementSeparator()}.
 * <p>
 * Each element is a list of objects separated by the {@link Factory#getObjectSeparator()}.
 * <p>
 * FIXME: there is a degree of double-decoding in {@link #decode(String, char, boolean)}.
 * No harm done, but wasting cpu cycles. This is due to the (default) assumption
 * that each element in a list can in turn be a list.
 *
 * @param <E> the type of the elements stored in the string.
 */
public class StringList<E> {

    @NonNull
    private final Factory<E> mFactory;

    /**
     * Constructor.
     *
     * @param factory that can encode/decode strings to objects and vice versa.
     */
    public StringList(@NonNull final Factory<E> factory) {
        mFactory = factory;
    }

    /**
     * Convenience constructor for a StringList consisting of String elements.
     */
    public static StringList<String> newInstance() {
        return new StringList<>(new Factory<String>() {
        });
    }

    /**
     * Decode the StringList into a list of elements.
     *
     * @param stringList String representing the list
     *
     * @return Array of elements
     */
    @NonNull
    public ArrayList<E> decode(@Nullable final String stringList) {
        return decode(stringList, mFactory.getElementSeparator(), false);
    }

    /**
     * Decode an element into a list of objects.
     *
     * @param stringList String representing the list of items
     *
     * @return Array of objects
     */
    @NonNull
    public ArrayList<E> decodeElement(@Nullable final String stringList) {
        return decode(stringList, mFactory.getObjectSeparator(), false);
    }

    /**
     * Decode a string list separated by 'delimiter' and encoded by {@link Factory#escape}.
     *
     * @param stringList String representing the list
     * @param delimiter  delimiter to use
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return Array of objects resulting from list
     */
    @NonNull
    public ArrayList<E> decode(@Nullable final String stringList,
                               final char delimiter,
                               final boolean allowBlank) {
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
                        sb.append(c);
                        break;
                }
                inEsc = false;
            } else {
                if (c == '\\') {
                    inEsc = true;

                } else if (c == delimiter) {
                    // we reached the end of an element
                    String source = sb.toString().trim();
                    if (allowBlank || !source.isEmpty()) {
                        // decode it and add it to the list
                        list.add(mFactory.decode(source));
                    }
                    // reset, and start on the next element
                    sb.setLength(0);
                } else {
                    // keep building the element string
                    sb.append(c);
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
    public String encodeList(@NonNull final Collection<E> list) {
        // The factory will encode each element, and then we simply concat all of them.
        return Csv.join(String.valueOf(mFactory.getElementSeparator()), list, mFactory::encode);
    }

    /**
     * Encode a single element.
     *
     * @param element to encode
     *
     * @return Encoded string
     */
    @NonNull
    public String encodeElement(@NonNull final E element) {
        return mFactory.encode(element);
    }

    /**
     * A factory is responsible for encoding and decoding individual elements.
     *
     * <strong>All</strong> methods have defaults with
     * <ul>
     * <li>{@link #encode} method returning toString()</li>
     * <li>{@link #decode} returning the input</li>
     * </ul>
     *
     * @param <E> type of element.
     */
    public interface Factory<E> {

        /**
         * Encode an object to a String representation.
         * <p>
         * The default implementation returns the input as a toString().
         *
         * @param obj Object to encode
         *
         * @return string
         */
        @NonNull
        default String encode(@NonNull final E obj) {
            return obj.toString();
        }

        /**
         * Decode a single element.
         * <p>
         * The default implementation returns the input.
         *
         * @param element to decode
         *
         * @return the object
         */
        @NonNull
        default E decode(@NonNull final String element) {
            //noinspection unchecked
            return (E) element;
        }


        /**
         * the default separator character used between list elements.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        default char getElementSeparator() {
            return '|';
        }

        /**
         * the default separator character used between fields in a single element.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        default char getObjectSeparator() {
            return '*';
        }

        /**
         * Encode a string by 'escaping' all instances of:
         * <ul>
         * <li>{@link #getElementSeparator()}</li>
         * <li>{@link #getObjectSeparator()}</li>
         * <li>any '\', \'r', '\n', '\t'</li>
         * <li>any additional 'escapeChars'</li>
         * </ul>
         * The escape char is '\'.
         *
         * @param source      String to encode
         * @param escapeChars additional characters to escape. Case sensitive.
         *
         * @return encoded string
         */
        default String escape(@NonNull final String source,
                              final char... escapeChars) {

            char[] temp = new char[escapeChars.length + 2];
            System.arraycopy(escapeChars, 0, temp, 2, escapeChars.length);
            temp[0] = getElementSeparator();
            temp[1] = getObjectSeparator();

            return ParseUtils.escape(source, temp);
        }
    }
}
