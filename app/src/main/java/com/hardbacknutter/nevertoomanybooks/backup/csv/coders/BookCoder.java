/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvFormat;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvGoodreads;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.LegacyUpgrades;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.TagMapper;

/**
 * Note: the keys for the CSV columns are not the same as the internal Book keys
 * due to backward compatibility.
 * <p>
 * <strong>LIMITATIONS:</strong> Calibre book data is handled, but Calibre library is NOT.
 * The Calibre native string-id is written out with the book.
 * <p>
 * When reading, the Calibre native string-id is checked against already existing data,
 * but if there is no match all Calibre data for the book is discarded.
 * <p>
 * In other words: this coder is NOT a full backup/restore!
 */
@SuppressWarnings("SameParameterValue")
public class BookCoder {

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
    /** Obsolete header from BC. */
    private static final String LEGACY_LAST_GOODREADS_SYNC_DATE = "last_goodreads_sync_date";
    /** Migrated to tags starting in app version 7.0. */
    private static final String LEGACY_GENRE = "genre";

    @NonNull
    private final StringList<Author> authorCoder;
    @NonNull
    private final StringList<Bookshelf> bookshelfCoder;
    @NonNull
    private final StringList<Publisher> publisherCoder;
    @NonNull
    private final StringList<Series> seriesCoder;
    @NonNull
    private final StringList<TocEntry> tocCoder;

    @NonNull
    private final Author unknownAuthor;
    @NonNull
    private final CsvFormat csvFormat;
    @NonNull
    private final Style defaultStyle;
    private final FullDateParser dateParser;
    private final RatingParser ratingParser;
    private final TagMapper tagMapper;
    private final RealNumberParser realNumberParser;
    @Nullable
    private CsvGoodreads goodreads;
    @Nullable
    private Map<String, Long> calibreLibraryStr2IdMap;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param csvFormat    type/origin of the input data to decode
     * @param defaultStyle the default style to use for {@link Bookshelf}s
     */
    public BookCoder(@NonNull final Context context,
                     @NonNull final CsvFormat csvFormat,
                     @NonNull final Style defaultStyle) {
        this.csvFormat = csvFormat;
        this.defaultStyle = defaultStyle;

        authorCoder = new StringList<>(new AuthorCoder());
        // Backwards compatibility: BookshelfCoder elementSeparator MUST be a ','
        bookshelfCoder = new StringList<>(new BookshelfCoder(',', defaultStyle));
        publisherCoder = new StringList<>(new PublisherCoder());
        seriesCoder = new StringList<>(new SeriesCoder());
        tocCoder = new StringList<>(new TocEntryCoder());

        unknownAuthor = Author.createUnknownAuthor(context);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(userLocales);
        dateParser = new FullDateParser(new ISODateParser(systemLocale), allLocales);

        // Make sure US is added so we can parse "dot" decimal-separator
        // even when the user Locale is a comma decimal-separator.
        // FIXME LOCALE: this does NOT support the case where a user's Locales only
        //  use "dot" decimal-separator and they are importing a number with a comma!
        if (allLocales.contains(Locale.US)) {
            realNumberParser = new RealNumberParser(allLocales);
            ratingParser = csvFormat.createRatingParser(allLocales);
        } else {
            final List<Locale> tmp = new ArrayList<>(allLocales);
            tmp.add(Locale.US);
            realNumberParser = new RealNumberParser(tmp);
            ratingParser = csvFormat.createRatingParser(tmp);
        }

        tagMapper = new TagMapper(context);
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

        // never used, remove if present
        book.remove(LEGACY_BOOKSHELF_ID);
        book.remove(LEGACY_LAST_GOODREADS_SYNC_DATE);

        // we MUST have a title.
        if (book.getTitle().isEmpty()) {
            book.setTitle(context.getString(R.string.unknown_title));
        }

        // check/fix the language
        book.getAndUpdateLocale(context, true);

        // Database access is strictly limited to fetching ID's for any list elements.
        processIsbn(book);
        processAuthors(book);
        processSeries(book);
        processPublishers(book);
        processToc(book);
        processBookshelves(book);
        processCalibreData(book);
        processRating(book);
        processPrice(book);
        processDescriptionAndNotes(book);
        processGenre(context, book);
        processExternalIds(book);

        verifyDates(book, DBKey.DATETIME_KEYS, false);
        verifyDates(book, DBKey.DATE_KEYS, true);

        return book;
    }

