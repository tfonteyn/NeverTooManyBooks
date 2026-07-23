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

package com.hardbacknutter.nevertoomanybooks.backup.csv.calibre;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.util.DateVerifier;
import com.hardbacknutter.nevertoomanybooks.backup.csv.util.SimpleAuthorCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.util.SimpleTagCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.util.StringList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomFieldDecoder;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreIdentifiers;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.Mapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.MapperFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * From a test export from Calibre 9.7.0 we got these known columns
 * if (in Calibre) we choose the full set in the original order.
 * Note that this set has less columns than when we do a sync
 * with the Calibre Content Server.
 * <pre>
 *      authors,               We use "authors" to make sure our extended re-ordering
 *                             rules are applied
 *      author_sort,           Ignored, see ^
 *      comments,              This column will break CSV in Calibre up to 9.9.0 as
 *                             it does not encode CR/LF.
 *                             Calibre 9.10 ? 10.0 ? coming soon... should fix this.
 *      cover,                 a path on disk
 *      timestamp,             the last-update datetime
 *      formats,               epub,mobi,...
 *      isbn,
 *      id,
 *      identifiers,
 *      languages,
 *      library_name,
 *      pubdate,
 *      publisher,
 *      rating,
 *      series,
 *      series_index,
 *      size,                   of the ebook
 *      tags,
 *      title,                  We use "title" to make sure our extended re-ordering
 *                              rules are applied
 *      title_sort,             Ignored, see ^
 *      uuid
 * </pre>
 * <p>
 * Note the "rating" column: this is the rating which Calibre gets from its metadata
 * sources. The user can of course update it.
 * <p>
 * In addition, the user can create a custom column "#rating".
 * When the latter is present, the "rating" is skipped.
 */
