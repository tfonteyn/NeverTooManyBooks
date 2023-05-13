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
 * This class (and similar UNICODE handling classes) MUST be tested with "androidTest"
 * as unit-testing will cause false positives/failures due to the lack/presence
 * of the flag {@code use the flag: Pattern.UNICODE_CHARACTER_CLASS}.
 * <p>
 * See <a href="https://issuetracker.google.com/issues/181655428">Google bug 181655428</a>
 * <pre>
 *  1. Normally we should use the flag: Pattern.UNICODE_CHARACTER_CLASS
 *     but android does not need/support it as it always uses unicode (it says...)
 *     When using {Alnum} Android will NOT use unicode contradicting the above.
 *
 *  2. Combining explicit unicode {IsAlphabetic} with 'd' for digits
 *     Pattern.compile("[^\\p{IsAlphabetic}\\d]");
 *     and unit testing on JDK 17 (Windows) works fine, but fails with on-device test.
 *     google bug: https://issuetracker.google.com/issues/181655428
 *
 *  3. Using as per google bug:
 *     Pattern.compile("[^\\p{Alpha}\\d]");
 *     unit testing fails on the hosting JDK 17 for non-latin (but works for latin),
 *     but works with on-device test.
 * </pre>
 * <p>
 * Passes "androidTest" on API 26,27,31,33
 */
public final class AlphabeticNormalizer {

    private static final Pattern ALFA_PATTERN = Pattern.compile("[^\\p{Alpha}\\d]");

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