    @NonNull
    private CsvGoodreads getGoodreads() {
        if (goodreads == null) {
            goodreads = new CsvGoodreads(defaultStyle);
        }
        return goodreads;
    }

    /**
     * Process alternative keys for the ISBN and clean the ISBN text as needed.
     *
     * @param book to process
     */
    private void processIsbn(@NonNull final Book book) {
        if (!book.hasIsbn() && book.contains(CsvGoodreads.ISBN10)) {
            book.setIsbn(book.getString(CsvGoodreads.ISBN10));
        }
        book.remove(CsvGoodreads.ISBN10);

        if (book.hasIsbn()) {
            // ALWAYS try to clean the ISBN.
            final String raw = book.getIsbn();

            // We have seen the string:  "9.78E+12"
            // The original writer must have been writing isbn numbers as floating-point values.
            // We're explicitly discarding these.
            if ("9.78E+12".equals(raw) || "9.79E+12".equals(raw)) {
                book.remove(DBKey.ISBN);
                return;
            }

            // We've seen Goodreads csv file with nightmares like this:
            //     "=""9789027409294"""  and "="""""
            // Note that we clean the string, but do NOT check on the length here.
            // We want non-isbn string with simple values to pass through
            book.setIsbn(ISBN.cleanText(raw));
        }
    }

    /**
     * Process the list of Authors.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param book to process
     */
    private void processAuthors(@NonNull final Book book) {
        final List<Author> list = book.getAuthors();

        processAuthor(book, authorCoder, CSV_COLUMN_AUTHORS, list);
        processAuthor(book, authorCoder, DBKey.AUTHOR.FORMATTED_FULL_NAME, list);
        processAuthor(book, authorCoder, LEGACY_AUTHOR_NAME, list);
        processAuthor(book, getGoodreads().getAuthorCoder(),
                      CsvGoodreads.ADDITIONAL_AUTHORS, list);

        // check for individual author family/given fields in the input
        if (book.contains(DBKey.AUTHOR.FAMILY_NAME)) {
            final String family = book.getString(DBKey.AUTHOR.FAMILY_NAME, null);
            if (family != null && !family.isEmpty()) {
                final String given = book.getString(DBKey.AUTHOR.GIVEN_NAMES, null);
                list.add(new Author(family, given));
            }
            book.remove(DBKey.AUTHOR.FAMILY_NAME);
            book.remove(DBKey.AUTHOR.GIVEN_NAMES);
        }

        // we MUST have an author.
        if (list.isEmpty()) {
            list.add(unknownAuthor);
        }

        book.setAuthors(list);
    }

