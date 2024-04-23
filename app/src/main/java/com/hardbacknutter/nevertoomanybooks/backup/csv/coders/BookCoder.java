/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Note: the keys for the CSV columns are not the same as the internal Book keys
 * due to backward compatibility.
 * TODO: make the current ones LEGACY, and start using the Books keys, but still support reading
 * the old ones.
 * <p>
 * <strong>LIMITATIONS:</strong> Calibre book data is handled, but Calibre library is NOT.
 * The Calibre native string-id is written out with the book.
 * <p>
 * When reading, the Calibre native string-id is checked against already existing data,
 * but if there is no match all Calibre data for the book is discarded.
 * <p>
 * In other words: this coder is NOT a full backup/restore!
 */
public class BookCoder {

    /**
     * The Goodreads Author field will be mapped to {@link DBKey#AUTHOR_FORMATTED}.
     * Any additional authors come in this key and will need to be added.
     */
    public static final String GOODREADS_ADDITIONAL_AUTHORS = "goodreads_additional_authors";
    /**
     * Data will have the Isbn13 field mapped to {@link DBKey#BOOK_ISBN}.
     * If empty, we get the Isbn10 code from this key.
     */
    public static final String GOODREADS_ISBN10 = "goodreads_isbn10";
    /** Goodreads bookshelves need special decoding. */
    public static final String GOODREADS_BOOKSHELVES = "goodreads_bookshelves";
    /**
     * An {@code int} 1..5; can be missing.
     * Decoded in combination with {@link #GOODREADS_AVERAGE_RATING}.
     */
    public static final String GOODREADS_MY_RATING = "goodreads_my_rating";
    /**
     * A {@code float} 0..5; can be missing.
     * Decoded in combination with {@link #GOODREADS_MY_RATING}.
     */
    public static final String GOODREADS_AVERAGE_RATING = "goodreads_average_rating";

    /** string-encoded column compatible with BC and NTMB 1.x-3.x. CSV files. */
    private static final String CSV_COLUMN_TOC = "anthology_titles";
    /** string-encoded column compatible with BC and NTMB 1.x-3.x. CSV files. */
    private static final String CSV_COLUMN_SERIES = "series_details";
    /** string-encoded column compatible with BC and NTMB 1.x-3.x. CSV files. */
    private static final String CSV_COLUMN_AUTHORS = "author_details";
    /** string-encoded column compatible with BC and NTMB 1.x-3.x. CSV files. */
    private static final String CSV_COLUMN_PUBLISHERS = "publisher";
    /** Obsolete/alternative header as used in BC: full given+family author name. */
    private static final String LEGACY_AUTHOR_NAME = "author_name";
    /** Obsolete/alternative header as used in BC: bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT = "bookshelf_text";
    /** Obsolete as used in BC, not used/needed, skipped. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** Obsolete/alternative header only used by NTMB pre-1.2 versions: bookshelf name. */
    private static final String LEGACY_BOOKSHELF_1_1 = "bookshelf";

    private final StringList<Author> authorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> seriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> publisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> tocCoder = new StringList<>(new TocEntryCoder());
    /** This is a COMMA separated string list. */
    @NonNull
    private final StringList<Bookshelf> bookshelfCoder;
    @NonNull
    private final Author unknownAuthor;
    @NonNull
    private final Style defaultStyle;
    /** This is a SPACE separated string list. */
    @Nullable
    private StringList<Bookshelf> goodreadsBookshelfCoder;
    @Nullable
    private Map<String, Long> calibreLibraryStr2IdMap;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param defaultStyle the default style to use for {@link Bookshelf}s
     */
    public BookCoder(@NonNull final Context context,
                     @NonNull final Style defaultStyle) {
        // Backwards compatibility: use the ',' separator
        this.bookshelfCoder = new StringList<>(new BookshelfCoder(',', defaultStyle));

        unknownAuthor = Author.createUnknownAuthor(context);
        this.defaultStyle = defaultStyle;
    }

    /**
     * Database access is strictly limited to fetching ID's for the list elements.
     * <p>
     * Both csv lists <strong>must</strong> be the same length.
     *
     * @param context        Current context
     * @param csvColumnNames the list with the field(column) names
     * @param csvDataRow     the list with the field data
     *
     * @return the decoded book
     */
    @NonNull
    public Book decode(@NonNull final Context context,
                       @NonNull final List<String> csvColumnNames,
                       @NonNull final List<String> csvDataRow) {
        final Book book = new Book();

        // Read all non-empty columns of the current row into the Bundle.
        // Note that some of them require further processing before being valid.
        for (int i = 0; i < csvColumnNames.size(); i++) {
            final String data = csvDataRow.get(i);
            if (!data.isEmpty()) {
                book.putString(csvColumnNames.get(i), data);
            }
        }

        // we MUST have a title.
        if (book.getTitle().isEmpty()) {
            book.putString(DBKey.TITLE, context.getString(R.string.unknown_title));
        }

        // check/fix the language
        book.getAndUpdateLocale(context, true);

        // Database access is strictly limited to fetching ID's for the list elements.
        processIsbn(book);
        processAuthors(book);
        processSeries(book);
        processPublishers(book);
        processToc(book);
        processBookshelves(context, book);
        processCalibreData(book);
        processRating(book);

        //FIXME: implement full parsing/formatting of incoming dates for validity
        //verifyDates(context, bookDao, book);

        return book;
    }

