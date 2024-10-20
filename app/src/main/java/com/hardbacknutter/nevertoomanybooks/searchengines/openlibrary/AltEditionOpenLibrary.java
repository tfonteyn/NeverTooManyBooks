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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;

/**
 * Edition data is returned by {@code https://openlibrary.org/works/OL5725956W/editions.json}.
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
    /** Guaranteed to be len=2. */
    private final long[] covers;

    /**
     * Constructor.
     *
     * @param olid      {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#SID_OPEN_LIBRARY}
     * @param isbn      of the book book
     * @param langIso3  language ISO3 code of the book
     * @param publisher primary publisher name
     * @param covers    the OL native cover id(s); the array <strong>must</strong> be 2 elements.
     *
     * @throws IllegalArgumentException if the covers array is not 2 elements.
     */
    AltEditionOpenLibrary(@NonNull final String olid,
                          @Nullable final String isbn,
                          @Nullable final String langIso3,
                          @Nullable final String publisher,
                          @NonNull final long[] covers) {
        if (covers.length != 2) {
            throw new IllegalArgumentException("covers must be long[2]");
        }

        this.olid = olid;
        this.isbn = isbn;
        this.langIso3 = langIso3;
        this.publisher = publisher;
        this.covers = covers;
    }

    private AltEditionOpenLibrary(@NonNull final Parcel in) {
        //noinspection DataFlowIssue
        olid = in.readString();
        isbn = in.readString();
        langIso3 = in.readString();
        publisher = in.readString();
        covers = new long[2];
        in.readLongArray(covers);
    }

    @Override
    public boolean mayHaveCover() {
        return covers[0] != 0;
    }

    /**
     * {@link com.hardbacknutter.nevertoomanybooks.database.DBKey#SID_OPEN_LIBRARY}.
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
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(olid);
        dest.writeString(isbn);
        dest.writeString(langIso3);
        dest.writeString(publisher);
        dest.writeLongArray(covers);
    }

    @Override
    public int describeContents() {
        return 0;
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
