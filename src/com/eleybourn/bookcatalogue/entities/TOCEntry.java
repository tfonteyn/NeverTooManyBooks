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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to represent a single title within an TOC(Anthology).
 *
 * Note:
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a List<TOCEntry> in one go. This circumvents the 'position' column
 * as the update will simply insert in-order and auto increment position.
 * Retrieving by bookId is always done ordered by position.
 *
 * @author pjw
 */
public class TOCEntry
        implements Parcelable, Utils.ItemWithIdFixup {

    /** {@link Parcelable}. */
    public static final Creator<TOCEntry> CREATOR =
            new Creator<TOCEntry>() {
                @Override
                public TOCEntry createFromParcel(@NonNull final Parcel source) {
                    return new TOCEntry(source);
                }

                @Override
                public TOCEntry[] newArray(final int size) {
                    return new TOCEntry[size];
                }
            };
    /**
     * import/export etc...
     *
     * "anthology title (year) * author ","anthology title (year) * author ",...
     */
    private static final char SEPARATOR = ',';
    private static final char TITLE_AUTHOR_DELIM = '*';

    /**
     * Find the publication year in a string like "some title (1960)".
     *
     * The pattern finds (1960), group 1 will then contain the pure 1960.
     *
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     */
    private static final Pattern YEAR_FROM_STRING = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");
    private long id;
    private Author mAuthor;
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate = "";

    /**
     * Constructor that will attempt to parse a single string into an TOCEntry.
     */
    public TOCEntry(@NonNull final String fromString) {
        fromString(fromString);
    }

    /**
     * Constructor.
     *
     * @param author Author of title
     * @param title  Title
     */
    public TOCEntry(@NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final String publicationDate) {
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
    }

    protected TOCEntry(@NonNull final Parcel in) {
        id = in.readLong();
        mAuthor = in.readParcelable(getClass().getClassLoader());
        mTitle = in.readString();
        mFirstPublicationDate = in.readString();
    }

    /**
     * Helper to check if all titles in a list have the same author.
     */
    public static boolean hasMultipleAuthors(@NonNull final List<TOCEntry> results) {
        // check if its all the same author or not
        boolean singleAuthor = true;
        if (results.size() > 1) {
            Author author = results.get(0).getAuthor();
            for (TOCEntry t : results) { // yes, we check 0 twice.. oh well.
                singleAuthor = author.equals(t.getAuthor());
                if (!singleAuthor) {
                    break;
                }
            }
        }
        return !singleAuthor;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeParcelable(mAuthor, flags);
        dest.writeString(mTitle);
        dest.writeString(mFirstPublicationDate);
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Support for decoding from a text file.
     */
    private void fromString(@NonNull final String encodedString) {
        // V82: Giants In The Sky * Blish, James
        // V83: Giants In The Sky (1952) * Blish, James

        List<String> list = StringList.decode(TITLE_AUTHOR_DELIM, encodedString);
        mAuthor = new Author(list.get(1));
        String title = list.get(0);

        //FIXME: fine for now, but should be made foolproof for full dates
        // (via DateUtils) instead of just the 4 digit year
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
     * Support for encoding to a text file.
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
            yearStr = " (" + mFirstPublicationDate + ')';
        } else {
            yearStr = "";
        }
        return StringList.encodeListItem(SEPARATOR, mTitle) +
                yearStr + ' ' + TITLE_AUTHOR_DELIM + ' ' + mAuthor;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    @NonNull
    public Author getAuthor() {
        return mAuthor;
    }

    public void setAuthor(@NonNull final Author author) {
        mAuthor = author;
    }

    @NonNull
    public String getFirstPublication() {
        return mFirstPublicationDate;
    }

    public void setFirstPublication(@NonNull final String publicationDate) {
        mFirstPublicationDate = publicationDate;
    }

    @Override
    public long fixupId(@NonNull final DBA db) {
        mAuthor.id = db.getAuthorIdByName(mAuthor.getFamilyName(), mAuthor.getGivenNames());
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
     * Equality.
     *
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. id == 0) but all their fields are equal
     * - their id's are the same
     *
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TOCEntry that = (TOCEntry) obj;
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


    //ENHANCE: use enum for ANTHOLOGY_BITMASK
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
                //noinspection UnnecessaryDefault
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

                // cover legacy bad data.
                case 0x10:
                case 0x11:
                    return multipleAuthors;
                default:
                    throw new RTE.IllegalTypeException("" + bitmask);
            }
        }
    }
}
