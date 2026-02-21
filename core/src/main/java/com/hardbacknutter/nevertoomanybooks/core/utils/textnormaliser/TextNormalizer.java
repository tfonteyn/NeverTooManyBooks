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

public interface TextNormalizer {

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book
     * Keep normalised basic characters and digits, strip spaces, make all lowercase.
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
     * Normalise the given string and remove any non-alpha/digit/space characters.
     * The case is preserved.
     *
     * @param text to normalise
     *
     * @return normalized text
     */
    @NonNull
    String normalize(@NonNull CharSequence text);

    /**
     * Normalise the given string and apply the given pattern.
     * The case is preserved.
     *
     * @param text to normalise
     *
     * @return normalised text
     */
    @NonNull
    String ftsNormalise(@NonNull CharSequence text);
}
