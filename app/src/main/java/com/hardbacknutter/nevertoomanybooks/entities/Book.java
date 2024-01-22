/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoImpl;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.ValidatorConfig;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.BlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DoubleValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.LongValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.NonBlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.OrValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * Represents the underlying data for a book.
 * <p>
 * Note that aside of book data, it can also contain additional/internal process data.
 *
 * <p>
 * A note on the Locale of a Book, Series, Author, ...
 * Some of this is not implemented yet and may never be.
 * <p>
 * A Spanish book (written in Spanish) should return the Spanish Locale.
 * i.e. an original language book, should (obviously) return its original Locale.
 * A Spanish book (translated from English) should return an Spanish Locale.
 * i.e. a translated book, should return its translation Locale.
 * <p>
 * A Series should return the Locale as set by the user for that Series (not implemented yet).
 * If not set, then the Locale of the first book in the series.
 * Edge-case: books original in English, user has first book in Spanish, second book in English
 * -> the Series is wrongly designated as Spanish. Solution; user manually sets the Series Locale.
 * <p>
 * A Author should return the Locale as set by the user for that Author (not implemented yet),
 * This should normally be the primary language the author writes in.
 * i.e. usually the author's native language, but some authors will e.g. use english/french...
 * to reach a larger market without translation needs.
 * If not set, then the Locale of the first book (oldest copyright? oldest 'added'?) of that author.
 * <p>
 * A TocEntry...
 */
