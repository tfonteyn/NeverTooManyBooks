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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;

import org.jsoup.nodes.Document;

/**
 * Note that the code (ISBN or Catalog-ID) and publisher are currently only set,
 * but never read. KEEP these for future usage.
 */
public class AltEditionIsfdb
        implements AltEdition {

    /** {@link Identifier#SID_ISFDB}. */
    private final long sid;
    @Nullable
    private final String productCode;
    @Nullable
    private final String publisher;
    @Nullable
    private final String langIso3;

    /**
     * If a fetch of editions resulted in a single book returned (via redirects),
     * then the doc is kept here for immediate processing.
     * If we get (at least) 2 editions, then this will always be {@code null}.
     */
    @Nullable
    private Document document;

    /**
     * Constructor: we found a link to a book.
     *
     * @param sid         {@link Identifier#SID_ISFDB}
     * @param productCode of this edition as found on the site;
     *                    this can be an ISBN, or a catalog-id
     * @param langIso3    the iso3 code for the language of this edition
     * @param publisher   primary publisher name of this edition
     */
    AltEditionIsfdb(final long sid,
                    @Nullable final String productCode,
                    @Nullable final String langIso3,
                    @Nullable final String publisher) {
        this.sid = sid;
        this.productCode = productCode;
        this.publisher = publisher;
        this.langIso3 = langIso3;
        document = null;
    }

    /**
     * Constructor: we found a single edition,
     * the document contains the book for further processing.
     *
     * @param sid         {@link Identifier#SID_ISFDB}
     * @param productCode we <strong>searched on</strong>; this can be an ISBN, or a catalog-id
     * @param document    the JSoup document of the edition we found
     */
    AltEditionIsfdb(final long sid,
                    @Nullable final ProductCode productCode,
                    @Nullable final Document document) {
        this.sid = sid;
        this.productCode = productCode != null ? productCode.asText() : null;
        this.publisher = null;
        this.langIso3 = null;
        this.document = document;
    }

    /**
     * If the edition was a single book, the document of the book page is cached.
     *
     * @return book page, or {@code null} if there were multiple editions.
     */
    @Nullable
    public Document getDocument() {
        return document;
    }

    /**
     * Remove the document to reduce memory usage.
     */
    void clearDocument() {
        document = null;
    }

    /**
     * {@link Identifier#SID_ISFDB}.
     *
     * @return the website id
     */
    long getSid() {
        return sid;
    }

    /**
     * ISBN or catalog-id.
     *
     * @return code
     */
    @Nullable
    public String getProductCode() {
        return productCode;
    }

    /**
     * The language of this edition.
     *
     * @return language; can be {@code null} if the site did not have it
     */
    @Nullable
    public String getLangIso3() {
        return langIso3;
    }

    /**
     * The publisher of this edition.
     *
     * @return name; can be {@code null} if the site did not have it
     */
    @Nullable
    public String getPublisher() {
        return publisher;
    }

    @Override
    @NonNull
    public String toString() {
        return "AltEditionIsfdb{"
               + "sid=" + sid
               + ", productCode=`" + productCode + '`'
               + ", langIso3=`" + langIso3 + '`'
               + ", publisher=`" + publisher + '`'
               + ", document?=" + (document != null)
               + '}';
    }
}