    private void processAuthor(@NonNull final Book book,
                               @NonNull final StringList<Author> coder,
                               @NonNull final String key,
                               @NonNull final List<Author> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            book.remove(key);

            if (encodedList != null && !encodedList.isEmpty()) {
                // Do not add if already there.
                // We need to do this here (before going to the database)
                // so we can keep them in the exact order as they come in.
                // This is particularly important for Goodreads imports
                coder.decodeList(encodedList).forEach(author -> {
                    if (list.stream().noneMatch(a -> a.isSameName(author))) {
                        list.add(author);
                    }
                });
                // In addition we can have the following duplicates:
                //
                // Author,Author l-f,Additional Authors
                // Liu Cixin,"Cixin, Liu","Ken Liu, Cixin Liu"
                // First field is skipped
                // Second field is the primary Author in "Last, Firstname" format.
                // Third fields is a comma sep. list
                // PROBLEM: normally the 3rd field is e.g. "Isaac Asimov"
                // in other words, "FIRSTNAME LASTNAME" and the decoding will work properly.
                // For chinese names (which we want to test explicitly here) the
                // notation is always "LASTNAME FIRSTNAME" and we end up with a duplicate
                // in the wrong order.

                // [Author{id=1, familyName=`Cixin`, givenNames=`Liu`, ...
                // Author{id=2, familyName=`Liu`, givenNames=`Ken`, ...
                // Author{id=3, familyName=`Liu`, givenNames=`Cixin`, ...

                // FIXME: do NOT do this in the AuthorDao#prune method; do it HERE
                //  AuthorDao#prune method is used in locations where the user supposedly
                //  already cleaned Author names; so we might get false positives.
                // But HERE, we already warned the user that a CSV import is not foolproof.
                // and based on the currently supported sources:
                // BC/NTMB: should not be an issue unless the data is simply bad.
                // Goodreads: see above, doing it here WILL generate correct data.
            }
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
        final List<Series> list = book.getSeries();

        processSeries(book, seriesCoder, CSV_COLUMN_SERIES, list);

        // check for individual series title/number fields in the input
        if (book.contains(DBKey.SERIES.TITLE)) {
            final String title = book.getString(DBKey.SERIES.TITLE, null);
            if (title != null && !title.isEmpty()) {
                final Series series = new Series(title);
                // number will be "" if it's not present
                series.setNumber(book.getString(DBKey.SERIES.BOOK_SERIES_NUMBER, null));
                list.add(series);
            }
            book.remove(DBKey.SERIES.TITLE);
            book.remove(DBKey.SERIES.BOOK_SERIES_NUMBER);
        }

        Series.checkForSeriesNameInTitle(book);

        if (!list.isEmpty()) {
            book.setSeries(list);
        }
    }

