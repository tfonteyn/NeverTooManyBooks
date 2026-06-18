/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;

public class AltEditionIsbn
        implements AltEdition {

    @NonNull
    private final String isbn;

    /**
     * Constructor.
     *
     * @param validIsbn <strong>must</strong> be valid.
     */
    public AltEditionIsbn(@NonNull final String validIsbn) {
        this.isbn = validIsbn;
    }

    /**
     * The ISBN for this edition.
     *
     * @return isbn
     */
    @NonNull
    public String getIsbn() {
        return isbn;
    }

    @Override
    @NonNull
    public String toString() {
        return "AltEditionIsbn{"
               + "isbn=`" + isbn + '`'
               + '}';
    }
}
