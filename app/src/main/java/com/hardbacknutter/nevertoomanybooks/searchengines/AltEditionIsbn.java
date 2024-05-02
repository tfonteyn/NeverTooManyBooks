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
import androidx.annotation.Nullable;

public class AltEditionIsbn
        implements AltEdition {

    @Nullable
    private final String isbn;

    public AltEditionIsbn(@Nullable final String isbn) {
        this.isbn = isbn;
    }

    @Override
    @Nullable
    public String getIsbn() {
        return isbn;
    }

    @Nullable
    @Override
    public String getPublisher() {
        return null;
    }

    @Nullable
    @Override
    public String getLangIso3() {
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return "AltEditionIsbn{"
               + "isbn=`" + isbn + '`'
               + '}';
    }
}