    private void processSeries(@NonNull final Book book,
                               @NonNull final StringList<Series> coder,
                               @NonNull final String key,
                               @NonNull final List<Series> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            book.remove(key);

            if (encodedList != null && !encodedList.isEmpty()) {
                // Weeding out duplicates here is likely overkill but oh well.
                coder.decodeList(encodedList).forEach(series -> {
                    if (list.stream().noneMatch(bs -> bs.isSameName(series)
                                                      && bs.getNumber()
                                                           .equals(series.getNumber()))) {
                        list.add(series);
                    }
                });
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
        final List<Publisher> list = book.getPublishers();

        processPublisher(book, publisherCoder, CSV_COLUMN_PUBLISHERS, list);
        processPublisher(book, publisherCoder, DBKey.PUBLISHER.NAME, list);

        if (!list.isEmpty()) {
            book.setPublishers(list);
        }
    }

    private void processPublisher(@NonNull final Book book,
                                  @NonNull final StringList<Publisher> coder,
                                  @NonNull final String key,
                                  @NonNull final List<Publisher> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            book.remove(key);

            if (encodedList != null && !encodedList.isEmpty()) {
                // Weeding out duplicates here is likely overkill but oh well.
                coder.decodeList(encodedList).forEach(publisher -> {
                    if (list.stream().noneMatch(bs -> bs.isSameName(publisher))) {
                        list.add(publisher);
                    }
                });
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
        final List<TocEntry> list = book.getToc();

        processToc(book, tocCoder, CSV_COLUMN_TOC, list);

        if (!list.isEmpty()) {
            book.setToc(list);
        }
    }

    private void processToc(@NonNull final Book book,
                            @NonNull final StringList<TocEntry> coder,
                            @NonNull final String key,
                            @NonNull final List<TocEntry> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            book.remove(key);

            if (encodedList != null && !encodedList.isEmpty()) {
                // Weeding out duplicates here is likely overkill but oh well.
                coder.decodeList(encodedList).forEach(tocEntry -> {
                    if (list.stream().noneMatch(bs -> bs.isSameName(tocEntry))) {
                        list.add(tocEntry);
                    }
                });
            }
        }
    }

    /**
     * Process the list of Bookshelves.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param book to process
     */
    private void processBookshelves(@NonNull final Book book) {
        final List<Bookshelf> list = book.getBookshelves();

        processBookshelf(book, bookshelfCoder, DBKey.BOOKSHELF.NAME, list);
        processBookshelf(book, bookshelfCoder, LEGACY_BOOKSHELF_1_1, list);
        processBookshelf(book, bookshelfCoder, LEGACY_BOOKSHELF_TEXT, list);

        if (book.contains(CsvGoodreads.BOOKSHELVES)
            || book.contains(CsvGoodreads.EXCLUSIVE_SHELF)) {
            //ENHANCE: provide mapping for the Goodreads "read", "to-read" and "currently-reading"
            // fixed shelves. For now we just create those 3 when not there yet.
            // If 'read' is present, we also set our DBKey.READ__BOOL flag.
            final StringList<Bookshelf> grBookshelfCoder = getGoodreads().getBookshelfCoder();
            processBookshelf(book, grBookshelfCoder, CsvGoodreads.BOOKSHELVES, list);
            processBookshelf(book, grBookshelfCoder, CsvGoodreads.EXCLUSIVE_SHELF, list);

            if (list.stream().anyMatch(bookshelf -> "read".equals(bookshelf.getName()))) {
                // DO NOT use book.setRead(true) as that will set related fields
                // which is not desired here as that might overwrite incoming data
                book.putBoolean(DBKey.READ__BOOL, true);
            }
        }
        book.remove(CsvGoodreads.BOOKSHELVES);
        book.remove(CsvGoodreads.EXCLUSIVE_SHELF);

        if (!list.isEmpty()) {
            book.setBookshelves(list);
        }
    }

    private void processBookshelf(@NonNull final Book book,
                                  @NonNull final StringList<Bookshelf> coder,
                                  @NonNull final String key,
                                  @NonNull final List<Bookshelf> list) {
        if (book.contains(key)) {
            final String encodedList = book.getString(key, null);
            book.remove(key);

            if (encodedList != null && !encodedList.isEmpty()) {
                // Do not add if already there.
                // We need to do this here (before going to the database)
                // so we can keep them in the exact order as they come in.
                // This is particularly important for Goodreads imports
                coder.decodeList(encodedList).forEach(bookshelf -> {
                    if (list.stream().noneMatch(bs -> bs.isSameName(bookshelf))) {
                        list.add(bookshelf);
                    }
                });
            }
        }
    }

    private void processCalibreData(@NonNull final Book book) {
        // we need to convert the string id to the row id.
        final String stringId = book.getString(DBKey.CALIBRE.LIBRARY_STRING_ID, null);
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

            // and discard the string-id
            book.remove(DBKey.CALIBRE.LIBRARY_STRING_ID);
        }
    }

    @SuppressWarnings("IfStatementWithNegatedCondition")
    private void processRating(@NonNull final Book book) {
        // Read the values as String and parse to verify they are valid.
        // The order of the if's is important!

        if (book.contains(DBKey.RATING)) {
            // If the key DBKey.RATING is present, we ALWAYS use it even if it's 0/empty
            ratingParser.parse(book.getString(DBKey.RATING)).ifPresent(book::setRating);
        } else {
            // Goodreads imports:
            // Try MY_RATING first
            String s = book.getString(CsvGoodreads.MY_RATING);
            if (!NumberParser.isZero(s)) {
                // if we have a non-zero, we use it.
                ratingParser.parse(s).ifPresent(book::setRating);
            } else {
                // otherwise try AVERAGE_RATING
                s = book.getString(CsvGoodreads.AVERAGE_RATING);
                if (!NumberParser.isZero(s)) {
                    // if we have a non-zero, we use it.
                    ratingParser.parse(book.getString(CsvGoodreads.AVERAGE_RATING))
                                .ifPresent(book::setRating);
                }
            }
        }
        // no need to keep these now
        book.remove(CsvGoodreads.MY_RATING);
        book.remove(CsvGoodreads.AVERAGE_RATING);
    }

