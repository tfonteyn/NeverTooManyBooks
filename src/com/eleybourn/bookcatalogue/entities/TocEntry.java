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
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to represent a single title within an TOC(Anthology).
 * <p>
 * Note:
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a List<TocEntry> in one go. This circumvents the 'position' column
 * as the update will simply insert in-order and auto increment position.
 * Retrieving by bookId is always done ordered by position.
 *
 * @author pjw
 */
public class TocEntry
        implements Parcelable, Utils.ItemWithIdFixup {

    /** {@link Parcelable}. */
    public static final Creator<TocEntry> CREATOR =
            new Creator<TocEntry>() {
                @Override
                public TocEntry createFromParcel(@NonNull final Parcel source) {
                    return new TocEntry(source);
                }

                @Override
                public TocEntry[] newArray(final int size) {
                    return new TocEntry[size];
                }
            };

    /** String encoding use. */
    private static final char FIELD_SEPARATOR = '*';

    /**
     * Find the publication year in a string like "some title (1960)".
     * <p>
     * The pattern finds (1960), group 1 will then contain the pure 1960.
     * <p>
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     */
    private static final Pattern YEAR_FROM_STRING = Pattern.compile("\\(([1|2]\\d\\d\\d)\\)");
    private long mId;
    private Author mAuthor;
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate;

    /**
     * Constructor.
     *
     * @param author Author of title
     * @param title  Title
     * @param publicationDate year of first publication
     */
    public TocEntry(@NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final String publicationDate) {
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
    }

    /**
     * Full constructor.
     *
     * @param id     row id
     * @param author Author of title
     * @param title  Title
     * @param publicationDate year of first publication
     */
    public TocEntry(final long id,
                    @NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final String publicationDate) {
        mId = id;
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
    }

    /** {@link Parcelable}. */
    protected TocEntry(@NonNull final Parcel in) {
        mId = in.readLong();
        mAuthor = in.readParcelable(getClass().getClassLoader());
        mTitle = in.readString();
        //noinspection ConstantConditions
        mFirstPublicationDate = in.readString();
    }

    /**
     * Helper to check if all titles in a list have the same author.
     */
    public static boolean hasMultipleAuthors(@NonNull final List<TocEntry> list) {
        // check if its all the same author or not
        boolean singleAuthor = true;
        if (list.size() > 1) {
            Author author = list.get(0).getAuthor();
            // yes, we check 0 twice.. oh well.
            for (TocEntry tocEntry : list) {
                singleAuthor = author.equals(tocEntry.getAuthor());
                if (!singleAuthor) {
                    break;
                }
            }
        }
        return !singleAuthor;
    }

    /** {@link Parcelable}. */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
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
     * Constructor that will attempt to parse a single string into an TocEntry.
     */
    public static TocEntry fromString(@NonNull final String encodedString) {
        // V82: Giants In The Sky * Blish, James
        // V83: Giants In The Sky (1952) * Blish, James
        List<String> list = new StringList<String>()
                .decode(FIELD_SEPARATOR, encodedString, false);

        Author author =  Author.fromString(list.get(1));
        String title = list.get(0);

        //FIXME: fine for now, but should be made foolproof for full dates
        // (via DateUtils) instead of just the 4 digit year
        Matcher matcher = TocEntry.YEAR_FROM_STRING.matcher(title);
        if (matcher.find()) {
            return new TocEntry(author,
                                title.replace(matcher.group(0), "").trim(),
                                matcher.group(1));
        } else {
            return new TocEntry(author, title, "");
        }
    }


    @Override
    @NonNull
    public String toString() {
        return stringEncoded();
    }

    /**
     * Support for encoding to a text file.
     *
     * @return the object encoded as a String.
     * <p>
     * If the year is known:
     * "Giants In The Sky (1952) * Blish, James"
     * else:
     * "Giants In The Sky * Blish, James"
     */
    public String stringEncoded() {
        String yearStr;
        if (!mFirstPublicationDate.isEmpty()) {
            // start with a space !
            yearStr = " (" + mFirstPublicationDate + ')';
        } else {
            yearStr = "";
        }
        return StringList.escapeListItem(FIELD_SEPARATOR, "(", mTitle) + yearStr
                + ' ' + FIELD_SEPARATOR + ' '
                + mAuthor.stringEncoded();
    }

    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
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
        mAuthor.fixupId(db);
        mId = db.getTOCEntryId(mAuthor.getId(), mTitle);
        return mId;
    }

    /**
     * Each TocEntry is defined exactly by a unique ID.
     */
    @Override
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Equality.
     * <p>
     * - it's the same Object duh..
     * - one or both of them is 'new' (e.g. mId == 0) but all their fields are equal
     * - their mId's are the same
     * <p>
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
        TocEntry that = (TocEntry) obj;
        if (this.mId == 0 || that.mId == 0) {
            return Objects.equals(this.mAuthor, that.mAuthor)
                    && Objects.equals(this.mTitle, that.mTitle)
                    && Objects.equals(this.mFirstPublicationDate, that.mFirstPublicationDate);
        }
        return this.mId == that.mId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mAuthor, mTitle);
    }


    //ENHANCE: use enum for ANTHOLOGY_BITMASK
    public enum Type {
        no, singleAuthor, multipleAuthors;

        /**
         * The original code was a bit vague on the exact meaning of the 'anthology mask'.
         * So this information was mainly written for myself.
         * <p>
         * Original, it looked like this was the meaning:
         * 0%00 == book by single author
         * 0%01 == anthology by one author
         * 0%11 == anthology by multiple authors.
         * which would mean it missed books with a single story, but multiple authors; e.g. the 0%10
         * <p>
         * A more complete definition below.
         * <p>
         * {@link DatabaseDefinitions#DOM_BOOK_ANTHOLOGY_BITMASK}
         * <p>
         * 0%00 = contains one 'work' and is written by a single author.
         * 0%01 = multiple 'work' and is written by a single author (anthology from ONE author)
         * 0%10 = multiple authors cooperating on a single 'work'
         * 0%11 = multiple authors and multiple 'work's (it's an anthology from multiple author)
         * <p>
         * or in other words:
         * * bit 0 indicates if a book has one (bit unset) or multiple (bit set) works
         * * bit 1 indicates if a book has one (bit unset) or multiple (bit set) authors.
         * <p>
         * Having said all that, the 0%10 should not actually occur, as this is a simple case of
         * collaborating authors which is covered without the use of
         * {@link DatabaseDefinitions#DOM_BOOK_ANTHOLOGY_BITMASK}
         * <p>
         * Which of course brings it back full-circle to the original and correct meaning.
         * <p>
         * Leaving all this here, as it will remind myself (and maybe others) of the 'missing' bit.
         * <p>
         * ENHANCE: Think about actually updating the column to 0%10 as a cache for a book
         * having multiple authors without the need to 'count' them in the book_author table ?
         */
        public static final int SINGLE_AUTHOR_SINGLE_WORK = 0;
        public static final int MULTIPLE_WORKS = 1;
        public static final int MULTIPLE_AUTHORS = 1 << 1;

        public int getBitmask() {
            switch (this) {
                case no:
                    return SINGLE_AUTHOR_SINGLE_WORK;

                case singleAuthor:
                    return MULTIPLE_WORKS;

                case multipleAuthors:
                    return MULTIPLE_WORKS | MULTIPLE_AUTHORS;

                //noinspection UnnecessaryDefault
                default:
                    return SINGLE_AUTHOR_SINGLE_WORK;
            }
        }

        public Type get(final int bitmask) {
            switch (bitmask) {
                case SINGLE_AUTHOR_SINGLE_WORK:
                    return no;

                case MULTIPLE_WORKS:
                    return singleAuthor;

                // cover legacy bad data.
                case 0x10:
                case MULTIPLE_WORKS | MULTIPLE_AUTHORS:
                    return multipleAuthors;

                default:
                    throw new RTE.IllegalTypeException("" + bitmask);
            }
        }
    }
}