public class Book
        extends DataManager
        implements AuthorWork, Entity {

    /** {@link Parcelable}. */
    public static final Creator<Book> CREATOR = new Creator<>() {

        @Override
        @NonNull
        public Book createFromParcel(@NonNull final Parcel in) {
            return new Book(in);
        }

        @Override
        @NonNull
        public Book[] newArray(final int size) {
            return new Book[size];
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

    /**
     * Bundle key for {@code CalibreLibrary (Parcelable)}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_CALIBRE_LIBRARY = "calibre_library";

    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;

    /**
     * A book (and dustcover) condition goes from 1(worst)..5(best) or 0 for not-set.
     * In code we only need 5(best) which is used as default when adding a new book.
     * <p>
     * <string-array name="conditions_book">
     * <item>@string/unknown</item>
     * <item>@string/lbl_condition_reading_copy</item>
     * <item>@string/lbl_condition_fair</item>
     * <item>@string/lbl_condition_good</item>
     * <item>@string/lbl_condition_very_good</item>
     * <item>@string/lbl_condition_fine</item>
     * </string-array>
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    public static final int CONDITION_AS_NEW = 5;

    /** Log tag. */
    private static final String TAG = "Book";

    /**
     * Bundle key to pass book data around.
     * <p>
     * <br>type: {@link Book}
     */
    public static final String BKEY_BOOK_DATA = TAG;
    /**
     * Bundle key for an {@code ArrayList<Long>} of book ID's.
     *
     * @see ParcelUtils#wrap(List)
     * @see ParcelUtils#unwrap(Bundle, String)
     */
    public static final String BKEY_BOOK_ID_LIST = TAG + ":id_list";
    /**
     * Single front/back cover file specs for handling a temporary cover during edit.
     * <p>
     * <br>type: {@code String}
     */
    public static final String[] BKEY_TMP_FILE_SPEC = {
            TAG + ":fileSpec:0",
            TAG + ":fileSpec:1"};
    private static final String ERROR_INVALID_BOOK_ID = "bookId";

    /** the stage of the book entity. */
    private final EntityStage stage;

    /**
     * Validator and validator results.
     * <p>
     * Not parcelled and only created when editing a book.
     */
    @Nullable
    private ValidatorConfig validatorConfig;

    /**
     * Constructor.
     */
    public Book() {
        super(ServiceLocator.getInstance().newBundle());
        stage = new EntityStage();
    }

    /**
     * Constructor for tests. Loads the data <strong>without</strong> type checks.
     *
     * @param data data bundle to use for the Book
     */
    @VisibleForTesting
    public Book(@NonNull final Bundle data) {
        super(data);
        stage = new EntityStage();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    public Book(@NonNull final Parcel in) {
        super(in);
        stage = in.readParcelable(getClass().getClassLoader());
    }

    /**
     * Constructor. Load the book details from the database.
     *
     * @param bookId of book
     *
     * @return new instance; can be empty but never {@code null}.
     *
     * @throws IllegalArgumentException if the book id is not valid
     */
    @NonNull
    public static Book from(@IntRange(from = 1) final long bookId) {
        if (bookId <= 0) {
            throw new IllegalArgumentException(ERROR_INVALID_BOOK_ID);
        }

        final Book book = new Book();
        try (Cursor bookCursor = ServiceLocator.getInstance().getBookDao().fetchById(bookId)) {
            if (bookCursor.moveToFirst()) {
                book.load(bookId, bookCursor);
            }
        }
        return book;
    }

    /**
     * Constructor. Load the book details from the cursor.
     *
     * @param bookCursor an already positioned Cursor to read from
     *
     * @return new instance
     */
    @NonNull
    public static Book from(@NonNull final Cursor bookCursor) {
        final Book book = new Book();
        final int idCol = bookCursor.getColumnIndex(DBKey.PK_ID);
        final long bookId = bookCursor.getLong(idCol);
        book.load(bookId, bookCursor);
        return book;
    }

    /**
     * Copy Constructor. Loads the bundle <strong>with</strong> type checks.
     *
     * @param context Current context
     * @param data    book to copy all data from
     *
     * @return new instance; flagged as {@link EntityStage.Stage#Dirty}
     */
    @NonNull
    public static Book from(@NonNull final Context context,
                            @NonNull final Book data) {
        final Book book = new Book();
        book.putAll(context, data);
        // has unsaved data, hence 'Dirty'
        book.setStage(EntityStage.Stage.Dirty);
        return book;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(stage, flags);
    }

    /**
     * Load the book details from the database.
     * The current book data is cleared before loading.
     *
     * @param bookId     of book must be != 0
     * @param bookCursor an already positioned Cursor to read from
     *
     * @throws IllegalArgumentException if the book id is not valid
     */
    public void load(@IntRange(from = 1) final long bookId,
                     @NonNull final Cursor bookCursor) {
        if (bookId <= 0) {
            throw new IllegalArgumentException(ERROR_INVALID_BOOK_ID);
        }

        clearData();
        putAll(bookCursor);

        // load lists (or init with empty lists)
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        setBookshelves(serviceLocator.getBookshelfDao().getByBookId(bookId));
        setAuthors(serviceLocator.getAuthorDao().getByBookId(bookId));
        setSeries(serviceLocator.getSeriesDao().getByBookId(bookId));
        setPublishers(serviceLocator.getPublisherDao().getByBookId(bookId));
        setToc(serviceLocator.getTocEntryDao().getByBookId(bookId));

        // do NOT preload the full Calibre library object. We hardly ever need it as such.
        // see #getCalibreLibrary
    }

    /**
     * Duplicate a book by copying APPLICABLE (not simply all of them) fields.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     * See {@link #from(Context, Book)} for the latter.
     * <p>
     * <strong>Dev. note:</strong> keep the list of data we duplicate
     * in sync with {@link BookDaoImpl} .SqlAllBooks#BOOK
     *
     * @param context Current context
     *
     * @return new Book
     */
    @NonNull
    public Book duplicate(@NonNull final Context context) {
        final Book duplicate = new Book();
        final RealNumberParser realNumberParser = new RealNumberParser(
                LocaleListUtils.asList(context));

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
        //
        // //NEWTHINGS: new fields

        duplicate.putString(DBKey.TITLE, getTitle());
        duplicate.putString(DBKey.TITLE_ORIGINAL_LANG, getString(DBKey.TITLE_ORIGINAL_LANG));
        duplicate.putString(DBKey.BOOK_ISBN, getString(DBKey.BOOK_ISBN));

        if (duplicate.contains(BKEY_AUTHOR_LIST)) {
            duplicate.setAuthors(getAuthors());
        }
        if (duplicate.contains(BKEY_SERIES_LIST)) {
            duplicate.setSeries(getSeries());
        }
        if (duplicate.contains(BKEY_PUBLISHER_LIST)) {
            duplicate.setPublishers(getPublishers());
        }
        if (duplicate.contains(BKEY_TOC_LIST)) {
            duplicate.setToc(getToc());
        }

        // publication data
        duplicate.putString(DBKey.PRINT_RUN, getString(DBKey.PRINT_RUN));
        duplicate.putLong(DBKey.BOOK_CONTENT_TYPE, getLong(DBKey.BOOK_CONTENT_TYPE));
        duplicate.putString(DBKey.BOOK_PUBLICATION__DATE, getString(DBKey.BOOK_PUBLICATION__DATE));
        duplicate.putDouble(DBKey.PRICE_LISTED, getDouble(DBKey.PRICE_LISTED, realNumberParser));
        duplicate.putString(DBKey.PRICE_LISTED_CURRENCY, getString(DBKey.PRICE_LISTED_CURRENCY));
        duplicate.putString(DBKey.FIRST_PUBLICATION__DATE,
                            getString(DBKey.FIRST_PUBLICATION__DATE));

        duplicate.putString(DBKey.FORMAT, getString(DBKey.FORMAT));
        duplicate.putString(DBKey.COLOR, getString(DBKey.COLOR));
        duplicate.putString(DBKey.GENRE, getString(DBKey.GENRE));
        duplicate.putString(DBKey.LANGUAGE, getString(DBKey.LANGUAGE));
        duplicate.putString(DBKey.PAGE_COUNT, getString(DBKey.PAGE_COUNT));
        // common blurb
        duplicate.putString(DBKey.DESCRIPTION, getString(DBKey.DESCRIPTION));

        // partially edition info, partially use-owned info.
        duplicate.putLong(DBKey.EDITION__BITMASK, getLong(DBKey.EDITION__BITMASK));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        duplicate.putLong(DBKey.SIGNED__BOOL, getLong(DBKey.SIGNED__BOOL));

        duplicate.putFloat(DBKey.RATING, getFloat(DBKey.RATING, realNumberParser));
        duplicate.putString(DBKey.PERSONAL_NOTES, getString(DBKey.PERSONAL_NOTES));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        duplicate.putLong(DBKey.READ__BOOL, getLong(DBKey.READ__BOOL));
        duplicate.putString(DBKey.READ_START__DATE, getString(DBKey.READ_START__DATE));
        duplicate.putString(DBKey.READ_END__DATE, getString(DBKey.READ_END__DATE));

        duplicate.putString(DBKey.DATE_ACQUIRED, getString(DBKey.DATE_ACQUIRED));
        duplicate.putDouble(DBKey.PRICE_PAID, getDouble(DBKey.PRICE_PAID, realNumberParser));
        duplicate.putString(DBKey.PRICE_PAID_CURRENCY, getString(DBKey.PRICE_PAID_CURRENCY));

        duplicate.putInt(DBKey.BOOK_CONDITION, getInt(DBKey.BOOK_CONDITION));
        duplicate.putInt(DBKey.BOOK_CONDITION_COVER, getInt(DBKey.BOOK_CONDITION_COVER));

        return duplicate;
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
     * Get the id.
     *
     * @return the book id; will be {@code 0} if this book is 'new'
     */
    @Override
    public long getId() {
        return getLong(DBKey.PK_ID);
    }

    /**
     * Get the <strong>unformatted</strong> title.
     *
     * @return the title; can be empty but never {@code null}
     */
    @NonNull
    public String getTitle() {
        return getString(DBKey.TITLE);
    }

    @NonNull
    public List<BookLight> getBookTitles(@NonNull final Context context) {
        return Collections.singletonList(new BookLight(this));
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @NonNull final Style style) {

        if (style.isShowReorderedTitle()) {
            final ReorderHelper reorderHelper = ServiceLocator.getInstance().getReorderHelper();
            return reorderHelper.reorder(context, getTitle(), getLocaleOrUserLocale(context));
        } else {
            return getTitle();
        }
    }

    /**
     * Set or remove the publication-date for this book.
     *
     * @param date to set; {@code null} to remove
     */
    public void setPublicationDate(@Nullable final LocalDateTime date) {
        if (date != null) {
            putString(DBKey.BOOK_PUBLICATION__DATE,
                      date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            remove(DBKey.BOOK_PUBLICATION__DATE);
        }
    }

    /**
     * Set or remove the publication-date for this book.
     *
     * @param year to set; {@code 0} to remove
     */
    public void setPublicationDate(@IntRange(from = 0) final int year) {
        if (year > 0) {
            putString(DBKey.BOOK_PUBLICATION__DATE, String.valueOf(year));
        } else {
            remove(DBKey.BOOK_PUBLICATION__DATE);
        }
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return new PartialDate(getString(DBKey.FIRST_PUBLICATION__DATE));
    }

    /**
     * Set or remove the first-publication-date for this work.
     *
     * @param date to set; a {@code null} or a 'not-present' date will remove the field
     */
    public void setFirstPublicationDate(@Nullable final PartialDate date) {
        if (date != null && date.isPresent()) {
            putString(DBKey.FIRST_PUBLICATION__DATE, date.getIsoString());
        } else {
            remove(DBKey.FIRST_PUBLICATION__DATE);
        }
    }

    /**
     * Check if this Book contains a non-blank ISBN string. Does not check if the ISBN is valid.
     *
     * @return {@code true} if present
     */
    public boolean hasIsbn() {
        final String isbnStr = getString(DBKey.BOOK_ISBN, null);
        return isbnStr != null && !isbnStr.isEmpty();
    }

    /**
     * Get the Book's Locale (based on its language).
     *
     * @param context Current context
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Optional<Locale> getLocale(@NonNull final Context context) {
        final Optional<Locale> updatedLocale = getAndUpdateLocale(context, false);
        if (updatedLocale.isPresent()) {
            return updatedLocale;
        } else {
            return Optional.of(context.getResources().getConfiguration().getLocales().get(0));
        }
    }

    /**
     * Convenience method which return the locale directly.
     *
     * @param context Current context
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Locale getLocaleOrUserLocale(@NonNull final Context context) {
        return getAndUpdateLocale(context, false)
                .orElseGet(() -> context.getResources().getConfiguration().getLocales().get(0));
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param context        Current context
     * @param updateLanguage {@code true} to update the language field with the ISO code
     *                       if needed. {@code false} to leave it unchanged.
     *
     * @return the Locale.
     */
    @NonNull
    public Optional<Locale> getAndUpdateLocale(@NonNull final Context context,
                                               final boolean updateLanguage) {
        if (contains(DBKey.LANGUAGE)) {
            final String lang = getString(DBKey.LANGUAGE);

            final Optional<Locale> bookLocale = ServiceLocator.getInstance().getAppLocale()
                                                              .getLocale(context, lang);
            if (bookLocale.isPresent()) {
                if (updateLanguage) {
                    putString(DBKey.LANGUAGE, lang);
                }
                return bookLocale;
            }
        }
        return Optional.empty();
    }


    @Override
    @NonNull
    public AuthorWork.Type getWorkType() {
        return AuthorWork.Type.Book;
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
     * @return List
     */
    @NonNull
    public List<Author> getAuthors() {
        return getParcelableArrayList(BKEY_AUTHOR_LIST);
    }

    /**
     * Replace the list of {@link Author}s with the given list.
     *
     * @param authors list
     */
    public void setAuthors(@NonNull final List<Author> authors) {
        putParcelableArrayList(BKEY_AUTHOR_LIST, authors);
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
     * Update all {@link Bookshelf} details from DB.
     *
     * @param context Current context
     */
    public void refreshBookshelves(@NonNull final Context context) {
        if (contains(BKEY_BOOKSHELF_LIST)) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            getBookshelves().forEach(bookshelf -> bookshelfDao
                    .refresh(context, bookshelf, locale));
        }
    }

    /**
     * Update all {@link Author} details from DB.
     *
     * @param context Current context
     */
    public void refreshAuthors(@NonNull final Context context) {
        if (contains(BKEY_AUTHOR_LIST)) {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            final Locale bookLocale = getLocaleOrUserLocale(context);
            getAuthors().forEach(author -> authorDao
                    .refresh(context, author, bookLocale));
        }
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context Current context
     */
    public void pruneAuthors(@NonNull final Context context) {
        final List<Author> authors = getAuthors();
        if (!authors.isEmpty()) {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

            if (authorDao.pruneList(context, authors, author -> locale)) {
                stage.setStage(EntityStage.Stage.Dirty);
            }
        }

        // No authors ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        if (authors.isEmpty()) {
            final String searchText = getString(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
            if (!searchText.isEmpty()) {
                authors.add(Author.from(searchText));
                remove(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
                stage.setStage(EntityStage.Stage.Dirty);
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
     * @return List
     */
    @NonNull
    public List<Series> getSeries() {
        return getParcelableArrayList(BKEY_SERIES_LIST);
    }

    /**
     * Replace the list of {@link Series}s with the given list.
     *
     * @param series list
     */
    public void setSeries(@NonNull final List<Series> series) {
        putParcelableArrayList(BKEY_SERIES_LIST, series);
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
     * @param index  index at which the specified element is to be inserted
     * @param series to add
     */
    public void add(final int index,
                    @NonNull final Series series) {
        getSeries().add(index, series);
    }

    /**
     * Update all {@link Series} details from DB.
     *
     * @param context Current context
     */
    public void refreshSeries(@NonNull final Context context) {
        if (contains(BKEY_SERIES_LIST)) {
            final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
            final Locale bookLocale = getLocaleOrUserLocale(context);
            getSeries().forEach(series -> seriesDao
                    .refresh(context, series, series.getLocale(context).orElse(bookLocale)));
        }
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context Current context
     */
    public void pruneSeries(@NonNull final Context context) {
        if (contains(BKEY_SERIES_LIST)) {
            final List<Series> seriesList = getSeries();
            if (!seriesList.isEmpty()) {
                final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();

                if (seriesDao.pruneList(context, seriesList,
                                        series -> series.getLocale(context).orElseGet(
                                                () -> getLocaleOrUserLocale(context)))) {
                    stage.setStage(EntityStage.Stage.Dirty);
                }
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
     * @return List
     */
    @NonNull
    public List<Publisher> getPublishers() {
        return getParcelableArrayList(BKEY_PUBLISHER_LIST);
    }

    /**
     * Replace the list of {@link Publisher}s with the given list.
     *
     * @param publishers list
     */
    public void setPublishers(@NonNull final List<Publisher> publishers) {
        putParcelableArrayList(BKEY_PUBLISHER_LIST, publishers);
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
     * Update all {@link Publisher} details from DB.
     *
     * @param context Current context
     */
    public void refreshPublishers(@NonNull final Context context) {
        if (contains(BKEY_PUBLISHER_LIST)) {
            final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
            final Locale bookLocale = getLocaleOrUserLocale(context);
            getPublishers().forEach(publisher -> publisherDao
                    .refresh(context, publisher, bookLocale));
        }
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context Current context
     */
    public void prunePublishers(@NonNull final Context context) {
        if (contains(BKEY_PUBLISHER_LIST)) {
            final List<Publisher> publishers = getPublishers();
            if (!publishers.isEmpty()) {
                final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
                final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

                if (publisherDao.pruneList(context, publishers, publisher -> locale)) {
                    stage.setStage(EntityStage.Stage.Dirty);
                }
            }
        }
    }

    /**
     * Get the list of {@link Bookshelf}s.
     *
     * @return List
     */
    @NonNull
    public List<Bookshelf> getBookshelves() {
        return getParcelableArrayList(BKEY_BOOKSHELF_LIST);
    }

    /**
     * Replace the list of {@link Bookshelf}s with the given list.
     *
     * @param bookShelves list
     */
    public void setBookshelves(@NonNull final List<Bookshelf> bookShelves) {
        putParcelableArrayList(BKEY_BOOKSHELF_LIST, bookShelves);
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
     * @return List
     */
    @NonNull
    public List<TocEntry> getToc() {
        return getParcelableArrayList(BKEY_TOC_LIST);
    }

    /**
     * Replace the list of {@link TocEntry}s with the given list.
     *
     * @param tocEntries list
     */
    public void setToc(@NonNull final List<TocEntry> tocEntries) {
        putParcelableArrayList(BKEY_TOC_LIST, tocEntries);
    }

    /**
     * Get the type of content of this book; i.e. Book/Collection/Anthology.
     *
     * @return type
     */
    @NonNull
    public ContentType getContentType() {
        return ContentType.getType(getInt(DBKey.BOOK_CONTENT_TYPE));
    }

    /**
     * Set the type of content of this book; i.e. Book/Collection/Anthology.
     *
     * @param type to set
     */
    public void setContentType(@NonNull final ContentType type) {
        putLong(DBKey.BOOK_CONTENT_TYPE, type.getId());
    }

    /**
     * Get the {@link CalibreLibrary}.
     *
     * @return library
     */
    @NonNull
    public Optional<CalibreLibrary> getCalibreLibrary() {
        // We MIGHT have it (probably not) ...
        if (contains(BKEY_CALIBRE_LIBRARY)) {
            final CalibreLibrary library = getParcelable(BKEY_CALIBRE_LIBRARY);
            if (library == null) {
                return Optional.empty();
            } else {
                return Optional.of(library);
            }
        } else {
            // but if not, go explicitly fetch it.
            final Optional<CalibreLibrary> library = ServiceLocator
                    .getInstance().getCalibreLibraryDao()
                    .findById(getLong(DBKey.FK_CALIBRE_LIBRARY));
            // store for reuse
            library.ifPresent(
                    calibreLibrary -> putParcelable(BKEY_CALIBRE_LIBRARY, calibreLibrary));
            return library;
        }
    }

    /**
     * Set the {@link CalibreLibrary}.
     *
     * @param library to set
     */
    public void setCalibreLibrary(@Nullable final CalibreLibrary library) {
        if (library != null) {
            putLong(DBKey.FK_CALIBRE_LIBRARY, library.getId());
            putParcelable(BKEY_CALIBRE_LIBRARY, library);
        } else {
            remove(DBKey.FK_CALIBRE_LIBRARY);
            remove(BKEY_CALIBRE_LIBRARY);

            remove(DBKey.CALIBRE_BOOK_ID);
            remove(DBKey.CALIBRE_BOOK_UUID);
            remove(DBKey.CALIBRE_BOOK_MAIN_FORMAT);
        }
    }

    /**
     * Ensure the book has a bookshelf.
     * If the book is not on any Bookshelf, add the current/default bookshelf
     *
     * @param context Current context
     */
    public void ensureBookshelf(@NonNull final Context context) {
        final List<Bookshelf> list = getParcelableArrayList(BKEY_BOOKSHELF_LIST);
        if (list.isEmpty()) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            Bookshelf bookshelf = bookshelfDao.getBookshelf(context,
                                                            Bookshelf.USER_DEFAULT,
                                                            Bookshelf.HARD_DEFAULT)
                                              .orElseThrow();
            if (bookshelf.getId() == Bookshelf.ALL_BOOKS) {
                // the user was "on" the "All Books" virtual shelf.
                // For lack of anything better, set the default shelf instead.
                bookshelf = bookshelfDao.getBookshelf(context, Bookshelf.HARD_DEFAULT)
                                        .orElseThrow();
            }
            list.add(bookshelf);
        }
    }

    /**
     * Ensure the book has a language.
     * If the book does not:
     * <ol>
     *     <li>use the language of the last book the user added/updated</li>
     *     <li>lacking that (i.e. at first use) the language the user is using the app in</li>
     * </ol>
     *
     * @param context Current context
     */
    public void ensureLanguage(@NonNull final Context context) {
        if (getString(DBKey.LANGUAGE).isEmpty()) {
            final List<String> previouslyUsed = ServiceLocator.getInstance()
                                                              .getLanguageDao().getList();
            if (previouslyUsed.isEmpty()) {
                putString(DBKey.LANGUAGE,
                          // user locale
                          context.getResources().getConfiguration().getLocales().get(0)
                                 .getISO3Language());
            } else {
                putString(DBKey.LANGUAGE, previouslyUsed.get(0));
            }
        }
    }

    /**
     * Get the name of the loanee (if any).
     *
     * @return {@link Optional} with a non-blank loanee name
     */
    @NonNull
    public Optional<String> getLoanee() {
        final String loanee;
        // We SHOULD have it...
        if (contains(DBKey.LOANEE_NAME)) {
            loanee = getString(DBKey.LOANEE_NAME);

        } else {
            // but if not, go explicitly fetch it.
            loanee = ServiceLocator.getInstance().getLoaneeDao().findLoaneeByBookId(getId());
            if (loanee != null) {
                // store for reuse - note we store "" as well, to prevent calling the db repeatedly
                putString(DBKey.LOANEE_NAME, loanee);
            }
        }

        if (loanee == null || loanee.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(loanee);
        }
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead() {
        return setRead(!getBoolean(DBKey.READ__BOOL));
    }

    /**
     * Update the 'read' status of a book in the database + sets the 'read end' to today.
     * The book will have its 'read' status updated ONLY if the update went through.
     *
     * @param isRead Flag for the 'read' status
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    private boolean setRead(final boolean isRead) {
        final boolean old = getBoolean(DBKey.READ__BOOL);

        if (ServiceLocator.getInstance().getBookDao().setRead(this, isRead)) {
            return isRead;
        }

        return old;
    }

    /**
     * Get the <strong>current</strong> cover file for this book.
     * <p>
     * Depending on the {@link #stage} this method gets a temporary cover,
     * or the persisted cover.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @param cIdx 0..n image index
     *
     * @return file
     */
    @NonNull
    public Optional<File> getCover(@IntRange(from = 0, to = 1) final int cIdx) {
        if (contains(BKEY_TMP_FILE_SPEC[cIdx])) {
            // we have a previously set temporary cover, but it could be ""
            final String fileSpec = getString(BKEY_TMP_FILE_SPEC[cIdx]);
            @Nullable
            File coverFile = null;
            if (!fileSpec.isEmpty()) {
                coverFile = new File(fileSpec);
                if (!coverFile.exists()) {
                    coverFile = null;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                LoggerFactory.getLogger()
                             .e(TAG, new Throwable("getCoverFile"),
                                "bookId=" + getId()
                                + "|cIdx=" + cIdx
                                + "|file="
                                + (coverFile == null ? "null" : coverFile.getAbsolutePath())
                             );
            }
            if (coverFile != null && coverFile.exists()) {
                return Optional.of(coverFile);
            }
        } else {
            // Get the permanent, UUID based, cover file for this book.
            final String uuid = getString(DBKey.BOOK_UUID, null);
            if (uuid != null && !uuid.isEmpty()) {
                return ServiceLocator.getInstance().getCoverStorage().getPersistedFile(uuid, cIdx);
            }
        }
        return Optional.empty();
    }

    /**
     * Syntax sugar for {@link #setCover(int, File)} with a {@code null} file.
     *
     * @param cIdx 0..n image index
     */
    public void removeCover(@IntRange(from = 0, to = 1) final int cIdx) {
        try {
            setCover(cIdx, null);
        } catch (@NonNull final IOException | StorageException ignore) {
            // safe to ignore, can't happen with a 'null' input.
        }
    }

    /**
     * Update the book cover with the given file.
     * <p>
     * Depending on the {@link #stage} this method sets a temporary cover,
     * or persists the cover to storage.
     *
     * @param cIdx 0..n image index
     * @param file cover file or {@code null} to delete the cover
     *
     * @return the File after processing (either original, or a renamed/moved file)
     *
     * @throws StorageException      The covers directory is not available
     * @throws IOException           on generic/other IO failures
     * @throws IllegalStateException if the UUID is missing
     */
    @SuppressWarnings({"UnusedReturnValue", "OverlyBroadThrowsClause"})
    @Nullable
    public File setCover(@IntRange(from = 0, to = 1) final int cIdx,
                         @Nullable final File file)
            throws StorageException, IOException {

        if (stage.getStage() == EntityStage.Stage.WriteAble
            || stage.getStage() == EntityStage.Stage.Dirty) {
            // We're editing, use BKEY_TMP_FILE_SPEC storage.

            if (file != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    LoggerFactory.getLogger()
                                 .e(TAG, new Throwable("setCover"),
                                    "editing"
                                    + "|bookId=" + getId()
                                    + "|cIdx=" + cIdx
                                    + "|file=" + file.getAbsolutePath()
                                 );
                }
                // BookDaoHelper#persistCovers will do the actual work
                // at the time of insert/update -ing the book
                putString(BKEY_TMP_FILE_SPEC[cIdx], file.getAbsolutePath());

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    LoggerFactory.getLogger()
                                 .e(TAG, new Throwable("setCover"),
                                    "editing"
                                    + "|bookId=" + getId()
                                    + "|cIdx=" + cIdx
                                    + "|deleting"
                                 );
                }
                // explicitly set to "" to let BookDaoHelper#persistCovers do a delete
                // at the time of insert/update -ing the book
                putString(BKEY_TMP_FILE_SPEC[cIdx], "");
            }

            // switch from WriteAble to Dirty (or from Dirty to Dirty)
            stage.setStage(EntityStage.Stage.Dirty);

            // just return the incoming file, it has not been changed or renamed
            return file;

        } else {
            // we're in read-only mode, use the UUID storage based file name
            final String uuid = getString(DBKey.BOOK_UUID, null);
            if (uuid == null || uuid.isEmpty()) {
                throw new IllegalStateException("Missing uuid");
            }

            // the file to return from this method, after the incoming file has been processed
            @Nullable
            File destination = file;

            // See BookDaoHelper#persistCovers which does the same as below for BKEY_TMP_FILE_SPEC
            if (file != null) {
                if (file.getName().startsWith(uuid)) {
                    // No further action needed as we have the cover "in-place"
                    // ... not actually sure when this would be the case; keep an eye on logs
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        LoggerFactory.getLogger()
                                     .e(TAG, new Throwable("setCover"),
                                        "readOnly"
                                        + "|bookId=" + getId()
                                        + "|cIdx=" + cIdx
                                        + "|uuid, in-place"
                                     );
                    }
                } else {
                    // Rename the temp file to the uuid permanent file name
                    destination = ServiceLocator.getInstance().getCoverStorage()
                                                .persist(file, uuid, cIdx);
                }
            } else {
                // a null file indicates we need to delete the cover
                ServiceLocator.getInstance().getCoverStorage().delete(uuid, cIdx);
            }

            ServiceLocator.getInstance().getBookDao().touch(this);

            return destination;
        }
    }

    /**
     * Get the last date-time that this book was modified.
     *
     * @param dateParser to use
     *
     * @return the last date-time; or {@code Optional.empty()} if never saved.
     */
    @NonNull
    public Optional<LocalDateTime> getLastModified(@NonNull final DateParser dateParser) {
        return getLocalDateTime(DBKey.DATE_LAST_UPDATED__UTC, dateParser);
    }

    /**
     * Set the last date-time that this book was modified.
     * If not set, a default of 'now' will be used when saved.
     *
     * @param dateTime to use
     */
    public void setLastModified(@NonNull final LocalDateTime dateTime) {
        putLocalDateTime(DBKey.DATE_LAST_UPDATED__UTC, dateTime);
    }

    /**
     * Add validators.
     *
     * @param context Current context
     */
    public void addValidators(@NonNull final Context context) {

        validatorConfig = new ValidatorConfig();

        final RealNumberParser realNumberParser =
                new RealNumberParser(LocaleListUtils.asList(context));

        final DataValidator priceValidator = new OrValidator(
                new BlankValidator(),
                new DoubleValidator(realNumberParser));
        final DataValidator longValidator = new LongValidator(realNumberParser);
        final DataValidator nonBlankValidator = new NonBlankValidator(realNumberParser);

        validatorConfig.addValidator(DBKey.TITLE,
                                     nonBlankValidator, R.string.lbl_title);
        validatorConfig.addValidator(BKEY_AUTHOR_LIST,
                                     nonBlankValidator, R.string.lbl_author);

        validatorConfig.addValidator(DBKey.LANGUAGE,
                                     nonBlankValidator, R.string.lbl_language);

        validatorConfig.addValidator(DBKey.EDITION__BITMASK,
                                     longValidator, R.string.lbl_edition);
        validatorConfig.addValidator(DBKey.BOOK_CONTENT_TYPE,
                                     longValidator, R.string.lbl_table_of_content);

        validatorConfig.addValidator(DBKey.PRICE_LISTED,
                                     priceValidator, R.string.lbl_price_listed);
        validatorConfig.addValidator(DBKey.PRICE_PAID,
                                     priceValidator, R.string.lbl_price_paid);

        validatorConfig.addCrossValidator((c, book) -> {
            final String start = book.getString(DBKey.READ_START__DATE);
            if (start.isEmpty()) {
                return;
            }
            final String end = book.getString(DBKey.READ_END__DATE);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(c.getString(R.string.vldt_read_start_after_end));
            }
        });
    }

    /**
     * Run all validators.
     * <p>
     * If this method returns {@code false}
     * call {@link #getValidationExceptionMessage} for the failure message.
     *
     * @param context Current context
     *
     * @return {@code true} if all validations passed
     */
    public boolean validate(@NonNull final Context context) {
        //noinspection DataFlowIssue
        return validatorConfig.validate(context, this);
    }

    /**
     * Retrieve the text message associated with the {@link #validate} exceptions (if any).
     *
     * @param context Current context
     *
     * @return a user displayable list of error messages, or {@code null} if none present
     */
    @Nullable
    public String getValidationExceptionMessage(@NonNull final Context context) {
        //noinspection DataFlowIssue
        return validatorConfig.getValidationExceptionMessage(context);
    }

    /**
     * Get the modification stage of this book.
     *
     * @return the stage
     */
    @NonNull
    public EntityStage.Stage getStage() {
        return stage.getStage();
    }

    /**
     * Set the modification stage of this book.
     *
     * @param stage to set
     */
    public void setStage(@NonNull final EntityStage.Stage stage) {
        this.stage.setStage(stage);
    }

    /** Used exclusively during display / populating the Views when loading the book. */
    public void lockStage() {
        stage.lock();
    }

    /** Used exclusively during display / populating the Views when loading the book. */
    public void unlockStage() {
        stage.unlock();
    }

    /**
     * Creates a chooser with matched apps for sharing some text.
     * <b>"I'm reading " + title + series + " by " + author + ratingString</b>
     *
     * @param context Current context
     * @param style   to apply
     *
     * @return the intent
     */
    @NonNull
    public Intent getShareIntent(@NonNull final Context context,
                                 @NonNull final Style style) {
        final String title = getTitle();

        final Author author = getPrimaryAuthor();
        final String authorStr = author != null
                                 ? author.getLabel(context, Details.AutoSelect, style)
                                 : context.getString(R.string.unknown_author);

        final String seriesStr = getPrimarySeries()
                .map(value -> context.getString(R.string.brackets,
                                                value.getLabel(context, Details.AutoSelect, style)))
                .orElse("");

        final RealNumberParser realNumberParser =
                new RealNumberParser(LocaleListUtils.asList(context));

        //remove trailing 0's
        final float rating = getFloat(DBKey.RATING, realNumberParser);
        String ratingStr;
        if (rating > 0) {
            // force rounding down and check the fraction
            final int ratingTmp = (int) rating;
            ratingStr = String.valueOf(rating - ratingTmp > 0 ? rating : ratingTmp);
            ratingStr = context.getString(R.string.brackets, ratingStr + '/' + RATING_STARS);

        } else {
            ratingStr = "";
        }

        final String text = context.getString(R.string.info_share_book_im_reading,
                                              title, seriesStr, authorStr, ratingStr);

        final Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);

        getCover(0).ifPresent(file -> intent
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM,
                          GenericFileProvider.createUri(context, file, title)));


        return Intent.createChooser(intent, context.getString(R.string.whichSendApplication));
    }

    /**
     * Database representation of column {@link DBKey#BOOK_CONTENT_TYPE}.
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
        public static ContentType getType(final int value) {
            switch (value) {
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
        public String getLabel(@NonNull final Context context,
                               @Nullable final Details details,
                               @NonNull final Style style) {
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

        /** generic/unknown edition. */
        public static final int UNKNOWN = 0;

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
