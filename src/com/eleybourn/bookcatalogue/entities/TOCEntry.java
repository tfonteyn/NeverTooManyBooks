/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.entities;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to represent a single title within an TOC(Anthology)
 *
 * Note:
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a List<TOCEntry> in one go. This circumvents the 'position' column
 * as the update will simply insert in-order and auto increment position.
 * Retrieving by bookId is always done ordered by position.
 *
 *
 * @author pjw
 */
public class TOCEntry implements Parcelable, Utils.ItemWithIdFixup {
    /**
     * import/export etc...
     *
     * "anthology title (year) * author ","anthology title (year) * author ",...
     */
    private static final char SEPARATOR = ',';
    private static final char TITLE_AUTHOR_DELIM = '*';

    /**
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     *
     * find the publication year in a string like "some title (1960)"
     *
     * pattern finds (1960), group 1 will then contain the pure 1960
     */
    private static final Pattern YEAR_FROM_STRING = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");

    private long id = 0;
    private Author mAuthor;
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate = "";

    /**
     * Constructor that will attempt to parse a single string into an TOCEntry.
     */
    public TOCEntry(final @NonNull String fromString) {
        fromString(fromString);
    }

    /**
     * Constructor
     *
     * @param author Author of title
     * @param title  Title
     */
    public TOCEntry(final @NonNull Author author,
                    final @NonNull String title,
                    final @NonNull String publicationDate) {
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
    }

    protected TOCEntry(Parcel in) {
        id = in.readLong();
        mAuthor = in.readParcelable(getClass().getClassLoader());
        mTitle = in.readString();
        mFirstPublicationDate = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeParcelable(mAuthor, flags);
        dest.writeString(mTitle);
        dest.writeString(mFirstPublicationDate);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TOCEntry> CREATOR = new Creator<TOCEntry>() {
        @Override
        public TOCEntry createFromParcel(Parcel in) {
            return new TOCEntry(in);
        }

        @Override
        public TOCEntry[] newArray(int size) {
            return new TOCEntry[size];
        }
    };

    /**
     * Helper to check if all titles in a list have the same author.
     */
    public static boolean isSingleAuthor(final @NonNull List<TOCEntry> results) {
        // check if its all the same author or not
        boolean sameAuthor = true;
        if (results.size() > 1) {
            Author author = results.get(0).getAuthor();
            for (TOCEntry t : results) { // yes, we check 0 twice.. oh well.
                sameAuthor = author.equals(t.getAuthor());
                if (!sameAuthor) {
                    break;
                }
            }
        }
        return sameAuthor;
    }

    /**
     * Support for decoding from a text file
     */
    private void fromString(final @NonNull String encodedString) {
        // V82: Giants In The Sky * Blish, James
        // V83: Giants In The Sky (1952) * Blish, James

        List<String> list = StringList.decode(TITLE_AUTHOR_DELIM, encodedString);
        mAuthor = new Author(list.get(1));
        String title = list.get(0);

        //TOMF FIXME: fine for now, but should be made foolproof for full dates (via DateUtils) instead of just the 4 digit year
        Matcher matcher = TOCEntry.YEAR_FROM_STRING.matcher(title);
        if (matcher.find()) {
            mFirstPublicationDate = matcher.group(1);
            mTitle = title.replace(matcher.group(0), "").trim();
        } else {
            mFirstPublicationDate = "";
            mTitle = title;
        }
    }

    /**
     * Support for encoding to a text file
     *
     * @return the object encoded as a String.
     *
     * If the year is known:
     * "Giants In The Sky (1952) * Blish, James"
     * else:
     * "Giants In The Sky * Blish, James"
     */
    @Override
    @NonNull
    public String toString() {
        String yearStr;
        if (!mFirstPublicationDate.isEmpty()) {
            // start with a space !
            yearStr = " (" + mFirstPublicationDate + ")";
        } else {
            yearStr = "";
        }
        return StringList.encodeListItem(SEPARATOR, mTitle) + yearStr + " " + TITLE_AUTHOR_DELIM + " " + mAuthor;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(final @NonNull String title) {
        mTitle = title;
    }

    @NonNull
    public Author getAuthor() {
        return mAuthor;
    }

    public void setAuthor(final @NonNull Author author) {
        mAuthor = author;
    }

    @NonNull
    public String getFirstPublication() {
        return mFirstPublicationDate;
    }

    public void setFirstPublication(final @NonNull String publicationDate) {
        mFirstPublicationDate = publicationDate;
    }

    @Override
    public long fixupId(final @NonNull CatalogueDBAdapter db) {
        this.mAuthor.id = db.getAuthorIdByName(mAuthor.familyName, mAuthor.givenNames);
        this.id = db.getTOCEntryId(mAuthor.id, mTitle);
        return this.id;
    }

    /**
     * Each TOCEntry is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Two are the same if:
     *
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) but all their fields are equal
     * - their id's are the same
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TOCEntry that = (TOCEntry) o;
        if (this.id == 0 || that.id == 0) {
            return Objects.equals(this.mAuthor, that.mAuthor)
                    && Objects.equals(this.mTitle, that.mTitle)
                    && Objects.equals(this.mFirstPublicationDate, that.mFirstPublicationDate);
        }
        return (this.id == that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, mAuthor, mTitle);
    }



    //ENHANCE use enum for ANTHOLOGY_BITMASK
    public enum Type {
        no, singleAuthor, multipleAuthors;

        public int getBitmask() {
            switch (this) {
                case no:
                    return 0x00;
                case singleAuthor:
                    return 0x01;
                case multipleAuthors:
                    return 0x11;
                default:
                    return 0x00;
            }
        }

        public Type get(final int bitmask) {
            switch (bitmask) {
                case 0x00:
                    return no;
                case 0x01:
                    return singleAuthor;
                case 0x10: // cover legacy mistakes?
                case 0x11:
                    return multipleAuthors;
                default:
                    throw new RTE.IllegalTypeException("" + bitmask);
            }
        }
    }
}