    /**
     * Process alternative keys for the ISBN and clean the ISBN text as needed.
     *
     * @param book to process
     */
    private void processIsbn(@NonNull final Book book) {
        if (!book.contains(DBKey.BOOK_ISBN) && book.contains(GOODREADS_ISBN10)) {
            book.putString(DBKey.BOOK_ISBN, book.getString(GOODREADS_ISBN10));
            book.remove(GOODREADS_ISBN10);
        }

        // ALWAYS clean the ISBN here. We've seen Goodreads csv file with
        // nightmares like this: "=""9789027409294"""  and "="""""
        if (book.contains(DBKey.BOOK_ISBN)) {
            final String cleanText = ISBN.cleanText(book.getString(DBKey.BOOK_ISBN));
            if (cleanText.isEmpty()) {
                book.remove(DBKey.BOOK_ISBN);
            } else {
                book.putString(DBKey.BOOK_ISBN, cleanText);
            }
        }
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param book to process
     */
    private void processAuthors(@NonNull final Book book) {

        final String encodedList = book.getString(CSV_COLUMN_AUTHORS, null);
        book.remove(CSV_COLUMN_AUTHORS);

        final List<Author> list;
        if (encodedList == null || encodedList.isEmpty()) {
            // check for individual author (full/family/given) fields in the input
            list = new ArrayList<>();
            if (book.contains(DBKey.AUTHOR_FAMILY_NAME)) {
                final String family = book.getString(DBKey.AUTHOR_FAMILY_NAME, null);
                if (family != null && !family.isEmpty()) {
                    final String given = book.getString(DBKey.AUTHOR_GIVEN_NAMES, null);
                    list.add(new Author(family, given));
                }
                book.remove(DBKey.AUTHOR_FAMILY_NAME);
                book.remove(DBKey.AUTHOR_GIVEN_NAMES);
            }
            processAuthor(book, DBKey.AUTHOR_FORMATTED, list);
            processAuthor(book, LEGACY_AUTHOR_NAME, list);
            processAuthor(book, GOODREADS_ADDITIONAL_AUTHORS, list);
        } else {
            list = authorCoder.decodeList(encodedList);
        }

        // we MUST have an author.
        if (list.isEmpty()) {
            list.add(unknownAuthor);
        }
        book.setAuthors(list);
    }

    private void processAuthor(@NonNull final Book book,
                               @NonNull final String key,
                               @NonNull final List<Author> list) {
        if (book.contains(key)) {
            final String a = book.getString(key, null);
            if (a != null && !a.isEmpty()) {
                list.add(Author.from(a));
            }
            book.remove(key);
        }
    }

    /**
     * Process the list of Series.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param book to process
     */
    private void processSeries(@NonNull final Book book) {

        final String encodedList = book.getString(CSV_COLUMN_SERIES, null);
        book.remove(CSV_COLUMN_SERIES);

        if (encodedList == null || encodedList.isEmpty()) {
            // check for individual series title/number fields in the input
            if (book.contains(DBKey.SERIES_TITLE)) {
                final String title = book.getString(DBKey.SERIES_TITLE, null);
                if (title != null && !title.isEmpty()) {
                    final Series series = new Series(title);
                    // number will be "" if it's not present
                    series.setNumber(book.getString(DBKey.SERIES_BOOK_NUMBER, null));
                    final List<Series> list = new ArrayList<>();
                    list.add(series);
                    book.setSeries(list);
                }
                book.remove(DBKey.SERIES_TITLE);
                book.remove(DBKey.SERIES_BOOK_NUMBER);
            } else {
                Series.checkForSeriesNameInTitle(book);
            }
        } else {
            final List<Series> list = seriesCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                book.setSeries(list);
            }
        }
    }

