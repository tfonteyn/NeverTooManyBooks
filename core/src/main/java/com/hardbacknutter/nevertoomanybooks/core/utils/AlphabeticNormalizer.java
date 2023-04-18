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
public final class AlphabeticNormalizer {

    // Normally we should use the flag: Pattern.UNICODE_CHARACTER_CLASS
    // but android does not need/support it as it always uses unicode.
    private static final Pattern ALFA_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}]");

    private AlphabeticNormalizer() {
    }

    /**
     * Normalize the given string and remove any non-alphabetic characters.
     * The case is preserved.
     *
     * @param text to normalize
     *
     * @return normalized text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text) {
        final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return ALFA_PATTERN.matcher(normalized).replaceAll("");
    }
}
