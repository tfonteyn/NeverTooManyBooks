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
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.covers.Cover;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.ValidatorConfig;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.BlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DataValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.DoubleValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.LongValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.NonBlankValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.OrValidator;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

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
        extends BookData
        implements AuthorWork {

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

    /**
     * Bundle key for {@code CalibreLibrary (Parcelable)}.
     * <strong>No prefix, NEVER change this string as it's used in export/import.</strong>
     */
    public static final String BKEY_CALIBRE_LIBRARY = "calibre_library";
    /** re-usable validator. */
    private static final DataValidator PRICE_VALIDATOR = new OrValidator(
            new BlankValidator(),
            new DoubleValidator());
    /** re-usable validator. */
    private static final DataValidator LONG_VALIDATOR = new LongValidator();
    /** re-usable validator. */
    private static final DataValidator NON_BLANK_VALIDATOR = new NonBlankValidator();

    /** Log tag. */
    private static final String TAG = "Book";

    /**
     * Single front/back cover file specs for handling a temporary cover during edit.
     * <p>
     * <br>type: {@code String}
     */
    public static final String[] BKEY_TMP_FILE_SPEC = {
            TAG + ":fileSpec:0",
            TAG + ":fileSpec:1"};

    /**
     * Bundle key for an {@code ArrayList<Long>} of book ID's.
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils#wrap(List)
     * @see com.hardbacknutter.nevertoomanybooks.utils.ParcelUtils#unwrap(Bundle, String)
     */
    public static final String BKEY_BOOK_ID_LIST = TAG + ":id_list";

    /** the stage of the book entity. */
    private final EntityStage stage = new EntityStage();

    /** Validator and validator results. */
    @NonNull
    private final ValidatorConfig validatorConfig;

    /**
     * Constructor.
     */
    public Book() {
        validatorConfig = new ValidatorConfig();
    }

    /**
     * Constructor for tests. Loads the data <strong>without</strong> type checks.
     * As this is for testing, the stage will not be set.
     *
     * @param bookData data bundle to use for the Book
     */
    @VisibleForTesting
    public Book(@NonNull final DataManager bookData) {
        super(bookData);
        validatorConfig = new ValidatorConfig();
    }

    public Book(@NonNull final Parcel in) {
        throw new UnsupportedOperationException("A book is not parcelable");
    }

    /**
     * Constructor. Load the book details from the database.
     *
     * @param bookId of book
     *
     * @return new instance
     */
    @NonNull
    public static Book from(@IntRange(from = 1) final long bookId) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

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
     * Constructor. Loads the bundle <strong>with</strong> type checks.
     *
     * @param bookData data bundle to use for the Book
     *
     * @return new instance; flagged as {@link EntityStage.Stage#Dirty}
     */
    @NonNull
    public static Book from(@NonNull final DataManager bookData) {
        final Book book = new Book();
        book.putAll(bookData);
        // has unsaved data, hence 'Dirty'
        book.setStage(EntityStage.Stage.Dirty);
        return book;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        throw new UnsupportedOperationException("A book is not parcelable");
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

        setBookshelves(serviceLocator.getBookshelfDao().getByBookId(bookId));
        setAuthors(serviceLocator.getAuthorDao().getByBookId(bookId));
        setSeries(serviceLocator.getSeriesDao().getByBookId(bookId));
        setPublishers(serviceLocator.getPublisherDao().getByBookId(bookId));
        setToc(serviceLocator.getTocEntryDao().getByBookId(bookId));

        // do NOT preload the full Calibre library object. We hardly ever need it as such.
        // see #getCalibreLibrary
    }

    @NonNull
    public List<BookLight> getBookTitles(@NonNull final Context context) {
        return Collections.singletonList(new BookLight(this));
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return new PartialDate(getString(DBKey.FIRST_PUBLICATION__DATE));
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

            remove(DBKey.CALIBRE_BOOK_ID);
            remove(DBKey.CALIBRE_BOOK_UUID);
            remove(DBKey.CALIBRE_BOOK_MAIN_FORMAT);
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
            loanee = ServiceLocator.getInstance().getLoaneeDao().getLoaneeByBookId(getId());
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
            File coverFile = null;
            if (!fileSpec.isEmpty()) {
                coverFile = new File(fileSpec);
                if (!coverFile.exists()) {
                    coverFile = null;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Logger.d(TAG, new Throwable("getCoverFile"),
                         "bookId=" + getId()
                         + "|cIdx=" + cIdx
                         + "|file=" + (coverFile == null ? "null" : coverFile.getAbsolutePath())
                );
            }
            if (coverFile != null && coverFile.exists()) {
                return Optional.of(coverFile);
            }
        } else {
            // Get the permanent, UUID based, cover file for this book.
            final String uuid = getString(DBKey.BOOK_UUID, null);
            if (uuid != null && !uuid.isEmpty()) {
                return new Cover(uuid, cIdx).getPersistedFile();
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
     * Depending on the {@link #getStage()} this method sets a temporary cover,
     * or persists the cover to storage.
     *
     * @param cIdx 0..n image index
     * @param file cover file or {@code null} to delete the cover
     *
     * @return the File after processing (either original, or a renamed/moved file)
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on generic/other IO failures
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public File setCover(@IntRange(from = 0, to = 1) final int cIdx,
                         @Nullable final File file)
            throws StorageException, IOException {

        if (stage.getStage() == EntityStage.Stage.WriteAble
            || stage.getStage() == EntityStage.Stage.Dirty) {
            // We're editing, use BKEY_TMP_FILE_SPEC storage.

            if (file != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, new Throwable("setCover"),
                             "editing"
                             + "|bookId=" + getId()
                             + "|cIdx=" + cIdx
                             + "|file=" + file.getAbsolutePath()
                    );
                }
                // #storeCovers will do the actual storing
                putString(BKEY_TMP_FILE_SPEC[cIdx], file.getAbsolutePath());

            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                    Logger.d(TAG, new Throwable("setCover"),
                             "editing"
                             + "|bookId=" + getId()
                             + "|cIdx=" + cIdx
                             + "|deleting"
                    );
                }
                // explicitly set to "" to let #storeCovers do the delete
                putString(BKEY_TMP_FILE_SPEC[cIdx], "");
            }

            // switch from WriteAble to Dirty (or from Dirty to Dirty)
            stage.setStage(EntityStage.Stage.Dirty);

            // just return the incoming file, it has not been changed or renamed
            return file;

        } else {
            // we're in read-only mode, use the UUID storage based file name
            final String uuid = getString(DBKey.BOOK_UUID);
            SanityCheck.requireValue(uuid, "uuid");

            // the file to return from this method, after the incoming file has been processed
            @Nullable
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
                                 + "|uuid, in-place"
                        );
                    }
                } else {
                    // Rename the temp file to the uuid permanent file name
                    destination = new Cover(uuid, cIdx).persist(file);
                }
            } else {
                new Cover(uuid, cIdx).delete();
            }

            ServiceLocator.getInstance().getBookDao().touch(this);

            return destination;
        }
    }


    /**
     * Add validators.
     */
    public void addValidators() {

        validatorConfig.addValidator(DBKey.TITLE,
                                     NON_BLANK_VALIDATOR, R.string.lbl_title);
        validatorConfig.addValidator(BKEY_AUTHOR_LIST,
                                     NON_BLANK_VALIDATOR, R.string.lbl_author);

        validatorConfig.addValidator(DBKey.LANGUAGE,
                                     NON_BLANK_VALIDATOR, R.string.lbl_language);

        validatorConfig.addValidator(DBKey.EDITION__BITMASK,
                                     LONG_VALIDATOR, R.string.lbl_edition);
        validatorConfig.addValidator(DBKey.TOC_TYPE__BITMASK,
                                     LONG_VALIDATOR, R.string.lbl_table_of_content);

        validatorConfig.addValidator(DBKey.PRICE_LISTED,
                                     PRICE_VALIDATOR, R.string.lbl_price_listed);
        validatorConfig.addValidator(DBKey.PRICE_PAID,
                                     PRICE_VALIDATOR, R.string.lbl_price_paid);

        validatorConfig.addCrossValidator((context, book) -> {
            final String start = book.getString(DBKey.READ_START__DATE);
            if (start.isEmpty()) {
                return;
            }
            final String end = book.getString(DBKey.READ_END__DATE);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(context.getString(R.string.vldt_read_start_after_end));
            }
        });
    }

    public boolean validate(@NonNull final Context context) {
        return validatorConfig.validate(context, this);
    }

    @Nullable
    public String getValidationExceptionMessage(@NonNull final Context context) {
        return validatorConfig.getValidationExceptionMessage(context);
    }


    @NonNull
    public EntityStage.Stage getStage() {
        return stage.getStage();
    }

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


    public void pruneAuthors(@NonNull final Context context,
                             final boolean lookupLocale) {
        final ArrayList<Author> authors = getAuthors();
        if (!authors.isEmpty()) {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            if (authorDao.pruneList(context, authors, lookupLocale, getLocale(context))) {
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

    public void pruneSeries(@NonNull final Context context,
                            final boolean lookupLocale) {
        if (contains(BKEY_SERIES_LIST)) {
            final ArrayList<Series> series = getSeries();
            if (!series.isEmpty()) {
                final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
                if (seriesDao.pruneList(context, series, lookupLocale, getLocale(context))) {
                    stage.setStage(EntityStage.Stage.Dirty);
                }
            }
        }
    }

    public void prunePublishers(@NonNull final Context context,
                                final boolean lookupLocale) {
        if (contains(BKEY_PUBLISHER_LIST)) {
            final ArrayList<Publisher> publishers = getPublishers();
            if (!publishers.isEmpty()) {
                final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
                if (publisherDao.pruneList(context, publishers, lookupLocale, getLocale(context))) {
                    stage.setStage(EntityStage.Stage.Dirty);
                }
            }
        }
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
        final String authorStr = author != null ? author.getFormattedName(context, style)
                                                : context.getString(R.string.unknown_author);

        final String seriesStr = getPrimarySeries()
                .map(value -> context.getString(R.string.brackets, value.getLabel(context)))
                .orElse("");

        //remove trailing 0's
        final float rating = getFloat(DBKey.RATING);
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

    @Override
    @NonNull
    public Type getWorkType() {
        return AuthorWork.Type.Book;
    }
}
