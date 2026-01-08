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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import android.icu.text.Transliterator;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.regex.Pattern;

/**
 * Adding the full com.ibm icu would make a Transliterator usable with api 26+,
 * but would add 10Mb to the app size. Granted we could tweak this but given
 * the plan is to drop support for Android 8/9/10 soonish... no point.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public final class TextNormalizerApi29 {

    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance(
            "NFD; [:Nonspacing Mark:] Remove; Latin-ASCII");

    /** Replace repeated/special whitespace characters with a single space. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizerApi29() {
    }

    /**
     * Normalise the given string.
     * The case is preserved.
     *
     * @param text to process
     * @param keep negated pattern of characters to keep after transliteration
     *
     * @return normalized/filtered text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text,
                                   @NonNull final Pattern keep) {
        String result = TRANSLITERATOR.transliterate(text.toString());
        // Condense all special or duplicate whitespace into single spaces
        result = WHITESPACE.matcher(result).replaceAll(" ");
        // Remove unwanted characters
        return keep.matcher(result).replaceAll("");
    }
}
