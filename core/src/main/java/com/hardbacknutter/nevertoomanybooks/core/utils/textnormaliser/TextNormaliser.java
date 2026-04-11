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

import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

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
 * @see TransliteratorApi26
 * @see TransliteratorApi29
 */
public class TextNormaliser
        implements TextTransliterator {

    /** KEEP alpha/digit. KEEP SINGLE spaces. */
    private static final Pattern KEEP_SPACES_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");

    /** KEEP alpha/digit. REMOVE ALL white-space */
    private static final Pattern REMOVE_SPACES_PATTERN = Pattern.compile("[^\\p{Alpha}\\d]");

    /** Replace ALL white-space characters with a single space. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final String SINGLE_SPACE = " ";
    private static final String REMOVE = "";
    @NonNull
    private final TextTransliterator transliterator;

    /**
     * Constructor.
     */
    public TextNormaliser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            transliterator = new TransliteratorApi29();
        } else {
            transliterator = new TransliteratorApi26();
        }
    }

    @NonNull
    @Override
    public String transliterate(@NonNull final CharSequence text) {
        return transliterator.transliterate(text);
    }

    /**
     * Normalise the given string but keep it user readable.
     * <ol>
     *     <li>Remove any non-alpha/digit characters</li>
     *     <li>Condense white-space to a single-space and <strong>KEEP</strong></li>
     *     <li>Case is preserved</li>
     * </ol>
     *
     * @param text to normalise
     *
     * @return normalised text
     */
    @NonNull
    public String normalise(@NonNull final CharSequence text) {
        return normalise(text, true);
    }

    /**
     * Normalise the given string fully.
     * <ol>
     *     <li>Remove any non-alpha/digit characters</li>
     *     <li>White-space is <strong>REMOVED</strong></li>
     *     <li>Forced to lowercase</li>
     * </ol>
     *
     * @param text   to normalise
     * @param locale Current Locale
     *
     * @return normalised text; always lowercase
     */
    @NonNull
    public String strict(@NonNull final CharSequence text,
                         @NonNull final Locale locale) {
        return normalise(text, false).toLowerCase(locale);
    }

    /**
     * Normalise the given string.
     * <ol>
     *     <li>Remove any non-alpha/digit characters</li>
     *     <li>Case is preserved</li>
     * </ol>
     *
     * @param text             to normalise
     * @param keepSingleSpaces {@code true} to condense white-space to a single-space
     *                         and <strong>KEEP</strong>,
     *                         or {@code false} to remove ALL white-space.
     *
     * @return normalised text
     */
    @NonNull
    private String normalise(@NonNull final CharSequence text,
                             final boolean keepSingleSpaces) {
        String result = transliterator.transliterate(text);

        if (keepSingleSpaces) {
            // REPLACE unwanted characters with a space; spaces are KEPT
            result = KEEP_SPACES_PATTERN.matcher(result).replaceAll(SINGLE_SPACE);
            // Condense all special or duplicate whitespace into single spaces
            result = WHITESPACE.matcher(result).replaceAll(SINGLE_SPACE).strip();
            return result;

        } else {
            // remove unwanted characters; spaces are REMOVED
            return REMOVE_SPACES_PATTERN.matcher(result).replaceAll(REMOVE);
        }
    }
}
