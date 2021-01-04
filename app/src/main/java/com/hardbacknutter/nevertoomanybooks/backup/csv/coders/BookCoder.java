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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;

/**
 * Note: the keys for the CSV columns are not the same as the internal Book keys
 * due to backward compatibility.
 * TODO: make the current ones LEGACY, and start using the Books keys, but still support reading
 * the old ones.
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

    private static final String EMPTY_QUOTED_STRING = "\"\"";
    private static final String COMMA = ",";

    /** Obsolete/alternative header: full given+family author name. */
    private static final String LEGACY_AUTHOR_NAME = "author_name";
    /** Obsolete/alternative header: bookshelf name. */
    private static final String LEGACY_BOOKSHELF_TEXT = "bookshelf_text";
    /** Obsolete, not used. */
    private static final String LEGACY_BOOKSHELF_ID = "bookshelf_id";
    /** Obsolete/alternative header: bookshelf name. Used by pre-1.2 versions. */
    private static final String LEGACY_BOOKSHELF_1_1_x = "bookshelf";

    /**
     * The order of the header MUST be the same as the order used to write the data (obvious eh?).
     * <p>
     * The fields CSV_COLUMN_* are {@link StringList} encoded
     * <p>
     * External id columns will be added to the end before writing starts.
     */
    private static final String EXPORT_FIELD_HEADERS_BASE =
            '"' + DBDefinitions.KEY_PK_ID + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_UUID + '"'
            + COMMA + '"' + DBDefinitions.KEY_UTC_LAST_UPDATED + '"'
            + COMMA + '"' + CSV_COLUMN_AUTHORS + '"'
            + COMMA + '"' + DBDefinitions.KEY_TITLE + '"'
            + COMMA + '"' + DBDefinitions.KEY_ISBN + '"'
            + COMMA + '"' + CSV_COLUMN_PUBLISHERS + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRINT_RUN + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_PUBLISHED + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_FIRST_PUBLICATION + '"'
            + COMMA + '"' + DBDefinitions.KEY_EDITION_BITMASK + '"'
            + COMMA + '"' + DBDefinitions.KEY_RATING + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOKSHELF_NAME + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ + '"'
            + COMMA + '"' + CSV_COLUMN_SERIES + '"'
            + COMMA + '"' + DBDefinitions.KEY_PAGES + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRIVATE_NOTES + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_CONDITION + '"'
            + COMMA + '"' + DBDefinitions.KEY_BOOK_CONDITION_COVER + '"'

            + COMMA + '"' + DBDefinitions.KEY_PRICE_LISTED + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_LISTED_CURRENCY + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_PAID + '"'
            + COMMA + '"' + DBDefinitions.KEY_PRICE_PAID_CURRENCY + '"'
            + COMMA + '"' + DBDefinitions.KEY_DATE_ACQUIRED + '"'

            + COMMA + '"' + DBDefinitions.KEY_TOC_BITMASK + '"'
            + COMMA + '"' + DBDefinitions.KEY_LOCATION + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ_START + '"'
            + COMMA + '"' + DBDefinitions.KEY_READ_END + '"'
            + COMMA + '"' + DBDefinitions.KEY_FORMAT + '"'
            + COMMA + '"' + DBDefinitions.KEY_COLOR + '"'
            + COMMA + '"' + DBDefinitions.KEY_SIGNED + '"'
            + COMMA + '"' + DBDefinitions.KEY_LOANEE + '"'
            + COMMA + '"' + CSV_COLUMN_TOC + '"'
            + COMMA + '"' + DBDefinitions.KEY_DESCRIPTION + '"'
            + COMMA + '"' + DBDefinitions.KEY_GENRE + '"'
            + COMMA + '"' + DBDefinitions.KEY_LANGUAGE + '"'
            + COMMA + '"' + DBDefinitions.KEY_UTC_ADDED + '"'

            + COMMA + '"' + DBDefinitions.KEY_CALIBRE_ID + '"'
            + COMMA + '"' + DBDefinitions.KEY_CALIBRE_UUID + '"'
            + COMMA + '"' + DBDefinitions.KEY_CALIBRE_FILE_URL + '"';

    private final StringList<Author> mAuthorCoder = new StringList<>(new AuthorCoder());
    private final StringList<Series> mSeriesCoder = new StringList<>(new SeriesCoder());
    private final StringList<Publisher> mPublisherCoder = new StringList<>(new PublisherCoder());
    private final StringList<TocEntry> mTocCoder = new StringList<>(new TocEntryCoder());
    private final StringList<Bookshelf> mBookshelfCoder;

    private final List<Domain> externalIdDomains;

    public BookCoder(@NonNull final ListStyle defStyle) {
        mBookshelfCoder = new StringList<>(new BookshelfCoder(defStyle));

        externalIdDomains = SearchEngineRegistry.getInstance().getExternalIdDomains();
    }

    @NonNull
    public String encodeHeader() {
        // row 0 with the column labels
        final StringBuilder columnLabels = new StringBuilder(EXPORT_FIELD_HEADERS_BASE);
        for (final Domain domain : externalIdDomains) {
            columnLabels.append(COMMA).append('"').append(domain.getName()).append('"');
        }
        //NEWTHINGS: adding a new search engine: optional: add engine specific keys
        columnLabels.append(COMMA).append('"')
                    .append(DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE).append('"');

        return columnLabels.toString();
    }

    @NonNull
    public String encode(@NonNull final Book book) {
        final StringJoiner line = new StringJoiner(",");

        line.add(encode(book.getLong(DBDefinitions.KEY_PK_ID)));
        line.add(encode(book.getString(DBDefinitions.KEY_BOOK_UUID)));
        line.add(encode(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED)));
        line.add(encode(mAuthorCoder.encodeList(
                book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST))));
        line.add(encode(book.getString(DBDefinitions.KEY_TITLE)));
        line.add(encode(book.getString(DBDefinitions.KEY_ISBN)));
        line.add(encode(mPublisherCoder.encodeList(
                book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST))));
        line.add(encode(book.getString(DBDefinitions.KEY_PRINT_RUN)));
        line.add(encode(book.getString(DBDefinitions.KEY_DATE_PUBLISHED)));
        line.add(encode(book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION)));
        line.add(encode(book.getLong(DBDefinitions.KEY_EDITION_BITMASK)));
        line.add(encode(book.getDouble(DBDefinitions.KEY_RATING)));
        line.add(encode(mBookshelfCoder.encodeList(
                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST))));
        line.add(encode(book.getInt(DBDefinitions.KEY_READ)));
        line.add(encode(mSeriesCoder.encodeList(
                book.getParcelableArrayList(Book.BKEY_SERIES_LIST))));
        line.add(encode(book.getString(DBDefinitions.KEY_PAGES)));
        line.add(encode(book.getString(DBDefinitions.KEY_PRIVATE_NOTES)));
        line.add(encode(book.getString(DBDefinitions.KEY_BOOK_CONDITION)));
        line.add(encode(book.getString(DBDefinitions.KEY_BOOK_CONDITION_COVER)));
        line.add(encode(book.getDouble(DBDefinitions.KEY_PRICE_LISTED)));
        line.add(encode(book.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY)));
        line.add(encode(book.getDouble(DBDefinitions.KEY_PRICE_PAID)));
        line.add(encode(book.getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY)));
        line.add(encode(book.getString(DBDefinitions.KEY_DATE_ACQUIRED)));
        line.add(encode(book.getLong(DBDefinitions.KEY_TOC_BITMASK)));
        line.add(encode(book.getString(DBDefinitions.KEY_LOCATION)));
        line.add(encode(book.getString(DBDefinitions.KEY_READ_START)));
        line.add(encode(book.getString(DBDefinitions.KEY_READ_END)));
        line.add(encode(book.getString(DBDefinitions.KEY_FORMAT)));
        line.add(encode(book.getString(DBDefinitions.KEY_COLOR)));
        line.add(encode(book.getInt(DBDefinitions.KEY_SIGNED)));
        line.add(encode(book.getString(DBDefinitions.KEY_LOANEE)));
        line.add(encode(mTocCoder.encodeList(book.getParcelableArrayList(Book.BKEY_TOC_LIST))));
        line.add(encode(book.getString(DBDefinitions.KEY_DESCRIPTION)));
        line.add(encode(book.getString(DBDefinitions.KEY_GENRE)));
        line.add(encode(book.getString(DBDefinitions.KEY_LANGUAGE)));
        line.add(encode(book.getString(DBDefinitions.KEY_UTC_ADDED)));

        line.add(encode(book.getInt(DBDefinitions.KEY_CALIBRE_ID)));
        line.add(encode(book.getString(DBDefinitions.KEY_CALIBRE_UUID)));
        line.add(encode(book.getString(DBDefinitions.KEY_CALIBRE_FILE_URL)));

        // external ID's
        for (final Domain domain : externalIdDomains) {
            line.add(encode(book.getString(domain.getName())));
        }
        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        line.add(encode(book.getString(DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE)));

        return line.toString();
    }

    @NonNull
    private String encode(final long cell) {
        return encode(String.valueOf(cell));
    }

    @NonNull
    private String encode(final double cell) {
        return encode(String.valueOf(cell));
    }

    /**
     * Double quote all "'s and remove all newlines.
     *
     * @param source to encode
     *
     * @return The encoded cell enclosed in escaped quotes
     */
    @NonNull
    private String encode(@Nullable final String source) {

        try {
            if (source == null || "null".equalsIgnoreCase(source) || source.trim().isEmpty()) {
                return EMPTY_QUOTED_STRING;
            }

            final StringBuilder sb = new StringBuilder("\"");
            final int endPos = source.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                final char c = source.charAt(pos);
                switch (c) {
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;

                    case '"':
                        // quotes are escaped by doubling them
                        sb.append(EMPTY_QUOTED_STRING);
                        break;

                    default:
                        sb.append(c);
                        break;
                }
                pos++;

            }
            return sb.append('"').toString();
        } catch (@NonNull final NullPointerException e) {
            return EMPTY_QUOTED_STRING;
        }
    }


    public Book decode(@NonNull final Context context,
                       @NonNull final DAO db,
                       @NonNull final String[] csvColumnNames,
                       @NonNull final String[] csvDataRow) {
        final Book book = new Book();

        // Read all columns of the current row into the Bundle.
        // Note that some of them require further processing before being valid.
        for (int i = 0; i < csvColumnNames.length; i++) {
            book.putString(csvColumnNames[i], csvDataRow[i]);
        }

        // check/add a title
        if (book.getString(DBDefinitions.KEY_TITLE).isEmpty()) {
            book.putString(DBDefinitions.KEY_TITLE, context.getString(R.string.unknown_title));
        }

        // check/fix the language
        final Locale bookLocale = book.getLocale(context);

        // Database access is strictly limited to fetching ID's for the list elements.
        decodeAuthors(context, db, book, bookLocale);
        decodeSeries(context, db, book, bookLocale);
        decodePublishers(context, db, book, bookLocale);
        decodeToc(context, db, book, bookLocale);
        decodeBookshelves(db, book);

        //URGENT: implement full parsing/formatting of incoming dates for validity
        //verifyDates(context, mDb, book);

        return book;
    }

    /**
     * Process the bookshelves.
     * Database access is strictly limited to fetching ID's.
     *
     * @param db   Database Access
     * @param book the book
     */
    private void decodeBookshelves(@NonNull final DAO db,
                                   @NonNull final Book /* in/out */ book) {

        String encodedList = null;

        if (book.contains(DBDefinitions.KEY_BOOKSHELF_NAME)) {
            // current version
            encodedList = book.getString(DBDefinitions.KEY_BOOKSHELF_NAME);

        } else if (book.contains(LEGACY_BOOKSHELF_1_1_x)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_1_1_x);

        } else if (book.contains(LEGACY_BOOKSHELF_TEXT)) {
            // obsolete
            encodedList = book.getString(LEGACY_BOOKSHELF_TEXT);
        }

        if (encodedList != null && !encodedList.isEmpty()) {
            final ArrayList<Bookshelf> bookshelves = mBookshelfCoder.decodeList(encodedList);
            if (!bookshelves.isEmpty()) {
                Bookshelf.pruneList(bookshelves, db);
                book.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelves);
            }
        }

        book.remove(LEGACY_BOOKSHELF_ID);
        book.remove(LEGACY_BOOKSHELF_TEXT);
        book.remove(LEGACY_BOOKSHELF_1_1_x);
        book.remove(DBDefinitions.KEY_BOOKSHELF_NAME);
    }

    /**
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeAuthors(@NonNull final Context context,
                               @NonNull final DAO db,
                               @NonNull final Book /* in/out */ book,
                               @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(BookCoder.CSV_COLUMN_AUTHORS);
        book.remove(BookCoder.CSV_COLUMN_AUTHORS);

        final ArrayList<Author> list;
        if (!encodedList.isEmpty()) {
            list = mAuthorCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                Author.pruneList(list, context, db, false, bookLocale);
            }
        } else {
            // check for individual author (full/family/given) fields in the input
            list = new ArrayList<>();
            if (book.contains(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                final String name = book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                if (!name.isEmpty()) {
                    list.add(Author.from(name));
                }
                book.remove(DBDefinitions.KEY_AUTHOR_FORMATTED);

            } else if (book.contains(DBDefinitions.KEY_AUTHOR_FAMILY_NAME)) {
                final String family = book.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                if (!family.isEmpty()) {
                    // given will be "" if it's not present
                    final String given = book.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                    list.add(new Author(family, given));
                }
                book.remove(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                book.remove(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);

            } else if (book.contains(LEGACY_AUTHOR_NAME)) {
                final String a = book.getString(LEGACY_AUTHOR_NAME);
                if (!a.isEmpty()) {
                    list.add(Author.from(a));
                }
                book.remove(LEGACY_AUTHOR_NAME);
            }
        }

        // we MUST have an author.
        if (list.isEmpty()) {
            list.add(Author.createUnknownAuthor(context));
        }
        book.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, list);
    }

    /**
     * Process the list of Series.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeSeries(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final Book /* in/out */ book,
                              @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(BookCoder.CSV_COLUMN_SERIES);
        book.remove(BookCoder.CSV_COLUMN_SERIES);

        if (!encodedList.isEmpty()) {
            final ArrayList<Series> list = mSeriesCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                Series.pruneList(list, context, db, false, bookLocale);
                book.putParcelableArrayList(Book.BKEY_SERIES_LIST, list);
            }
        } else {
            // check for individual series title/number fields in the input
            if (book.contains(DBDefinitions.KEY_SERIES_TITLE)) {
                final String title = book.getString(DBDefinitions.KEY_SERIES_TITLE);
                if (!title.isEmpty()) {
                    final Series series = new Series(title);
                    // number will be "" if it's not present
                    series.setNumber(book.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES));
                    final ArrayList<Series> list = new ArrayList<>();
                    list.add(series);
                    book.putParcelableArrayList(Book.BKEY_SERIES_LIST, list);
                }
                book.remove(DBDefinitions.KEY_SERIES_TITLE);
                book.remove(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
            }
        }
    }

    /**
     * Process the list of Publishers.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodePublishers(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final Book /* in/out */ book,
                                  @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(BookCoder.CSV_COLUMN_PUBLISHERS);
        book.remove(BookCoder.CSV_COLUMN_PUBLISHERS);

        if (!encodedList.isEmpty()) {
            final ArrayList<Publisher> list = mPublisherCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                Publisher.pruneList(list, context, db, false, bookLocale);
                book.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, list);
            }
        }
    }

    /**
     * Process the list of Toc entries.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Ignores the actual value of the DBDefinitions.KEY_TOC_BITMASK.
     * It will be computed when storing the book data.
     *
     * @param context    Current context
     * @param db         Database Access
     * @param book       the book
     * @param bookLocale of the book, already resolved
     */
    private void decodeToc(@NonNull final Context context,
                           @NonNull final DAO db,
                           @NonNull final Book /* in/out */ book,
                           @NonNull final Locale bookLocale) {

        final String encodedList = book.getString(BookCoder.CSV_COLUMN_TOC);
        book.remove(BookCoder.CSV_COLUMN_TOC);

        if (!encodedList.isEmpty()) {
            final ArrayList<TocEntry> list = mTocCoder.decodeList(encodedList);
            if (!list.isEmpty()) {
                // Force using the Book Locale, otherwise the import is far to slow.
                TocEntry.pruneList(list, context, db, false, bookLocale);
                book.putParcelableArrayList(Book.BKEY_TOC_LIST, list);
            }
        }
    }

}
