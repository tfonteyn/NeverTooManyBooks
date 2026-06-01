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
package com.hardbacknutter.nevertoomanybooks.backup.csv.goodreads;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.Mapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.MapperFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * From a test export on 2024-04-22 we got these known columns.
 * <p>
 * Book Id,Title,
 * Author,Author l-f,Additional Authors,
 * ISBN,ISBN13,
 * My Rating,Average Rating,
 * Publisher,Binding,Number of Pages,
 * Year Published,Original Publication Year,
 * Date Read,Date Added,
 * Bookshelves,Bookshelves with positions, Exclusive Shelf,
 * My Review,Spoiler,Private Notes,
 * Read Count,Owned Copies
 * <p>
 * Dates are in short ISO format but using a '/', e.g. "2024/05/23".
 */
@SuppressWarnings("SameParameterValue")
public class GoodreadsBookCoder
        implements BookCoder {

    private static final String TAG = "GoodreadsBookCoder";

    @NonNull
    private final StringList<Author> authorCoder;
    @NonNull
    private final StringList<Bookshelf> bookshelfCoder;
    @NonNull
    private final StringList<Publisher> publisherCoder;

    @NonNull
    private final Author unknownAuthor;
    private final DateParser<LocalDateTime> dateParser;
    private final RatingParser ratingParser;
    private final List<Locale> userLocales;

    private final List<String> csvColumnNames;
    private final Collection<Mapper> mappers;

    /**
     * Constructor.
     *
     * @param context        Current context
     * @param defaultStyle   the default style to use for {@link Bookshelf}s
     * @param userLocales    to use for parsing
     * @param csvColumnNames the list with the field(column) names
     */
    public GoodreadsBookCoder(@NonNull final Context context,
                              @NonNull final Style defaultStyle,
                              @NonNull final List<Locale> userLocales,
                              @NonNull final List<String> csvColumnNames) {

        this.userLocales = userLocales;
        this.csvColumnNames = csvColumnNames;

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        this.dateParser = new ISODateParser(systemLocale);

        this.ratingParser = new RatingParser(5);

        authorCoder = new StringList<>(new AuthorCoder());
        bookshelfCoder = new StringList<>(new BookshelfCoder(defaultStyle));
        publisherCoder = new StringList<>(new PublisherCoder());

        mappers = MapperFactory.create(context);
        unknownAuthor = Author.createUnknownAuthor(context);
    }

    /**
     * Database access is strictly limited to fetching ID's for the list elements.
     * <p>
     * Both csv lists <strong>must</strong> be the same length.
     *
     * @param context    Current context
     * @param csvDataRow the list with the field data
     *
     * @return the decoded book
     */
    @NonNull
    @Override
    public Book decode(@NonNull final Context context,
                       @NonNull final List<String> csvDataRow) {

        final Book book = new Book();

        for (int c = 0; c < csvColumnNames.size(); c++) {
            final String name = csvColumnNames.get(c).toLowerCase(Locale.ENGLISH);
            final String value = csvDataRow.get(c);
            if (value.isBlank()) {
                continue;
            }

            switch (name) {
                case "book id": {
                    book.setIdentifierValue(Identifier.SID_GOODREADS, value);
                    break;
                }
                case "title": {
                    book.setTitle(value);
                    Series.checkForSeriesNameInTitle(book);
                    break;
                }
                case "author l-f": {
                    // Always a single name, e.g. "Zelazny, Roger"
                    book.add(Author.from(value));
                    break;
                }
                case "additional authors": {
                    // Added in addition to the one above
                    // CSV list, e.g. "Ken Liu, Cixin Liu"
                    processAuthor(value, book);
                    break;
                }
                case "isbn": {
                    // ISBN-10
                    if (!book.hasIsbn()) {
                        book.setIsbn(ISBN.cleanText(value));
                    }
                    break;
                }
                case "isbn13": {
                    // We've seen Goodreads csv file with nightmares like this:
                    //     "=""9789027409294"""  and "="""""
                    // Note that we clean the string, but do NOT check on the length here.
                    // We want non-isbn string with simple values to pass through
                    book.setIsbn(ISBN.cleanText(value));
                    break;
                }
                case "my rating": {
                    processRating(value, book);
                    break;
                }
                case "average rating": {
                    // Don't overwrite "my rating"
                    // fetch as string to avoid unneeded parsing
                    final String s = book.getString(DBKey.RATING, null);
                    if (s == null || s.isEmpty()) {
                        processRating(value, book);
                    }
                    break;
                }
                case "publisher": {
                    processPublisher(value, book);
                    break;
                }
                case "binding": {
                    book.setFormat(value);
                    break;
                }
                case "number of pages": {
                    book.setPages(value);
                    break;
                }
                case "year published": {
                    dateParser.parse(value.replace('/', '-'))
                              .ifPresent(book::setPublicationDate);
                    break;
                }
                case "original publication year": {
                    dateParser.parse(value.replace('/', '-'))
                              .ifPresent(book::setFirstPublicationDate);
                    break;
                }
                case "date read": {
                    book.putString(DBKey.READ_END__DATE, value.replace('/', '-'));
                    break;
                }
                case "date added": {
                    book.putString(DBKey.DATE_ADDED__UTC, value.replace('/', '-'));
                    break;
                }
                case "bookshelves":
                case "exclusive shelf": {
                    processBookshelf(value, book);
                    break;
                }
                case "my review": {
                    processDescriptionAndNotes(value, false, book);
                    break;
                }
                case "private notes": {
                    processDescriptionAndNotes(value, true, book);
                    break;
                }

                // The next set are ignored for now
                case "author":
                    // ignored in favour of the "author l-f" field
                case "bookshelves with positions":
                    // we don't support positions for bookshelves
                case "spoiler":
                    // I believe this is a flag set when the "my review" field is
                    // considered to contain spoilers - not supported.
                case "read count":
                    // We only support read == true/false
                case "owned copies":
                    // We do not have a concept of multiple copies
                    // (although this could be a valid enhancement as we support lending out books)

                    break;

                default: {
                    // Unknown on 2024-04-22; log them for future support
                    LoggerFactory.getLogger()
                                 .w(TAG, "Unknown Goodreads csv column=" + name);
                    break;
                }
            }
        }

        // we MUST have a title.
        if (book.getTitle().isEmpty()) {
            book.setTitle(context.getString(R.string.unknown_title));
        }

        // we MUST have an author.
        if (book.getAuthors().isEmpty()) {
            book.add(unknownAuthor);
        }

        // check/fix the standard language field
        book.getLocaleAndUpdateLanguage(userLocales.get(0), true);

        mappers.forEach(mapper -> mapper.map(context, book));

        // Verifying the dates is overkill for now, but leaving it
        // as protection from input changes.

        // Full DateTime stamp.
        verifyDates(book, DBKey.getDateTimeKeys(), false, true);
        // Full Date stamp, no time
        verifyDates(book, DBKey.getFullDateKeys(), false, false);
        // Partial Date stamp, no time
        verifyDates(book, DBKey.getPartialDateKeys(), true, false);

        // GitHub #205: force the "read" flag to =1 if a read_end" is present
        // after the above validation of the date fields.
        if (book.contains(DBKey.READ_END__DATE)) {
            book.putBoolean(DBKey.READ__BOOL, true);
        }

        return book;
    }

    /**
     * Process the list of Authors.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * Get the list of authors from whatever source is available.
     * If none found, a generic "[Unknown author]" will be used.
     *
     * @param value to process
     * @param book  to update
     */
    private void processAuthor(@NonNull final CharSequence value,
                               @NonNull final Book book) {

        final List<Author> list = book.getAuthors();

        // Do not add if already there.
        // We need to do this here (before going to the database)
        // so we can keep them in the exact order as they come in.
        authorCoder.decodeList(value).forEach(author -> {
            if (list.stream().noneMatch(a -> a.isSameName(author))) {
                list.add(author);
            }
        });

        book.setAuthors(list);

        // In addition, we can have the following duplicates:
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
        // Goodreads: see above, doing it here WILL generate correct data.
    }

    /**
     * Process the list of Publishers.
     * <p>
     * Database access is strictly limited to fetching ID's.
     *
     * @param value to process
     * @param book  to update
     */
    private void processPublisher(@NonNull final CharSequence value,
                                  @NonNull final Book book) {
        final List<Publisher> list = book.getPublishers();

        // Weeding out duplicates here is likely overkill but oh well.
        publisherCoder.decodeList(value).forEach(publisher -> {
            if (list.stream().noneMatch(bs -> bs.isSameName(publisher))) {
                list.add(publisher);
            }
        });

        book.setPublishers(list);
    }

    /**
     * Process the list of Bookshelves.
     * <p>
     * Database access is strictly limited to fetching ID's.
     * <p>
     * ENHANCE: provide mapping for the Goodreads
     *     "read", "to-read", "currently-reading" and "did-not-finish"
     *      fixed shelves. For now we just create those when not there yet.
     *      If 'read' is present, we also set our DBKey.READ__BOOL flag.
     *
     * @param book to process
     */
    private void processBookshelf(@NonNull final CharSequence value,
                                  @NonNull final Book book) {

        final List<Bookshelf> list = book.getBookshelves();

        // Do not add if already there.
        // We need to do this here (before going to the database)
        // so we can keep them in the exact order as they come in.
        // This is particularly important for Goodreads imports
        bookshelfCoder.decodeList(value).forEach(bookshelf -> {
            if (list.stream().noneMatch(bs -> bs.isSameName(bookshelf))) {
                list.add(bookshelf);
            }
        });

        if (list.stream().anyMatch(bookshelf -> "read".equals(bookshelf.getName()))) {
            // DO NOT use book.setRead(true) as that will set related fields
            // which is not desired here as that might overwrite incoming data
            book.putBoolean(DBKey.READ__BOOL, true);
        }

        book.setBookshelves(list);
    }

    private void processRating(@NonNull final String value,
                               @NonNull final Book book) {
        if (!NumberParser.isZero(value)) {
            // if we have a non-zero, we use it.
            ratingParser.parse(value).ifPresent(book::setRating);
        }
    }

    private void processDescriptionAndNotes(@NonNull final String value,
                                            final boolean isNote,
                                            @NonNull final Book book) {

        String notes = book.getString(DBKey.PERSONAL_NOTES);
        if (notes.isEmpty()) {
            book.putString(DBKey.PERSONAL_NOTES, value);
            return;
        }

        //ENHANCE: Create a new field for a personal review.
        // For now, just concatenate with the private notes... we'll probably regret this later...
        // .
        // We don't want to use the DBKey.DESCRIPTION field!
        // The description is supposed to be a generic description, the back cover text, etc...

        if (isNote) {
            // prepend
            notes = value + "\n\n" + notes;
        } else {
            // append
            notes = notes + "\n\n" + value;
        }
        book.putString(DBKey.PERSONAL_NOTES, notes);
    }

    /**
     * Verify the given date keys for containing valid dates.
     *
     * @param book        to verify
     * @param keys        to verify
     * @param partialDate flag: {@code true} to cut dates down to partial dates.
     *                    i.e. remove time and any tailing "-01".
     * @param keepTime    flag: whether to keep a time component or strip it
     */
    private void verifyDates(@NonNull final Book book,
                             @NonNull final Set<String> keys,
                             final boolean partialDate,
                             final boolean keepTime) {
        keys.stream().filter(book::contains).forEach(key -> {
            final String s = book.getString(key);
            final Optional<LocalDateTime> date = dateParser.parse(s);
            if (date.isPresent()) {
                String iso = SqlEncode.dateTime(date.get());

                // cut off the time if present & required
                if (!keepTime && iso.length() > 10) {
                    iso = iso.substring(0, 10);
                }

                // Cut 'YYYY-MM-DD' down to month or year if possible & required
                if (partialDate && iso.length() > 4) {
                    while (iso.endsWith("-01")) {
                        iso = iso.substring(0, iso.length() - 3);
                    }
                }
                book.putString(key, iso);
            } else {
                book.remove(key);
            }
        });
    }
}
