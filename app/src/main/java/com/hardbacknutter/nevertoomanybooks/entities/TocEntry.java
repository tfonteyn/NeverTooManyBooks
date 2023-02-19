/*
 * @Copyright 2018-2022 HardBackNutter
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
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Represent a single title within an TOC(Anthology).
 * <p>
 * Currently limited to having ONE author only.
 * <p>
 * <strong>Note:</strong>
 * these are always inserted/updated ONLY when a book is inserted/updated
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
        implements Parcelable, Entity, Mergeable, AuthorWork {

    /** {@link Parcelable}. */
    public static final Creator<TocEntry> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public TocEntry createFromParcel(@NonNull final Parcel source) {
            return new TocEntry(source);
        }

        @Override
        @NonNull
        public TocEntry[] newArray(final int size) {
            return new TocEntry[size];
        }
    };

    /** Row ID. */
    private long id;
    @NonNull
    private Author author;
    @NonNull
    private String title;
    @NonNull
    private PartialDate firstPublicationDate;

    /** in-memory use only. Number of books this TocEntry appears in. */
    private final int bookCount;

    /**
     * Constructor.
     *
     * @param author Author of title
     * @param title  Title
     */
    public TocEntry(@NonNull final Author author,
                    @NonNull final String title) {
        this(author, title, new PartialDate(null));
    }

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
        this(author, title, new PartialDate(firstPublicationDate));
    }

    /**
     * Constructor.
     *
     * @param author               Author of title
     * @param title                Title
     * @param firstPublicationDate year of first publication
     */
    public TocEntry(@NonNull final Author author,
                    @NonNull final String title,
                    @NonNull final PartialDate firstPublicationDate) {
        this.author = author;
        this.title = title.trim();
        this.firstPublicationDate = firstPublicationDate;
        bookCount = 1;
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
    @VisibleForTesting
    TocEntry(final long id,
             @NonNull final Author author,
             @NonNull final String title,
             @Nullable final String firstPublicationDate,
             final int bookCount) {
        this.id = id;
        this.author = author;
        this.title = title.trim();
        this.firstPublicationDate = new PartialDate(firstPublicationDate);
        this.bookCount = bookCount;
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the TocEntry in the database.
     * @param rowData with data
     */
    public TocEntry(final long id,
                    @NonNull final DataHolder rowData) {
        this(id, new Author(rowData.getLong(DBKey.FK_AUTHOR), rowData), rowData);
    }

    /**
     * Full constructor.
     *
     * @param id      ID of the TocEntry in the database.
     * @param author  Author of title
     * @param rowData with data
     */
    public TocEntry(final long id,
                    @NonNull final Author author,
                    @NonNull final DataHolder rowData) {
        this.id = id;
        this.author = author;
        this.title = rowData.getString(DBKey.TITLE);
        this.firstPublicationDate = new PartialDate(
                rowData.getString(DBKey.FIRST_PUBLICATION__DATE));
        this.bookCount = rowData.getInt(DBKey.BOOK_COUNT);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private TocEntry(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection ConstantConditions
        author = in.readParcelable(Author.class.getClassLoader());
        //noinspection ConstantConditions
        title = in.readString();
        //noinspection ConstantConditions
        firstPublicationDate = in.readParcelable(PartialDate.class.getClassLoader());

        bookCount = in.readInt();
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
            final Author firstAuthor = list.get(0).getPrimaryAuthor();
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
        return bookCount;
    }

    /**
     * Replace local details from another TocEntry.
     *
     * @param source TocEntry to copy from
     */
    public void copyFrom(@NonNull final TocEntry source) {
        // While editing, the user was only giving us a (potential) new name,
        // so don't use Author#copyFrom; and don't reference the entire author
        author.setName(source.author.getFamilyName(), source.author.getGivenNames());
        title = source.title;
        firstPublicationDate = source.firstPublicationDate;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeParcelable(author, flags);
        dest.writeString(title);
        dest.writeParcelable(firstPublicationDate, flags);
        dest.writeInt(bookCount);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(final long id) {
        this.id = id;
    }

    @Override
    @NonNull
    public Type getWorkType() {
        return AuthorWork.Type.TocEntry;
    }

    /**
     * Get the <strong>unformatted</strong> title.
     *
     * @return the title
     */
    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull final String title) {
        this.title = title;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @Nullable final Style style) {
        if (ReorderHelper.forDisplay(context)) {
            // Using the locale here is overkill;  see #getLocale(..)
            return ReorderHelper.reorder(context, title);
        } else {
            return title;
        }
    }

    @NonNull
    public Author getPrimaryAuthor() {
        return author;
    }

    public void setPrimaryAuthor(@NonNull final Author author) {
        this.author = author;
    }

    @Override
    @NonNull
    public List<BookLight> getBookTitles(@NonNull final Context context) {
        return ServiceLocator.getInstance().getTocEntryDao().getBookTitles(id, author);
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return firstPublicationDate;
    }

    public void setFirstPublicationDate(@NonNull final PartialDate firstPublicationDate) {
        this.firstPublicationDate = firstPublicationDate;
    }

    /**
     * Get the Locale of the actual item; e.g. a book written in Spanish should
     * return an Spanish Locale even if for example the user runs the app in German,
     * and the device in Danish.
     *
     * @param context    Current context
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the item Locale, or the bookLocale.
     */
    @Override
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale bookLocale) {
        //ENHANCE: The TocEntry Locale should be based on either a specific language
        // setting for the TocEntry itself, or on the Locale of the primary book.
        return bookLocale;
    }

    @NonNull
    @Override
    public List<String> getNameFields() {
        final List<String> all = new ArrayList<>();
        all.add(title);
        all.addAll(author.getNameFields());
        return all;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, author);
    }

    /**
     * Equality: <strong>id, Author(id) and Title</strong>.
     * <ul>
     *   <li>mFirstPublicationDate is excluded on purpose due to too many discrepancies
     *       depending on source.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
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
        final TocEntry that = (TocEntry) obj;
        // if both 'exist' but have different ID's -> different.
        if (id != 0 && that.id != 0 && id != that.id) {
            return false;
        }
        return Objects.equals(author, that.author)
               && Objects.equals(title, that.title);
    }

    @Override
    @NonNull
    public String toString() {
        return "TocEntry{"
               + "id=" + id
               + ", author=" + author
               + ", title=`" + title + '`'
               + ", firstPublicationDate=`" + firstPublicationDate + '`'
               + ", bookCount=`" + bookCount + '`'
               + '}';
    }
}
