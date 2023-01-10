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
import android.os.Bundle;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoImpl;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;

/**
 * This class is a wrapper around {@link DataManager} facilitating {@link Book} specific keys.
 * <p>
 * In contrast to {@link Book}, this class <strong>is Parcelable</strong>
 * <p>
 * Note that aside of book data, it can also contain additional/internal process data.
 */
public class BookData
        extends DataManager
        implements Entity {

    public static final Creator<BookData> CREATOR = new Creator<>() {

        @Override
        @NonNull
        public BookData createFromParcel(@NonNull final Parcel in) {
            return new BookData(in);
        }

        @Override
        @NonNull
        public BookData[] newArray(final int size) {
            return new BookData[size];
        }
    };

    /**
     * Bundle key for {@code ParcelableArrayList<Author>}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_AUTHOR_LIST = "author_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Series>}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_SERIES_LIST = "series_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Publisher>}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_PUBLISHER_LIST = "publisher_list";
    /**
     * Bundle key for {@code ParcelableArrayList<TocEntry>}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_TOC_LIST = "toc_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Bookshelf>}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_BOOKSHELF_LIST = "bookshelf_list";

    /** Log tag. */
    private static final String TAG = "BookData";

    /**
     * Bundle key to pass book data around.
     * <p>
     * <br>type: {@link BookData}
     */
    public static final String BKEY_BOOK_DATA = TAG;

    public BookData() {
    }

    @VisibleForTesting
    public BookData(@NonNull final Bundle data) {
        super(data);
    }

    @VisibleForTesting
    public BookData(@NonNull final DataManager data) {
        super(data);
    }

    public BookData(@NonNull final Parcel in) {
        super(in);
    }

    /**
     * Check if this book has not been saved to the database yet.
     *
     * @return {@code true} if this is a new book
     */
    public boolean isNew() {
        return getId() == 0;
    }

    /**
     * get the id.
     *
     * @return the book id.
     */
    public long getId() {
        return getLong(DBKey.PK_ID);
    }

    /**
     * Get the <strong>unformatted</strong> title.
     *
     * @return the title
     */
    @NonNull
    public String getTitle() {
        return getString(DBKey.TITLE);
    }

    /**
     * Get the label to use for <strong>displaying</strong>.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getLabel(context, getTitle(), () -> getLocale(context));
    }


    /**
     * Get the first {@link Author} in the list of Authors for this book.
     *
     * @return the {@link Author} or {@code null} if none present
     */
    @Nullable
    public Author getPrimaryAuthor() {
        final List<Author> authors = getAuthors();
        return authors.isEmpty() ? null : authors.get(0);
    }

    /**
     * Get the list of {@link Author}s.
     *
     * @return new List
     */
    @NonNull
    public ArrayList<Author> getAuthors() {
        return getParcelableArrayList(BKEY_AUTHOR_LIST);
    }

    /**
     * Replace the list of {@link Author}s with the given list.
     *
     * @param authors list
     */
    public void setAuthors(@NonNull final List<Author> authors) {
        putParcelableArrayList(BKEY_AUTHOR_LIST, new ArrayList<>(authors));
    }

    /**
     * Add a single {@link Author}.
     *
     * @param author to add
     */
    public void add(@NonNull final Author author) {
        getAuthors().add(author);
    }

    /**
     * Update author details from DB.
     *
     * @param context Current context
     */
    public void refreshAuthors(@NonNull final Context context) {
        if (contains(BKEY_AUTHOR_LIST)) {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            final Locale bookLocale = getLocale(context);
            final ArrayList<Author> list = getAuthors();
            for (final Author author : list) {
                authorDao.refresh(context, author, true, bookLocale);
            }
        }
    }

    /**
     * Get the first {@link Series} in the list of Series for this book.
     *
     * @return Optional of the first {@link Series}
     */
    @NonNull
    public Optional<Series> getPrimarySeries() {
        final List<Series> list = getSeries();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Get the list of {@link Series}.
     *
     * @return new List
     */
    @NonNull
    public ArrayList<Series> getSeries() {
        return getParcelableArrayList(BKEY_SERIES_LIST);
    }

    /**
     * Replace the list of {@link Series}s with the given list.
     *
     * @param series list
     */
    public void setSeries(@NonNull final List<Series> series) {
        putParcelableArrayList(BKEY_SERIES_LIST, new ArrayList<>(series));
    }

    /**
     * Add a single {@link Series}.
     *
     * @param series to add
     */
    public void add(@NonNull final Series series) {
        getSeries().add(series);
    }

    /**
     * Add a single {@link Series} at the given position in the list.
     *
     * @param series to add
     */
    public void add(final int index,
                    @NonNull final Series series) {
        getSeries().add(index, series);
    }

    /**
     * Update Series details from DB.
     *
     * @param context Current context
     */
    public void refreshSeries(@NonNull final Context context) {
        if (contains(BKEY_SERIES_LIST)) {
            final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
            final Locale bookLocale = getLocale(context);
            final ArrayList<Series> list = getSeries();
            for (final Series series : list) {
                seriesDao.refresh(context, series, true, bookLocale);
            }
        }
    }

    /**
     * Get the first {@link Publisher} in the list of Publishers for this book.
     *
     * @return Optional of the first {@link Publisher}
     */
    @NonNull
    public Optional<Publisher> getPrimaryPublisher() {
        final List<Publisher> list = getPublishers();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Get the list of {@link Publisher}s.
     *
     * @return new List
     */
    @NonNull
    public ArrayList<Publisher> getPublishers() {
        return getParcelableArrayList(BKEY_PUBLISHER_LIST);
    }

    /**
     * Replace the list of {@link Publisher}s with the given list.
     *
     * @param publishers list
     */
    public void setPublishers(@NonNull final List<Publisher> publishers) {
        putParcelableArrayList(BKEY_PUBLISHER_LIST, new ArrayList<>(publishers));
    }

    /**
     * Add a single {@link Publisher}.
     *
     * @param publisher to add
     */
    public void add(@NonNull final Publisher publisher) {
        getPublishers().add(publisher);
    }

    /**
     * Update Publisher details from DB.
     *
     * @param context Current context
     */
    public void refreshPublishers(@NonNull final Context context) {
        if (contains(BKEY_PUBLISHER_LIST)) {
            final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
            final Locale bookLocale = getLocale(context);
            final ArrayList<Publisher> list = getPublishers();
            for (final Publisher publisher : list) {
                publisherDao.refresh(context, publisher, true, bookLocale);
            }
        }
    }

    /**
     * Get the list of {@link Bookshelf}s.
     *
     * @return new List
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        return getParcelableArrayList(BKEY_BOOKSHELF_LIST);
    }

    /**
     * Replace the list of {@link Bookshelf}s with the given list.
     *
     * @param bookShelves list
     */
    public void setBookshelves(@NonNull final List<Bookshelf> bookShelves) {
        putParcelableArrayList(BKEY_BOOKSHELF_LIST, new ArrayList<>(bookShelves));
    }

    /**
     * Add a single {@link Bookshelf}.
     *
     * @param bookshelf to add
     */
    public void add(@NonNull final Bookshelf bookshelf) {
        getBookshelves().add(bookshelf);
    }

    /**
     * Get the list of {@link TocEntry}s.
     *
     * @return new List
     */
    @NonNull
    public ArrayList<TocEntry> getToc() {
        return getParcelableArrayList(BKEY_TOC_LIST);
    }

    /**
     * Replace the list of {@link TocEntry}s with the given list.
     *
     * @param tocEntries list
     */
    public void setToc(@NonNull final List<TocEntry> tocEntries) {
        putParcelableArrayList(BKEY_TOC_LIST, new ArrayList<>(tocEntries));
    }

    @NonNull
    public ContentType getContentType() {
        return ContentType.getType(getLong(DBKey.TOC_TYPE__BITMASK));
    }

    public void setContentType(@NonNull final ContentType type) {
        putLong(DBKey.TOC_TYPE__BITMASK, type.getId());
    }

    /**
     * Ensure the book has a bookshelf.
     * If the book is not on any Bookshelf, add the preferred/current bookshelf
     *
     * @param context Current context
     */
    public void ensureBookshelf(@NonNull final Context context) {
        final ArrayList<Bookshelf> list = getParcelableArrayList(BKEY_BOOKSHELF_LIST);
        if (list.isEmpty()) {
            list.add(Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT));
        }
    }

    /**
     * Ensure the book has a language.
     * If the book does not, add the preferred/current language the user is using the app in.
     *
     * @param context Current context
     */
    public void ensureLanguage(@NonNull final Context context) {
        if (getString(DBKey.LANGUAGE).isEmpty()) {
            putString(DBKey.LANGUAGE,
                      // user locale
                      context.getResources().getConfiguration().getLocales().get(0)
                             .getISO3Language());
        }
    }

    /**
     * Duplicate this object by copying APPLICABLE (not simply all of them) fields.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     * <p>
     * <b>Dev. note:</b> keep in sync with {@link BookDaoImpl} .SqlAllBooks#BOOK
     *
     * @return bundle with book data
     */
    @NonNull
    public BookData duplicate() {
        final BookData bookData = new BookData();

        // Q: Why don't we get the DataManager#mRawData, remove the identifiers/dates and use that?
        // A: because we would need to clone mRawData before we can start removing fields,
        //  From Bundle#clone() docs: Clones the current Bundle.
        //  The internal map is cloned, but the keys and values to which it refers are
        //  copied by reference.
        // ==> by reference...  so we would in effect be removing fields from the original book.
        // This would be ok if we discard the original object (in memory only)
        // but lets play this safe.

        // Do not copy any identifiers.
        // PK_ID
        // BOOK_UUID
        // SID_LIBRARY_THING
        // SID_ISFDB
        // SID_GOODREADS
        // ...
        // Do not copy the Bookshelves list
        // ...
        // Do not copy these specific dates.
        // BOOK_DATE_ADDED
        // DATE_LAST_UPDATED

        bookData.putString(DBKey.TITLE, getTitle());
        bookData.putString(DBKey.BOOK_ISBN, getString(DBKey.BOOK_ISBN));

        if (bookData.contains(BKEY_AUTHOR_LIST)) {
            bookData.setAuthors(getAuthors());
        }
        if (bookData.contains(BKEY_SERIES_LIST)) {
            bookData.setSeries(getSeries());
        }
        if (bookData.contains(BKEY_PUBLISHER_LIST)) {
            bookData.setPublishers(getPublishers());
        }
        if (bookData.contains(BKEY_TOC_LIST)) {
            bookData.setToc(getToc());
        }

        // publication data
        bookData.putString(DBKey.PRINT_RUN, getString(DBKey.PRINT_RUN));
        bookData.putLong(DBKey.TOC_TYPE__BITMASK, getLong(DBKey.TOC_TYPE__BITMASK));
        bookData.putString(DBKey.BOOK_PUBLICATION__DATE, getString(DBKey.BOOK_PUBLICATION__DATE));
        bookData.putDouble(DBKey.PRICE_LISTED, getDouble(DBKey.PRICE_LISTED));
        bookData.putString(DBKey.PRICE_LISTED_CURRENCY, getString(DBKey.PRICE_LISTED_CURRENCY));
        bookData.putString(DBKey.FIRST_PUBLICATION__DATE, getString(DBKey.FIRST_PUBLICATION__DATE));

        bookData.putString(DBKey.FORMAT, getString(DBKey.FORMAT));
        bookData.putString(DBKey.COLOR, getString(DBKey.COLOR));
        bookData.putString(DBKey.GENRE, getString(DBKey.GENRE));
        bookData.putString(DBKey.LANGUAGE, getString(DBKey.LANGUAGE));
        bookData.putString(DBKey.PAGE_COUNT, getString(DBKey.PAGE_COUNT));
        // common blurb
        bookData.putString(DBKey.DESCRIPTION, getString(DBKey.DESCRIPTION));

        // partially edition info, partially use-owned info.
        bookData.putLong(DBKey.EDITION__BITMASK, getLong(DBKey.EDITION__BITMASK));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBKey.SIGNED__BOOL, getLong(DBKey.SIGNED__BOOL));

        bookData.putFloat(DBKey.RATING, getFloat(DBKey.RATING));
        bookData.putString(DBKey.PERSONAL_NOTES, getString(DBKey.PERSONAL_NOTES));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBKey.READ__BOOL, getLong(DBKey.READ__BOOL));
        bookData.putString(DBKey.READ_START__DATE, getString(DBKey.READ_START__DATE));
        bookData.putString(DBKey.READ_END__DATE, getString(DBKey.READ_END__DATE));

        bookData.putString(DBKey.DATE_ACQUIRED, getString(DBKey.DATE_ACQUIRED));
        bookData.putDouble(DBKey.PRICE_PAID, getDouble(DBKey.PRICE_PAID));
        bookData.putString(DBKey.PRICE_PAID_CURRENCY, getString(DBKey.PRICE_PAID_CURRENCY));

        bookData.putInt(DBKey.BOOK_CONDITION, getInt(DBKey.BOOK_CONDITION));
        bookData.putInt(DBKey.BOOK_CONDITION_COVER, getInt(DBKey.BOOK_CONDITION_COVER));

        return bookData;
    }

    /**
     * Convenience method.
     * <p>
     * Get the Book's Locale (based on its language).
     *
     * @param context Current context
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return getAndUpdateLocale(context, userLocale, false);
    }

    /**
     * Get the Book's Locale (based on its language).
     *
     * @param context Current context
     * @param unused  a Book will <strong>always</strong> use the user-locale as fallback.
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale unused) {
        return getLocale(context);
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param context        Current context
     * @param fallbackLocale Locale to use if the Book does not have a Locale of its own.
     * @param updateLanguage {@code true} to update the language field with the ISO code
     *                       if needed. {@code false} to leave it unchanged.
     *
     * @return the Locale.
     */
    @NonNull
    public Locale getAndUpdateLocale(@NonNull final Context context,
                                     @NonNull final Locale fallbackLocale,
                                     final boolean updateLanguage) {
        Locale bookLocale = null;
        if (contains(DBKey.LANGUAGE)) {
            final String lang = getString(DBKey.LANGUAGE);

            bookLocale = ServiceLocator.getInstance().getAppLocale().getLocale(context, lang);
            if (bookLocale == null) {
                return fallbackLocale;

            } else if (updateLanguage) {
                putString(DBKey.LANGUAGE, lang);
            }
        }

        // none, use fallback.
        return Objects.requireNonNullElse(bookLocale, fallbackLocale);
    }

    /**
     * Database representation of column {@link DBKey#TOC_TYPE__BITMASK}.
     */
    public enum ContentType
            implements Entity {
        /** Single work. One or more authors. */
        Book(0, R.string.lbl_book_type_book),
        /** Multiple works, all by a single Author. */
        Collection(1, R.string.lbl_book_type_collection),
        // value 2 not in use.
        /** Multiple works, multiple Authors. */
        Anthology(3, R.string.lbl_book_type_anthology);

        private final int value;
        @StringRes
        private final int labelResId;

        ContentType(final int value,
                    @StringRes final int labelResId) {
            this.value = value;
            this.labelResId = labelResId;
        }

        @NonNull
        public static ContentType getType(final long value) {
            switch ((int) value) {
                case 3:
                    return Anthology;
                case 1:
                    return Collection;
                case 0:
                default:
                    return Book;
            }
        }

        @NonNull
        public static List<ContentType> getAll() {
            return Arrays.asList(values());
        }

        @Override
        public long getId() {
            return value;
        }

        @NonNull
        @Override
        public String getLabel(@NonNull final Context context) {
            return context.getString(labelResId);
        }

    }

    /**
     * Database representation of column {@link DBKey#EDITION__BITMASK}.
     * <p>
     * 0b00000000 = a generic edition, or we simply don't know what edition it is.
     * 0b00000001 = first edition
     * 0b00000010 = first impression
     * 0b00000100 = limited edition
     * 0b00001000 = slipcase
     * 0b00010000 = signed
     * <p>
     * 0b10000000 = book club
     * <p>
     * NEWTHINGS: edition: add bit flag and add to mask
     * Never change the bit value!
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Edition {

        /** first edition ever of this work/content/story. */
        public static final int FIRST = 1;
        /** First printing of 'this' edition. */
        @VisibleForTesting
        public static final int FIRST_IMPRESSION = 1 << 1;
        /** This edition had a limited run. (Numbered or not). */
        @VisibleForTesting
        public static final int LIMITED = 1 << 2;
        /** This edition comes in a slipcase. */
        @VisibleForTesting
        public static final int SLIPCASE = 1 << 3;
        /** This edition is signed. i.e the whole print-run of this edition is signed. */
        @VisibleForTesting
        public static final int SIGNED = 1 << 4;
        /** It's a bookclub edition. */
        @VisibleForTesting
        public static final int BOOK_CLUB = 1 << 7;
        /** Bitmask for all editions. Bit 5/6 not in use for now. */
        public static final int BITMASK_ALL_BITS = FIRST
                                                   | FIRST_IMPRESSION
                                                   | LIMITED
                                                   | SLIPCASE
                                                   | SIGNED
                                                   | BOOK_CLUB;

        /** mapping the edition bit to a resource string for displaying. Ordered. */
        private static final Map<Integer, Integer> ALL = new LinkedHashMap<>();

        /*
         * NEWTHINGS: edition: add label for the type
         *
         * This is a LinkedHashMap, the order below is the order these will show up on the screen.
         */
        static {
            ALL.put(FIRST, R.string.lbl_edition_first_edition);
            ALL.put(FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
            ALL.put(LIMITED, R.string.lbl_edition_limited);
            ALL.put(SIGNED, R.string.lbl_edition_signed);
            ALL.put(SLIPCASE, R.string.lbl_edition_slipcase);

            ALL.put(BOOK_CLUB, R.string.lbl_edition_book_club);
        }

        private Edition() {
        }

        /**
         * Retrieve a <strong>copy</strong> of the ALL map.
         *
         * @return map
         */
        @NonNull
        public static Map<Integer, Integer> getAll() {
            return new LinkedHashMap<>(ALL);
        }
    }
}
