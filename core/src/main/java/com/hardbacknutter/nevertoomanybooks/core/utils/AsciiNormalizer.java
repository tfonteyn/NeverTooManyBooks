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
 * <p>
 * TODO: In hindsight replacing non-ascii characters with a space might not have been
 *  the best idea.
 *  We should just use {@code Normalizer.normalize(text, Normalizer.Form.NFD)}
 *  everywhere we now call {@link #normalize(CharSequence)}.
 *  And ALSO call this on the text as entered by the user for doing a search.
 *  Once implemented, schedule a rebuild for the ORDER BY and the FTS table.
 */
public final class AsciiNormalizer {

    /** See {@link #normalize}. */
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
     * @return cleansed text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text) {
        final Character.UnicodeScript script = Character.UnicodeScript.of(
                text.toString().strip().codePointAt(0));

        if (script == Character.UnicodeScript.LATIN) {
            final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
            return ASCII_PATTERN.matcher(normalized).replaceAll("");
        } else {
            return text.toString();
        }
    }
}
