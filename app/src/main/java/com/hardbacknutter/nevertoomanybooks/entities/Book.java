/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BaseDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * Represents the underlying data for a book.
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
        implements ItemWithTitle, AuthorWork {

    /**
     * {@link DBDefinitions#KEY_TOC_BITMASK}
     * <p>
     * 0b001 = indicates if a book has one (bit unset) or multiple (bit set) works
     * 0b010 = indicates if a book has one (bit unset) or multiple (bit set) authors.
     * <p>
     * or in other words:
     * 0b000 = contains one 'work' and is written by a single author.
     * 0b001 = multiple 'work' and is written by a single author (anthology from ONE author)
     * 0b010 = multiple authors cooperating on a single 'work'
     * 0b011 = multiple authors and multiple 'work's (it's an anthology from multiple author)
     * <p>
     * Bit 0b010 should not actually occur, as this is a simple case of
     * collaborating authors on a single 'work' which is covered without the use of this field.
     */
    public static final int TOC_SINGLE_AUTHOR_SINGLE_WORK = 0;
    public static final int TOC_MULTIPLE_WORKS = 1;
    public static final int TOC_MULTIPLE_AUTHORS = 1 << 1;
    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;
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

    /** Log tag. */
    private static final String TAG = "Book";
    /**
     * Single front/back cover file specs.
     * <p>
     * <br>type: {@code String}
     */
    public static final String[] BKEY_TMP_FILE_SPEC = new String[]{
            TAG + ":fileSpec:0",
            TAG + ":fileSpec:1"};
    /**
     * Bundle key for an {@code ArrayList<Long>} of book ID's.
     * <p>
     * <br>type: {@code Serializable}
     */
    public static final String BKEY_BOOK_ID_LIST = TAG + ":id_list";
    /**
     * Bundle key to pass a Bundle with book data around.
     * i.e. before the data becomes an actual {@link Book}.
     * <p>
     * <br>type: {@code Bundle}
     */
    public static final String BKEY_DATA_BUNDLE = TAG + ":plainBundle";
    /** Used to transform Java-ISO to SQL-ISO datetime format. */
    private static final Pattern T = Pattern.compile("T");
    /** the stage of the book entity. */
    private final EntityStage mStage = new EntityStage();

    /**
     * Constructor.
     */
    public Book() {
    }

    /**
     * Constructor for Mock tests. Loads the bundle <strong>without</strong> type checks.
     * As this is for testing, the stage will not be set.
     *
     * @param rawData raw data bundle to use for the Book
     */
    @VisibleForTesting
    public Book(@NonNull final Bundle rawData) {
        super(rawData);
    }

    /**
     * Constructor. Load the book details from the database.
     *
     * @param bookId  of book
     * @param bookDao Database Access
     *
     * @return new instance
     */
    public static Book from(@IntRange(from = 1) final long bookId,
                            @NonNull final BookDao bookDao) {
        final Book book = new Book();
        book.load(bookId, bookDao);
        return book;
    }

    /**
     * Constructor. Load the book details from the cursor.
     *
     * @param bookCursor an already positioned Cursor to read from
     * @param bookDao         to load linked array data from
     *
     * @return new instance
     */
    public static Book from(@NonNull final Cursor bookCursor,
                            @NonNull final BookDao bookDao) {
        final Book book = new Book();
        final int idCol = bookCursor.getColumnIndex(DBDefinitions.KEY_PK_ID);
        final long bookId = bookCursor.getLong(idCol);
        book.load(bookId, bookCursor, bookDao);
        return book;
    }

    /**
     * Constructor. Loads the bundle <strong>with</strong> type checks.
     *
     * @param bookData data bundle to use for the Book
     *
     * @return new instance
     */
    public static Book from(@NonNull final Bundle bookData) {
        final Book book = new Book();
        book.putAll(bookData);
        return book;
    }

    /**
     * return the cover for the given uuid. We'll attempt to find a jpg or a png.
     *
     * @param context Current context
     * @param uuid    UUID of the book
     * @param cIdx    0..n image index
     *
     * @return The File object for existing files, or {@code null}
     *
     * @see #getUuidCoverFileOrNew(Context, int)
     */
    @Nullable
    public static File getUuidCoverFile(@NonNull final Context context,
                                        @NonNull final String uuid,
                                        @IntRange(from = 0, to = 1) final int cIdx) {
        final File coverDir;
        try {
            coverDir = AppDir.Covers.get(context);
        } catch (@NonNull final ExternalStorageException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getUuidCoverFile", e);
            }
            return null;
        }

        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid + "";
        }

        final File jpg = new File(coverDir, name + ".jpg");
        if (jpg.exists()) {
            return jpg;
        }
        // could be a png
        final File png = new File(coverDir, name + ".png");
        if (png.exists()) {
            return png;
        }

        return null;
    }

    /**
     * Load the book details from the database.
     *
     * @param bookId of book must be != 0
     * @param bookDao     Database Access
     */
    public void load(@IntRange(from = 1) final long bookId,
                     @NonNull final BookDao bookDao) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

        try (Cursor bookCursor = bookDao.fetchBookById(bookId)) {
            if (bookCursor.moveToFirst()) {
                load(bookId, bookCursor, bookDao);
            }
        }
    }

    /**
     * Load the book details from the database.
     * The current book data is cleared before loading.
     *
     * @param bookId     of book must be != 0
     * @param bookCursor an already positioned Cursor to read from
     * @param bookDao         to load linked array data from
     */
    public void load(@IntRange(from = 1) final long bookId,
                     @NonNull final Cursor bookCursor,
                     @NonNull final BookDao bookDao) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

        clearData();
        putAll(bookCursor);
        // load lists (or init with empty lists)
        putParcelableArrayList(BKEY_BOOKSHELF_LIST, bookDao.getBookshelvesByBookId(bookId));
        putParcelableArrayList(BKEY_AUTHOR_LIST, bookDao.getAuthorsByBookId(bookId));
        putParcelableArrayList(BKEY_SERIES_LIST, bookDao.getSeriesByBookId(bookId));
        putParcelableArrayList(BKEY_PUBLISHER_LIST, bookDao.getPublishersByBookId(bookId));
        putParcelableArrayList(BKEY_TOC_LIST, bookDao.getTocEntryByBookId(bookId));

        // do NOT preload the full library object. We hardly ever need it as such.
        // see #getCalibreLibrary
