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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
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

    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_TOC = "anthology_titles";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_SERIES = "series_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_AUTHORS = "author_details";
    /** column in CSV file - string-encoded - used in import/export, never change this string. */
    private static final String CSV_COLUMN_PUBLISHERS = "publisher";

    /** Obsolete/alternative header: full given+family author name. */
    private static final String LEGACY_AUTHOR_NAME = "author_name";
    /** Obsolete/alternative header: bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT = "bookshelf_text";
    /** Obsolete, not used. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** Obsolete/alternative header: bookshelf name. Used by pre-1.2 versions. */
    private static final String LEGACY_BOOKSHELF_1_1_x = "bookshelf";

    private final StringList<Author> authorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> seriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> publisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> tocCoder = new StringList<>(new TocEntryCoder());
    @NonNull
    private final StringList<Bookshelf> bookshelfCoder;

    @NonNull
    private final ServiceLocator serviceLocator;
    @Nullable
    private Map<String, Long> calibreLibraryStr2IdMap;

    public BookCoder(@NonNull final Context context) {
        serviceLocator = ServiceLocator.getInstance();

        bookshelfCoder = new StringList<>(new BookshelfCoder(context));
    }

    /**
     * Database access is strictly limited to fetching ID's for the list elements.
     *
     * @param context Current context
     *
     * @return the decoded book
     */
    @NonNull
    public Book decode(@NonNull final Context context,
                       @NonNull final String[] csvColumnNames,
                       @NonNull final String[] csvDataRow) {
        final Book book = new Book();

        // Read all columns of the current row into the Bundle.
        // Note that some of them require further processing before being valid.
        for (int i = 0; i < csvColumnNames.length; i++) {
            book.putString(csvColumnNames[i], csvDataRow[i]);
        }

        // check/add a title
        if (book.getTitle().isEmpty()) {
            book.putString(DBKey.TITLE, context.getString(R.string.unknown_title));
        }

        // check/fix the language
        final Locale bookLocale = book.getLocale(context);

        // Database access is strictly limited to fetching ID's for the list elements.
        decodeAuthors(context, book, bookLocale);
        decodeSeries(context, book, bookLocale);
        decodePublishers(context, book, bookLocale);
        decodeToc(context, book, bookLocale);
        decodeBookshelves(book);
        decodeCalibreData(book);

        //FIXME: implement full parsing/formatting of incoming dates for validity
        //verifyDates(context, bookDao, book);

        return book;
    }

    private void decodeCalibreData(@NonNull final Book /* in/out */ book) {
        // we need to convert the string id to the row id.
        final String stringId = book.getString(DBKey.CALIBRE_LIBRARY_STRING_ID);
        // and discard the string-id
        book.remove(DBKey.CALIBRE_LIBRARY_STRING_ID);

        if (!stringId.isEmpty()) {
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

    /**
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param book the book
     */
    private void decodeBookshelves(@NonNull final Book /* in/out */ book) {

        String encodedList = null;

        if (book.contains(DBKey.BOOKSHELF_NAME)) {
            // current version
            encodedList = book.getString(DBKey.BOOKSHELF_NAME);

        } else if (book.contains(LEGACY_BOOKSHELF_1_1_x)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_1_1_x);

        } else if (book.contains(LEGACY_BOOKSHELF_TEXT)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_TEXT);
        }

        if (encodedList != null && !encodedList.isEmpty()) {
            final ArrayList<Bookshelf> bookshelves = bookshelfCoder.decodeList(encodedList);
            if (!bookshelves.isEmpty()) {
                serviceLocator.getBookshelfDao().pruneList(bookshelves);
                book.setBookshelves(bookshelves);
            }
        }

        book.remove(LEGACY_BOOKSHELF_ID);
        book.remove(LEGACY_BOOKSHELF_TEXT);
        book.remove(LEGACY_BOOKSHELF_1_1_x);
        book.remove(DBKey.BOOKSHELF_NAME);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeAuthors(@NonNull final Context context,
                               @NonNull final Book /* in/out */ book,
                               @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_AUTHORS);
        book.remove(CSV_COLUMN_AUTHORS);

        final ArrayList<Author> list;
        if (encodedList.isEmpty()) {
            // check for individual author (full/family/given) fields in the input
            list = new ArrayList<>();
            if (book.contains(DBKey.AUTHOR_FORMATTED)) {
                final String name = book.getString(DBKey.AUTHOR_FORMATTED);
                if (!name.isEmpty()) {
                    list.add(Author.from(name));
                }
                book.remove(DBKey.AUTHOR_FORMATTED);

            } else if (book.contains(DBKey.AUTHOR_FAMILY_NAME)) {
                final String family = book.getString(DBKey.AUTHOR_FAMILY_NAME);
                if (!family.isEmpty()) {
                    // given will be "" if it's not present
                    final String given = book.getString(DBKey.AUTHOR_GIVEN_NAMES);
                    list.add(new Author(family, given));
                }
                book.remove(DBKey.AUTHOR_FAMILY_NAME);
                book.remove(DBKey.AUTHOR_GIVEN_NAMES);

            } else if (book.contains(LEGACY_AUTHOR_NAME)) {
                final String a = book.getString(LEGACY_AUTHOR_NAME);
                if (!a.isEmpty()) {
                    list.add(Author.from(a));
                }
                book.remove(LEGACY_AUTHOR_NAME);
            }
        } else {
            list = authorCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getAuthorDao().pruneList(context, list, false, bookLocale);
            }
        }

        // we MUST have an author.
        if (list.isEmpty()) {
            list.add(Author.createUnknownAuthor(context));
        }
        book.setAuthors(list);
    }

    /**
     * Process the list of Series.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeSeries(@NonNull final Context context,
                              @NonNull final Book /* in/out */ book,
                              @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_SERIES);
        book.remove(CSV_COLUMN_SERIES);

        if (encodedList.isEmpty()) {
            // check for individual series title/number fields in the input
            if (book.contains(DBKey.SERIES_TITLE)) {
                final String title = book.getString(DBKey.SERIES_TITLE);
                if (!title.isEmpty()) {
                    final Series series = new Series(title);
                    // number will be "" if it's not present
                    series.setNumber(book.getString(DBKey.SERIES_BOOK_NUMBER));
                    final List<Series> list = new ArrayList<>();
                    list.add(series);
                    book.setSeries(list);
                }
                book.remove(DBKey.SERIES_TITLE);
                book.remove(DBKey.SERIES_BOOK_NUMBER);
            }
        } else {
            final ArrayList<Series> list = seriesCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getSeriesDao().pruneList(context, list, false, bookLocale);
                book.setSeries(list);
            }
        }
    }

    /**
     * Process the list of Publishers.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodePublishers(@NonNull final Context context,
                                  @NonNull final Book /* in/out */ book,
                                  @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_PUBLISHERS);
        book.remove(CSV_COLUMN_PUBLISHERS);

        if (!encodedList.isEmpty()) {
            final ArrayList<Publisher> list = publisherCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getPublisherDao().pruneList(context, list, false, bookLocale);
                book.setPublishers(list);
            }
        }
    }

    /**
     * Process the list of Toc entries.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignores the actual value of the {@link DBDefinitions#DOM_BOOK_TOC_TYPE}.
     * It will be computed when storing the book data.
     *
     * @param context    Current context
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeToc(@NonNull final Context context,
                           @NonNull final Book /* in/out */ book,
                           @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(CSV_COLUMN_TOC);
        book.remove(CSV_COLUMN_TOC);

        if (!encodedList.isEmpty()) {
            final ArrayList<TocEntry> list = tocCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                serviceLocator.getTocEntryDao().pruneList(context, list, false, bookLocale);
                book.setToc(list);
            }
        }
    }

}
