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

@RequiresApi(api = Build.VERSION_CODES.Q)
class TransliteratorApi29
        implements TextTransliterator {

    /** Remove Unicode combining marks (accents, diacritics). */
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance(
            "NFD; [:Nonspacing Mark:] Remove; Latin-ASCII");

    @NonNull
    @Override
    public String transliterate(@NonNull final CharSequence text) {
        return TRANSLITERATOR.transliterate(text.toString());
    }
}
