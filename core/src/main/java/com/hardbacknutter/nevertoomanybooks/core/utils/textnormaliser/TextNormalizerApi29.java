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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Locale;

@RequiresApi(api = Build.VERSION_CODES.Q)
class TextNormalizerApi29
        implements TextNormalizer {

    /** Remove Unicode combining marks (accents, diacritics). */
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance(
            "NFD; [:Nonspacing Mark:] Remove; Latin-ASCII");

    @NonNull
    @Override
    public String transliterate(@NonNull final CharSequence text) {
        return TRANSLITERATOR.transliterate(text.toString());
    }

    @Override
    @NonNull
    public String normalize(@NonNull final CharSequence text) {
        String result;
        result = TRANSLITERATOR.transliterate(text.toString());
        // REPLACE unwanted characters with a space; spaces are KEPT
        result = TNP.NORMALIZE_PATTERN.matcher(result).replaceAll(TNP.SINGLE_SPACE);
        // Condense all special or duplicate whitespace into single spaces
        result = TNP.WHITESPACE.matcher(result).replaceAll(TNP.SINGLE_SPACE);

        return result.strip();
    }

    @Override
    @NonNull
    public String orderByColumn(@NonNull final CharSequence text,
                                @NonNull final Locale locale) {
        String result;
        result = TRANSLITERATOR.transliterate(text.toString());
        // remove unwanted characters; spaces are REMOVED
        result = TNP.ORDERBY_PATTERN.matcher(result).replaceAll(TNP.REMOVE);

        return result.toLowerCase(locale);
    }

    @Override
    @NonNull
    public String ftsNormalise(@NonNull final CharSequence text) {
        String result;
        result = TRANSLITERATOR.transliterate(text.toString());
        // REMOVE unwanted characters; whitespace and '-'  are KEPT
        result = TNP.FTS_PATTERN.matcher(result).replaceAll(TNP.REMOVE);
        // Condense all special or duplicate whitespace into single spaces
        result = TNP.WHITESPACE.matcher(result).replaceAll(TNP.SINGLE_SPACE);

        return result.strip();
    }
}
