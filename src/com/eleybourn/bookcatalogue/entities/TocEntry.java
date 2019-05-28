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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StringList;
import com.eleybourn.bookcatalogue.utils.Utils;

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

    public static final char TYPE_TOC = 'T';
    public static final char TYPE_BOOK = 'B';

    /**
     * Find the publication year in a string like "some title (1978-04-22)".
     * <p>
     * The pattern finds (1987), (1978-04) or (1987-04-22)
     * Result is found in group 1.
     * <p>
     * Used by:
     * - ISFDB import of anthology titles
     * - export/import
     *
     * TODO: simplify pattern
     */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\(([1|2]\\d\\d\\d|[1|2]\\d\\d\\d-\\d\\d|[1|2]\\d\\d\\d-\\d\\d-\\d\\d)\\)");

    private long mId;
    private Author mAuthor;
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate;

    /** in-memory use only. Type of entry. */
    private char mType = TYPE_TOC;

    /**
     * Constructor.
     *
     * @param author          Author of title
     * @param title           Title
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
     * @param id              row id
     * @param author          Author of title
     * @param title           Title
     * @param publicationDate year of first publication
     * @param type            TYPE_TOC or TYPE_BOOK
     */
    public TocEntry(final long id,
                    @NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final String publicationDate,
                    final char type) {
        mId = id;
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
        setType(type);
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
     *
     * @return {@code true} if there is more then 1 author in the TOC
     */
    public static boolean hasMultipleAuthors(@NonNull final List<TocEntry> list) {
        if (list.size() > 1) {
            // use the first one as the comparator.
            Author firstAuthor = list.get(0).getAuthor();
            for (int i = 1, listSize = list.size(); i < listSize; i++) {
                if (!firstAuthor.equals(list.get(i).getAuthor())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Constructor that will attempt to parse a single string into an TocEntry.
     * <br>The date *must* match a patter of a (partial) SQL date string:
     * <ul>
     *     <li>(YYYY)</li>
     *     <li>(YYYY-MM)</li>
     *     <li>(YYYY-MM-DD)</li>
     *     <li>(YYYY-DD-MM) might work depending on the user's locale. Not tested.</li>
     * </ul>
     * <br>V82 had no dates: Giants In The Sky * Blish, James
     *  <br>V200 accepts:
     * <ul>
     *     <li>Giants In The Sky (1952) * Blish, James</li>
     *     <li>Giants In The Sky (1952-03) * Blish, James</li>
     *     <li>Giants In The Sky (1952-03-22) * Blish, James</li>
     * </ul>
     */
    public static TocEntry fromString(@NonNull final String encodedString) {

        List<String> list = new StringList<String>()
                .decode(FIELD_SEPARATOR, encodedString, false);

        Author author = Author.fromString(list.get(1));
        String title = list.get(0);

        Matcher matcher = TocEntry.DATE_PATTERN.matcher(title);
        if (matcher.find()) {
            // strip out the found pattern (including the brackets)
            title = title.replace(matcher.group(0), "").trim();
            return new TocEntry(author, title, matcher.group(1));
        } else {
            return new TocEntry(author, title, "");
        }
    }

    /**
     * @return 'B' == book title; or 'T' == Generic TOC entry(e.g. short story, intro, etc..)
     */
    public char getType() {
        return mType;
    }

    /**
     * @param type 'B' == book title; or 'T' == Generic TOC entry(e.g. short story, intro, etc..)
     */
    public void setType(final char type) {
        if (BuildConfig.DEBUG) {
            if (type != TYPE_BOOK && type != TYPE_TOC) {
                throw new IllegalTypeException("type=`" + type + '`');
            }
        }
        mType = type;
    }

    /**
     * Replace local details from another TocEntry.
     *
     * @param source TocEntry to copy from
     */
    public void copyFrom(@NonNull final TocEntry source) {
        mAuthor = source.mAuthor;
        mTitle = source.mTitle;
        mFirstPublicationDate = source.mFirstPublicationDate;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeParcelable(mAuthor, flags);
        dest.writeString(mTitle);
        dest.writeString(mFirstPublicationDate);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "TocEntry{"
                + "mId=" + mId
                + ", mAuthor=" + mAuthor
                + ", mTitle=`" + mTitle + '`'
                + ", mFirstPublicationDate=`" + mFirstPublicationDate + '`'
                + '}';
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

    /**
     * Stopgap.... makes the code elsewhere clean.
     * <p>
     * ENHANCE: The locale of the TocEntry
     * should be based on either a specific language setting for
     * the TocEntry itself, or on the locale of the primary book.
     * Neither is implemented for now. So we cheat.
     *
     * @return the locale of the TocEntry
     */
    public Locale getLocale() {
        return LocaleUtils.getSystemLocale();
    }

    @Override
    public long fixupId(@NonNull final DAO db) {
        mAuthor.fixupId(db);
        mId = db.getTocEntryId(getLocale(), mAuthor.getId(), mTitle);
        return mId;
    }

    /**
     * Each TocEntry is defined exactly by a unique ID.
     */
    @Override
    @SuppressWarnings("SameReturnValue")
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
        if (mId == 0 || that.mId == 0) {
            return Objects.equals(mAuthor, that.mAuthor)
                    && Objects.equals(mTitle, that.mTitle)
                    && Objects.equals(mFirstPublicationDate, that.mFirstPublicationDate);
        }
        return mId == that.mId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mAuthor, mTitle);
    }

    /**
     * The original code was IMHO a bit vague on the exact meaning of the 'anthology mask'.
     * So this information was mainly written for myself.
     * <p>
     * Original, it looked like this was the meaning:
     * 0%00 == book by single author
     * 0%01 == anthology by one author
     * 0%11 == anthology by multiple authors.
     * which would mean it missed books with a single story, but multiple authors; i.e. the 0%10
     * <p>
     * A more complete definition below.
     * <p>
     * {@link DBDefinitions#DOM_BOOK_TOC_BITMASK}
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
     * {@link DBDefinitions#DOM_BOOK_TOC_BITMASK}
     * <p>
     * Which of course brings it back full-circle to the original and correct (!) meaning.
     * <p>
     * Leaving all this here, as it will remind myself (and maybe others) of the 'missing' bit.
     * <p>
     * ENHANCE: Think about actually updating the column to 0%10 as a cache for a book
     * having multiple authors without the need to 'count' them in the book_author table ?
     * <p>
     * ENHANCE: currently we use the bit definitions directly. Should use the enum as an enum.
     */
    public enum Authors {
        singleAuthorSingleWork, singleAuthorCollection, multipleAuthorsCollection;

        /** Bit definitions. */
        public static final int SINGLE_AUTHOR_SINGLE_WORK = 0;
        public static final int MULTIPLE_WORKS = 1;
        public static final int MULTIPLE_AUTHORS = 1 << 1;

        /**
         * @return the int representation as stored in the database.
         */
        public int getBitmask() {
            switch (this) {
                case singleAuthorSingleWork:
                    return SINGLE_AUTHOR_SINGLE_WORK;

                case singleAuthorCollection:
                    return MULTIPLE_WORKS;

                case multipleAuthorsCollection:
                    return MULTIPLE_WORKS | MULTIPLE_AUTHORS;

                //noinspection UnnecessaryDefault
                default:
                    return SINGLE_AUTHOR_SINGLE_WORK;
            }
        }

        /**
         * @param bitmask the int representation as stored in the database.
         *
         * @return the enum representation
         */
        public Authors get(final int bitmask) {
            switch (bitmask) {
                case SINGLE_AUTHOR_SINGLE_WORK:
                    return singleAuthorSingleWork;

                case MULTIPLE_WORKS:
                    return singleAuthorCollection;

                // cover legacy bad data.
                case 0x10:
                case MULTIPLE_WORKS | MULTIPLE_AUTHORS:
                    return multipleAuthorsCollection;

                default:
                    throw new IllegalTypeException(String.valueOf(bitmask));
            }
        }
    }
}
