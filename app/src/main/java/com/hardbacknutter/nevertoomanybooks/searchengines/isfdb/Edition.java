/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Document;

/**
 * A value class for holding the ISFDB book id and its (optional) Document (web page).
 */
public class Edition {

    @Nullable
    private final String isbn;

    /** The ISFDB book ID. */
    private final long isfdbId;

    /**
     * If a fetch of editions resulted in a single book returned (via redirects),
     * then the doc is kept here for immediate processing.
     * If we get (at least) 2 editions, then this will always be {@code null}.
     */
    @Nullable
    private final Document document;
    @Nullable
    private final String langIso3;

    /**
     * Constructor: we found a link to a book.
     *
     * @param isfdbId  of the book we found
     * @param isbn     of the book we found (as read from the site)
     * @param langIso3 the iso3 code for the language of this edition
     */
    Edition(final long isfdbId,
            @Nullable final String isbn,
            @Nullable final String langIso3) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        this.langIso3 = langIso3;
        document = null;
    }

    /**
     * Constructor: we found a single edition, the doc contains the book for further processing.
     *
     * @param isfdbId  of the book we found
     * @param isbn     of the book we <strong>searched</strong>
     * @param document the JSoup document of the book we found
     */
    Edition(final long isfdbId,
            @Nullable final String isbn,
            @Nullable final String langIso3,
            @Nullable final Document document) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        this.document = document;
        this.langIso3 = langIso3;
    }

    @Nullable
    public Document getDocument() {
        return document;
    }

    @Nullable
    public String getIsbn() {
        return isbn;
    }

    long getIsfdbId() {
        return isfdbId;
    }

    @Nullable
    public String getLangIso3() {
        return langIso3;
    }

    @Override
    @NonNull
    public String toString() {
        return "Edition{"
               + "mIsfdbId=" + isfdbId
               + ", mIsbn=`" + isbn + '`'
               + ", mLangIso3=`" + langIso3 + '`'
               + ", mDoc?=" + (document != null)
               + '}';
    }
}
