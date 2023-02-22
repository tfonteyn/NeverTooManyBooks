/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


@SuppressWarnings("WeakerAccess")
public final class ParseUtils {

    private ParseUtils() {
    }

    /**
     * Encode a string by 'escaping' all instances of:
     * <ul>
     *      <li>'\\', '\r', '\n', '\t'</li>
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
    public static String escape(final char elementSeparator,
                                final char objectSeparator,
                                @NonNull final CharSequence source,
                                @NonNull final char... escapeChars) {

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
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
                    if (c == elementSeparator) {
                        // list of elements, just once
                        sb.append("\\");

                    } else if (c == objectSeparator) {
                        // list of objects, inside a list of elements, so TWICE
                        sb.append("\\\\");

                    } else {
                        for (final char e : escapeChars) {
                            if (c == e) {
                                sb.append('\\');
                                // break from the for (char e : escapeChars)
                                break;
                            }
                        }
                    }
                    // add the actual character
                    sb.append(c);
                    break;
            }
        }
        return sb.toString().trim();
    }

    /**
     * Decode a string by removing any escapes.
     * <strong>Does NOT recurse.</strong>
     *
     * @param source String to decode
     *
     * @return decoded string
     */
    @NonNull
    public static String unEscape(@Nullable final String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        boolean isEsc = false;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
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
                } else {
                    // keep building the element string
                    sb.append(c);
                }
            }
        }
        return sb.toString().trim();
    }


}
