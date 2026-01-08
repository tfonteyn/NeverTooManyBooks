/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Discouraged;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.citations.Citation;
import com.hardbacknutter.nevertoomanybooks.citations.CitationFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.ParcelUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.ImageOwner;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
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
import com.hardbacknutter.nevertoomanybooks.localsearch.LocalSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreBookData;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.provider.GenericFileProvider;
import com.hardbacknutter.util.logger.LoggerFactory;

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
 * A Spanish book (translated from English) should return a Spanish Locale.
 * i.e. a translated book, should return its translation Locale.
 * <p>
 * A Series should return the Locale as set by the user for that Series (not implemented yet).
 * If not set, then the Locale of the first book in the series.
 * Edge-case: books original in English, user has a first book in Spanish, second book in English
 * -> the Series is wrongly designated as Spanish. Solution; user manually sets the Series Locale.
 * <p>
 * An Author should return the Locale as set by the user for that Author (not implemented yet),
 * This should normally be the primary language the author writes in.
 * i.e. usually the author's native language, but some authors will e.g. use english/French...
 * to reach a larger market without translation needs.
 * If not set, then the Locale of the first book (oldest copyright? oldest 'added'?) of that author.
 * <p>
 * A TocEntry...
 */
public class Book
        extends DataManager
        implements AuthorWork, Entity, IdentifierOwner, ImageOwner {

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
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_AUTHOR_LIST = "author_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Series>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_SERIES_LIST = "series_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Publisher>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_PUBLISHER_LIST = "publisher_list";
    /**
     * Bundle key for {@code ParcelableArrayList<TocEntry>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_TOC_LIST = "toc_list";
    /**
     * Bundle key for {@code ParcelableArrayList<Bookshelf>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_BOOKSHELF_LIST = "bookshelf_list";

    /**
     * Bundle key for {@code ArrayList<Tag>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_TAG_LIST = "tag_list";

    /**
     * Bundle key for {@code ArrayList<Identifier.Value>}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_IDENTIFIER_LIST = "identifier_list";

    /**
     * Bundle key for {@code CalibreLibrary (Parcelable)}.
     * <strong>Used in export/import, NEVER change the string</strong>
     */
    public static final String BKEY_CALIBRE_LIBRARY = "calibre_library";

    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;

    /**
     * A book (and dustcover) condition goes from 1(worst) to 5(best) or 0 for not-set.
     * In code, we only need 5(best) which is used as default when adding a new book.
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
     * File specs for handling temporary images during edit.
     * <p>
     * <br>type: {@code String}
     */
    public static final String[] BKEY_TMP_FILE_SPEC = {
            TAG + ":fileSpec:0",
            TAG + ":fileSpec:1",
            TAG + ":fileSpec:2",
            TAG + ":fileSpec:3"
    };
    private static final String ERROR_INVALID_BOOK_ID = "bookId";

    /** the stage of the book entity. */
    private final EntityStage stage;
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();
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
    private Book(@NonNull final Parcel in) {
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
     * @param data             book to copy all data from
     * @param realNumberParser to use for number parsing
     *
     * @return new instance; flagged as {@link EntityStage.Stage#Dirty}
     */
    @NonNull
    public static Book from(@NonNull final Book data,
                            @NonNull final RealNumberParser realNumberParser) {
        final Book book = new Book();
        book.putAll(data, realNumberParser);

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
        setIdentifiers(serviceLocator.getBookIdentifierDao().getByFkId(bookId));
        setTags(serviceLocator.getTagDao().getByBookId(bookId));

        // do NOT preload the full Calibre library object. We hardly ever need it as such.
        // see #getCalibreLibrary
    }

    /**
     * Duplicate a book by copying APPLICABLE (not simply all of them) fields.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     * See {@link #from(Book, RealNumberParser)} for the latter.
     * <p>
     * The intended (only?) use for this method is the user choosing to
     * duplicate an existing book and manually edit the copy.
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
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final RealNumberParser realNumberParser = new RealNumberParser(allLocales);

        // Q: Why don't we get the DataManager#mRawData, remove the identifiers/dates and use that?
        // A: because we would need to clone mRawData before we can start removing fields,
        //  From Bundle#clone() docs: Clones the current Bundle.
        //  The internal map is cloned, but the keys and values to which it refers are
        //  copied by reference.
        // ==> by reference...  so we would in effect be removing fields from the original book.
        // This would be OK if we discard the original object (in memory only)
        // but let's play this safe.

        // Do not copy any identifiers:
        // PK_ID
        // BOOK_UUID
        // Identifier.*
        // sync related fields (Calibre, StripInfo,...)
        // ...
        // Do not copy these specific dates.
        // BOOK_DATE_ADDED
        // DATE_LAST_UPDATED
        //
        // //NEWTHINGS: new fields

        duplicate.setTitle(getString(DBKey.TITLE, null));
        duplicate.setIsbn(getString(DBKey.ISBN, null));

        if (duplicate.contains(BKEY_BOOKSHELF_LIST)) {
            duplicate.setBookshelves(getBookshelves());
        }
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
        if (duplicate.contains(BKEY_TAG_LIST)) {
            duplicate.setTags(getTags());
        }

        duplicate.setTranslatedFromTitle(getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        duplicate.setTranslatedFromLanguage(getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        // publication data
        duplicate.setPrintRun(getString(DBKey.PRINT_RUN, null));
        duplicate.setContentType(getContentType());
        duplicate.setPublicationDate(getString(DBKey.PUBLICATION_DATE, null));
        duplicate.putDouble(DBKey.PRICE_LISTED, getDouble(DBKey.PRICE_LISTED, realNumberParser));
        duplicate.putString(DBKey.PRICE_LISTED_CURRENCY, getString(DBKey.PRICE_LISTED_CURRENCY));
        duplicate.setFirstPublicationDate(getString(DBKey.FIRST_PUBLICATION_DATE, null));

        duplicate.setFormat(getString(DBKey.FORMAT, null));
        duplicate.setColor(getString(DBKey.COLOR, null));
        duplicate.setLanguage(getString(DBKey.LANGUAGE, null));
        duplicate.setPages(getString(DBKey.PAGES, null));
        // common blurb
        duplicate.setDescription(getString(DBKey.DESCRIPTION, null));

        // partially edition info, partially use-owned info.
        duplicate.setEdition(getEdition());

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        duplicate.putLong(DBKey.SIGNED__BOOL, getLong(DBKey.SIGNED__BOOL));

        duplicate.setRating(getFloat(DBKey.RATING, realNumberParser));
        duplicate.putString(DBKey.PERSONAL_NOTES, getString(DBKey.PERSONAL_NOTES));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        duplicate.putLong(DBKey.READ__BOOL, getLong(DBKey.READ__BOOL));
        duplicate.putString(DBKey.READ_PROGRESS, getString(DBKey.READ_PROGRESS));
        duplicate.putString(DBKey.READ_START__DATE, getString(DBKey.READ_START__DATE));
        duplicate.putString(DBKey.READ_END__DATE, getString(DBKey.READ_END__DATE));

        duplicate.putString(DBKey.DATE_ACQUIRED, getString(DBKey.DATE_ACQUIRED));
        duplicate.putDouble(DBKey.PRICE_PAID, getDouble(DBKey.PRICE_PAID, realNumberParser));
        duplicate.putString(DBKey.PRICE_PAID_CURRENCY, getString(DBKey.PRICE_PAID_CURRENCY));

        duplicate.putInt(DBKey.CONDITION_BOOK, getInt(DBKey.CONDITION_BOOK));
        duplicate.putInt(DBKey.CONDITION_COVER, getInt(DBKey.CONDITION_COVER));

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
     * Get the {@code UUID}.
     * <p>
     * <strong>IMPORTANT</strong> (but not really...)
     * Yes, despite we refer to this as a UUID, this is NOT a real UUID.
     * It's just an SQLite randomly generated 16 byte hex string.
     * <p>
     * Formatted as a <strong>16 character hex string</strong>, i.e. there are NO '-' separators.
     *
     * @return the uuid; can be empty but never {@code null}
     */
    @NonNull
    public String getUuid() {
        return getString(DBKey.BOOK_UUID);
    }

    @NonNull
    @Override
    public Optional<String> getImageUuid() {
        final String uuid = getUuid();
        return uuid.isEmpty() ? Optional.empty() : Optional.of(uuid);
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

    /**
     * Set the title.
     *
     * @param title to set; a {@code null} or an empty string will remove the field
     */
    public void setTitle(@Nullable final String title) {
        if (title != null && !title.isBlank()) {
            putString(DBKey.TITLE, title);
        } else {
            remove(DBKey.TITLE);
        }
    }

    @NonNull
    public List<BookLite> getBookTitles(@NonNull final Context context) {
        return Collections.singletonList(new BookLite(this));
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @NonNull final Style style) {

        if (style.isShowReorderedTitle()) {
            final List<Locale> userLocales = LocaleListUtils.asList(
                    context.getResources().getConfiguration().getLocales());

            final Locale bookLocale = getLocale(userLocales.get(0)).orElse(userLocales.get(0));
            return new ReorderHelper(userLocales).reorder(context, getTitle(), bookLocale);
        } else {
            return getTitle();
        }
    }

    /**
     * Get the publication-date for this book.
     *
     * @return date; can be {@link PartialDate#NOT_SET}
     */
    @NonNull
    public PartialDate getPublicationDate() {
        return partialDateParser.parse(getString(DBKey.PUBLICATION_DATE))
                                .orElse(PartialDate.NOT_SET);
    }

    /**
     * Set or remove the publication-date for this book.
     *
     * @param date to set; {@code null} to remove
     */
    public void setPublicationDate(@Nullable final LocalDateTime date) {
        if (date != null) {
            putString(DBKey.PUBLICATION_DATE, date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            remove(DBKey.PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the publication-date for this book.
     *
     * @param date to set; {@code null} to remove
     */
    public void setPublicationDate(@Nullable final PartialDate date) {
        if (date != null) {
            putString(DBKey.PUBLICATION_DATE, date.getIsoString());
        } else {
            remove(DBKey.PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the publication-date for this book.
     *
     * @param year to set; {@code 0} to remove
     */
    public void setPublicationDate(@IntRange(from = 0) final int year) {
        if (year > 0) {
            putString(DBKey.PUBLICATION_DATE, String.valueOf(year));
        } else {
            remove(DBKey.PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the publication-date for this book.
     * <p>
     * <strong>IMPORTANT:</strong> the format <strong>MUST</strong>
     * be a full or partial ISO date string. <strong>NO CHECKS ARE DONE</strong>.
     *
     * @param dateStr to set; {@code 0} to remove
     *
     * @see #setPublicationDate(int)
     * @see #setPublicationDate(PartialDate)
     * @see #setPublicationDate(LocalDateTime)
     */
    @Discouraged(message = "Whenever possible, use one of the other setPublicationDate(...)")
    public void setPublicationDate(@Nullable final String dateStr) {
        if (dateStr != null && !dateStr.isBlank()) {
            putString(DBKey.PUBLICATION_DATE, dateStr);
        } else {
            remove(DBKey.PUBLICATION_DATE);
        }
    }

    @Override
    @NonNull
    public PartialDate getFirstPublicationDate() {
        return partialDateParser.parse(getString(DBKey.FIRST_PUBLICATION_DATE))
                                .orElse(PartialDate.NOT_SET);
    }

    /**
     * Set or remove the first-publication-date for this work.
     *
     * @param year to set; {@code 0} to remove
     */
    public void setFirstPublicationDate(@IntRange(from = 0) final int year) {
        if (year > 0) {
            putString(DBKey.FIRST_PUBLICATION_DATE, String.valueOf(year));
        } else {
            remove(DBKey.FIRST_PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the first-publication-date for this work.
     *
     * @param date to set; {@code null} to remove
     */
    public void setFirstPublicationDate(@Nullable final LocalDateTime date) {
        if (date != null) {
            putString(DBKey.FIRST_PUBLICATION_DATE,
                      date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            remove(DBKey.FIRST_PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the first-publication-date for this work.
     *
     * @param date to set; a {@code null} or a 'not-present' date will remove the field
     */
    public void setFirstPublicationDate(@Nullable final PartialDate date) {
        if (date != null && date.isPresent()) {
            putString(DBKey.FIRST_PUBLICATION_DATE, date.getIsoString());
        } else {
            remove(DBKey.FIRST_PUBLICATION_DATE);
        }
    }

    /**
     * Set or remove the first-publication-date for this book.
     * <p>
     * <strong>IMPORTANT:</strong> the format <strong>MUST</strong>
     * be a full or partial ISO date string. <strong>NO CHECKS ARE DONE</strong>.
     *
     * @param dateStr to set; {@code 0} to remove
     *
     * @see #setFirstPublicationDate(int)
     * @see #setFirstPublicationDate(PartialDate)
     * @see #setFirstPublicationDate(LocalDateTime)
     */
    @Discouraged(message = "Whenever possible, use one of the other setFirstPublicationDate(...)")
    public void setFirstPublicationDate(@Nullable final String dateStr) {
        if (dateStr != null && !dateStr.isBlank()) {
            putString(DBKey.FIRST_PUBLICATION_DATE, dateStr);
        } else {
            remove(DBKey.FIRST_PUBLICATION_DATE);
        }
    }

    /**
     * Check if this Book contains a non-blank ISBN string. Does not check if the ISBN is valid.
     *
     * @return {@code true} if present
     */
    public boolean hasIsbn() {
        final String isbnStr = getString(DBKey.ISBN, null);
        return isbnStr != null && !isbnStr.isEmpty();
    }

    /**
     * Get the ISBN as a raw {@code String}.
     *
     * @return isbn; can be empty but never {@code null}
     */
    @NonNull
    public String getIsbn() {
        return getString(DBKey.ISBN);
    }

    /**
     * Set the ISBN with a raw {@code String}.
     *
     * @param isbnStr to set; a {@code null} or an empty string will remove the field
     */
    public void setIsbn(@Nullable final String isbnStr) {
        if (isbnStr != null && !isbnStr.isEmpty()) {
            putString(DBKey.ISBN, isbnStr);
        } else {
            remove(DBKey.ISBN);
        }
    }

    /**
     * Get the description.
     *
     * @return the description; can be empty but never {@code null}
     */
    @NonNull
    public String getDescription() {
        return getString(DBKey.DESCRIPTION);
    }

    /**
     * Set the description.
     *
     * @param description to set; a {@code null} or an empty string will remove the field
     */
    public void setDescription(@Nullable final String description) {
        if (description != null && !description.isBlank()) {
            putString(DBKey.DESCRIPTION, description);
        } else {
            remove(DBKey.DESCRIPTION);
        }
    }

    /**
     * Set the colour.
     *
     * @param color to set; a {@code null} or an empty string will remove the field
     */
    public void setColor(@Nullable final String color) {
        if (color != null && !color.isBlank()) {
            putString(DBKey.COLOR, color);
        } else {
            remove(DBKey.COLOR);
        }
    }

    /**
     * Set the format.
     *
     * @param format to set; a {@code null} or an empty string will remove the field
     */
    public void setFormat(@Nullable final String format) {
        if (format != null && !format.isBlank()) {
            putString(DBKey.FORMAT, format);
        } else {
            remove(DBKey.FORMAT);
        }
    }

    /**
     * Set the number of pages.
     *
     * @param pages to set; a {@code 0} or negative number will remove the field
     */
    public void setPages(final int pages) {
        setPages(pages > 0 ? String.valueOf(pages) : null);
    }

    /**
     * Set the number of pages / pages description.
     *
     * @param pages to set; a {@code null} or an empty string will remove the field
     */
    public void setPages(@Nullable final String pages) {
        if (pages != null && !pages.isBlank()) {
            putString(DBKey.PAGES, pages);
        } else {
            remove(DBKey.PAGES);
        }
    }

    /**
     * Set the print-run description.
     *
     * @param printRun to set; a {@code null} or an empty string will remove the field
     */
    public void setPrintRun(@Nullable final String printRun) {
        if (printRun != null && !printRun.isBlank()) {
            putString(DBKey.PRINT_RUN, printRun);
        } else {
            remove(DBKey.PRINT_RUN);
        }
    }

    /**
     * Get the language.
     *
     * @return display-name or iso3 code; can be empty but never {@code null}
     */
    @NonNull
    public String getLanguage() {
        return getString(DBKey.LANGUAGE);
    }

    /**
     * Set the language.
     * Ideally an iso3 code, but iso2, "display" names,
     * or unofficial languages are accepted.
     *
     * @param language to set; a {@code null} or an empty string will remove the field
     */
    public void setLanguage(@Nullable final String language) {
        if (language != null && !language.isBlank()) {
            putString(DBKey.LANGUAGE, language);
        } else {
            remove(DBKey.LANGUAGE);
        }
    }

    /**
     * Convenience method which returns the book locale without updating the language field.
     *
     * @param userLocale Current Locale
     *
     * @return the Locale
     *
     * @see #getLocaleAndUpdateLanguage(Locale, boolean)
     */
    @NonNull
    public Optional<Locale> getLocale(@NonNull final Locale userLocale) {
        return getLocaleAndUpdateLanguage(userLocale, false);
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param userLocale     Current Locale
     * @param updateLanguage {@code true} to force update the language field with the ISO code
     *                       {@code false} to leave it unchanged.
     *
     * @return the Locale.
     */
    @NonNull
    public Optional<Locale> getLocaleAndUpdateLanguage(@NonNull final Locale userLocale,
                                                       final boolean updateLanguage) {
        final String lang = getString(DBKey.LANGUAGE, null);
        if (lang == null || lang.isBlank()) {
            return Optional.empty();
        }

        final Optional<Locale> bookLocale = ServiceLocator.getInstance().getAppLocale()
                                                          .getLocale(lang, userLocale);
        if (bookLocale.isPresent() && updateLanguage) {
            putString(DBKey.LANGUAGE, bookLocale.get().getISO3Language());
        }

        return bookLocale;
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
    @Override
    @Nullable
    public Author getPrimaryAuthor() {
        final List<Author> authors = getAuthors();
        return authors.isEmpty() ? null : authors.get(0);
    }

    @Override
    @NonNull
    public List<Author> getAuthors() {
        return getParcelableArrayList(BKEY_AUTHOR_LIST);
    }

    /**
     * Set/replace the list of {@link Author}s.
     *
     * @param authors list
     */
    public void setAuthors(@NonNull final Collection<Author> authors) {
        putParcelableCollection(BKEY_AUTHOR_LIST, authors);
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
     * Update all {@link Bookshelf} details from/with the database.
     *
     * @param context Current context
     */
    public void refreshBookshelves(@NonNull final Context context) {
        if (contains(BKEY_BOOKSHELF_LIST)) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            // Bookshelves always use the users preferred Locale
            getBookshelves().forEach(bookshelf -> bookshelfDao
                    .refresh(context, bookshelf, locale));
        }
    }

    /**
     * Update all {@link Author} details from/with the database.
     * <p>
     * Uses the Book or when not available, the user {@link Locale}.
     *
     * @param context Current context
     */
    public void refreshAuthors(@NonNull final Context context) {
        if (contains(BKEY_AUTHOR_LIST)) {
            final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Author's always use the book Locale
            getAuthors().forEach(author -> authorDao.refresh(context, author, bookLocale));
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
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Author's always use the book Locale
            if (authorDao.pruneList(context, authors, author -> bookLocale)) {
                stage.setStage(EntityStage.Stage.Dirty);
            }
        }

        // None present ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        if (authors.isEmpty()) {
            final String searchText = getString(LocalSearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
            if (!searchText.isEmpty()) {
                authors.add(Author.from(searchText));
                remove(LocalSearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
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
     * Set/replace the list of {@link Series}s.
     *
     * @param series list
     */
    public void setSeries(@NonNull final Collection<Series> series) {
        putParcelableCollection(BKEY_SERIES_LIST, series);
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
     * Update all {@link Series} details from/with the database.
     *
     * @param context Current context
     */
    public void refreshSeries(@NonNull final Context context) {
        if (contains(BKEY_SERIES_LIST)) {
            final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Series have their own Locale with fallback to the book-locale
            getSeries().forEach(series -> seriesDao
                    .refresh(context, series, series.getLocale(userLocale).orElse(bookLocale)));
        }
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context Current context
     */
    public void pruneSeries(@NonNull final Context context) {
        final List<Series> seriesList = getSeries();
        if (!seriesList.isEmpty()) {
            final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Series have their own Locale with fallback to the book-locale
            if (seriesDao.pruneList(context, seriesList,
                                    series -> series.getLocale(userLocale).orElse(bookLocale))) {
                stage.setStage(EntityStage.Stage.Dirty);
            }
        }

        // None present ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        if (seriesList.isEmpty()) {
            final String searchText = getString(LocalSearchCriteria.BKEY_SEARCH_TEXT_SERIES);
            if (!searchText.isEmpty()) {
                seriesList.add(Series.from(searchText, getString(DBKey.SERIES.BOOK_SERIES_NUMBER)));
                remove(LocalSearchCriteria.BKEY_SEARCH_TEXT_SERIES);
                remove(DBKey.SERIES.BOOK_SERIES_NUMBER);
                stage.setStage(EntityStage.Stage.Dirty);
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
     * Set/replace the list of {@link Publisher}s.
     *
     * @param publishers list
     */
    public void setPublishers(@NonNull final Collection<Publisher> publishers) {
        putParcelableCollection(BKEY_PUBLISHER_LIST, publishers);
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
     * Update all {@link Publisher} details from/with the database.
     *
     * @param context Current context
     */
    public void refreshPublishers(@NonNull final Context context) {
        if (contains(BKEY_PUBLISHER_LIST)) {
            final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Publisher's always use the book Locale
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
        final List<Publisher> publishers = getPublishers();
        if (!publishers.isEmpty()) {
            final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            final Locale bookLocale = getLocale(userLocale).orElse(userLocale);
            // Publisher's always use the book Locale
            if (publisherDao.pruneList(context, publishers, publisher -> bookLocale)) {
                stage.setStage(EntityStage.Stage.Dirty);
            }
        }

        // None present ? Fallback to a potential failed search result
        // which would contain whatever the user searched for.
        if (publishers.isEmpty()) {
            final String searchText = getString(LocalSearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER);
            if (!searchText.isEmpty()) {
                publishers.add(Publisher.from(searchText));
                remove(LocalSearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER);
                stage.setStage(EntityStage.Stage.Dirty);
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
     * Set/replace the list of {@link Bookshelf}s.
     *
     * @param bookShelves list
     */
    public void setBookshelves(@NonNull final Collection<Bookshelf> bookShelves) {
        putParcelableCollection(BKEY_BOOKSHELF_LIST, bookShelves);
    }

    /**
     * Set the rating.
     *
     * @param rating to set; a {@code 0} or negative number will remove the field
     */
    public void setRating(final float rating) {
        if (rating > 0) {
            putFloat(DBKey.RATING, rating);
        } else {
            remove(DBKey.RATING);
        }
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
     * Set/replace the list of {@link TocEntry}s.
     *
     * @param tocEntries list
     */
    public void setToc(@NonNull final Collection<TocEntry> tocEntries) {
        putParcelableCollection(BKEY_TOC_LIST, tocEntries);
    }

    /**
     * Get the list of {@link Tag}s.
     *
     * @return List
     */
    @NonNull
    public List<Tag> getTags() {
        return getParcelableArrayList(BKEY_TAG_LIST);
    }

    /**
     * Set/replace the list of {@link Tag}s.
     *
     * @param tags list
     */
    public void setTags(@NonNull final Collection<Tag> tags) {
        putParcelableCollection(BKEY_TAG_LIST, tags);
    }

    /**
     * Convenience method combining get/add/set.
     * Eliminates blanks and duplicates.
     *
     * @param tags to add
     */
    public void addTags(@NonNull final Collection<Tag> tags) {
        List<Tag> bt = getParcelableArrayList(BKEY_TAG_LIST);
        bt.addAll(tags);
        bt = bt.stream()
               .filter(t -> !t.getName().isBlank())
               .distinct()
               .collect(Collectors.toList());
        putParcelableCollection(BKEY_TAG_LIST, bt);
    }

    @Override
    @NonNull
    public List<Identifier.Value> getIdentifiers() {
        return getParcelableArrayList(BKEY_IDENTIFIER_LIST);
    }

    @Override
    public void setIdentifiers(@NonNull final Collection<Identifier.Value> ivs) {
        putParcelableCollection(BKEY_IDENTIFIER_LIST, ivs);
    }

    /**
     * Get the type of content of this book; i.e. Book/Collection/Anthology.
     *
     * @return type
     */
    @NonNull
    public ContentType getContentType() {
        return ContentType.byId(getInt(DBKey.CONTENT_TYPE));
    }

    /**
     * Set the type of content of this book; i.e. Book/Collection/Anthology.
     *
     * @param type to set
     */
    public void setContentType(@NonNull final ContentType type) {
        putLong(DBKey.CONTENT_TYPE, type.getId());
    }

    /**
     * Check if this book matches the given edition bits.
     *
     * @param bitmask to check
     *
     * @return {@code true} if the book matches (at least) the required edition
     */
    public boolean isEdition(@Edition.Bitmask final long bitmask) {
        return (getEdition() & bitmask) != 0;
    }

    /**
     * Get the edition bitmask value.
     *
     * @return edition bitmask
     *
     * @see Edition
     */
    @Edition.Bitmask
    public long getEdition() {
        return getLong(DBKey.EDITION) & Edition.BITMASK_ALL_BITS;
    }

    /**
     * Set the edition bitmask value.
     *
     * @param bitmask to et
     *
     * @see Edition
     */
    public void setEdition(@Edition.Bitmask final long bitmask) {
        putLong(DBKey.EDITION, bitmask & Edition.BITMASK_ALL_BITS);
    }

    /**
     * Get the {@link StripInfoCollectionData}.
     *
     * @return collection data
     */
    @NonNull
    public Optional<StripInfoCollectionData> getStripInfoCollectionData() {
        // Sanity check
        if (getIdentifierValue(Identifier.SID_STRIP_INFO).isEmpty()) {
            return Optional.empty();
        }

        // We MIGHT have it (probably not) ...
        if (contains(StripInfoCollectionData.BKEY)) {
            return Optional.ofNullable(getParcelable(StripInfoCollectionData.BKEY));
        } else {
            // but if not, go explicitly fetch/create it.
            final Optional<StripInfoCollectionData> oData = ServiceLocator
                    .getInstance()
                    .getStripInfoDao()
                    .findByLocalBookId(getId());
            // store for reuse
            oData.ifPresent(data -> putParcelable(StripInfoCollectionData.BKEY, data));
            return oData;
        }
    }

    /**
     * Set or remove the StripInfo collection data.
     *
     * @param data to set; use {@code null} to remove.
     *             This does NOT remove the {@link Identifier#SID_STRIP_INFO}
     *             if there is one.
     */
    public void setStripInfoCollectionData(@Nullable final StripInfoCollectionData data) {
        if (data == null) {
            remove(StripInfoCollectionData.BKEY);
        } else {
            putParcelable(StripInfoCollectionData.BKEY, data);
        }
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
            return Optional.ofNullable(getParcelable(BKEY_CALIBRE_LIBRARY));
        } else {
            // but if not, go explicitly fetch it.
            final Optional<CalibreLibrary> oLibrary = ServiceLocator
                    .getInstance()
                    .getCalibreLibraryDao()
                    .findById(getLong(DBKey.FK_CALIBRE_LIBRARY));
            // store for reuse
            oLibrary.ifPresent(library -> putParcelable(BKEY_CALIBRE_LIBRARY, library));
            return oLibrary;
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
            remove(BKEY_CALIBRE_LIBRARY);
            remove(CalibreBookData.BKEY);

            remove(DBKey.FK_CALIBRE_LIBRARY);
            remove(DBKey.CALIBRE.BOOK_ID);
            remove(DBKey.CALIBRE.BOOK_UUID);
            remove(DBKey.CALIBRE.BOOK_MAIN_FORMAT);
        }
    }

    @NonNull
    public Optional<CalibreBookData> getCalibreBookData() {
        // temp hack...
        if (!contains(CalibreBookData.BKEY)
            && contains(DBKey.FK_CALIBRE_LIBRARY)
            && contains(DBKey.CALIBRE.BOOK_ID)) {

            final CalibreBookData calibreBookData = new CalibreBookData(
                    getLong(DBKey.FK_CALIBRE_LIBRARY),
                    getLong(DBKey.CALIBRE.BOOK_ID),
                    getString(DBKey.CALIBRE.BOOK_UUID),
                    getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT, null));
            putParcelable(CalibreBookData.BKEY, calibreBookData);
            return Optional.of(calibreBookData);
        }

        // We MIGHT have it (probably not) ...
        if (contains(CalibreBookData.BKEY)) {
            final CalibreBookData calibreBookData = getParcelable(CalibreBookData.BKEY);
            return Optional.ofNullable(calibreBookData);
        } else {
            // but if not, go explicitly fetch it.
            final Optional<CalibreBookData> calibreBookData = ServiceLocator
                    .getInstance()
                    .getCalibreDao()
                    .findByLocalBookId(getId());
            // store for reuse
            calibreBookData.ifPresent(bookData -> putParcelable(CalibreBookData.BKEY, bookData));
            return calibreBookData;
        }
    }

    public void setCalibreBookData(@Nullable final CalibreBookData data) {
        if (data != null) {
            putParcelable(CalibreBookData.BKEY, data);
        } else {
            remove(BKEY_CALIBRE_LIBRARY);
            remove(CalibreBookData.BKEY);

            remove(DBKey.FK_CALIBRE_LIBRARY);
            remove(DBKey.CALIBRE.BOOK_ID);
            remove(DBKey.CALIBRE.BOOK_UUID);
            remove(DBKey.CALIBRE.BOOK_MAIN_FORMAT);
        }
    }

    /**
     * Ensure the book has a bookshelf.
     * If the book is not on any Bookshelf, adds the current/default bookshelf
     */
    public void ensureBookshelf() {
        final List<Bookshelf> list = getParcelableArrayList(BKEY_BOOKSHELF_LIST);
        if (list.isEmpty()) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            Bookshelf bookshelf = bookshelfDao.getCurrent().orElseGet(bookshelfDao::getDefault);
            if (bookshelf.getId() == Bookshelf.ALL_BOOKS) {
                // the user was "on" the "All Books" virtual shelf.
                // For lack of anything better, set the default shelf instead.
                bookshelf = bookshelfDao.getDefault();
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
     * @param userLocale Current Locale
     */
    public void ensureLanguage(@NonNull final Locale userLocale) {
        final String lang = getString(DBKey.LANGUAGE, null);
        if (lang == null || lang.isEmpty()) {
            final List<String> previouslyUsed = ServiceLocator.getInstance()
                                                              .getLanguageDao().getList();
            if (previouslyUsed.isEmpty()) {
                putString(DBKey.LANGUAGE, userLocale.getISO3Language());
            } else {
                putString(DBKey.LANGUAGE, previouslyUsed.get(0));
            }
        }
    }

    /**
     * Ensure the book has a condition set.
     * If the book does not, set it to {@link Book#CONDITION_AS_NEW}.
     */
    public void ensureCondition() {
        if (ServiceLocator.getInstance().isFieldEnabled(DBKey.CONDITION_BOOK)
            && !contains(DBKey.CONDITION_BOOK)) {
            putInt(DBKey.CONDITION_BOOK, Book.CONDITION_AS_NEW);
        }
    }

    /**
     * Ensure the book has a date-acquired set.
     * If the book does not, set it to {@code now}.
     */
    public void ensureDateAcquired() {
        if (!contains(DBKey.DATE_ACQUIRED)) {
            putLocalDateTime(DBKey.DATE_ACQUIRED, LocalDateTime.now());
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
     * For a translated book, set the original title.
     * <p>
     * An attempt to guess the language will be made.
     * A subsequent call to {@link #setTranslatedFromLanguage(String)} will
     * override this guess.
     *
     * @param originalTitle to set; a {@code null} or an empty string will remove the field
     */
    public void setTranslatedFromTitle(@Nullable final String originalTitle) {
        if (originalTitle != null && !originalTitle.isBlank()) {
            putString(DBKey.TRANSLATION_ORIGINAL_TITLE, originalTitle);

            if (!contains(DBKey.TRANSLATION_ORIGINAL_LANGUAGE)) {
                // a wild attempt at guessing the language...
                final String lc = originalTitle.toLowerCase(Locale.ENGLISH);
                if (lc.startsWith("the ") || lc.contains(" the ")) {
                    putString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, "eng");
                }
            }

        } else {
            remove(DBKey.TRANSLATION_ORIGINAL_TITLE);
        }
    }

    /**
     * For a translated book, set the original language.
     *
     * @param originalLanguage to set; a {@code null} or an empty string will remove the field
     *
     * @see #setLanguage(String)
     */
    public void setTranslatedFromLanguage(@Nullable final String originalLanguage) {
        if (originalLanguage != null && !originalLanguage.isBlank()) {
            putString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, originalLanguage);
        } else {
            remove(DBKey.TRANSLATION_ORIGINAL_LANGUAGE);
        }
    }

    /**
     * FIXME: 27/09/2024 unify 'isRead' with 'getReadingProgress()'
     * <p>
     * Get the Read/Unread status.
     *
     * @return {@code true} if this book was read/finished.
     */
    public boolean isRead() {
        return getBoolean(DBKey.READ__BOOL);
    }

    /**
     * Set the Read/Unread status.
     * Related fields are updated as needed.
     * <p>
     * If set to {@code true}, the read-end date will be set to {@code now}.
     *
     * @param read flag
     */
    public void setReadNow(final boolean read) {
        final String now = SqlEncode.dateTime(LocalDateTime.now());
        final String endDate = read ? now : "";
        internalSetReadingProgress(read, endDate);
    }

    /**
     * Get the progress the reader has made on this book.
     *
     * @return progress
     */
    @NonNull
    public ReadingProgress getReadingProgress() {
        final ReadingProgress readingProgress;

        if (isRead()) {
            readingProgress = ReadingProgress.finished(true);
        } else {
            readingProgress = ReadingProgress.fromJson(getString(DBKey.READ_PROGRESS));
        }

        // Copy the total number of pages if needed/possible.
        // The 'empty' default is 1, but paranoia ... so check for <= 1
        if (readingProgress.getTotalPages() <= 1) {
            // We didn't have a total-pages,
            // try to coerce the detailed TEXT value into a raw number
            final String pageCountStr = getString(DBKey.PAGES);
            if (!pageCountStr.isEmpty()) {
                try {
                    final int totalPages = Integer.parseInt(pageCountStr);
                    if (totalPages > 0) {
                        readingProgress.setTotalPages(totalPages);
                    }
                } catch (@NonNull final NumberFormatException ignore) {
                    // The field was likely a description of some sort,
                    // and not a simple page count number.
                    // See the docs on DBKey.PAGE_COUNT
                }
            }
        }

        return readingProgress;
    }

    /**
     * Set the progress the reader has made on this book.
     * Related fields are updated as needed.
     * <p>
     * If set to {@code read/completed}, the read-end date will be set to {@code now}.
     *
     * @param progress to set
     */
    public void setReadingProgress(@NonNull final ReadingProgress progress) {
        final boolean read = progress.isRead();

        final String now = SqlEncode.dateTime(LocalDateTime.now());
        final String endDate = read ? now : "";

        // If the separate page-count field is empty, and we have a total-pages value,
        // set it as well.
        // Keep in sync with {@link BookDaoHelper#processReadProgress()} !
        String pageCount = getString(DBKey.PAGES);
        if (!progress.asPercentage() && pageCount.isEmpty()) {
            pageCount = String.valueOf(progress.getTotalPages());
        }

        internalSetReadingProgress(progress, endDate, pageCount);
    }

    /**
     * <strong>WARNING</strong> this method only to be used by
     * {@link #setReadNow(boolean)} and
     * {@link BookDao#setRead(Book, boolean)}.
     * <p>
     * Dev. note: using this method forces us to keep the related fields in a consistent state.
     *
     * @param read    value for {@link DBKey#READ__BOOL}
     * @param endDate value for {@link DBKey#READ_END__DATE}
     */
    public void internalSetReadingProgress(final boolean read,
                                           @NonNull final String endDate) {
        putBoolean(DBKey.READ__BOOL, read);
        putString(DBKey.READ_END__DATE, endDate);
        putString(DBKey.READ_PROGRESS, "");
    }

    /**
     * <strong>WARNING</strong> this method only to be used by
     * {@link #setReadingProgress(ReadingProgress)} and
     * {@link BookDao#setReadingProgress(Book, ReadingProgress)}.
     * <p>
     * Dev. note: using this method forces us to keep the related fields in a consistent state.
     *
     * @param readingProgress value for {@link DBKey#READ_PROGRESS}
     * @param endDate         value for {@link DBKey#READ_END__DATE}
     * @param pageCount       value for {@link DBKey#PAGES}
     */
    public void internalSetReadingProgress(@NonNull final ReadingProgress readingProgress,
                                           @NonNull final String endDate,
                                           @NonNull final String pageCount) {
        putBoolean(DBKey.READ__BOOL, readingProgress.isRead());
        putString(DBKey.READ_END__DATE, endDate);
        putString(DBKey.READ_PROGRESS, readingProgress.toJson());
        putString(DBKey.PAGES, pageCount);
    }


    /**
     * Get the <strong>current</strong> cover file for this book.
     * <p>
     * Depending on the {@link #stage} this method gets a temporary cover,
     * or the persisted cover.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @param context Current context
     * @param cIdx    0..n image index
     *
     * @return file
     */
    @NonNull
    public Optional<File> getImage(@NonNull final Context context,
                                   @IntRange(from = 0, to = 3) final int cIdx) {
        if (contains(BKEY_TMP_FILE_SPEC[cIdx])) {
            // we have a previously set temporary cover, but it could be ""
            final String fileSpec = getString(BKEY_TMP_FILE_SPEC[cIdx]);
            @Nullable
            File coverFile = null;
            if (!fileSpec.isEmpty()) {
                coverFile = new File(fileSpec);
                if (!coverFile.exists() || coverFile.length() == 0) {
                    coverFile = null;
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger()
                             .e(TAG, new Throwable("getCoverFile"),
                                "bookId=" + getId()
                                + "|cIdx=" + cIdx
                                + "|file="
                                + (coverFile == null ? "null" : coverFile.getAbsolutePath())
                             );
            }
            // If it exists, it will be a valid file as we check before storing it
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
     * Update the book cover with the given file.
     * <p>
     * Depending on the {@link #stage} this method sets a temporary cover,
     * or persists the cover to storage.
     *
     * @param context Current context
     * @param cIdx    0..n image index
     * @param file    cover file or {@code null} to delete the cover
     *
     * @throws StorageException      The covers directory is not available
     * @throws IOException           on generic/other IO failures
     * @throws IllegalStateException (debug) if the UUID is missing
     */
    @SuppressWarnings({"UnusedReturnValue", "OverlyBroadThrowsClause"})
    @Override
    @WorkerThread
    public void setImage(@NonNull final Context context,
                         @IntRange(from = 0, to = 3) final int cIdx,
                         @Nullable final File file)
            throws StorageException, IOException {

        if (stage.getStage() == EntityStage.Stage.WriteAble
            || stage.getStage() == EntityStage.Stage.Dirty) {
            // We're editing, use BKEY_TMP_FILE_SPEC storage.

            if (file != null) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                    LoggerFactory.getLogger()
                                 .e(TAG, new Throwable("setImage"),
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
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                    LoggerFactory.getLogger()
                                 .e(TAG, new Throwable("setImage"),
                                    "editing"
                                    + "|bookId=" + getId()
                                    + "|cIdx=" + cIdx
                                    + "|deleting"
                                 );
                }
                // explicitly set to "" to let BookDaoHelper#persistCovers delete
                // the file at the time of insert/update -ing the book
                putString(BKEY_TMP_FILE_SPEC[cIdx], "");
            }

            // switch from WriteAble to Dirty (or from Dirty to Dirty)
            stage.setStage(EntityStage.Stage.Dirty);

            // just return the incoming file, it has not been changed or renamed

        } else {
            // we're in read-only mode, use the UUID storage based file name
            final String uuid = getString(DBKey.BOOK_UUID, null);
            if (uuid == null || uuid.isEmpty()) {
                throw new IllegalStateException("Missing uuid");
            }

            // See BookDaoHelper#persistCovers which does the same as below for BKEY_TMP_FILE_SPEC
            if (file != null) {
                if (file.getName().startsWith(uuid)) {
                    // No further action needed as we have the image "in-place"
                    // ... not actually sure when this would be the case; keep an eye on logs
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                        LoggerFactory.getLogger()
                                     .e(TAG, new Throwable("setImage"),
                                        "readOnly"
                                        + "|bookId=" + getId()
                                        + "|cIdx=" + cIdx
                                        + "|uuid, in-place"
                                     );
                    }
                } else {
                    // Rename the temp file to the uuid permanent file name
                    ServiceLocator.getInstance().getCoverStorage().persist(file, uuid, cIdx);
                }
            } else {
                // a null file indicates we need to delete the cover
                ASyncExecutor.STORAGE_WRITES.execute(
                        () -> ServiceLocator.getInstance()
                                            .getCoverStorage()
                                            .delete(uuid, cIdx));
            }

            ServiceLocator.getInstance().getBookDao().touch(this);
        }
    }

    @Override
    @WorkerThread
    public void removeImage(@NonNull final Context context,
                            @IntRange(from = 0, to = 3) final int cIdx) {
        try {
            setImage(context, cIdx, null);
        } catch (@NonNull final IOException | StorageException ignore) {
            // safe to ignore, can't happen with a 'null' input.
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
    public Optional<LocalDateTime> getLastModified(
            @NonNull final DateParser<LocalDateTime> dateParser) {
        return getLocalDateTime(DBKey.DATE_LAST_UPDATED__UTC, dateParser);
    }

    /**
     * Set the last date-time that this book was modified.
     * If not set, a default of 'now' will be used when saved.
     * <p>
     * <strong>WARNING</strong> this method only to be used by the DAO or by synchronization
     * services (e.g. Calibre...)
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

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        final RealNumberParser realNumberParser = new RealNumberParser(allLocales);

        final DataValidator priceValidator = new OrValidator(
                new BlankValidator(),
                new DoubleValidator(realNumberParser));
        final DataValidator longValidator = new LongValidator(realNumberParser);
        final DataValidator nonBlankValidator = new NonBlankValidator(realNumberParser);

        validatorConfig.addValidator(DBKey.TITLE,
                                     nonBlankValidator, R.string.lbl_title);
        validatorConfig.addValidator(BKEY_AUTHOR_LIST,
                                     nonBlankValidator, R.string.lbl_author);
        //URGENT: force a bookshelf te be entered?
        // 2024-11-28: up to now, no-shelf automatically adds the book
        // to the default shell, which we presume is ok for the large majority of users.
        // We came across this situation when manually edit the bookshelves list
        // and deliberately removing all.
        //validatorConfig.addValidator(BKEY_BOOKSHELF_LIST,
        //                             nonBlankValidator, R.string.lbl_bookshelf);

        validatorConfig.addValidator(DBKey.LANGUAGE,
                                     nonBlankValidator, R.string.lbl_language);

        validatorConfig.addValidator(DBKey.EDITION,
                                     longValidator, R.string.lbl_edition);
        validatorConfig.addValidator(DBKey.CONTENT_TYPE,
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
     *
     * @param context Current context
     * @param style   to apply
     *
     * @return the intent
     */
    @NonNull
    public Intent getShareIntent(@NonNull final Context context,
                                 @NonNull final Style style) {

        final Citation citation = CitationFactory.create(style);
        final String text = citation.cite(context, this);

        final Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);

        getImage(context, 0).ifPresent(file -> {
            try {
                final Uri uri = GenericFileProvider.createUri(file, getTitle());
                // read access to the input uri
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      .putExtra(Intent.EXTRA_STREAM, uri);
            } catch (@NonNull final IllegalArgumentException e) {
                // Ignore the error, but log it. If the GenericFileProvider
                // is at fault, the user will be hit with this exception
                // when they add/edit covers.
                LoggerFactory.getLogger().e(TAG, e, file.getAbsolutePath());
            }
        });

        return Intent.createChooser(intent, context.getString(R.string.whichSendApplication));
    }

    /**
     * Copy any external id's present in the given bookData to this Book.
     *
     * @param bookData to copy from
     */
    public void copyExternalIdsFrom(@NonNull final Book bookData) {
        ServiceLocator.getInstance()
                      .getIdentifierDao()
                      .getAll()
                      .stream()
                      .map(Identifier::getKey)
                      .filter(bookData::contains)
                      .forEach(name -> putString(name, bookData.getString(name)));
    }

    /**
     * Database representation of column {@link DBKey#CONTENT_TYPE}.
     */
    public enum ContentType
            implements Entity, Parcelable {
        /** Single work. One or more authors. */
        Book(0),
        /** Multiple works, all by a single Author. */
        Collection(1),
        // value 2 not in use.
        /** Multiple works, multiple Authors. */
        Anthology(3);

        /** {@link Parcelable}. */
        public static final Creator<ContentType> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public ContentType createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public ContentType[] newArray(final int size) {
                return new ContentType[size];
            }
        };

        private final int id;

        ContentType(final int id) {
            this.id = id;
        }

        @NonNull
        public static List<ContentType> getAll() {
            return Arrays.asList(values());
        }

        /**
         * Lookup by id.
         * <p>
         * Import/Export and database usage only.
         *
         * @param id to lookup
         *
         * @return type; or {@link #Book} for any invalid id.
         */
        @NonNull
        public static ContentType byId(final int id) {
            return Arrays.stream(values())
                         .filter(v -> v.id == id)
                         .findFirst()
                         .orElse(Book);
        }

        /**
         * Get the internal id.
         * <p>
         * Import/Export and database usage only.
         *
         * @return id
         */
        @Override
        public long getId() {
            return id;
        }

        @NonNull
        @Override
        public String getLabel(@NonNull final Context context,
                               @Nullable final Details details,
                               @Nullable final Style style) {
            return context.getResources().getStringArray(R.array.lbl_book_content_type)[id];
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }

    /**
     * Database representation of column {@link DBKey#EDITION}.
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
        /** This edition is signed. i.e. the whole print-run of this edition is signed. */
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

        @IntDef(flag = true,
                value = {UNKNOWN, FIRST, FIRST_IMPRESSION, LIMITED, SLIPCASE, SIGNED, BOOK_CLUB})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Bitmask {

        }
    }
}
