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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoImpl;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

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
     * {@link DBKey#BITMASK_TOC}
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
     * A book (and dustcover) condition goes from 1(worst)..5(best) or 0 for not-set.
     * In code we only need 0/5.
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
    public static final int CONDITION_UNKNOWN = 0;
    public static final int CONDITION_AS_NEW = 5;

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
    public static final String[] BKEY_TMP_FILE_SPEC = {
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
    @NonNull
    public static Book from(@IntRange(from = 1) final long bookId,
                            @NonNull final BookDao bookDao) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

        final Book book = new Book();
        try (Cursor bookCursor = bookDao.fetchById(bookId)) {
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
     * Constructor. Loads the bundle <strong>with</strong> type checks.
     *
     * @param bookData data bundle to use for the Book
     *
     * @return new instance; flagged as {@link EntityStage.Stage#Dirty}
     */
    @NonNull
    public static Book from(@NonNull final Bundle bookData) {
        final Book book = new Book();
        book.putAll(bookData);
        // has unsaved data, hence 'Dirty'
        book.setStage(EntityStage.Stage.Dirty);
        return book;
    }

    /**
     * Get the cover for the given uuid. We'll attempt to find a jpg or a png.
     * <p>
     * Any {@link CoverStorageException} is <strong>IGNORED</strong>
     *
     * @param uuid UUID of the book
     * @param cIdx 0..n image index
     *
     * @return The File object for existing files, or {@code null}
     */
    @Nullable
    public static File getPersistedCoverFile(@NonNull final String uuid,
                                             @IntRange(from = 0, to = 1) final int cIdx) {
        final File coverDir;
        try {
            coverDir = CoverDir.getDir(ServiceLocator.getAppContext());
        } catch (@NonNull final CoverStorageException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getPersistedCoverFile", e);
            }
            return null;
        }

        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid;
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
     * The current book data is cleared before loading.
     *
     * @param bookId     of book must be != 0
     * @param bookCursor an already positioned Cursor to read from
     */
    public void load(@IntRange(from = 1) final long bookId,
                     @NonNull final Cursor bookCursor) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

        clearData();
        putAll(bookCursor);

        // load lists (or init with empty lists)
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        putParcelableArrayList(BKEY_BOOKSHELF_LIST,
                               serviceLocator.getBookshelfDao().getBookshelvesByBookId(bookId));

        putParcelableArrayList(BKEY_AUTHOR_LIST,
                               serviceLocator.getAuthorDao().getAuthorsByBookId(bookId));

        putParcelableArrayList(BKEY_SERIES_LIST,
                               serviceLocator.getSeriesDao().getSeriesByBookId(bookId));

        putParcelableArrayList(BKEY_PUBLISHER_LIST,
                               serviceLocator.getPublisherDao().getPublishersByBookId(bookId));

        putParcelableArrayList(BKEY_TOC_LIST,
                               serviceLocator.getTocEntryDao().getTocEntryByBookId(bookId));

        // do NOT preload the full Calibre library object. We hardly ever need it as such.
        // see #getCalibreLibrary
    }

    /**
     * Duplicate a book by putting APPLICABLE (not simply all of them) fields
     * in a Bundle ready for further processing.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     *
     * @return bundle with book data
     * <p>
     * <b>Developer:</b> keep in sync with {@link BookDaoImpl} .SqlAllBooks#BOOK
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
        // Do not copy the Bookshelves list
        // ...
        // Do not copy these specific dates.
        // KEY_BOOK_DATE_ADDED
        // KEY_DATE_LAST_UPDATED

        bookData.putString(DBKey.KEY_TITLE, getString(DBKey.KEY_TITLE));
        bookData.putString(DBKey.KEY_ISBN, getString(DBKey.KEY_ISBN));

        bookData.putParcelableArrayList(BKEY_AUTHOR_LIST,
                                        getParcelableArrayList(BKEY_AUTHOR_LIST));
        bookData.putParcelableArrayList(BKEY_SERIES_LIST,
                                        getParcelableArrayList(BKEY_SERIES_LIST));
        bookData.putParcelableArrayList(BKEY_PUBLISHER_LIST,
                                        getParcelableArrayList(BKEY_PUBLISHER_LIST));
        bookData.putParcelableArrayList(BKEY_TOC_LIST,
                                        getParcelableArrayList(BKEY_TOC_LIST));

        // publication data
        bookData.putString(DBKey.KEY_PRINT_RUN, getString(DBKey.KEY_PRINT_RUN));
        bookData.putLong(DBKey.BITMASK_TOC, getLong(DBKey.BITMASK_TOC));
        bookData.putString(DBKey.DATE_BOOK_PUBLICATION, getString(DBKey.DATE_BOOK_PUBLICATION));
        bookData.putDouble(DBKey.PRICE_LISTED, getDouble(DBKey.PRICE_LISTED));
        bookData.putString(DBKey.PRICE_LISTED_CURRENCY, getString(DBKey.PRICE_LISTED_CURRENCY));
        bookData.putString(DBKey.DATE_FIRST_PUBLICATION, getString(DBKey.DATE_FIRST_PUBLICATION));

        bookData.putString(DBKey.KEY_FORMAT, getString(DBKey.KEY_FORMAT));
        bookData.putString(DBKey.KEY_COLOR, getString(DBKey.KEY_COLOR));
        bookData.putString(DBKey.KEY_GENRE, getString(DBKey.KEY_GENRE));
        bookData.putString(DBKey.KEY_LANGUAGE, getString(DBKey.KEY_LANGUAGE));
        bookData.putString(DBKey.KEY_PAGES, getString(DBKey.KEY_PAGES));
        // common blurb
        bookData.putString(DBKey.KEY_DESCRIPTION, getString(DBKey.KEY_DESCRIPTION));

        // partially edition info, partially use-owned info.
        bookData.putLong(DBKey.BITMASK_EDITION, getLong(DBKey.BITMASK_EDITION));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBKey.BOOL_SIGNED, getLong(DBKey.BOOL_SIGNED));

        bookData.putFloat(DBKey.KEY_RATING, getFloat(DBKey.KEY_RATING));
        bookData.putString(DBKey.KEY_PRIVATE_NOTES, getString(DBKey.KEY_PRIVATE_NOTES));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBKey.BOOL_READ, getLong(DBKey.BOOL_READ));
        bookData.putString(DBKey.DATE_READ_START, getString(DBKey.DATE_READ_START));
        bookData.putString(DBKey.DATE_READ_END, getString(DBKey.DATE_READ_END));

        bookData.putString(DBKey.DATE_ACQUIRED, getString(DBKey.DATE_ACQUIRED));
        bookData.putDouble(DBKey.PRICE_PAID, getDouble(DBKey.PRICE_PAID));
        bookData.putString(DBKey.PRICE_PAID_CURRENCY, getString(DBKey.PRICE_PAID_CURRENCY));

        bookData.putInt(DBKey.KEY_BOOK_CONDITION, getInt(DBKey.KEY_BOOK_CONDITION));
        bookData.putInt(DBKey.KEY_BOOK_CONDITION_COVER, getInt(DBKey.KEY_BOOK_CONDITION_COVER));

        return bookData;
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
        return getLong(DBKey.PK_ID);
    }

    /**
     * Set the id.
     *
     * @param id the book id.
     */
    @Override
    public void setId(final long id) {
        putLong(DBKey.PK_ID, id);
    }

    /**
     * Get the unformatted title.
     *
     * @return title
     */
    @Override
    @NonNull
    public String getTitle() {
        return getString(DBKey.KEY_TITLE);
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
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return getAndUpdateLocale(context, userLocale, false);
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
        if (contains(DBKey.KEY_LANGUAGE)) {
            final String lang = getString(DBKey.KEY_LANGUAGE);

            bookLocale = ServiceLocator.getInstance().getAppLocale().getLocale(context, lang);
            if (bookLocale == null) {
                return fallbackLocale;

            } else if (updateLanguage) {
                putString(DBKey.KEY_LANGUAGE, lang);
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
     * Get the first Publisher in the list of Publishers for this book.
     *
     * @return the Publisher, or {@code null} if none present
     */
    @Nullable
    public Publisher getPrimaryPublisher() {
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_LIST);
        return list.isEmpty() ? null : list.get(0);
    }

    @NonNull
    @Override
    public PartialDate getFirstPublicationDate() {
        return new PartialDate(getString(DBKey.DATE_FIRST_PUBLICATION));
    }


    @Nullable
    public CalibreLibrary getCalibreLibrary() {
        // We MIGHT have it (probably not) ...
        if (contains(BKEY_CALIBRE_LIBRARY)) {
            return getParcelable(BKEY_CALIBRE_LIBRARY);

        } else {
            // but if not, go explicitly fetch it.
            final CalibreLibrary library = ServiceLocator
                    .getInstance().getCalibreLibraryDao()
                    .getLibraryById(getLong(DBKey.FK_CALIBRE_LIBRARY));

            if (library != null) {
                // store for reuse
                putParcelable(BKEY_CALIBRE_LIBRARY, library);
            }
            return library;
        }
    }

    public void setCalibreLibrary(@Nullable final CalibreLibrary library) {
        if (library != null) {
            putLong(DBKey.FK_CALIBRE_LIBRARY, library.getId());
            putParcelable(BKEY_CALIBRE_LIBRARY, library);
        } else {
            remove(DBKey.FK_CALIBRE_LIBRARY);
            remove(BKEY_CALIBRE_LIBRARY);

            remove(DBKey.KEY_CALIBRE_BOOK_ID);
            remove(DBKey.KEY_CALIBRE_BOOK_UUID);
            remove(DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT);
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
        if (contains(DBKey.KEY_LOANEE)) {
            return getString(DBKey.KEY_LOANEE);

        } else {
            // but if not, go explicitly fetch it.
            String loanee = ServiceLocator.getInstance().getLoaneeDao().getLoaneeByBookId(getId());
            if (loanee == null) {
                loanee = "";
            }
            // store for reuse
            putString(DBKey.KEY_LOANEE, loanee);
            return loanee;
        }
    }

    public boolean isAvailable() {
        return getLoanee().isEmpty();
    }


    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead() {
        return setRead(!getBoolean(DBKey.BOOL_READ));
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
        final boolean old = getBoolean(DBKey.BOOL_READ);

        if (ServiceLocator.getInstance().getBookDao().setRead(this, isRead)) {
            return isRead;
        }

        return old;
    }

    /**
     * Persist the given cover file.
     * <p>
     * Name format: "{uuid}.jpg" or "{uuid}_{cIdx}".jpg".
     * The index only gets appended to the name if it's > 0.
     * <p>
     * Keep in mind that internally we always use PNG compression (except for the cache).
     * So a jpg named file can be a png encoded file. (But we don't need to care about that.)
     *
     * @param cIdx 0..n image index
     *
     * @return The persisted file
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on any failure
     * @see #getPersistedCoverFile(int)
     */
    public File persistCover(@NonNull final File downloadedFile,
                             @IntRange(from = 0, to = 1) final int cIdx)
            throws CoverStorageException, IOException {

        final String uuid = getString(DBKey.KEY_BOOK_UUID);
        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx + ".jpg";
        } else {
            name = uuid + ".jpg";
        }

        final File destination = new File(CoverDir.getDir(ServiceLocator.getAppContext()), name);
        FileUtils.rename(downloadedFile, destination);
        return destination;
    }


    /**
     * See {@link #getPersistedCoverFile(String, int)}.
     *
     * @param cIdx 0..n image index
     *
     * @return The File object for existing files, or {@code null}
     *
     * @see #persistCover(File, int)
     */
    @Nullable
    public File getPersistedCoverFile(@IntRange(from = 0, to = 1) final int cIdx) {

        final String uuid = getString(DBKey.KEY_BOOK_UUID);
        return getPersistedCoverFile(uuid, cIdx);
    }

    /**
     * Get the <strong>current</strong> cover file for this book.
     * <p>
     * Any {@link CoverStorageException} is <strong>IGNORED</strong>
     *
     * @param cIdx 0..n image index
     *
     * @return a guaranteed existing File, or {@code null}
     */
    @Nullable
    public File getCoverFile(@IntRange(from = 0, to = 1) final int cIdx) {

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
            final String uuid = getString(DBKey.KEY_BOOK_UUID);
            if (!uuid.isEmpty()) {
                final String name;
                if (cIdx > 0) {
                    name = uuid + "_" + cIdx;
                } else {
                    name = uuid;
                }

                final File coverDir;
                try {
                    coverDir = CoverDir.getDir(ServiceLocator.getAppContext());
                } catch (@NonNull final CoverStorageException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "getCoverFile", e);
                    }
                    return null;
                }

                // should be / try jpg first
                coverFile = new File(coverDir, name + ".jpg");
                if (!coverFile.exists()) {
                    // no cover, try for a png
                    coverFile = new File(coverDir, name + ".png");
                    if (!coverFile.exists()) {
                        coverFile = null;
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Logger.d(TAG, "bookId=" + getId()
                          + "|cIdx=" + cIdx
                          + "|file=" + (coverFile == null ? "null" : coverFile.getAbsolutePath()),
                     new Throwable("getCoverFile")
                    );
        }
        return coverFile;
    }

    /**
     * Create a temporary cover file for this book.
     * If there is a permanent cover, we get a <strong>copy of that one</strong>.
     * If there is no cover, we get a new File object with a temporary name.
     *
     * @param cIdx 0..n image index
     *
     * @return the File
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on failure to make a copy of the permanent file
     */
    @NonNull
    public File createTempCoverFile(@IntRange(from = 0, to = 1) final int cIdx)
            throws CoverStorageException, IOException {

        // the temp file we'll return
        // do NOT set BKEY_TMP_FILE_SPEC in this method.
        final File coverFile = new File(CoverDir.getTemp(ServiceLocator.getAppContext()),
                                        System.nanoTime() + ".jpg");

        final File uuidFile = getCoverFile(cIdx);
        if (uuidFile != null && uuidFile.exists()) {
            // We have a permanent file, copy it into the temp location
            FileUtils.copy(uuidFile, coverFile);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Logger.d(TAG, "bookId=" + getId()
                          + "|cIdx=" + cIdx
                          + "|exists=" + coverFile.exists()
                          + "|file=" + coverFile.getAbsolutePath(),
                     new Throwable("createTempCoverFile")
                    );
        }
        return coverFile;
    }


    public void removeCover(@IntRange(from = 0, to = 1) final int cIdx) {
        try {
            setCover(cIdx, null);
        } catch (@NonNull final IOException | CoverStorageException ignore) {
            // safe to ignore, can't happen with a 'null' input.
        }
    }

    /**
     * Update the book cover with the given file.
     *
     * @param cIdx 0..n image index
     * @param file cover file or {@code null} to delete the cover
     *
     * @return the File after processing (either original, or a renamed/moved file)
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public File setCover(@IntRange(from = 0, to = 1) final int cIdx,
                         @Nullable final File file)
            throws CoverStorageException, IOException {

        if (mStage.getStage() == EntityStage.Stage.WriteAble
            || mStage.getStage() == EntityStage.Stage.Dirty) {
            // We're editing, use BKEY_TMP_FILE_SPEC storage.

            if (file != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "editing"
                                  + "|bookId=" + getId()
                                  + "|cIdx=" + cIdx
                                  + "|file=" + file.getAbsolutePath(), new Throwable("setCover")
                            );
                }
                // #storeCovers will do the actual storing
                putString(BKEY_TMP_FILE_SPEC[cIdx], file.getAbsolutePath());

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "editing"
                                  + "|bookId=" + getId()
                                  + "|cIdx=" + cIdx
                                  + "|deleting", new Throwable("setCover")
                            );
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
            final String uuid = getString(DBKey.KEY_BOOK_UUID);
            SanityCheck.requireValue(uuid, "uuid");

            // the file to return from this method, after the incoming file has been processed
            @Nullable
            File destination = file;

            if (file != null) {
                if (file.getName().startsWith(uuid)) {
                    // No further action needed as we have the cover "in-place"
                    // ... not actually sure when this would be the case; keep an eye on logs
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Logger.d(TAG, "readOnly"
                                      + "|bookId=" + getId()
                                      + "|cIdx=" + cIdx
                                      + "|uuid, in-place", new Throwable("setCover")
                                );
                    }
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Logger.d(TAG, "readOnly"
                                      + "|bookId=" + getId()
                                      + "|cIdx=" + cIdx
                                      + "|will rename="
                                      + file.getAbsolutePath(), new Throwable("setCover")
                                );
                    }

                    // Rename the temp file to the uuid permanent file name
                    destination = persistCover(file, cIdx);
                }
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, "readOnly"
                                  + "|bookId=" + getId()
                                  + "|cIdx=" + cIdx
                                  + "|deleting", new Throwable("setCover")
                            );
                }

                FileUtils.delete(getPersistedCoverFile(cIdx));
                if (ImageUtils.isImageCachingEnabled()) {
                    // We delete *all* files related to this book from the cache.
                    ServiceLocator.getInstance().getCoverCacheDao().delete(uuid);
                }
            }

            ServiceLocator.getInstance().getBookDao().touch(this);

            return destination;
        }
    }


    /**
     * Add validators.
     */
    public void addValidators() {

        addValidator(DBKey.KEY_TITLE,
                     NON_BLANK_VALIDATOR, R.string.lbl_title);
        addValidator(BKEY_AUTHOR_LIST,
                     NON_BLANK_VALIDATOR, R.string.lbl_author);

        addValidator(DBKey.KEY_LANGUAGE,
                     NON_BLANK_VALIDATOR, R.string.lbl_language);

        addValidator(DBKey.BITMASK_EDITION,
                     LONG_VALIDATOR, R.string.lbl_edition);
        addValidator(DBKey.BITMASK_TOC,
                     LONG_VALIDATOR, R.string.lbl_table_of_content);

        addValidator(DBKey.PRICE_LISTED,
                     PRICE_VALIDATOR, R.string.lbl_price_listed);
        addValidator(DBKey.PRICE_PAID,
                     PRICE_VALIDATOR, R.string.lbl_price_paid);

        addCrossValidator((context, book) -> {
            final String start = book.getString(DBKey.DATE_READ_START);
            if (start.isEmpty()) {
                return;
            }
            final String end = book.getString(DBKey.DATE_READ_END);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(context.getString(R.string.vldt_read_start_after_end));
            }
        });
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
        if (getString(DBKey.KEY_LANGUAGE).isEmpty()) {
            putString(DBKey.KEY_LANGUAGE, context.getResources().getConfiguration()
                                                 .getLocales().get(0)
                                                 .getISO3Language());
        }
    }


    @NonNull
    public EntityStage.Stage getStage() {
        return mStage.getStage();
    }

    public void setStage(@NonNull final EntityStage.Stage stage) {
        mStage.setStage(stage);
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
        final ArrayList<Author> authors = getParcelableArrayList(BKEY_AUTHOR_LIST);
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

    /**
     * Update author details from DB.
     *
     * @param context Current context
     */
    public void refreshAuthorList(@NonNull final Context context) {

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
        final Locale bookLocale = getLocale(context);
        final ArrayList<Author> list = getParcelableArrayList(BKEY_AUTHOR_LIST);
        for (final Author author : list) {
            authorDao.refresh(context, author, bookLocale);
        }
    }

    public void pruneSeries(@NonNull final Context context,
                            final boolean lookupLocale) {
        final ArrayList<Series> series = getParcelableArrayList(BKEY_SERIES_LIST);
        if (!series.isEmpty()) {
            if (Series.pruneList(series, context, lookupLocale, getLocale(context))) {
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }
    }

    /**
     * Update Series details from DB.
     *
     * @param context Current context
     */
    public void refreshSeriesList(@NonNull final Context context) {

        final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
        final Locale bookLocale = getLocale(context);
        final ArrayList<Series> list = getParcelableArrayList(BKEY_SERIES_LIST);
        for (final Series series : list) {
            seriesDao.refresh(context, series, bookLocale);
        }
    }

    public void prunePublishers(@NonNull final Context context,
                                final boolean lookupLocale) {
        final ArrayList<Publisher> publishers = getParcelableArrayList(BKEY_PUBLISHER_LIST);
        if (!publishers.isEmpty()) {
            if (Publisher.pruneList(publishers, context, lookupLocale, getLocale(context))) {
                mStage.setStage(EntityStage.Stage.Dirty);
            }
        }
    }

    /**
     * Update Publisher details from DB.
     *
     * @param context Current context
     */
    public void refreshPublishersList(@NonNull final Context context) {

        final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
        final Locale bookLocale = getLocale(context);
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_LIST);
        for (final Publisher publisher : list) {
            publisherDao.refresh(context, publisher, bookLocale);
        }
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
        final String title = getString(DBKey.KEY_TITLE);

        final Author author = getPrimaryAuthor();
        final String authorStr = author != null ? author.getFormattedName(true)
                                                : context.getString(R.string.unknown_author);

        final Series series = getPrimarySeries();
        final String seriesStr = series != null ? " (" + series.getLabel(context) + ')' : "";

        //remove trailing 0's
        final float rating = getFloat(DBKey.KEY_RATING);
        final String ratingStr;
        if (rating > 0) {
            // force rounding down
            final int ratingTmp = (int) rating;
            // get fraction
            final float decimal = rating - ratingTmp;
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
//        File coverFile = CoverDir.getCoverFile(context, uuid, 0);
//        if (coverFile.exists()) {
//            Uri uri = GenericFileProvider.getUriForFile(context, coverFile);
//        }

        final String text = context.getString(R.string.txt_share_book_im_reading,
                                              title, seriesStr, authorStr, ratingStr);

        return Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, text),
                                    context.getString(R.string.whichSendApplication));
    }

    @Override
    public char getType() {
        return AuthorWork.TYPE_BOOK;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {TOC_MULTIPLE_WORKS, TOC_MULTIPLE_AUTHORS})
    public @interface TocBits {

    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CONDITION_UNKNOWN, CONDITION_AS_NEW})
    public @interface Condition {

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

        private Edition() {
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