    private void processPrice(@NonNull final Book book) {
        if (book.contains(DBKey.PRICE_LISTED)) {
            final String s = book.getString(DBKey.PRICE_LISTED);
            if (s.isEmpty()) {
                // might as well remove empty values
                book.remove(DBKey.PRICE_LISTED);
            } else {
                try {
                    final double v = realNumberParser.parseDouble(s);
                    book.putDouble(DBKey.PRICE_LISTED, v);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore, drop the field
                    book.remove(DBKey.PRICE_LISTED);
                }
            }
        }
    }

    private void processDescriptionAndNotes(@NonNull final Book book) {
        //ENHANCE: Create a new field for a personal review.
        // For now, just concatenate with the private notes... we'll probably regret this later...
        // .
        // We don't want to use the DBKey.DESCRIPTION field!
        // The description is supposed to be a generic description, the back cover text, etc...
        final String review = book.getString(CsvGoodreads.MY_REVIEW);
        if (!review.isEmpty()) {
            String notes = book.getString(DBKey.PERSONAL_NOTES);
            if (!notes.isEmpty()) {
                notes += "\n\n";
            }
            notes += review;
            book.putString(DBKey.PERSONAL_NOTES, notes);
            book.remove(CsvGoodreads.MY_REVIEW);
        }
    }

    private void processGenre(@NonNull final Context context,
                              @NonNull final Book book) {
        final String genre = book.getString(LEGACY_GENRE);
        if (!genre.isEmpty()) {
            book.getTags().addAll(LegacyUpgrades.migrateGenre(genre));
            tagMapper.map(context, book);
            book.remove(LEGACY_GENRE);
        }
    }

    /**
     * We have no certainty about the csv columns containing external {@link Identifier} keys.
     * What we CAN do is simply check the keys present against the known identifier keys.
     * <p>
     * It's up to the user to add an unknown {@link Identifier} key <strong>before</strong>
     * doing the csv import.
     *
     * @param book the book
     */
    private void processExternalIds(@NonNull final Book book) {
        final Set<String> known = ServiceLocator.getInstance().getIdentifierDao().getAll()
                                                .stream().map(Identifier::getKey)
                                                .collect(Collectors.toSet());
        final List<Identifier.Value> ivs = new ArrayList<>();
        book.keySet().forEach(key -> {
            if (known.contains(key)) {
                final String sid = book.getString(key);
                if (!sid.isEmpty() && !"0".equals(sid)) {
                    ivs.add(new Identifier.Value(key, sid));
                }
                book.remove(key);
            }
        });
        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }
    }

    /**
     * Verify the given date keys for containing valid dates.
     *
     * @param book        to verify
     * @param keys        to verify
     * @param shortenDate flag: {@code true} to cut dates down to partial dates.
     *                    i.e. remove time and any tailing "-01".
     */
    private void verifyDates(@NonNull final Book book,
                             @NonNull final Set<String> keys,
                             final boolean shortenDate) {
        keys.stream().filter(book::contains).forEach(key -> {
            final String s = book.getString(key);
            final Optional<LocalDateTime> date = dateParser.parse(s);
            if (date.isPresent()) {
                String iso = SqlEncode.dateTime(date.get());
                if (shortenDate) {
                    if (iso.length() > 10) {
                        // cut off the time
                        iso = iso.substring(0, 10);
                        // 'YYYY-MM-DD' cut down to month or year if possible
                        while (iso.endsWith("-01")) {
                            iso = iso.substring(0, iso.length() - 3);
                        }
                    }
                }
                book.putString(key, iso);
            } else {
                book.remove(key);
            }
        });
    }
}
