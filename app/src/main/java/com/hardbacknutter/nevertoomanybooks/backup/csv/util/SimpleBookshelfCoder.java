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

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;


public final class SimpleBookshelfCoder
        implements StringList.Coder<Bookshelf> {

    private final char elementSeparator;
    @NonNull
    private final Style defaultStyle;

    /**
     * Constructor.
     *
     * @param elementSeparator The separator character used between list elements.
     * @param defaultStyle     the default style to use
     */
    private SimpleBookshelfCoder(final char elementSeparator,
                                 @NonNull final Style defaultStyle) {
        this.elementSeparator = elementSeparator;
        this.defaultStyle = defaultStyle;
    }

    @NonNull
    public static StringList<Bookshelf> create(final char elementSeparator,
                                               @NonNull final Style defaultStyle) {
        return new StringList<>(new SimpleBookshelfCoder(elementSeparator, defaultStyle));
    }

    @Override
    public char getElementSeparator() {
        return elementSeparator;
    }

    @Override
    @NonNull
    public Bookshelf decode(@NonNull final String element) {
        return new Bookshelf(element, defaultStyle);
    }
}
