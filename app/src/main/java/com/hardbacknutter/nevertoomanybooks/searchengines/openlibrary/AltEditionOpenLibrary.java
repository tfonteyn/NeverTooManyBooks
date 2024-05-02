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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

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

/**
 * Edition data is returned by {@code https://openlibrary.org/works/OL5725956W/editions.json}
 * <pre>
 *     {
 *       "type": {
 *         "key": "/type/edition"
 *       },
 *       "authors": [
 *         {
 *           "key": "/authors/OL1392395A"
 *         }
 *       ],
 *       "local_id": [
 *         "urn:bwbsku:KO-252-870"
 *       ],
 *       "publish_date": "2001-01-01",
 *       "publishers": [
 *         "Viking"
 *       ],
 *       "source_records": [
 *         "promise:bwb_daily_pallets_2020-11-19"
 *       ],
 *       "title": "ARTEMIS FOWL",
 *       "full_title": "ARTEMIS FOWL",
 *       "works": [
 *         {
 *           "key": "/works/OL5725956W"
 *         }
 *       ],
 *       "key": "/books/OL43570585M",
 *       "covers": [
 *         13075137
 *       ],
 *       "identifiers": {},
 *       "isbn_10": [
 *         "0670911836"
 *       ],
 *       "isbn_13": [
 *         "9780670911837"
 *       ],
 *       "classifications": {},
 *       "latest_revision": 4,
 *       "revision": 4,
 *       "created": {
 *         "type": "/type/datetime",
 *         "value": "2022-12-09T13:51:21.522166"
 *       },
 *       "last_modified": {
 *         "type": "/type/datetime",
 *         "value": "2022-12-14T19:46:05.463571"
 *       }
 *     },
 * </pre>
 */
public class AltEditionOpenLibrary
        implements AltEdition {

    /** {@link Parcelable}. */
    public static final Creator<AltEditionOpenLibrary> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public AltEditionOpenLibrary createFromParcel(@NonNull final Parcel in) {
            return new AltEditionOpenLibrary(in);
        }

        @Override
        @NonNull
        public AltEditionOpenLibrary[] newArray(final int size) {
            return new AltEditionOpenLibrary[size];
        }
    };

    @NonNull
    private final String olid;
    @Nullable
    private final String isbn;
    @Nullable
    private final String langIso3;
    @Nullable
    private final String publisher;

    AltEditionOpenLibrary(@NonNull final String olid,
                          @Nullable final String isbn,
                          @Nullable final String langIso3,
                          @Nullable final String publisher) {
        this.olid = olid;
        this.isbn = isbn;
        this.langIso3 = langIso3;
        this.publisher = publisher;
    }

    private AltEditionOpenLibrary(@NonNull final Parcel in) {
        //noinspection DataFlowIssue
        olid = in.readString();
        isbn = in.readString();
        langIso3 = in.readString();
        publisher = in.readString();
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

    @NonNull
    public String getOlid() {
        return olid;
    }

    @Override
    @Nullable
    public String getIsbn() {
        return isbn;
    }

    @Override
    @Nullable
    public String getLangIso3() {
        return langIso3;
    }

    @Override
    @Nullable
    public String getPublisher() {
        return publisher;
    }


    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(olid);
        dest.writeString(isbn);
        dest.writeString(langIso3);
        dest.writeString(publisher);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Edition{"
               + "olid=`" + olid + '`'
               + ", isbn=`" + isbn + '`'
               + ", langIso3=`" + langIso3 + '`'
               + ", publisher=`" + publisher + '`'
               + '}';
    }
}