    /**
     * Process the list of Publishers.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param book to process
     */
    private void processPublishers(@NonNull final Book book) {

        final String encodedList = book.getString(CSV_COLUMN_PUBLISHERS, null);
        book.remove(CSV_COLUMN_PUBLISHERS);

        if (encodedList != null && !encodedList.isEmpty()) {
            final List<Publisher> list = publisherCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                book.setPublishers(list);
            }
        }
    }

    /**
     * Process the list of Toc entries.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignores the actual value of the {@link DBDefinitions#DOM_BOOK_CONTENT_TYPE}.
     * It will be computed when storing the book data.
     *
     * @param book to process
     */
    private void processToc(@NonNull final Book book) {

        final String encodedList = book.getString(CSV_COLUMN_TOC, null);
        book.remove(CSV_COLUMN_TOC);

        if (encodedList != null && !encodedList.isEmpty()) {
            final List<TocEntry> list = tocCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                book.setToc(list);
            }
        }
    }

    /**
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param context Current context
     * @param book    to process
     */
    private void processBookshelves(@NonNull final Context context,
                                    @NonNull final Book book) {

        final List<Bookshelf> bookshelves = new ArrayList<>();

        processBookshelf(book, bookshelfCoder, DBKey.BOOKSHELF_NAME, bookshelves);
        processBookshelf(book, bookshelfCoder, LEGACY_BOOKSHELF_1_1, bookshelves);
        processBookshelf(book, bookshelfCoder, LEGACY_BOOKSHELF_TEXT, bookshelves);

        if (book.contains(GOODREADS_BOOKSHELVES)) {
            if (goodreadsBookshelfCoder == null) {
                goodreadsBookshelfCoder = new StringList<>(new BookshelfCoder(' ', defaultStyle));
            }
            //ENHANCE: provide mapping for the Goodreads "read", "to-read" and "currently-reading"
            // fixed shelves. For now we just create those 3 when not there yet.
            processBookshelf(book, goodreadsBookshelfCoder, GOODREADS_BOOKSHELVES, bookshelves);
            if (bookshelves.stream().anyMatch(bookshelf -> "read".equals(bookshelf.getName()))) {
                // DO NOT use book.setRead(true) as that will set related fields
                // which is not desired here as this might overwrite incoming data
                book.putBoolean(DBKey.READ__BOOL, true);
            }
        }

        // never used, just remove
        book.remove(LEGACY_BOOKSHELF_ID);

        if (!bookshelves.isEmpty()) {
            ServiceLocator.getInstance().getBookshelfDao().pruneList(context, bookshelves);
            book.setBookshelves(bookshelves);
        }
    }

    private void processBookshelf(@NonNull final Book book,
                                  @NonNull final StringList<Bookshelf> bookshelfCoder,
                                  @NonNull final String key,
                                  @NonNull final List<Bookshelf> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            if (encodedList != null && !encodedList.isEmpty()) {
                list.addAll(bookshelfCoder.decodeList(encodedList));
            }
            book.remove(key);
        }
    }

    private void processCalibreData(@NonNull final Book book) {
        // we need to convert the string id to the row id.
        final String stringId = book.getString(DBKey.CALIBRE_LIBRARY_STRING_ID, null);
        // and discard the string-id
        book.remove(DBKey.CALIBRE_LIBRARY_STRING_ID);

        if (stringId != null && !stringId.isEmpty()) {
            if (calibreLibraryStr2IdMap == null) {
                calibreLibraryStr2IdMap = new HashMap<>();

                ServiceLocator.getInstance().getCalibreLibraryDao().getAllLibraries()
                              .forEach(library -> calibreLibraryStr2IdMap.put(
                                      library.getLibraryStringId(), library.getId()));

            }
            final Long id = calibreLibraryStr2IdMap.get(stringId);
            if (id != null) {
                book.putLong(DBKey.FK_CALIBRE_LIBRARY, id);
            } else {
                // Don't try to recover; just remove all calibre keys from this book.
                book.setCalibreLibrary(null);
            }
        }
    }

    private void processRating(@NonNull final Book book) {
        if (!book.contains(DBKey.RATING) && book.contains(GOODREADS_MY_RATING)) {
            try {
                final int rating = Integer.parseInt(
                        book.getString(GOODREADS_MY_RATING));
                if (rating > 0) {
                    book.putInt(DBKey.RATING, MathUtils.clamp(rating, 1, 5));
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
            book.remove(GOODREADS_MY_RATING);
        }

        if (!book.contains(DBKey.RATING) && book.contains(GOODREADS_AVERAGE_RATING)) {
            try {
                final int rating = Math.round(Float.parseFloat(
                        book.getString(GOODREADS_AVERAGE_RATING)));
                if (rating > 0) {
                    book.putInt(DBKey.RATING, MathUtils.clamp(rating, 1, 5));
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
            book.remove(GOODREADS_AVERAGE_RATING);
        }
    }
}
