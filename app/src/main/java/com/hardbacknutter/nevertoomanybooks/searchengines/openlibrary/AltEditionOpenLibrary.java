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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;

public class AltEditionOpenLibrary
        implements AltEdition {

    @NonNull
    private final String olid;
    @Nullable
    private final String isbn;
    @Nullable
    private final String langIso3;
    @Nullable
    private final String publisher;
    private final long[] covers = new long[DBKey.NR_OF_BOOK_COVERS];

    /**
     * Constructor.
     *
     * @param olid      {@link Identifier#SID_OPEN_LIBRARY}
     * @param isbn      of the book
     * @param langIso3  language ISO3 code of the book
     * @param publisher primary publisher name
     * @param covers    the OL native cover id(s).
     *                  Up to {@code DBKey.NR_OF_BOOK_COVERS} will be used.
     */
    AltEditionOpenLibrary(@NonNull final String olid,
                          @Nullable final String isbn,
                          @Nullable final String langIso3,
                          @Nullable final String publisher,
                          @NonNull final long[] covers) {
        this.olid = olid;
        this.isbn = isbn;
        this.langIso3 = langIso3;
        this.publisher = publisher;
        // paranoia: both should be the same length
        final int maxCovers = Math.min(covers.length, DBKey.NR_OF_BOOK_COVERS);
        System.arraycopy(covers, 0, this.covers, 0, maxCovers);
    }

    @Override
    public boolean mayHaveCover() {
        return covers[0] != 0;
    }

    /**
     * {@link Identifier#SID_OPEN_LIBRARY}.
     *
     * @return the website id
     */
    @NonNull
    public String getOLID() {
        return olid;
    }

    @Nullable
    public String getIsbn() {
        return isbn;
    }

    @Nullable
    public String getLangIso3() {
        return langIso3;
    }

    @Nullable
    public String getPublisher() {
        return publisher;
    }

    @NonNull
    public long[] getCovers() {
        return covers;
    }

    @Override
    @NonNull
    public String toString() {
        return "AltEditionOpenLibrary{"
               + "olid=`" + olid + '`'
               + ", isbn=`" + isbn + '`'
               + ", langIso3=`" + langIso3 + '`'
               + ", publisher=`" + publisher + '`'
               + ", covers=`" + Arrays.toString(covers) + '`'
               + '}';
    }
}
