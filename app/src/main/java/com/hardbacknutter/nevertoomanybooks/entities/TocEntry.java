/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Class to represent a single title within an TOC(Anthology).
 * <p>
 * <b>Note:</b>
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a {@code List<TocEntry>} in one go.
 * This circumvents the 'position' column as the update will simply insert in-order
 * and auto increment the position.
 * Retrieving by bookId is always done ordered by position.
 */
public class TocEntry
        implements Parcelable, ItemWithFixableId, ItemWithTitle {

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

    private long mId;
    @NonNull
    private Author mAuthor;
    @NonNull
    private String mTitle;
    @NonNull
    private String mFirstPublicationDate;
    /** in-memory use only. Type of entry. */
    private Type mType;
    /** in-memory use only. Number of books this TocEntry appears in. */
    private int mBookCount;
    /** cached locale. */
    private Locale mLocale;

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
        mType = Type.Toc;
        mBookCount = 1;
    }

    /**
     * Constructor used during a JOIN of a Toc and its book(s).
     *
     * @param id              row id
     * @param author          Author of title
     * @param title           Title
     * @param publicationDate year of first publication
     * @param type            {@link Type#TYPE_TOC} or {@link Type#TYPE_BOOK}
     * @param bookCount       number of books this TocEntry appears in
     */
    public TocEntry(final long id,
                    @NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final String publicationDate,
                    final char type,
                    final int bookCount) {
        mId = id;
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = publicationDate;
        mType = Type.get(type);
        mBookCount = bookCount;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private TocEntry(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mAuthor = in.readParcelable(getClass().getClassLoader());
        //noinspection ConstantConditions
        mTitle = in.readString();
        //noinspection ConstantConditions
        mFirstPublicationDate = in.readString();

        mType = Type.get((char) in.readInt());
        mBookCount = in.readInt();
    }

    /**
     * Helper to check if all titles in a list have the same author.
     *
     * @param list of entries
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
     * @return type
     */
    @NonNull
    public Type getType() {
        return mType;
    }

    public int getBookCount() {
        return mBookCount;
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
        mType = source.mType;
        mBookCount = source.mBookCount;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeParcelable(mAuthor, flags);
        dest.writeString(mTitle);
        dest.writeString(mFirstPublicationDate);
        dest.writeInt(mType.getInt());
        dest.writeInt(mBookCount);
    }

    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    @Override
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

    /**
     * Convenience method.
     *
     * @return list of Authors.
     */
    @NonNull
    public List<Author> getAuthors() {
        Author[] authors = {mAuthor};
        return Arrays.asList(authors);
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
     * the TocEntry itself, or on the locale of the <strong>primary</strong> book or
     * the Author.
     * None of those is implemented for now. So we cheat.
     *
     * @return the locale of the TocEntry
     */
    @NonNull
    @Override
    public Locale getLocale() {
        if (mLocale == null) {
            mLocale = LocaleUtils.getPreferredLocale();
        }
        return mLocale;
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      @NonNull final Locale tocLocale) {
        // let the Author use its own Locale.
        mAuthor.fixId(context, db);
        mId = db.getTocEntryId(context, this, tocLocale);
        return mId;
    }

    /**
     * Each TocEntry is defined exactly by a unique ID.
     * I.o.w. each combination of Title + Author (id) and publication date is unique.
     */
    @Override
    @SuppressWarnings("SameReturnValue")
    public boolean isUniqueById() {
        return true;
    }

    /**
     * Equality: <strong>id, Author(id) and Title</strong>.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(mId, mAuthor, mTitle);
    }

    /**
     * Equality: <strong>id, Author(id) and Title</strong>.
     * <p>
     * <li>it's the same Object</li>
     * <li>one or both of them are 'new' (e.g. id == 0) or have the same id<br>
     * AND all other fields are equal</li>
     * <p>
     * Compare is CASE SENSITIVE ! This allows correcting case mistakes even with identical id.
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
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mAuthor, that.mAuthor)
               && Objects.equals(mTitle, that.mTitle);

    }

    @Override
    @NonNull
    public String toString() {
        return "TocEntry{"
               + "mId=" + mId
               + ", mAuthor=" + mAuthor
               + ", mTitle=`" + mTitle + '`'
               + ", mFirstPublicationDate=`" + mFirstPublicationDate + '`'
               + ", mType=`" + mType + '`'
               + ", mLocale=" + mLocale
               + ", mBookCount=`" + mBookCount + '`'
               + '}';
    }

    /**
     * Translator for the database character type value to the enum values.
     * <p>
     * A TocEntry can be a real entry, or it can be a book posing as a pseudo entry.
     */
    public enum Type {
        Toc, Book;

        /** As used by the DAO. */
        public static final char TYPE_TOC = 'T';
        /** As used by the DAO. */
        public static final char TYPE_BOOK = 'B';

        /** Constructor. */
        public static Type get(final char c) {
            switch (c) {
                case TYPE_TOC:
                    return Toc;
                case TYPE_BOOK:
                    return Book;
                default:
                    throw new IllegalStateException("c=" + c);
            }
        }

        public int getInt() {
            switch (this) {
                case Toc:
                    return TYPE_TOC;
                case Book:
                    return TYPE_BOOK;
                default:
                    throw new IllegalStateException();
            }
        }
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
     * ENHANCE: currently we use the bit definitions directly. Should use the enum as an enum.
     */
    public enum Authors {
        singleAuthorSingleWork, singleAuthorCollection, multipleAuthorsCollection;

        /** Bit definitions. */
        public static final int SINGLE_AUTHOR_SINGLE_WORK = 0;
        public static final int MULTIPLE_WORKS = 1;
        public static final int MULTIPLE_AUTHORS = 1 << 1;

        /**
         * Get the int representation as stored in the database.
         *
         * @return bitmask
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
                    throw new IllegalStateException(String.valueOf(bitmask));
            }
        }
    }
}
