/*
 * @Copyright 2018-2021 HardBackNutter
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.org.json.JSONException;

/**
 * A StringList contains a list of elements, separated by the {@link Coder#getElementSeparator()}.
 * <p>
 * Each element is a list of objects separated by the {@link Coder#getObjectSeparator()}.
 *
 * @param <E> the type of the elements stored in the string.
 */
public class StringList<E> {

    @NonNull
    private final Coder<E> mCoder;

    /**
     * Constructor.
     *
     * @param coder that can encode/decode strings to objects and vice versa.
     */
    public StringList(@NonNull final Coder<E> coder) {
        mCoder = coder;
    }

    /**
     * Convenience constructor for a StringList consisting of String elements.
     *
     * @return instance
     */
    public static StringList<String> newInstance() {
        return new StringList<>(new Coder<>() {
            @NonNull
            @Override
            public String encode(@NonNull final String obj) {
                return obj;
            }

            @NonNull
            @Override
            public String decode(@NonNull final String element) {
                return element;
            }
        });
    }

    /**
     * Decode the StringList into a list of <strong>elements</strong>.
     *
     * @param stringList String representing the list
     *
     * @return ArrayList (so it's Parcelable) of elements
     */
    @NonNull
    public ArrayList<E> decodeList(@Nullable final CharSequence stringList) {
        return decode(stringList, mCoder.getElementSeparator(), false);
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
        return decode(stringList, mCoder.getObjectSeparator(), false);
    }

    /**
     * Decode a string list separated by 'delimiter' and encoded by {@link Coder#escape}.
     * The elements will be decoded by the factory.
     *
     * @param stringList String representing the list
     * @param delimiter  delimiter to use
     * @param allowBlank Flag to allow adding empty (non-null) strings
     *
     * @return Array of objects resulting from list
     */
    @NonNull
    public ArrayList<E> decode(@Nullable final CharSequence stringList,
                               final char delimiter,
                               final boolean allowBlank) {
        final StringBuilder sb = new StringBuilder();
        final ArrayList<E> list = new ArrayList<>();
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
                        list.add(mCoder.decode(element));
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
            list.add(mCoder.decode(element));
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
    public String encodeList(@NonNull final Collection<E> list)
            throws JSONException {
        // The factory will encode each element, and we simply concat all of them.
        return list.stream()
                   .map(mCoder::encode)
                   .collect(Collectors.joining(String.valueOf(mCoder.getElementSeparator())));
    }

    /**
     * Encode a single element.
     *
     * @param element to encode
     *
     * @return Encoded string
     */
    @NonNull
    public String encodeElement(@NonNull final E element)
            throws JSONException {
        return mCoder.encode(element);
    }

    /**
     * A Coder is responsible for encoding and decoding <strong>individual</strong> elements.
     *
     * @param <E> type of element.
     */
    public interface Coder<E> {

        /**
         * Encode an object to a String representation.
         *
         * @param obj Object to encode
         *
         * @return string
         */
        @NonNull
        String encode(@NonNull E obj)
                throws JSONException;

        /**
         * Decode a <string>SINGLE</string> element.
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
            return '|';
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
        default String escape(@NonNull final String source,
                              @NonNull final char... escapeChars) {
            // add the factory specific separators
            return ParseUtils.escape(getElementSeparator(), getObjectSeparator(),
                                     source, escapeChars);
        }
    }
}
