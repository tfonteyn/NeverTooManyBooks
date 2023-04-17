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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Used to create {@code ORDER BY} suitable strings, FTS storage and similar.
 */
public final class AsciiNormalizer {

    /**
     * See {@link #normalize}.
     * <p>
     * \p{ASCII} 	All ASCII:[\x00-\x7F]
     * <p>
     * URGENT: https://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum
     *  Classes for Unicode blocks and categories
     */
    private static final Pattern ASCII_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    private AsciiNormalizer() {
    }

    /**
     * Normalize/clean the given string.
     * <p>
     * Latin alphabet strings will get normalized and cleansed of non-ascii characters.
     * Non-latin strings are returned as-is.
     *
     * @param text to normalize
     *
     * @return normalized text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text) {
        if (text.toString().isBlank()) {
            return "";
        }

        final Character.UnicodeScript script = Character.UnicodeScript.of(
                text.toString().strip().codePointAt(0));

        final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);

        if (script == Character.UnicodeScript.LATIN) {
            return ASCII_PATTERN.matcher(normalized)
                                .replaceAll("");
        } else {
            // URGENT: use Classes for Unicode blocks and categories
            return normalized;
        }
    }
}
