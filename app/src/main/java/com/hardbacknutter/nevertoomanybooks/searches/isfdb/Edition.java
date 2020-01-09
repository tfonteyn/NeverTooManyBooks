/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Document;

/**
 * A data class for holding the ISFDB native book id and its (optional) doc (web page).
 */
public class Edition {

    @Nullable
    public final String isbn;
    /** The ISFDB native book ID. */
    final long isfdbId;
    /**
     * If a fetch of editions resulted in a single book returned (via redirects),
     * then the doc is kept here for immediate processing.
     * If we get (at least) 2 editions, then this will always be {@code null}.
     */
    @Nullable
    final Document doc;

    /**
     * Constructor: we found a link to a book.
     *
     * @param isfdbId of the book we found
     * @param isbn    of the book we found (as read from the site)
     */
    Edition(final long isfdbId,
            @Nullable final String isbn) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        doc = null;
    }

    /**
     * Constructor: we found a single edition, the doc contains the book for further processing.
     *
     * @param isfdbId of the book we found
     * @param isbn    of the book we found (as searched for, not yet read from the 'doc')
     * @param doc     of the book we found
     */
    Edition(final long isfdbId,
            @Nullable final String isbn,
            @Nullable final Document doc) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        this.doc = doc;
    }

    @Override
    @NonNull
    public String toString() {
        return "Edition{"
               + "isfdbId=" + isfdbId
               + ", isbn=`" + isbn + '`'
               + ", doc? =" + (doc != null)
               + '}';
    }
}