public class CalibreBookCoder
        implements BookCoder {

    private static final String TAG = "CalibreBookCoder";
    /** Last-updated. */
    private static final String COL_TIMESTAMP = "timestamp";

    private final Author unknownAuthor;

    private final List<Locale> userLocales;
    private final List<String> csvColumnNames;
    private final List<CalibreCustomField> customFields;

    private final RatingParser ratingParser;
    private final FullDateParser dateParser;
    private final DateVerifier dateVerifier;
    private final Collection<Mapper> mappers;

    private final StringList<Author> authorCoder;
    private final StringList<Tag> tagCoder;
    private final CalibreCustomFieldDecoder customFieldDecoder;

    private final BookshelfDao bookshelfDao;
    private final Style defaultStyle;


    /**
     * Constructor.
     *
     * @param context        Current context
     * @param defaultStyle   the default style to use for {@link Bookshelf}s
     * @param userLocales    to use for parsing
     * @param csvColumnNames the list with the field(column) names
     * @param updateOption   to use
     *
     * @throws DataReaderException when the import cannot go ahead
     */
    public CalibreBookCoder(@NonNull final Context context,
                            @NonNull final Style defaultStyle,
                            @NonNull final List<Locale> userLocales,
                            @NonNull final List<String> csvColumnNames,
                            @NonNull final DataReader.Updates updateOption)
            throws DataReaderException {
        this.defaultStyle = defaultStyle;

        // If a sync was requested, we'll need this column or cannot proceed.
        if (updateOption == DataReader.Updates.OnlyNewer) {
            requireColumnOrThrow(context, csvColumnNames, COL_TIMESTAMP);
        }

        this.userLocales = userLocales;
        this.csvColumnNames = csvColumnNames;

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        customFields = serviceLocator.getCalibreCustomFieldDao().getCustomFields();

        ratingParser = new RatingParser(5);
        final Locale systemLocale = serviceLocator.getSystemLocaleList().get(0);
        dateParser = new FullDateParser(new ISODateParser(systemLocale), userLocales);
        dateVerifier = new DateVerifier(dateParser);
        mappers = MapperFactory.create(context);

        customFieldDecoder = new CalibreCustomFieldDecoder(dateParser);

        bookshelfDao = serviceLocator.getBookshelfDao();

        authorCoder = SimpleAuthorCoder.create('&');
        tagCoder = SimpleTagCoder.create(',');

        unknownAuthor = Author.createUnknownAuthor(context);
    }

    @NonNull
    @Override
    public Book decode(@NonNull final Context context,
                       @NonNull final List<String> csvDataRow) {
        final Book book = new Book();

        Series series = null;

        for (int c = 0; c < csvColumnNames.size(); c++) {
            final String name = csvColumnNames.get(c).toLowerCase(Locale.ENGLISH);
            final String value = csvDataRow.get(c);
            if (value.isBlank()) {
                continue;
            }

            if (name.startsWith("#")) {
                customFields.stream()
                            .filter(cf -> cf.getCalibreKey().equals(name))
                            .forEach(cf -> customFieldDecoder.decode(cf, value, book));
            } else {
                switch (name) {
                    case "authors": {
                        processAuthors(value, book);
                        break;
                    }
                    case "title": {
                        book.setTitle(value);
                        break;
                    }
                    case "author_sort":
                    case "title_sort": {
                        // ignored
                        break;
                    }
                    case "comments": {
                        book.setDescription(SearchEngineUtils.cleanText(value));
                        break;
                    }
                    case "isbn": {
                        book.setRawProductCode(ISBN.cleanText(value));
                        break;
                    }
                    case "languages": {
                        processLanguage(value, book);
                        break;
                    }
                    case "pubdate": {
                        dateParser.parse(value).ifPresent(book::setPublicationDate);
                        break;
                    }
                    case "publisher": {
                        // Single name only
                        book.add(Publisher.from(value));
                        break;
                    }
                    case "series": {
                        // Single name only
                        series = Series.from(value);
                        break;
                    }
                    case "series_index": {
                        if (series != null) {
                            series.setNumber(value);
                        }
                        break;
                    }
                    case "identifiers": {
                        convertIdentifiers(value, book);
                        break;
                    }
                    case "tags": {
                        processTags(value, book);
                        break;
                    }
                    case "rating": {
                        // don't overwrite the "#rating" field
                        if (!book.contains(DBKey.RATING)) {
                            if (!NumberParser.isZero(value)) {
                                // if we have a non-zero, we use it.
                                ratingParser.parse(value).ifPresent(book::setRating);
                            }
                        }
                        break;
                    }
                    case COL_TIMESTAMP: {
                        // "2019-04-11T13:02:03+01:00"
                        dateParser.parse(value).ifPresent(book::setLastModified);
                        break;
                    }
                    case "cover": {
                        // It's a path on disk; ignored
                        break;
                    }

                    case "library_name": {
                        // The name is not enough to construct the full reference.
                        // Hence, we can't build the specific Calibre library information
                        // we normally do when connecting the the Content Server.
                        //
                        // Side note: in theory all books in the csv input will have the
                        // same library name. However, we ignore that on purpose which
                        // allows users to edit/change this for each book. Why? why not :)
                        //
                        // So... we use it as the bookshelf.
                        final Bookshelf bookshelf = bookshelfDao
                                .findByName(value)
                                .orElseGet(() -> new Bookshelf(value, defaultStyle));
                        book.add(bookshelf);
                        break;
                    }
                    case "id": {
                        // book.putString(DBKey.CALIBRE.BOOK_ID, value);
                        break;
                    }
                    case "uuid": {
                        // book.putString(DBKey.CALIBRE.BOOK_UUID, value);
                        break;
                    }
                    case "formats": {
                        processFormats(value, book);
                        break;
                    }
                    case "size": {
                        // ignored
                        break;
                    }

                    default: {
                        // Unknown on 2026-05-31; log them for future support
                        LoggerFactory.getLogger()
                                     .w(TAG, "Unknown Calibre csv column=" + name);
                        break;
                    }
                }
            }
        }

        if (series != null) {
            book.add(series);
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

        // Verifying the dates is overkill for now, but leaving it
        // as protection from input changes.

        // Full DateTime stamp.
        dateVerifier.verify(book, DBKey.getDateTimeKeys(), false, true);
        // Full Date stamp, no time
        dateVerifier.verify(book, DBKey.getFullDateKeys(), false, false);
        // Partial Date stamp, no time
        dateVerifier.verify(book, DBKey.getPartialDateKeys(), true, false);

        // GitHub #205: force the "read" flag to =1 if a read_end" is present
        // after the above validation of the date fields.
        if (book.contains(DBKey.READ_END__DATE)) {
            book.putBoolean(DBKey.READ__BOOL, true);
        }

        mappers.forEach(mapper -> mapper.map(context, book));

        return book;
    }

    private void processAuthors(@NonNull final CharSequence value,
                                @NonNull final Book book) {

        final List<Author> list = book.getAuthors();

        // Do not add if already there.
        // We need to do this here (before going to the database)
        // so we can keep them in the exact order as they come in.
        authorCoder.decodeList(value)
                   .stream()
                   .filter(author -> list.stream().noneMatch(a -> a.isSameName(author)))
                   .forEach(list::add);

        book.setAuthors(list);
    }

    private void processLanguage(@NonNull final String value,
                                 @NonNull final Book book) {
        // only ever seen a single entry, but lets assume ',' separated
        // Not using a coder, the entries are just iso3 codes.
        // We only support one language, so grab the first one
        book.setLanguage(value.split(",")[0]);
    }

    private void processFormats(@NonNull final String value,
                                @NonNull final Book book) {
        /// only ever seen a single entry, but lets assume ',' separated
        // We only can take the first anyhow.
        // Not using a coder, these are simple
        final String[] split = value.split(",");
        // Typically we would now insert "eBook", but as we can't
        // create the library info, we'll use the actual format here
        book.setFormat(split[0]);
    }

    private void processTags(@NonNull final CharSequence value,
                             @NonNull final Book book) {
        final List<Tag> tags = tagCoder.decodeList(value);
        if (!tags.isEmpty()) {
            final List<Tag> list = book.getTags();
            list.addAll(tags);
            book.setTags(list);
        }
    }

    /**
     * See {@link CalibreContentServerReader}#convertIdentifiers.
     *
     * @param value to parse
     * @param book  to update
     */
    private void convertIdentifiers(@NonNull final String value,
                                    @NonNull final Book book) {
        final List<Identifier.Value> ivs = new ArrayList<>();
        // "google:C2O96Fd-uwYC,amazon:076530953X,isbn:9780765309532"
        Arrays.stream(value.split(","))
              .map(ids -> ids.split(":"))
              .filter(ids -> !ids[1].isEmpty())
              // [0] name, [1] value
              // The name MUST be converted to lc before we try and map
              .forEach(id -> CalibreIdentifiers.convertIdentifier(
                      book, id[0].toLowerCase(Locale.ENGLISH), id[1], ivs));

        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed.
     *
     * @param context        Current context
     * @param columnsPresent the column names which are present
     * @param names          columns which should be checked for, in order of preference
     *
     * @throws DataReaderException if no suitable column is present
     */
    private void requireColumnOrThrow(@NonNull final Context context,
                                      @NonNull final List<String> columnsPresent,
                                      @NonNull final String... names)
            throws DataReaderException {


        for (final String name : names) {
            if (columnsPresent.contains(name)) {
                return;
            }
        }

        throw new DataReaderException(context.getString(
                R.string.error_import_csv_missing_columns_x, String.join(",", names)));
    }
}
