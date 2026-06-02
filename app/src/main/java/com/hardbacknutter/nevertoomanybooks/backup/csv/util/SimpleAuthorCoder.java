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
package com.hardbacknutter.nevertoomanybooks.backup.csv.util;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Author;

public final class SimpleAuthorCoder
        implements StringList.Coder<Author> {

    private final char elementSeparator;

    private SimpleAuthorCoder(final char elementSeparator) {
        this.elementSeparator = elementSeparator;
    }

    @NonNull
    public static StringList<Author> create(final char elementSeparator) {
        return new StringList<>(new SimpleAuthorCoder(elementSeparator));
    }

    @Override
    public char getElementSeparator() {
        return elementSeparator;
    }

    @Override
    @NonNull
    public Author decode(@NonNull final String element) {
        return Author.from(element);
    }
}
