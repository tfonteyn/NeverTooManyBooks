/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.core.utils.textnormaliser;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * This class (and the implementation classes) MUST be tested with "androidTest"
 * as unit-testing will cause false positives/failures due to the lack/presence
 * of the flag {@code Pattern.UNICODE_CHARACTER_CLASS}.
 * <p>
 * See <a href="https://issuetracker.google.com/issues/181655428">Google bug 181655428</a>
 * and <a href="https://issuetracker.google.com/issues/127290684">Google bug 127290684</a>
 * <pre>
 *  1. Normally we should use the flag:
 *          {@code Pattern.UNICODE_CHARACTER_CLASS}
 *     but android does not need/support it as it always uses Unicode (it says...)
 *     When using {Alnum} Android will NOT use Unicode contradicting the above.
 *
 *  2. Combining explicit Unicode {IsAlphabetic} with 'd' for digits
 *     Pattern.compile("[^\\p{IsAlphabetic}\\d ]");
 *     and unit testing on JDK 17 (Windows) works fine, but fails
 *     with on-device test.
 *     google bug: https://issuetracker.google.com/issues/181655428
 *
 *  3. Using as per Google bug:
 *     Pattern.compile("[^\\p{Alpha}\\d ]");
 *     unit testing fails on the hosting JDK 17 for non-latin
 *     (works for latin), but works with on-device test.
 * </pre>
 * <p>
 * Passes "androidTest" on API 26,27,31,33
 *
 * @see TextNormalizerApi26
 * @see TextNormalizerApi29
 */
public interface TextNormalizer {

    /**
     * Transliterate the given string.
     *
     * @param text to normalise
     *
     * @return normalized text
     */
    @NonNull
    String transliterate(@NonNull CharSequence text);

    /**
     * Normalise the given string and remove any non-alpha/digit/space characters.
     * <p>
     * The case is preserved.
     * White-space is condensed to a single-space and <strong>KEPT</strong>
     *
     * @param text to normalise
     *
     * @return normalized text
     */
    @NonNull
    String normalize(@NonNull CharSequence text);

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book...
     * <p>
     * The result is all lowercase.
     * White-space is <strong>REMOVED</strong>
     *
     * @param text   to normalise
     * @param locale Current Locale
     *
     * @return normalised text; always lowercase
     */
    @NonNull
    String orderByColumn(@NonNull CharSequence text,
                         @NonNull Locale locale);

    /**
     * Normalise the given string and apply the given pattern.
     * <p>
     * Dev. note: The difference with {@link #normalize(CharSequence)} is that the {@code -}
     * character is <strong>KEPT</strong> as a negation operator.
     * <p>
     * The case is preserved.
     * Spaces are <strong>KEPT</strong>
     *
     * @param text to normalise
     *
     * @return normalised text
     */
    @NonNull
    String ftsNormalise(@NonNull CharSequence text);
}