//        final CalibreLibrary calibreLibrary = db
//                .getCalibreLibrary(getLong(DBDefinitions.KEY_FK_CALIBRE_LIBRARY));
//        if (calibreLibrary != null) {
//            putParcelable(BKEY_CALIBRE_LIBRARY, calibreLibrary);
//        }
    }

    /**
     * Duplicate a book by putting APPLICABLE (not simply all of them) fields
     * in a Bundle ready for further processing.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     *
     * @return bundle with book data
     * <p>
     * <b>Developer:</b> keep in sync with {@link BookDao} .SqlAllBooks#BOOK
     */
    @NonNull
    public Bundle duplicate() {
        final Bundle bookData = new Bundle();

        // Q: Why don't we get the DataManager#mRawData, remove the identifiers/dates and use that?
        // A: because we would need to clone mRawData before we can start removing fields,
        //  From Bundle#clone() docs: Clones the current Bundle.
        //  The internal map is cloned, but the keys and values to which it refers are
        //  copied by reference.
        // ==> by reference...  so we would in effect be removing fields from the original book.
        // This would be ok if we discard the original object (in memory only)
        // but lets play this safe.

        // Do not copy any identifiers.
        // KEY_PK_ID
        // KEY_BOOK_UUID
        // KEY_ESID_LIBRARY_THING
        // KEY_ESID_ISFDB
        // KEY_ESID_GOODREADS
        // ...
        // Do not copy these specific dates.
        // KEY_BOOK_DATE_ADDED
        // KEY_DATE_LAST_UPDATED
        // KEY_BOOK_GOODREADS_LAST_SYNC_DATE

        bookData.putString(DBDefinitions.KEY_TITLE,
                           getString(DBDefinitions.KEY_TITLE));
        bookData.putString(DBDefinitions.KEY_ISBN,
                           getString(DBDefinitions.KEY_ISBN));

        bookData.putParcelableArrayList(BKEY_AUTHOR_LIST,
                                        getParcelableArrayList(BKEY_AUTHOR_LIST));
        bookData.putParcelableArrayList(BKEY_SERIES_LIST,
                                        getParcelableArrayList(BKEY_SERIES_LIST));
        bookData.putParcelableArrayList(BKEY_PUBLISHER_LIST,
                                        getParcelableArrayList(BKEY_PUBLISHER_LIST));
        bookData.putParcelableArrayList(BKEY_TOC_LIST,
                                        getParcelableArrayList(BKEY_TOC_LIST));

        // publication data
        bookData.putString(DBDefinitions.KEY_PRINT_RUN,
                           getString(DBDefinitions.KEY_PRINT_RUN));
        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK,
                         getLong(DBDefinitions.KEY_TOC_BITMASK));
        bookData.putString(DBDefinitions.KEY_BOOK_DATE_PUBLISHED,
                           getString(DBDefinitions.KEY_BOOK_DATE_PUBLISHED));
        bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED,
                           getDouble(DBDefinitions.KEY_PRICE_LISTED));
        bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                           getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                           getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION));
        bookData.putString(DBDefinitions.KEY_FORMAT,
                           getString(DBDefinitions.KEY_FORMAT));
        bookData.putString(DBDefinitions.KEY_COLOR,
                           getString(DBDefinitions.KEY_COLOR));
        bookData.putString(DBDefinitions.KEY_GENRE,
                           getString(DBDefinitions.KEY_GENRE));
        bookData.putString(DBDefinitions.KEY_LANGUAGE,
                           getString(DBDefinitions.KEY_LANGUAGE));
        bookData.putString(DBDefinitions.KEY_PAGES,
                           getString(DBDefinitions.KEY_PAGES));
        // common blurb
        bookData.putString(DBDefinitions.KEY_DESCRIPTION,
                           getString(DBDefinitions.KEY_DESCRIPTION));

        // partially edition info, partially use-owned info.
        bookData.putLong(DBDefinitions.KEY_EDITION_BITMASK,
                         getLong(DBDefinitions.KEY_EDITION_BITMASK));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBDefinitions.KEY_SIGNED,
                         getLong(DBDefinitions.KEY_SIGNED));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBDefinitions.KEY_READ,
                         getLong(DBDefinitions.KEY_READ));

        bookData.putDouble(DBDefinitions.KEY_RATING,
                           getDouble(DBDefinitions.KEY_RATING));

        bookData.putString(DBDefinitions.KEY_PRIVATE_NOTES,
                           getString(DBDefinitions.KEY_PRIVATE_NOTES));

        bookData.putInt(DBDefinitions.KEY_BOOK_CONDITION,
                        getInt(DBDefinitions.KEY_BOOK_CONDITION));
        bookData.putInt(DBDefinitions.KEY_BOOK_CONDITION_COVER,
                        getInt(DBDefinitions.KEY_BOOK_CONDITION_COVER));

        bookData.putString(DBDefinitions.KEY_READ_START,
                           getString(DBDefinitions.KEY_READ_START));
        bookData.putString(DBDefinitions.KEY_READ_END,
                           getString(DBDefinitions.KEY_READ_END));
        bookData.putString(DBDefinitions.KEY_DATE_ACQUIRED,
                           getString(DBDefinitions.KEY_DATE_ACQUIRED));
        bookData.putDouble(DBDefinitions.KEY_PRICE_PAID,
                           getDouble(DBDefinitions.KEY_PRICE_PAID));
        bookData.putString(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                           getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));

        return bookData;
    }

    @NonNull
    public EntityStage.Stage getStage() {
        return mStage.getStage();
    }

    public void setStage(@NonNull final EntityStage.Stage stage) {
        mStage.setStage(stage);
    }

    /**
     * return the cover for the given uuid. We'll attempt to find a jpg or a png.
     * <strong>If no file found, a jpg place holder is returned.</strong>
     * <p>
     * Keep in mind that internally we always use PNG compression (except for the cache).
     * So a jpg named file can be a png encoded file. (But we don't need to care about that.)
     * <p>
     * The index only gets appended to the name if it's > 0.
     *
     * @param context Current context
     * @param cIdx    0..n image index
     *
     * @return The File object for existing files, or a new jpg placeholder.
     *
     * @throws ExternalStorageException if the Shared Storage media is not available
     * @see #getUuidCoverFile(Context, int)
     */
    @NonNull
    public File getUuidCoverFileOrNew(@NonNull final Context context,
                                      @IntRange(from = 0, to = 1) final int cIdx)
            throws ExternalStorageException {
        final File coverDir = AppDir.Covers.get(context);

        final String uuid = getString(DBDefinitions.KEY_BOOK_UUID);

        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid + "";
        }

        final File jpg = new File(coverDir, name + ".jpg");
        if (jpg.exists()) {
            return jpg;
        }
        // could be a png
        final File png = new File(coverDir, name + ".png");
        if (png.exists()) {
            return png;
        }

        // we need a new file, return a placeholder with the jpg extension
        return jpg;
    }

    @Nullable
    public File getUuidCoverFile(@NonNull final Context context,
                                 @IntRange(from = 0, to = 1) final int cIdx) {

        final String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
        return getUuidCoverFile(context, uuid, cIdx);
    }

    @Override
    public char getType() {
        return AuthorWork.TYPE_BOOK;
    }

    @NonNull
    @Override
    public PartialDate getFirstPublicationDate() {
        return new PartialDate(getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION));
    }

    /**
     * Get the parsed last update date.
     * <p>
     * Note this method can return {@code null} while in theory, the date will
     * <strong>always</strong> be valid but as usual we code with paranoia in mind.
     *
     * @param context Current context
     *
     * @return the date
     */
    @Nullable
    public LocalDateTime getLastUpdateUtcDate(@NonNull final Context context) {
        return DateParser.getInstance(context)
                         .parseISO(getString(DBDefinitions.KEY_UTC_LAST_UPDATED));
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
    @Override
    public long getId() {
        return getLong(DBDefinitions.KEY_PK_ID);
    }

    /**
     * Set the id.
     *
     * @param id the book id.
     */
    @Override
    public void setId(final long id) {
        putLong(DBDefinitions.KEY_PK_ID, id);
    }

    /**
     * Get the unformatted title.
     *
     * @return title
     */
    @Override
    @NonNull
    public String getTitle() {
        return getString(DBDefinitions.KEY_TITLE);
    }

    /**
     * Get the label to use. This is for <strong>displaying only</strong>.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return reorderTitleForDisplaying(context, getLocale(context));
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
        return getAndUpdateLocale(context, AppLocale.getInstance().getUserLocale(context), false);
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
    private Locale getAndUpdateLocale(@NonNull final Context context,
                                      @NonNull final Locale fallbackLocale,
                                      final boolean updateLanguage) {
        Locale bookLocale = null;
        if (contains(DBDefinitions.KEY_LANGUAGE)) {
            final String lang = getString(DBDefinitions.KEY_LANGUAGE);

            bookLocale = AppLocale.getInstance().getLocale(context, lang);
            if (bookLocale == null) {
                return fallbackLocale;

            } else if (updateLanguage) {
                putString(DBDefinitions.KEY_LANGUAGE, lang);
            }
        }

        if (bookLocale != null) {
            return bookLocale;
        } else {
            // none, use fallback.
            return fallbackLocale;
        }
    }

    /**
     * Get the first author in the list of authors for this book.
     *
     * @return the Author or {@code null} if none present
     */
    @Nullable
    public Author getPrimaryAuthor() {
        final ArrayList<Author> authors = getParcelableArrayList(BKEY_AUTHOR_LIST);
        return authors.isEmpty() ? null : authors.get(0);
    }

    /**
     * Update author details from DB.
     *
     * @param context Current context
     */
    public void refreshAuthorList(@NonNull final Context context) {

        final AuthorDao authorDao = AuthorDao.getInstance();
        final Locale bookLocale = getLocale(context);
        final ArrayList<Author> list = getParcelableArrayList(BKEY_AUTHOR_LIST);
        for (final Author author : list) {
            authorDao.refresh(context, author, bookLocale);
        }
    }

    /**
     * Get the first Series in the list of Series for this book.
     *
     * @return the Series, or {@code null} if none present
     */
    @Nullable
    public Series getPrimarySeries() {
        final ArrayList<Series> list = getParcelableArrayList(BKEY_SERIES_LIST);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Update Series details from DB.
     *
     * @param context Current context
     */
    public void refreshSeriesList(@NonNull final Context context) {

        final SeriesDao seriesDao = SeriesDao.getInstance();

        final Locale bookLocale = getLocale(context);
        final ArrayList<Series> list = getParcelableArrayList(BKEY_SERIES_LIST);
        for (final Series series : list) {
            seriesDao.refresh(context, series, bookLocale);
        }
    }

    /**
     * Get the first Publisher in the list of Publishers for this book.
     *
     * @return the Publisher, or {@code null} if none present
     */
    @Nullable
    public Publisher getPrimaryPublisher() {
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_LIST);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Update Publisher details from DB.
     *
     * @param context Current context
     */
    public void refreshPublishersList(@NonNull final Context context) {

        final PublisherDao publisherDao = PublisherDao.getInstance();
        final Locale bookLocale = getLocale(context);
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_LIST);
        for (final Publisher publisher : list) {
            publisherDao.refresh(context, publisher, bookLocale);
        }
    }

    @Nullable
    public CalibreLibrary getCalibreLibrary() {
        // We MIGHT have it (probably not) ...
        if (contains(BKEY_CALIBRE_LIBRARY)) {
            return getParcelable(BKEY_CALIBRE_LIBRARY);

        } else {
            // but if not, go explicitly fetch it.
            final CalibreLibrary library = CalibreLibraryDao.getInstance().getLibrary(
                    getLong(DBDefinitions.KEY_FK_CALIBRE_LIBRARY));
            if (library != null) {
                // store for reuse
                putParcelable(BKEY_CALIBRE_LIBRARY, library);
            }
            return library;
        }
    }

    public void setCalibreLibrary(@Nullable final CalibreLibrary library) {
        if (library != null) {
            putLong(DBDefinitions.KEY_FK_CALIBRE_LIBRARY, library.getId());
            putParcelable(Book.BKEY_CALIBRE_LIBRARY, library);
        } else {
            remove(DBDefinitions.KEY_FK_CALIBRE_LIBRARY);
            remove(Book.BKEY_CALIBRE_LIBRARY);

            remove(DBDefinitions.KEY_CALIBRE_BOOK_ID);
            remove(DBDefinitions.KEY_CALIBRE_BOOK_UUID);
            remove(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT);
        }
    }

    /**
     * Get the name of the loanee (if any).
     *
     * @return name, or {@code ""} if none
     */
    @NonNull
    public String getLoanee() {
        // We SHOULD have it...
        if (contains(DBDefinitions.KEY_LOANEE)) {
            return getString(DBDefinitions.KEY_LOANEE);

        } else {
            // but if not, go explicitly fetch it.
            String loanee = LoaneeDao.getInstance().getLoaneeByBookId(getId());
            if (loanee == null) {
                loanee = "";
            }
            // store for reuse
            putString(DBDefinitions.KEY_LOANEE, loanee);
            return loanee;
        }
    }

    public boolean isAvailable() {
        return getLoanee().isEmpty();
    }

    /**
     * Toggle the read-status for this book.
     *
     * @param bookDao Database Access
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead(@NonNull final BookDao bookDao) {
        return setRead(bookDao, !getBoolean(DBDefinitions.KEY_READ));
    }

    /**
     * Update the 'read' status of a book in the database + sets the 'read end' to today.
     * The book will have its 'read' status updated ONLY if the update went through.
     *
     * @param bookDao     Database Access
     * @param isRead Flag for the 'read' status
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    private boolean setRead(@NonNull final BookDao bookDao,
                            final boolean isRead) {
        final boolean old = getBoolean(DBDefinitions.KEY_READ);

        if (bookDao.setBookRead(this, isRead)) {
            return isRead;
        }

        return old;
    }

    /**
     * Ensure the book has a bookshelf.
     * If the book is not on any Bookshelf, add the preferred/current bookshelf
     *
     * @param context Current context
     */
    public void ensureBookshelf(@NonNull final Context context) {
        final ArrayList<Bookshelf> list = getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
        if (list.isEmpty()) {
            list.add(Bookshelf.getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT));
        }
    }

    /**
     * Add validators.
     */
    public void addValidators() {

        addValidator(DBDefinitions.KEY_TITLE,
                     NON_BLANK_VALIDATOR, R.string.lbl_title);
        addValidator(BKEY_AUTHOR_LIST,
                     NON_BLANK_VALIDATOR, R.string.lbl_author);

        addValidator(DBDefinitions.KEY_LANGUAGE,
                     NON_BLANK_VALIDATOR, R.string.lbl_language);

        addValidator(DBDefinitions.KEY_EDITION_BITMASK,
                     LONG_VALIDATOR, R.string.lbl_edition);
        addValidator(DBDefinitions.KEY_TOC_BITMASK,
                     LONG_VALIDATOR, R.string.lbl_table_of_content);

        addValidator(DBDefinitions.KEY_PRICE_LISTED,
                     BLANK_OR_DOUBLE_VALIDATOR, R.string.lbl_price_listed);
        addValidator(DBDefinitions.KEY_PRICE_PAID,
                     BLANK_OR_DOUBLE_VALIDATOR, R.string.lbl_price_paid);

        addCrossValidator((context, book) -> {
            final String start = book.getString(DBDefinitions.KEY_READ_START);
            if (start.isEmpty()) {
                return;
            }
            final String end = book.getString(DBDefinitions.KEY_READ_END);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(context.getString(R.string.vldt_read_start_after_end));
            }
        });
    }


    /**
     * Examine the values and make any changes necessary before writing the data.
     * Called during {@link BookDao#insert(Context, Book, int)}
     * and {@link BookDao#update(Context, Book, int)}.
     *
     * @param context Current context
     * @param isNew   {@code true} if the book is new
     */
    public void preprocessForStoring(@NonNull final Context context,
                                     final boolean isNew) {

        // Handle Language field FIRST, we need it for _OB fields.
        final Locale bookLocale = getAndUpdateLocale(
                context, AppLocale.getInstance().getUserLocale(context), true);

        // Handle TITLE
        if (contains(DBDefinitions.KEY_TITLE)) {
            final String obTitle = reorderTitleForSorting(context, bookLocale);
            putString(DBDefinitions.KEY_TITLE_OB, BaseDao.encodeOrderByColumn(obTitle, bookLocale));
        }

        // Handle TOC_BITMASK only, no handling of actual titles here,
        // but making sure TOC_MULTIPLE_AUTHORS is correct.
        final ArrayList<TocEntry> tocEntries = getParcelableArrayList(BKEY_TOC_LIST);
        if (!tocEntries.isEmpty()) {
            @TocBits
            long type = getLong(DBDefinitions.KEY_TOC_BITMASK);
            if (TocEntry.hasMultipleAuthors(tocEntries)) {
                type |= Book.TOC_MULTIPLE_AUTHORS;
            }
            putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // make sure we only store valid bits
        if (contains(DBDefinitions.KEY_EDITION_BITMASK)) {
            final int editions = getInt(DBDefinitions.KEY_EDITION_BITMASK) & Edition.BITMASK_ALL;
            putInt(DBDefinitions.KEY_EDITION_BITMASK, editions);
        }

        // cleanup/build all price related fields
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_PAID,
                        DBDefinitions.KEY_PRICE_PAID_CURRENCY);

        // replace 'T' by ' ' and truncate pure date fields if needed
        preprocessDates();

        // make sure there are only valid external id's present
        preprocessExternalIds(isNew);

        // lastly, cleanup null and blank fields as needed.
        preprocessNullsAndBlanks(isNew);
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     * <p>
     *
     * @param bookLocale  the book Locale
     * @param valueKey    key for the value field
     * @param currencyKey key for the currency field
     */
    @VisibleForTesting
    void preprocessPrice(@NonNull final Locale bookLocale,
                         @NonNull final String valueKey,
                         @NonNull final String currencyKey) {
        // handle a price without a currency.
        if (contains(valueKey) && !contains(currencyKey)) {
            // we presume the user bought the book in their own currency.
            final Money money = new Money(bookLocale, getString(valueKey));
            if (money.getCurrency() != null) {
                putDouble(valueKey, money.doubleValue());
                putString(currencyKey, money.getCurrency().toUpperCase(bookLocale));
                return;
            }
            // else just leave the original in the data
        }

        // Make sure currencies are uppercase
        if (contains(currencyKey)) {
            putString(currencyKey, getString(currencyKey).toUpperCase(bookLocale));
        }
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     * <p>
     * Truncate Date fields to being pure dates without a time segment.
     * Replaces 'T' with ' ' to please SqLite/SQL datetime standards.
     * <p>
     * <strong>Note 1:</strong>: We do not fully parse each date string,
     * to verify/correct as an SQLite datetime string.
     * It is assumed that during normal logic flow this is already done.
     * The 'T' is the exception as that is easier to handle here for all fields.
     *
     * <strong>Note 2:</strong>: such a full parse should be done during import operations.
     */
    @VisibleForTesting
    void preprocessDates() {
        final List<Domain> domains = DBDefinitions.TBL_BOOKS.getDomains();

        // Partial/Full Date strings
        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_DATE))
               .map(Domain::getName)
               .filter(this::contains)
               .forEach(key -> {
                   final String date = getString(key);
                   // This is very crude... we simply truncate to 10 characters maximum
                   // i.e. 'YYYY-MM-DD', but do not verify if it's a valid date.
                   if (date.length() > 10) {
                       putString(key, date.substring(0, 10));
                   }
               });

        // Full UTC based DateTime strings
        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_DATETIME))
               .map(Domain::getName)
               .filter(this::contains)
               .forEach(key -> {
                   final String date = getString(key);
                   // Again, very crude logic... we simply check for the 11th char being a 'T'
                   // and if so, replace it with a space
                   if (date.length() > 10 && date.charAt(10) == 'T') {
                       putString(key, T.matcher(date).replaceFirst(" "));
                   }
               });
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     * <p>
     * Processes the external id keys.
     * <p>
     * For new books, REMOVE zero values, empty strings AND null values
     * Existing books, REPLACE zero values and empty string with a {@code null}
     * <p>
     * Invalid values are always removed.
     * <p>
     * Further processing should be done in {@link #preprocessNullsAndBlanks(boolean)}.
     *
     * @param isNew {@code true} if the book is new
     */
    @VisibleForTesting
    void preprocessExternalIds(final boolean isNew) {
        final List<Domain> domains = SearchEngineRegistry.getInstance().getExternalIdDomains();

        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_INTEGER))
               .map(Domain::getName)
               .filter(this::contains)
               .forEach(key -> {
                   final Object o = get(key);
                   try {
                       if (isNew) {
                           // For new books:
                           if (o == null) {
                               // remove null values
                               remove(key);
                           } else {
                               final long v = getLong(key);
                               if (v < 1) {
                                   // remove zero values
                                   remove(key);
                               }
                           }
                       } else {
                           // for existing books, leave null values as-is
                           if (o != null) {
                               final long v = getLong(key);
                               if (v < 1) {
                                   // replace zero values with a null
                                   putNull(key);
                               }
                           }
                       }
                   } catch (@NonNull final NumberFormatException e) {
                       // always remove illegal input
                       remove(key);

                       if (BuildConfig.DEBUG /* always */) {
                           Logger.d(TAG, "preprocessExternalIds",
                                    "NumberFormatException"
                                    + "|name=" + key
                                    + "|value=`" + o + '`');
                       }
                   }
               });

        domains.stream()
               .filter(domain -> domain.getType().equals(ColumnInfo.TYPE_TEXT))
               .map(Domain::getName)
               .filter(this::contains)
               .forEach(key -> {
                   final Object o = get(key);
                   if (isNew) {
                       // for new books,
                       if (o == null) {
                           // remove null values
                           remove(key);
                       } else {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // remove empty/zero values
                               remove(key);
                           }
                       }
                   } else {
                       // for existing books, leave null values as-is
                       if (o != null) {
                           final String v = o.toString();
                           if (v.isEmpty() || "0".equals(v)) {
                               // replace "0" and empty strings with a null
                               putNull(key);
                           }
                       }
                   }
               });
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     *
     * <ul>Fields in this Book, which have a default in the database and
     *      <li>which are null but not allowed to be null</li>
     *      <li>which are null/empty (i.e. blank) but not allowed to be blank</li>
     * </ul>
     * <p>
     * For new books, REMOVE those keys.
     * Existing books, REPLACE those keys with the default value for the column.
     *
     * @param isNew {@code true} if the book is new
     */
    @VisibleForTesting
    void preprocessNullsAndBlanks(final boolean isNew) {
        DBDefinitions.TBL_BOOKS
                .getDomains()
                .stream()
                .filter(domain -> contains(domain.getName()) && domain.hasDefault())
                .forEach(domain -> {
                    final Object o = get(domain.getName());
                    if (
                        // Fields which are null but not allowed to be null
                            (o == null && domain.isNotNull())
                            ||
                            // Fields which are null/empty (i.e. blank) but not allowed to be blank
                            ((o == null || o.toString().isEmpty()) && domain.isNotBlank())
                    ) {
                        if (isNew) {
                            remove(domain.getName());
                        } else {
                            // restore the column to its default value.
                            //noinspection ConstantConditions
                            putString(domain.getName(), domain.getDefault());
                        }
                    }
                });
    }


    /**
     * Get the <strong>current</strong> cover file for this book.
     *
     * @param context Current context
     * @param cIdx    0..n image index
     *
     * @return a guaranteed existing File, or {@code null}
     */
    @Nullable
    public File getCoverFile(@NonNull final Context context,
                             @IntRange(from = 0, to = 1) final int cIdx) {

        File coverFile = null;

        if (contains(BKEY_TMP_FILE_SPEC[cIdx])) {
            // we have a previously set temporary cover, but it could be ""
            final String fileSpec = getString(BKEY_TMP_FILE_SPEC[cIdx]);
            if (!fileSpec.isEmpty()) {
                coverFile = new File(fileSpec);
                if (!coverFile.exists()) {
                    coverFile = null;
                }
            }
        } else {
            // Get the permanent, UUID based, cover file for this book.
            final String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
            if (!uuid.isEmpty()) {
                final String name;
                if (cIdx > 0) {
                    name = uuid + "_" + cIdx;
                } else {
                    name = uuid + "";
                }

                try {
                    final File coverDir = AppDir.Covers.get(context);
                    // should be / try jpg first
                    coverFile = new File(coverDir, name + ".jpg");
                    if (!coverFile.exists()) {
                        // no cover, try for a png
                        coverFile = new File(coverDir, name + ".png");
                        if (!coverFile.exists()) {
                            coverFile = null;
                        }
                    }
                } catch (@NonNull final ExternalStorageException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "getUuidCoverFile", e);
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Logger.d(TAG, new Throwable("getCoverFile"),
                     "bookId=" + getId()
                     + "|cIdx=" + cIdx
                     + "|file=" + (coverFile == null ? "null" : coverFile.getAbsolutePath()));
        }
        return coverFile;
    }

    /**
     * Create a temporary cover file for this book.
     * If there is a permanent cover, we get a <strong>copy of that one</strong>.
     * If there is no cover, we get a new File object with a temporary name.
     * <p>
     * Location: {@link AppDir#Cache}
     *
     * @param context Current context
     * @param cIdx    0..n image index
     *
     * @return the File
     *
     * @throws IOException on failure to make a copy of the permanent file
     */
    @NonNull
    public File createTempCoverFile(@NonNull final Context context,
                                    @IntRange(from = 0, to = 1) final int cIdx)
            throws IOException {

        // the temp file we'll return
        // do NOT set BKEY_TMP_FILE_SPEC in this method.
        final File coverFile = AppDir.Cache.getFile(context, System.nanoTime() + ".jpg");

        final File uuidFile = getCoverFile(context, cIdx);
        if (uuidFile != null && uuidFile.exists()) {
            // We have a permanent file, copy it into the temp location
            FileUtils.copy(uuidFile, coverFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Logger.d(TAG, new Throwable("createTempCoverFile"),
                     "bookId=" + getId()
                     + "|cIdx=" + cIdx
                     + "|exists=" + coverFile.exists()
                     + "|file=" + coverFile.getAbsolutePath());
        }
        return coverFile;
    }

    /**
     * Update the book cover with the given file.
     *
     * @param context Current context
     * @param bookDao      Database Access
     * @param cIdx    0..n image index
     * @param file    cover file or {@code null} to delete the cover
     *
     * @return the File after processing (either original, or a renamed/moved file)
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public File setCover(@NonNull final Context context,
                         @NonNull final BookDao bookDao,
                         @IntRange(from = 0, to = 1) final int cIdx,
                         @Nullable final File file) {

        if (mStage.getStage() == EntityStage.Stage.WriteAble
            || mStage.getStage() == EntityStage.Stage.Dirty) {
            // We're editing, use BKEY_TMP_FILE_SPEC storage.

            if (file != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, new Throwable("setCover"),
                             "editing"
                             + "|bookId=" + getId()
                             + "|cIdx=" + cIdx
                             + "|file=" + file.getAbsolutePath());
                }
                // #storeCovers will do the actual storing
                putString(BKEY_TMP_FILE_SPEC[cIdx], file.getAbsolutePath());

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, new Throwable("setCover"),
                             "editing"
                             + "|bookId=" + getId()
                             + "|cIdx=" + cIdx
                             + "|deleting");
                }
                // explicitly set to "" to let #storeCovers do the delete
                putString(BKEY_TMP_FILE_SPEC[cIdx], "");
            }

            // switch from WriteAble to Dirty (or from Dirty to Dirty)
            mStage.setStage(EntityStage.Stage.Dirty);

            // just return the incoming file, it has not been changed or renamed
            return file;

        } else {
            // we're in read-only mode, use the UUID storage based file name
            final String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
            SanityCheck.requireValue(uuid, "uuid");

            // the file to return from this method, after the incoming file has been processed
            File destination = file;

            if (file != null) {
                if (file.getName().startsWith(uuid)) {
                    // No further action needed as we have the cover "in-place"
                    // ... not actually sure when this would be the case; keep an eye on logs
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Logger.d(TAG, new Throwable("setCover"),
                                 "readOnly"
                                 + "|bookId=" + getId()
                                 + "|cIdx=" + cIdx
                                 + "|uuid, in-place");
                    }
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Logger.d(TAG, new Throwable("setCover"),
                                 "readOnly"
                                 + "|bookId=" + getId()
                                 + "|cIdx=" + cIdx
                                 + "|will rename="
                                 + file.getAbsolutePath());
                    }


                    try {
                        // Rename the temp file to the uuid permanent file name
                        destination = getUuidCoverFileOrNew(context, cIdx);
                        FileUtils.rename(file, destination);

                    } catch (@NonNull final IOException e) {
                        Logger.error(context, TAG, e,
                                     "setCover|bookId=" + getId() + "|cIdx=" + cIdx);
                        return null;
                    }
                }

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, new Throwable("setCover")
                            , "readOnly"
                              + "|bookId=" + getId()
                              + "|cIdx=" + cIdx
                              + "|deleting");
                }

                FileUtils.delete(getUuidCoverFile(context, cIdx));
                if (ImageUtils.isImageCachingEnabled(context)) {
                    // We delete *all* files related to this book from the cache.
                    CoverCacheDao.getInstance(context).delete(context, uuid);
                }
            }

            bookDao.touchBook(this);

            return destination;
        }
    }

    /**
     * Called during {@link BookDao#insert(Context, Book, int)}
     * and {@link BookDao#update(Context, Book, int)}.
     *
     * @param context Current context
     *
     * @return {@code false} on failure
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean storeCovers(@NonNull final Context context) {

        final String uuid = getString(DBDefinitions.KEY_BOOK_UUID);

        if (BuildConfig.DEBUG /* always */) {
            // the UUID should always be valid here
            SanityCheck.requireValue(uuid, "uuid");
        }

        for (int cIdx = 0; cIdx < 2; cIdx++) {
            if (contains(BKEY_TMP_FILE_SPEC[cIdx])) {
                final String fileSpec = getString(BKEY_TMP_FILE_SPEC[cIdx]);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "storeCovers",
                             "BKEY_TMP_FILE_SPEC[" + cIdx + "]=`" + fileSpec + '`');
                }

                if (fileSpec.isEmpty()) {
                    // An empty fileSpec indicates we need to delete the cover
                    FileUtils.delete(getUuidCoverFile(context, cIdx));
                    // Delete from the cache. And yes, we also delete the ones
                    // where != index, but we don't care; it's a cache.
                    if (ImageUtils.isImageCachingEnabled(context)) {
                        CoverCacheDao.getInstance(context).delete(context, uuid);
                    }
                } else {
                    // Rename the temp file to the uuid permanent file name
                    final File file = new File(fileSpec);
                    try {
                        final File destination = getUuidCoverFileOrNew(context, cIdx);
                        FileUtils.rename(file, destination);
                    } catch (@NonNull final IOException e) {
                        Logger.error(context, TAG, e,
                                     "storeCovers|bookId=" + getId() + "|cIdx=" + cIdx);
                        return false;
                    }
                }

                remove(BKEY_TMP_FILE_SPEC[cIdx]);
            } else {
                // If the key is NOT present, we don't need to do anything!
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "storeCovers",
                             "BKEY_TMP_FILE_SPEC[" + cIdx + "]=<not present>");
                }
            }
        }

        return true;
    }


    /**
     * Creates a chooser with matched apps for sharing some text.
     * <b>"I'm reading " + title + series + " by " + author + ratingString</b>
     *
     * @param context Current context
     *
     * @return the intent
     */
    @NonNull
    public Intent getShareIntent(@NonNull final Context context) {
        final String title = getString(DBDefinitions.KEY_TITLE);
        final String author = getString(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Series series = getPrimarySeries();
        final String seriesStr;
        if (series != null) {
            final String number = series.getNumber();
            seriesStr = " (" + series.getTitle() + (number.isEmpty() ? "" : "%23" + number) + ')';
        } else {
            seriesStr = "";
        }

        //remove trailing 0's
        final double rating = getDouble(DBDefinitions.KEY_RATING);
        final String ratingStr;
        if (rating > 0) {
            // force rounding
            final int ratingTmp = (int) rating;
            // get fraction
            final double decimal = rating - ratingTmp;
            if (decimal > 0) {
                ratingStr = '(' + String.valueOf(rating) + '/' + RATING_STARS + ')';
            } else {
                ratingStr = '(' + String.valueOf(ratingTmp) + '/' + RATING_STARS + ')';
            }
        } else {
            ratingStr = "";
        }

        // The share intent is limited to a single *type* of data.
        // We cannot send the cover AND the text; for now we send the text only.
//        String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
//        // prepare the front-cover to post
//        File coverFile = AppDir.getCoverFile(context, uuid, 0);
//        if (coverFile.exists()) {
//            Uri uri = GenericFileProvider.getUriForFile(context, coverFile);
//        }

        final String text = context.getString(R.string.txt_share_book_im_reading,
                                              title, seriesStr, author, ratingStr);

        return Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, text),
                                    context.getString(R.string.whichSendApplication));
    }

    /** Used exclusively during display / populating the Views when loading the book. */
    public void lockStage() {
        mStage.lock();
    }

    /** Used exclusively during display / populating the Views when loading the book. */
    public void unlockStage() {
        mStage.unlock();
    }

    public void pruneAuthors(@NonNull final Context context,
                             final boolean lookupLocale) {
        final ArrayList<Author> authors = getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        if (!authors.isEmpty()) {
            if (Author.pruneList(authors, context, lookupLocale, getLocale(context))) {
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }

        // No authors ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        if (authors.isEmpty()) {
            final String searchText = getString(
                    SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
            if (!searchText.isEmpty()) {
                authors.add(Author.from(searchText));
                remove(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }
    }

    public void pruneSeries(@NonNull final Context context,
                            final boolean lookupLocale) {
        final ArrayList<Series> series = getParcelableArrayList(Book.BKEY_SERIES_LIST);
        if (!series.isEmpty()) {
            if (Series.pruneList(series, context, lookupLocale, getLocale(context))) {
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }
    }

    public void prunePublishers(@NonNull final Context context,
                                final boolean lookupLocale) {
        final ArrayList<Publisher> publishers = getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        if (!publishers.isEmpty()) {
            if (Publisher.pruneList(publishers, context, lookupLocale, getLocale(context))) {
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {TOC_MULTIPLE_WORKS, TOC_MULTIPLE_AUTHORS})
    public @interface TocBits {

    }

    public static final class Edition {

        /*
         * {@link DBDefinitions#KEY_EDITION_BITMASK}.
         * <p>
         * 0%00000000 = a generic edition, or we simply don't know what edition it is.
         * 0%00000001 = first edition
         * 0%00000010 = first impression
         * 0%00000100 = limited edition
         * 0%00001000 = slipcase
         * 0%00010000 = signed
         * <p>
         * 0%10000000 = book club
         * <p>
         * NEWTHINGS: edition: add bit flag and add to mask
         * Never change the bit value!
         */
        /** first edition ever of this work/content/story. */
        public static final int FIRST = 1;
        /** First printing of 'this' edition. */
        @SuppressWarnings("WeakerAccess")
        @VisibleForTesting
        public static final int FIRST_IMPRESSION = 1 << 1;
        /** This edition had a limited run. (Numbered or not). */
        @VisibleForTesting
        public static final int LIMITED = 1 << 2;
        /** This edition comes in a slipcase. */
        @SuppressWarnings("WeakerAccess")
        @VisibleForTesting
        public static final int SLIPCASE = 1 << 3;
        /** This edition is signed. i.e the whole print-run of this edition is signed. */
        @VisibleForTesting
        public static final int SIGNED = 1 << 4;
        /** It's a bookclub edition. */
        @SuppressWarnings("WeakerAccess")
        @VisibleForTesting
        public static final int BOOK_CLUB = 1 << 7;
        /** Bitmask for all editions. Bit 5/6 not in use for now. */
        public static final int BITMASK_ALL = FIRST
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
         * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
         */
        static {
            ALL.put(FIRST, R.string.lbl_edition_first_edition);
            ALL.put(FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
            ALL.put(LIMITED, R.string.lbl_edition_limited);
            ALL.put(SIGNED, R.string.lbl_signed);
            ALL.put(SLIPCASE, R.string.lbl_edition_slipcase);

            ALL.put(BOOK_CLUB, R.string.lbl_edition_book_club);
        }

        @NonNull
        public static Map<Integer, String> getEditions(@NonNull final Context context) {
            final Map<Integer, String> map = new LinkedHashMap<>();
            for (final Map.Entry<Integer, Integer> entry : ALL.entrySet()) {
                map.put(entry.getKey(), context.getString(entry.getValue()));
            }
            return map;
        }
    }
}
