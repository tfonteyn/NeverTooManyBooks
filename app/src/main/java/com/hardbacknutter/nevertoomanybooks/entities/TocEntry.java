/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Represent a single title within an TOC(Anthology).
 * <p>
 * <strong>Note:</strong>
 * these are always insert/update'd ONLY when a book is insert/update'd
 * Hence writes are always a {@code List<TocEntry>} in one go.
 * This circumvents the 'position' column as the update will simply insert in-order
 * and auto increment the position.
 * Retrieving by bookId is always done ordered by position.
 * <p>
 * TODO: orphaned TocEntry: when to delete entries ?
 * when last book is gone? or keep them for adding to new books / wish list?
 * - consider to add a purge based on book for orphaned TocEntry
 * - a purge based on Author is already done)
 */
public class TocEntry
        implements Entity, ItemWithTitle, AuthorWork {

    /** {@link Parcelable}. */
    public static final Creator<TocEntry> CREATOR = new Creator<TocEntry>() {
        @Override
        public TocEntry createFromParcel(@NonNull final Parcel source) {
            return new TocEntry(source);
        }

        @Override
        public TocEntry[] newArray(final int size) {
            return new TocEntry[size];
        }
    };

    /** Row ID. */
    private long mId;
    @NonNull
    private Author mAuthor;
    @NonNull
    private String mTitle;
    @NonNull
    private PartialDate mFirstPublicationDate;
    @Nullable
    private List<Pair<Long, String>> mBookTitles;

    /** in-memory use only. Number of books this TocEntry appears in. */
    private int mBookCount;

    /**
     * Constructor.
     *
     * @param author               Author of title
     * @param title                Title
     * @param firstPublicationDate year of first publication
     */
    public TocEntry(@NonNull final Author author,
                    @NonNull final String title,
                    @Nullable final String firstPublicationDate) {
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = new PartialDate(firstPublicationDate);
        mBookCount = 1;
    }

    /**
     * Constructor.
     *
     * @param id                   row id
     * @param author               Author of title
     * @param title                Title
     * @param firstPublicationDate year of first publication
     * @param bookCount            number of books this TocEntry appears in
     */
    public TocEntry(final long id,
                    @NonNull final Author author,
                    @NonNull final String title,
                    @Nullable final String firstPublicationDate,
                    final int bookCount) {
        mId = id;
        mAuthor = author;
        mTitle = title.trim();
        mFirstPublicationDate = new PartialDate(firstPublicationDate);
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
        mFirstPublicationDate = in.readParcelable(getClass().getClassLoader());

        mBookCount = in.readInt();
    }

    /**
     * Passed a list of Objects, remove duplicates.
     *
     * @param list         List to clean up
     * @param context      Current context
     * @param db           Database Access
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return {@code true} if the list was modified.
     */
    public static boolean pruneList(@NonNull final Collection<TocEntry> list,
                                    @NonNull final Context context,
                                    @NonNull final DAO db,
                                    final boolean lookupLocale,
                                    @NonNull final Locale bookLocale) {

        boolean listModified = false;

        // Keep track of hashCode -> TocEntry
        final Map<Integer, TocEntry> hashCodesMap = new HashMap<>();
        // We need to collect the 'previous' TocEntry to delete, so cannot use the iterator.remove
        final Collection<TocEntry> toDelete = new ArrayList<>();

        final Iterator<TocEntry> it = list.iterator();
        while (it.hasNext()) {
            final TocEntry tocEntry = it.next();

            final Locale locale;
            if (lookupLocale) {
                locale = tocEntry.getLocale(context, db, bookLocale);
            } else {
                locale = bookLocale;
            }
            // try to find and update the id. Don't lookup the locale a 2nd time.
            tocEntry.fixId(context, db, false, locale);

            final Integer hashCode = tocEntry.hashCode();

            if (!hashCodesMap.containsKey(hashCode)) {
                // Not there, so just add and continue
                hashCodesMap.put(hashCode, tocEntry);

            } else {
                final PartialDate firstPublicationDate = tocEntry.getFirstPublicationDate();

                // See if we can purge either one.
                if (firstPublicationDate.isEmpty()) {
                    // Always delete TocEntry with empty pubYear
                    // if an equal or more specific one exists
                    it.remove();
                    listModified = true;

                } else {
                    // See if the previous one also has a pubYear
                    final TocEntry previous = hashCodesMap.get(hashCode);
                    if (previous != null) {
                        if (previous.getFirstPublicationDate().isEmpty()) {
                            // It doesn't. Keep the current.
                            // Update our map (replacing the previous one)
                            hashCodesMap.put(hashCode, tocEntry);
                            // And remove the previous
                            toDelete.add(previous);
                            listModified = true;

                        } else {
                            // Both have numbers. See if they are the same.
                            if (firstPublicationDate.equals(previous.getFirstPublicationDate())) {
                                // Same exact TocEntry, delete this one, keep the previous one.
                                it.remove();
                                listModified = true;
                            }
                            // else: two entries with a different pubYear.
                            // This is almost certainly invalid, but we can't decide on the
                            // 'right' one. The user should clean up manually.
                        }
                    }
                }
            }
        }

        for (TocEntry tocEntry : toDelete) {
            list.remove(tocEntry);
        }

        return listModified;
    }

    /**
     * Helper to check if all titles in a list have the same author.
     *
     * @param list of entries
     *
     * @return {@code true} if there is more than 1 author in the TOC
     */
    public static boolean hasMultipleAuthors(@NonNull final List<TocEntry> list) {
        if (list.size() > 1) {
            // use the first one as the comparator.
            Author firstAuthor = list.get(0).getPrimaryAuthor();
            for (int i = 1, listSize = list.size(); i < listSize; i++) {
                if (!firstAuthor.equals(list.get(i).getPrimaryAuthor())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the number of books this entry appears in.
     *
     * @return #books
     */
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
        dest.writeParcelable(mFirstPublicationDate, flags);
        dest.writeInt(mBookCount);
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @Override
    public char getType() {
        return AuthorWork.TYPE_TOC;
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
    @Override
    public String getLabel(@NonNull final Context context) {
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
        // overkill...  see the getLocale method for more comments
        // try (DAO db = new DAO(TAG)) {
        //     locale = getLocale(context, db, AppLocale.getUserLocale(context));
        // }
        return reorderTitleForDisplaying(context, userLocale);
    }

    @NonNull
    public Author getPrimaryAuthor() {
        return mAuthor;
    }

    public void setPrimaryAuthor(@NonNull final Author author) {
        mAuthor = author;
    }


    /**
     * Lazy load the book id/title pair list this TOC entry is published in.
     *
     * @param db Database Access
     *
     * @return list of id/titles of books.
     */
    @NonNull
    public List<Pair<Long, String>> getBookTitles(@NonNull final DAO db) {
        if (mBookTitles == null) {
            mBookTitles = db.getBookTitlesForToc(mId);
        }
        return mBookTitles;
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return mFirstPublicationDate;
    }

    public void setFirstPublicationDate(@NonNull final CharSequence dateStr) {
        mFirstPublicationDate.setDate(dateStr);
    }

    public void setFirstPublicationDate(@NonNull final PartialDate firstPublicationDate) {
        mFirstPublicationDate = firstPublicationDate;
    }

    /**
     * Get the Locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param bookLocale Locale to use if the item does not have a Locale of its own.
     *
     * @return the item Locale, or the bookLocale.
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final DAO db,
                            @NonNull final Locale bookLocale) {
        //ENHANCE: The TocEntry Locale should be based on either a specific language
        // setting for the TocEntry itself, or on the Locale of the primary book.
        return bookLocale;
    }

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param context      Current context
     * @param db           Database Access
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Context context,
                      @NonNull final DAO db,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {

        mAuthor.fixId(context, db, lookupLocale, bookLocale);
        mId = db.getTocEntryId(context, this, lookupLocale, bookLocale);
        return mId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mAuthor, mTitle);
    }

    /**
     * Equality: <strong>id, Author(id) and Title</strong>.
     * <ul>
     *      <li>mFirstPublicationDate is excluded on purpose due to to many discrepancies
     *          depending on source.</li>
     * </ul>
     *
     * <strong>Compare is CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
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
               + ", mBookCount=`" + mBookCount + '`'
               + ", mBookTitles=" + mBookTitles
               + '}';
    }
}
