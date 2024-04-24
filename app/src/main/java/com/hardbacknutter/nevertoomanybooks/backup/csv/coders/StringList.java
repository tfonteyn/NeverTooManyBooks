/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;

/**
 * A StringList contains a list of elements, separated by the {@link Coder#getElementSeparator()}.
 * <p>
 * Each element is a list of objects separated by the {@link Coder#getObjectSeparator()}.
 *
 * @param <E> the type of the elements stored in the string.
 */
public class StringList<E> {

    @NonNull
    private final Coder<E> coder;

    /**
     * Constructor.
     *
     * @param coder that can encode/decode strings to objects and vice versa.
     */
    public StringList(@NonNull final Coder<E> coder) {
        this.coder = coder;
    }

    /**
     * Convenience constructor for a StringList consisting of String elements.
     *
     * @return instance
     */
    @NonNull
    public static StringList<String> newInstance() {
        return new StringList<>(element -> element);
    }

    /**
     * Decode the StringList into a list of <strong>elements</strong>.
     *
     * @param stringList String representing the list
     *
     * @return list
     */
    @NonNull
    public List<E> decodeList(@Nullable final CharSequence stringList) {
        return decode(stringList, coder.getElementSeparator(), false);
    }

    /**
     * Decode an element into a list of objects.
     *
     * @param stringList String representing the list of element sub-objects (parts)
     *
     * @return List of objects/parts
     */
    @NonNull
    public List<E> decodeElement(@Nullable final CharSequence stringList) {
        return decode(stringList, coder.getObjectSeparator(), false);
    }

    /**
     * Decode a string list separated by 'delimiter' and encoded by {@link Coder#escape}.
     * The elements will be decoded by the factory.
     *
     * @param stringList String representing the list
     * @param delimiter  delimiter to use
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return list
     */
    @NonNull
    public List<E> decode(@Nullable final CharSequence stringList,
                          final char delimiter,
                          final boolean allowBlank) {
        final StringBuilder sb = new StringBuilder();
        final List<E> list = new ArrayList<>();
        if (stringList == null) {
            return list;
        }

        boolean isEsc = false;
        for (int i = 0; i < stringList.length(); i++) {
            final char c = stringList.charAt(i);
            if (isEsc) {
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
                isEsc = false;

            } else {
                if (c == '\\') {
                    isEsc = true;

                } else if (c == delimiter) {
                    // we reached the end of an element
                    final String element = sb.toString().trim();
                    if (allowBlank || !element.isEmpty()) {
                        // decode it using the objects factory and add it to the list
                        list.add(coder.decode(element));
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
        final String element = sb.toString().trim();
        if (allowBlank || !element.isEmpty()) {
            list.add(coder.decode(element));
        }
        return list;
    }

    /**
     * A Coder is responsible for encoding and decoding <strong>individual</strong> elements.
     *
     * @param <E> type of element.
     */
    @FunctionalInterface
    public interface Coder<E> {

        char DEFAULT_ELEMENT_SEPARATOR = '|';

        /**
         * Decode a <strong>SINGLE</strong> element.
         *
         * @param element to decode
         *
         * @return the object
         */
        @NonNull
        E decode(@NonNull String element);


        /**
         * the default separator character used between list elements.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        default char getElementSeparator() {
            return DEFAULT_ELEMENT_SEPARATOR;
        }

        /**
         * the default separator character used between fields in a single element.
         * Some objects might need to override this default.
         *
         * @return the char
         */
        @SuppressWarnings("SameReturnValue")
        default char getObjectSeparator() {
            return '*';
        }

        /**
         * Encode a string by 'escaping' all instances of:
         * <ul>
         *      <li>{@link #getElementSeparator()}</li>
         *      <li>{@link #getObjectSeparator()}</li>
         *      <li>any additional 'escapeChars'</li>
         * </ul>
         * The escape char is '\'.
         *
         * @param source      String to encode
         * @param escapeChars additional characters to escape. Case sensitive.
         *
         * @return encoded string
         */
        @NonNull
        default String escape(@NonNull final String source,
                              @NonNull final char... escapeChars) {
            // add the factory specific separators
            return StringCoder.escape(getElementSeparator(), getObjectSeparator(),
                                      source, escapeChars);
        }
    }
}
