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

import android.icu.text.Transliterator;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class TextNormalizerApi29
        implements TextNormalizer {

    /** Remove Unicode combining marks (accents, diacritics). */
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance(
            "NFD; [:Nonspacing Mark:] Remove; Latin-ASCII");

    /** Replace repeated/special whitespace characters with a single space. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** KEEP alpha/digit. REMOVE SPACES */
    private static final Pattern ORDERBY_PATTERN = Pattern.compile("[^\\p{Alpha}\\d]");

    /** KEEP alpha/digit. KEEP white-space and '-' */
    private static final Pattern FTS_PATTERN = Pattern.compile("[^\\p{Alpha}\\d\\s-]");

    /** KEEP alpha/digit. KEEP single/actual spaces. */
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");

    /**
     * Constructor.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public TextNormalizerApi29() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d("TextNormalizer", "TextNormalizerApi29");
        }
    }

    @Override
    @NonNull
    public String orderByColumn(@NonNull final CharSequence text,
                                       @NonNull final Locale locale) {
        String result = TRANSLITERATOR.transliterate(text.toString());
        // remove unwanted characters; spaces are REMOVED
        result = ORDERBY_PATTERN.matcher(result).replaceAll("");

        return result.toLowerCase(locale);
    }

    @Override
    @NonNull
    public String ftsNormalise(@NonNull final CharSequence text) {
        String result = TRANSLITERATOR.transliterate(text.toString());
        // The order is important!
        // FIRST remove unwanted characters; spaces are KEPT
        result = FTS_PATTERN.matcher(result).replaceAll("");
        // THEN condense all special or duplicate whitespace into single spaces
        result = WHITESPACE.matcher(result).replaceAll(" ");

        return result;
    }

    @Override
    @NonNull
    public String normalize(@NonNull final CharSequence text) {
        String result = TRANSLITERATOR.transliterate(text.toString());
        // The order is important!
        // FIRST remove unwanted characters; spaces are KEPT
        result = NORMALIZE_PATTERN.matcher(result).replaceAll("");
        // THEN condense all special or duplicate whitespace into single spaces
        result = WHITESPACE.matcher(result).replaceAll(" ");

        return result;
    }
}
