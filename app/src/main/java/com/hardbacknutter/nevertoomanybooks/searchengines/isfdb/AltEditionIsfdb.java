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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;

/**
 * A value class for holding the ISFDB book id and its (optional) Document (web page).
 * <p>
 * IMPORTANT: {@link #document} is NOT parcelled. This is acceptable as all code
 * will assume the it's potentially {@code null} and (re)fetch it when needed.
 */
public class AltEditionIsfdb
        implements AltEdition {

    /** {@link Parcelable}. */
    public static final Creator<AltEditionIsfdb> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public AltEditionIsfdb createFromParcel(@NonNull final Parcel in) {
            return new AltEditionIsfdb(in);
        }

        @Override
        @NonNull
        public AltEditionIsfdb[] newArray(final int size) {
            return new AltEditionIsfdb[size];
        }
    };

    @Nullable
    private final String isbn;
    /** The ISFDB book ID. */
    private final long isfdbId;
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
     * @param isfdbId   of the book we found
     * @param isbn      of the book we found (as read from the site)
     * @param publisher of the book we found (as read from the site)
     * @param langIso3  the iso3 code for the language of this edition
     */
    AltEditionIsfdb(final long isfdbId,
                    @Nullable final String isbn,
                    @Nullable final String publisher,
                    @Nullable final String langIso3) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        this.publisher = publisher;
        this.langIso3 = langIso3;
        document = null;
    }

    /**
     * Constructor: we found a single edition, the doc contains the book for further processing.
     *
     * @param isfdbId  of the book we found
     * @param isbn     we <strong>searched on</strong>
     * @param document the JSoup document of the book we found
     */
    AltEditionIsfdb(final long isfdbId,
                    @Nullable final String isbn,
                    @Nullable final Document document) {
        this.isfdbId = isfdbId;
        this.isbn = isbn;
        this.publisher = null;
        this.langIso3 = null;
        this.document = document;
    }

    private AltEditionIsfdb(@NonNull final Parcel in) {
        isbn = in.readString();
        isfdbId = in.readLong();
        publisher = in.readString();
        langIso3 = in.readString();
    }

    @NonNull
    @Override
    public Optional<String> searchCover(@NonNull final Context context,
                                        @NonNull final SearchEngine.CoverByIsbn searchEngine,
                                        final int cIdx,
                                        @Nullable final Size size)
            throws SearchException, CredentialsException, StorageException {

        if (isbn != null) {
            return searchEngine.searchCoverByIsbn(context, isbn, cIdx, size);
        } else {
            return Optional.empty();
        }
    }


    @Nullable
    public Document getDocument() {
        return document;
    }

    public void clearDocument() {
        document = null;
    }

    @Override
    @Nullable
    public String getIsbn() {
        return isbn;
    }

    long getIsfdbId() {
        return isfdbId;
    }

    @Override
    @Nullable
    public String getPublisher() {
        return publisher;
    }

    @Override
    @Nullable
    public String getLangIso3() {
        return langIso3;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(isbn);
        dest.writeLong(isfdbId);
        dest.writeString(publisher);
        dest.writeString(langIso3);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Edition{"
               + "isfdbId=" + isfdbId
               + ", isbn=`" + isbn + '`'
               + ", publisher=`" + publisher + '`'
               + ", langIso3=`" + langIso3 + '`'
               + ", document?=" + (document != null)
               + '}';
    }
}
