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

package com.hardbacknutter.nevertoomanybooks.sync.calibre.coders;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.csv.calibre.CalibreBookCoder;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreIdentifiers;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * An example response which can be {@link #decode}'d.
 *
 * <pre>
 *     {
 *     "6": {
 *         "series": null,
 *         "tags": [
 *             "Fiction",
 *             "Science Fiction"
 *         ],
 *         "thumbnail": "/get/thumb/6/Calibre_Library",
 *         "author_sort": "Stross, Charles",
 *         "rating": 5,
 *         "pubdate": "2005-06-25T23:00:00+00:00",
 *         "application_id": 6,
 *         "cover": "/get/cover/6/Calibre_Library",
 *         "series_index": null,
 *         "author_link_map": {
 *             "Charles Stross": ""
 *         },
 *         "author_sort_map": {
 *             "Charles Stross": "Stross, Charles"
 *         },
 *         "publisher": "Ace",
 *         "user_categories": {},
 *         "comments": "<p>The Singularity. blah blah...</p>",
 *         "title_sort": "Accelerando",
 *         "identifiers": {
 *             "amazon": "0441014151",
 *             "isbn": "9780441014156",
 *             "google": "F3i9DAEACAAJ"
 *         },
 *         "uuid": "4ec36562-d8e8-4499-9c6c-d1e7ae2af42f",
 *         "title": "Accelerando",
 *         "authors": [
 *             "Charles Stross"
 *         ],
 *         "last_modified": "2020-11-20T11:17:51+00:00",
 *         "languages": [
 *             "eng"
 *         ],
 *         "timestamp": "2019-04-11T12:02:03+00:00",
 *         "user_metadata": {
 *             "#notes": {
 *                 "table": "custom_column_4",
 *                 "column": "value",
 *                 "datatype": "comments",
 *                 "is_multiple": null,
 *                 "kind": "field",
 *                 "name": "Notes",
 *                 "search_terms": [
 *                     "#notes"
 *                 ],
 *                 "label": "notes",
 *                 "colnum": 4,
 *                 "display": {
 *                     "description": "Personal notes",
 *                     "heading_position": "above",
 *                     "interpret_as": "html"
 *                 },
 *                 "is_custom": true,
 *                 "is_category": false,
 *                 "link_column": "value",
 *                 "category_sort": "value",
 *                 "is_csp": false,
 *                 "is_editable": true,
 *                 "rec_index": 22,
 *                 "#value#": null,
 *                 "#extra#": null,
 *                 "is_multiple2": {}
 *             },
 *             "#read": {
 *                 "table": "custom_column_2",
 *                 "column": "value",
 *                 "datatype": "bool",
 *                 "is_multiple": null,
 *                 "kind": "field",
 *                 "name": "Read",
 *                 "search_terms": [
 *                     "#read"
 *                 ],
 *                 "label": "read",
 *                 "colnum": 2,
 *                 "display": {
 *                     "description": ""
 *                 },
 *                 "is_custom": true,
 *                 "is_category": false,
 *                 "link_column": "value",
 *                 "category_sort": "value",
 *                 "is_csp": false,
 *                 "is_editable": true,
 *                 "rec_index": 23,
 *                 "#value#": null,
 *                 "#extra#": null,
 *                 "is_multiple2": {}
 *             },
 *             "#read_end": {
 *                 "table": "custom_column_3",
 *                 "column": "value",
 *                 "datatype": "datetime",
 *                 "is_multiple": null,
 *                 "kind": "field",
 *                 "name": "Finished reading",
 *                 "search_terms": [
 *                     "#read_end"
 *                 ],
 *                 "label": "read_end",
 *                 "colnum": 3,
 *                 "display": {
 *                     "date_format": null,
 *                     "description": ""
 *                 },
 *                 "is_custom": true,
 *                 "is_category": false,
 *                 "link_column": "value",
 *                 "category_sort": "value",
 *                 "is_csp": false,
 *                 "is_editable": true,
 *                 "rec_index": 24,
 *                 "#value#": "None",
 *                 "#extra#": null,
 *                 "is_multiple2": {}
 *             },
 *             "#read_start": {
 *                 "table": "custom_column_7",
 *                 "column": "value",
 *                 "datatype": "datetime",
 *                 "is_multiple": null,
 *                 "kind": "field",
 *                 "name": "Started reading",
 *                 "search_terms": [
 *                     "#read_start"
 *                 ],
 *                 "label": "read_start",
 *                 "colnum": 7,
 *                 "display": {
 *                     "date_format": null,
 *                     "description": ""
 *                 },
 *                 "is_custom": true,
 *                 "is_category": false,
 *                 "link_column": "value",
 *                 "category_sort": "value",
 *                 "is_csp": false,
 *                 "is_editable": true,
 *                 "rec_index": 25,
 *                 "#value#": "None",
 *                 "#extra#": null,
 *                 "is_multiple2": {}
 *             },
 *             "#status": {
 *                 "table": "custom_column_5",
 *                 "column": "value",
 *                 "datatype": "enumeration",
 *                 "is_multiple": null,
 *                 "kind": "field",
 *                 "name": "Status",
 *                 "search_terms": [
 *                     "#status"
 *                 ],
 *                 "label": "status",
 *                 "colnum": 5,
 *                 "display": {
 *                     "enum_values": [
 *                         "OK",
 *                         "spelling",
 *                         "OCR issues",
 *                         "bad"
 *                     ],
 *                     "use_decorations": 0,
 *                     "description": "",
 *                     "enum_colors": [
 *                         "green",
 *                         "blue",
 *                         "orange",
 *                         "red"
 *                     ]
 *                 },
 *                 "is_custom": true,
 *                 "is_category": true,
 *                 "link_column": "value",
 *                 "category_sort": "value",
 *                 "is_csp": false,
 *                 "is_editable": true,
 *                 "rec_index": 26,
 *                 "#value#": null,
 *                 "#extra#": null,
 *                 "is_multiple2": {}
 *             }
 *         },
 *         "format_metadata": {
 *             "pdf": {
 *                 "path": "/home/calibre/library
 *                     /Charles Stross
 *                     /Accelerando (6)
 *                     /Accelerando - Charles Stross.pdf",
 *                 "size": 21951985,
 *                 "mtime": "2021-01-09T13:55:00.100514+00:00"
 *             },
 *             "epub": {
 *                 "path": "/home/calibre/library
 *                      /Charles Stross
 *                      /Accelerando (6)
 *                      /Accelerando - Charles Stross.epub",
 *                 "size": 408763,
 *                 "mtime": "2020-09-18T15:26:14.871190+00:00"
 *             }
 *         },
 *         "formats": [
 *             "epub",
 *             "pdf"
 *         ],
 *         "main_format": {
 *             "epub": "/get/epub/6/Calibre_Library"
 *         },
 *         "other_formats": {
 *             "pdf": "/get/pdf/6/library"
 *         },
 *         "category_urls": {
 *             "series": {},
 *             "tags": {
 *                 "Fiction": "/ajax/books_in/74616773/3139/Calibre_Library",
 *                 "Science Fiction": "/ajax/books_in/74616773/34/Calibre_Library"
 *             },
 *             "publisher": {
 *                 "Ace": "/ajax/books_in/7075626c6973686572/3735/Calibre_Library"
 *             },
 *             "authors": {
 *                 "Charles Stross": "/ajax/books_in/617574686f7273/32/Calibre_Library"
 *             },
 *             "languages": {},
 *             "#status": {}
 *         }
 *     },
 * </pre>
 */
public final class BookCoder {

    private static final String TAG = "BookCoder";

    /** A text "null" as value. Should be considered an error. */
    private static final String VALUE_IS_NULL = "null";
    /** error text for {@link #VALUE_IS_NULL}. */
    private static final String ERROR_NULL_STRING = "'null' string";

    @NonNull
    private final BookshelfDao bookshelfDao;
    @NonNull
    private final DateParser<LocalDateTime> dateParser;
    @NonNull
    private final RealNumberParser realNumberParser;
    private final CalibreCustomFieldCoder customFieldCoder;
    private final RatingParser ratingParser;

    /**
     * Constructor.
     *
     * @param dateParser       to use
     * @param realNumberParser to use
     */
    public BookCoder(@NonNull final DateParser<LocalDateTime> dateParser,
                     @NonNull final RealNumberParser realNumberParser) {
        this.dateParser = dateParser;
        this.realNumberParser = realNumberParser;
        // When READING Calibre data, rating is a float, 0..5
        ratingParser = new RatingParser(realNumberParser, 5);
        customFieldCoder = new CalibreCustomFieldCoder(dateParser, ratingParser, realNumberParser);

        bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
    }

    /**
     * Decode a Calibre {@link JSONObject} book object to a {@link Book}.
     * <p>
     * <strong>WARNING:</strong> covers must be decoded separately with
     * {@link #decodeCovers(Context, CalibreContentServer, int, JSONObject, Book)}
     *
     * @param context       Current context
     * @param library       the library to which the given book belongs
     * @param calibreBookId numeric book id
     * @param calibreBook   to decode
     *
     * @return a Book
     *
     * @throws JSONException upon any parsing error
     */
    @NonNull
    public Book decode(@NonNull final Context context,
                       @NonNull final CalibreLibrary library,
                       final int calibreBookId,
                       @NonNull final JSONObject calibreBook)
            throws JSONException {

        final Book book = new Book();
        book.setStage(EntityStage.Stage.Dirty);

        book.putInt(DBKey.CALIBRE.BOOK_ID, calibreBookId);
        book.putString(DBKey.CALIBRE.BOOK_UUID, calibreBook.getString(CalibreBookJsonKey.UUID));

        // Always add the current library; i.e. the library the book came from.
        book.setCalibreLibrary(library);

        decodeLastModifiedDate(calibreBook).ifPresent(book::setLastModified);

        // paranoia ...
        if (!calibreBook.isNull(CalibreBookJsonKey.TITLE)) {
            book.setTitle(calibreBook.getString(CalibreBookJsonKey.TITLE));
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.COMMENTS)) {
            book.setDescription(calibreBook.getString(CalibreBookJsonKey.COMMENTS));
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.PAGES)) {
            // according to Calibre code:
            // null values are None or 0.
            // -1 is no countable # formats.
            // -2 is error processing formats,
            // -3 is DRMed.
            // ==> guard against 'None' and reject all unusable numbers.
            try {
                final int pages = calibreBook.getInt(CalibreBookJsonKey.PAGES);
                if (pages > 0) {
                    book.setPages(pages);
                }
            } catch (@NonNull final JSONException ignored) {
                // ignore
            }
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.RATING)) {
            // this is the rating which Calibre gets from its metadata
            // sources. The user can of course update it.
            // In addition, the user can create a custom column "#rating".
            // When the latter is present, the "rating" will be overwritten.
            // Reminder: we've seen int only, but Calibre docs state 'float'
            final String rating = calibreBook.optString(CalibreBookJsonKey.RATING, null);
            ratingParser.parse(rating).ifPresent(book::setRating);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.LANGUAGES_ARRAY)) {
            decodeLanguages(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.TAGS_ARRAY)) {
            decodeTags(calibreBook, book);
        }
        decodeAuthors(context, calibreBook, book);

        if (!calibreBook.isNull(CalibreBookJsonKey.SERIES)) {
            decodeSeries(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.PUBLISHER)) {
            decodePublisher(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.IDENTIFIERS)) {
            decodeIdentifiers(calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.USER_METADATA)) {
            decodeCustomFields(library, calibreBook, book);
        }

        if (!calibreBook.isNull(CalibreBookJsonKey.EBOOK_FORMAT)) {
            decodeFormat(calibreBook, book);
        }

        decodeVirtualLibraries(context, library, calibreBook, book);

        return book;
    }

    /**
     * Decode the last-modifed field and parse it.
     *
     * @param calibreBook to decode
     *
     * @return datetime stamp
     */
    @NonNull
    public Optional<LocalDateTime> decodeLastModifiedDate(@NonNull final JSONObject calibreBook) {
        if (!calibreBook.isNull(CalibreBookJsonKey.LAST_MODIFIED)) {
            try {
                // "last_modified": "2020-11-20T11:17:51+00:00",
                final String dateStr = calibreBook.getString(CalibreBookJsonKey.LAST_MODIFIED);
                return dateParser.parse(dateStr);
            } catch (@NonNull final JSONException ignore) {
                // ignore
            }
        }
        return Optional.empty();
    }

    /**
     * Convert the tags from Calibre to our tags.
     *
     * <pre>
     *   "tags": [
     *       "Action & Adventure",
     *       "Fiction",
     *       "Hard Science Fiction",
     *       "Science Fiction",
     *       "Space Opera"
     *   ],
     * </pre>
     *
     * @param calibreBook to parse
     * @param book        to update
     */
    private void decodeTags(@NonNull final JSONObject calibreBook,
                            @NonNull final Book book) {
        final JSONArray calTags = calibreBook.optJSONArray(CalibreBookJsonKey.TAGS_ARRAY);
        if (calTags != null && !calTags.isEmpty()) {
            final List<Tag> tags = new ArrayList<>();
            for (int i = 0; i < calTags.length(); i++) {
                tags.add(new Tag(calTags.getString(i)));
            }
            if (!tags.isEmpty()) {
                book.setTags(tags);
            }
        }
    }

    // "languages": [
    //      "eng"
    // ],
    private void decodeLanguages(@NonNull final JSONObject calibreBook,
                                 @NonNull final Book book) {
        final JSONArray languages = calibreBook.optJSONArray(CalibreBookJsonKey.LANGUAGES_ARRAY);
        if (languages != null && !languages.isEmpty()) {
            // We only support one language, so grab the first one
            book.setLanguage(languages.optString(0));
        }
    }

    // "authors": [
    //      "Charles Stross"
    // ],
    private void decodeAuthors(@NonNull final Context context,
                               @NonNull final JSONObject calibreBook,
                               @NonNull final Book book) {
        final List<Author> bookAuthors = new ArrayList<>();
        if (!calibreBook.isNull(CalibreBookJsonKey.AUTHOR_ARRAY)) {
            final JSONArray authors = calibreBook.optJSONArray(CalibreBookJsonKey.AUTHOR_ARRAY);
            if (authors != null && !authors.isEmpty()) {
                for (int i = 0; i < authors.length(); i++) {
                    final String author = authors.optString(i);
                    if (!author.isEmpty()) {
                        bookAuthors.add(Author.from(author));
                    }
                }
            }
        }
        if (bookAuthors.isEmpty()) {
            bookAuthors.add(Author.createUnknownAuthor(context));
        }
        book.setAuthors(bookAuthors);
    }

    // "series": null,
    // "series": "Argos Mythos / The devil is dead",
    private void decodeSeries(@NonNull final JSONObject calibreBook,
                              @NonNull final Book book) {
        final String seriesName = calibreBook.optString(CalibreBookJsonKey.SERIES);
        if (!seriesName.isEmpty()) {
            if (VALUE_IS_NULL.equals(seriesName)) {
                throw new IllegalArgumentException(ERROR_NULL_STRING);
            }
            final Series series = Series.from(seriesName);
            // "series_index": null,
            // "series_index": 2,  --> it's a float, but we grab it as a string
            String seriesNr = calibreBook.optString(CalibreBookJsonKey.SERIES_INDEX);
            if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                // transform "3.0" to just "3" (and similar) but leave "3.1" alone
                if (seriesNr.endsWith(".0")) {
                    seriesNr = seriesNr.substring(0, seriesNr.length() - 2);
                }
                series.setNumber(seriesNr);
            }
            final List<Series> bookSeries = new ArrayList<>();
            bookSeries.add(series);
            book.setSeries(bookSeries);
        }
    }

    private void decodePublisher(@NonNull final JSONObject calibreBook,
                                 @NonNull final Book book) {
        final String publisherName = calibreBook.optString(CalibreBookJsonKey.PUBLISHER);
        if (!publisherName.isEmpty()) {
            if (VALUE_IS_NULL.equals(publisherName)) {
                throw new IllegalArgumentException(ERROR_NULL_STRING);
            }

            final List<Publisher> bookPublishers = new ArrayList<>();
            bookPublishers.add(Publisher.from(publisherName));
            book.setPublishers(bookPublishers);
        }
    }

    /**
     * See {@link CalibreBookCoder}#convertIdentifiers.
     *
     * @param calibreBook to parse
     * @param book        to update
     */
    private void decodeIdentifiers(@NonNull final JSONObject calibreBook,
                                   @NonNull final Book book) {
        final JSONObject remotes = calibreBook.optJSONObject(CalibreBookJsonKey.IDENTIFIERS);
        if (remotes != null) {
            final List<Identifier.Value> ivs = new ArrayList<>();

            final Iterator<String> it = remotes.keys();
            while (it.hasNext()) {
                final String calKey = it.next();
                if (!remotes.isNull(calKey)) {
                    final String sid = remotes.optString(calKey);
                    if (!sid.isEmpty()) {
                        // MUST be converted to lc before we try and map
                        CalibreIdentifiers.convertIdentifier(
                                book, calKey.toLowerCase(Locale.ENGLISH), sid, ivs);
                    }
                }
            }
            ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
            if (!ivs.isEmpty()) {
                book.setIdentifiers(ivs);
            }
        }
    }

    /**
     * Example of the "user_metadata" with more fields.
     * <pre>
     *     {
     *     "#read_progress": {
     *         "is_category": false,
     *         "is_csp": false,
     *         "kind": "field",
     *         "display": {
     *             "composite_sort": "number",
     *             "composite_store_template_value_in_opf": false,
     *             "contains_html": false,
     *             "web_search_template": "",
     *             "description": "blah blah",
     *             "composite_template": "{id:reading_progress()}",
     *             "use_decorations": false,
     *             "make_category": false,
     *             "composite_show_in_comments": false
     *         },
     *         "column": "value",
     *         "is_editable": true,
     *         "#value#": "0%",
     *         "label": "read_progress",
     *         "is_multiple": null,
     *         "category_sort": "value",
     *         "search_terms": [
     *             "#read_progress"
     *         ],
     *         "rec_index": 26,
     *         "link_column": "value",
     *         "datatype": "composite",
     *         "name": "Read progress",
     *         "colnum": 1,
     *         "is_custom": true,
     *         "is_multiple2": {},
     *         "table": "custom_column_1"
     *     },
     *     "#country": {
     *         "is_category": true,
     *         "is_csp": false,
     *         "kind": "field",
     *         "display": {
     *             "enum_colors": [],
     *             "enum_values": [
     *                 "UK",
     *                 "US",
     *                 "EU"
     *             ],
     *             "web_search_template": "",
     *             "description": "",
     *             "use_decorations": false
     *         },
     *         "column": "value",
     *         "is_editable": true,
     *         "#value#": null,
     *         "label": "country",
     *         "is_multiple": null,
     *         "category_sort": "value",
     *         "search_terms": [
     *             "#country"
     *         ],
     *         "rec_index": 23,
     *         "#extra#": null,
     *         "link_column": "value",
     *         "datatype": "enumeration",
     *         "name": "Country",
     *         "colnum": 2,
     *         "is_custom": true,
     *         "is_multiple2": {},
     *         "table": "custom_column_2"
     *     },
     *     "#rating": {
     *         "is_category": true,
     *         "is_csp": false,
     *         "kind": "field",
     *         "display": {
     *             "allow_half_stars": false,
     *             "web_search_template": "",
     *             "description": ""
     *         },
     *         "column": "value",
     *         "is_editable": true,
     *         "#value#": null,
     *         "label": "rating",
     *         "is_multiple": null,
     *         "category_sort": "value",
     *         "search_terms": [
     *             "#rating"
     *         ],
     *         "rec_index": 24,
     *         "#extra#": null,
     *         "link_column": "value",
     *         "datatype": "rating",
     *         "name": "My Rating",
     *         "colnum": 3,
     *         "is_custom": true,
     *         "is_multiple2": {},
     *         "table": "custom_column_3"
     *     },
     *     "#read_end": {
     *         "is_category": false,
     *         "is_csp": false,
     *         "kind": "field",
     *         "display": {
     *             "web_search_template": "",
     *             "description": "",
     *             "date_format": null
     *         },
     *         "column": "value",
     *         "is_editable": true,
     *         "#value#": "None",
     *         "label": "read_end",
     *         "is_multiple": null,
     *         "category_sort": "value",
     *         "search_terms": [
     *             "#read_end"
     *         ],
     *         "rec_index": 25,
     *         "#extra#": null,
     *         "link_column": "value",
     *         "datatype": "datetime",
     *         "name": "Read Done",
     *         "colnum": 4,
     *         "is_custom": true,
     *         "is_multiple2": {},
     *         "table": "custom_column_4"
     *     }
     * }
     * </pre>
     *
     * @param library     to which the book belongs
     * @param calibreBook to convert
     * @param book        to update
     *
     * @throws JSONException upon any parsing error
     */
    private void decodeCustomFields(@NonNull final CalibreLibrary library,
                                    @NonNull final JSONObject calibreBook,
                                    @NonNull final Book book)
            throws JSONException {

        final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBookJsonKey.USER_METADATA);
        if (userMetaData != null) {
            for (final CalibreCustomField cf : library.getCustomFields()) {
                final JSONObject data = userMetaData.optJSONObject(cf.getCalibreKey());
                if (data != null) {
                    final String type = data.getString(CalibreCustomField.METADATA_DATATYPE);
                    if (cf.getType().equals(type)) {
                        // Sanity check, should always be present
                        if (!data.isNull(CalibreCustomField.VALUE)) {
                            customFieldCoder.decode(cf, data, book);
                        }
                    }
                }
            }
        }
    }

    private void decodeFormat(@NonNull final JSONObject calibreBook,
                              @NonNull final Book book) {
        final JSONObject mainFormat = calibreBook.optJSONObject(CalibreBookJsonKey.EBOOK_FORMAT);
        if (mainFormat != null) {
            final Iterator<String> it = mainFormat.keys();
            if (it.hasNext()) {
                final String format = it.next();
                if (format != null && !format.isEmpty()) {
                    book.putString(DBKey.CALIBRE.BOOK_MAIN_FORMAT, format);
                }
            }
        }
    }

    private void decodeVirtualLibraries(@NonNull final Context context,
                                        final CalibreLibrary library,
                                        @NonNull final JSONObject calibreBook,
                                        @NonNull final Book book) {
        // Current list, will be empty for new books
        final List<Bookshelf> bookShelves = book.getBookshelves();

        // Add the physical library mapped Bookshelf
        final Bookshelf mappedBookshelf = bookshelfDao
                .getBookshelf(context, library.getMappedBookshelfId())
                .or(bookshelfDao::getCurrent)
                .orElseGet(bookshelfDao::getDefault);

        if (bookShelves.isEmpty()) {
            // new book
            bookShelves.add(mappedBookshelf);
        } else {
            // updating, check before adding
            if (bookShelves.stream()
                           .map(Bookshelf::getId)
                           .noneMatch(id -> id == mappedBookshelf.getId())) {
                bookShelves.add(mappedBookshelf);
            }
        }

        final JSONArray virtualLibs = calibreBook.optJSONArray(
                CalibreBookJsonKey.NTMB_VIRTUAL_LIBRARY_LIST);

        if (virtualLibs != null && !virtualLibs.isEmpty()) {
            for (int i = 0; i < virtualLibs.length(); i++) {
                final String name = virtualLibs.getString(i);

                // lookup the matching virtual library.
                // TODO: we may want to create a hashmap before starting the loop
                library.getVirtualLibraries()
                       .stream()
                       .filter(vlib -> vlib.getName().equals(name))
                       .findFirst()
                       // it will always be present of course.
                       .ifPresent(vlib -> {
                           final Bookshelf vlibMappedBookshelf = bookshelfDao
                                   .getBookshelf(context, vlib.getMappedBookshelfId())
                                   .or(() -> bookshelfDao.getBookshelf(
                                           context, library.getMappedBookshelfId()))
                                   .orElseThrow();

                           // add the vlib mapped bookshelf if not already present.
                           if (bookShelves.stream()
                                          .map(Bookshelf::getId)
                                          .noneMatch(id -> id == vlibMappedBookshelf.getId())) {
                               bookShelves.add(vlibMappedBookshelf);
                           }
                       });
            }

            book.setBookshelves(bookShelves);
        }
    }

    /**
     * Fetch a cover from the server and add it to the book.
     * Download errors are ignored.
     *
     * @param context       Current context
     * @param server        to access
     * @param calibreBookId numeric book id
     * @param calibreBook   to fetch the cover for
     * @param book          to update
     */
    public void decodeCovers(@NonNull final Context context,
                             @NonNull final CalibreContentServer server,
                             final int calibreBookId,
                             @NonNull final JSONObject calibreBook,
                             @NonNull final Book book) {
        if (calibreBook.isNull(CalibreBookJsonKey.COVER)) {
            return;
        }
        final String coverUrl = calibreBook.optString(CalibreBookJsonKey.COVER);
        if (coverUrl.isEmpty()) {
            return;
        }

        try {
            final File file = server.getCover(calibreBookId, coverUrl).orElse(null);
            book.setImage(context, 0, file);
        } catch (@NonNull final IOException | StorageException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    /**
     * Encode a {@link Book} to a Calibre {@link JSONObject} book object.
     * <p>
     * Copies the wanted fields from the local {@link Book} into a {@link JSONObject}
     * with field names {@link CalibreBookJsonKey} as needed by Calibre.
     * <p>
     * <strong>WARNING:</strong> covers must be encoded separately with
     * {@link #encodeCovers(Context, Book, JSONObject)}
     *
     * @param library                the library to which the given book belongs
     * @param calibreBookIdentifiers the <strong>full</strong> list of identifiers for this
     *                               book as <strong>fetched from the Calibre server</strong>.
     *                               We need to send a full/combined set as the server does
     *                               not support delta values for indentifiers.
     * @param localBook              the Book to encode
     *
     * @return the JSON data to send to the Calibre server
     *
     * @throws JSONException on any failure constructing JSON objects
     */
    @NonNull
    public JSONObject encode(@NonNull final CalibreLibrary library,
                             @Nullable final JSONObject calibreBookIdentifiers,
                             @NonNull final Book localBook)
            throws JSONException {

        // Empty fields MUST be included to make the server remove the data.
        final JSONObject calibreBook = new JSONObject();
        calibreBook.put(CalibreBookJsonKey.TITLE,
                        localBook.getTitle());
        calibreBook.put(CalibreBookJsonKey.COMMENTS,
                        localBook.getDescription());
        // we don't read this field, but we DO write it.
        calibreBook.put(CalibreBookJsonKey.PUBLICATION_DATE,
                        localBook.getString(DBKey.PUBLICATION_DATE));
        calibreBook.put(CalibreBookJsonKey.LAST_MODIFIED,
                        localBook.getString(DBKey.DATE_LAST_UPDATED__UTC));
        calibreBook.put(CalibreBookJsonKey.RATING,
                        encodeRating(localBook));

        calibreBook.put(CalibreBookJsonKey.AUTHOR_ARRAY,
                        encodeAuthors(localBook));

        encodeSeries(localBook, calibreBook);

        calibreBook.put(CalibreBookJsonKey.PUBLISHER,
                        localBook.getPrimaryPublisher()
                                 .map(Publisher::getName)
                                 .orElse(""));

        calibreBook.put(CalibreBookJsonKey.TAGS_ARRAY,
                        encodeTags(localBook));

        calibreBook.put(CalibreBookJsonKey.LANGUAGES_ARRAY,
                        encodeLanguages(localBook));

        calibreBook.put(CalibreBookJsonKey.IDENTIFIERS,
                        encodeIdentifiers(calibreBookIdentifiers, localBook));

        encodePages(localBook, calibreBook);

        customFieldCoder.encode(library, localBook, calibreBook);

        return calibreBook;
    }

    private int encodeRating(@NonNull final Book localBook) {
        // The DB Endpoints#PUSH_CHANGES expects an int 0..10.
        // The local Book rating runs from 0.0 to 5.0; multiply by 2 for the site 1..10
        // and encode it as an int in string!
        // We clamp due to paranoia.
        final float rating = localBook.getRating(realNumberParser);
        return (int) MathUtils.clamp(rating * 2, 0, 10);
    }

    private void encodePages(@NonNull final Book localBook,
                             @NonNull final JSONObject changes) {
        // Calibre only supports an 'int' typed pages, but we use a string.
        // Hence, only add if we can convert to int.
        final String pagesStr = localBook.getPages();
        if (!pagesStr.isEmpty()) {
            try {
                final int pages = Integer.parseInt(pagesStr);
                changes.put(CalibreBookJsonKey.PAGES, pages);
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
    }

    @NonNull
    private JSONArray encodeAuthors(@NonNull final Book localBook) {
        final JSONArray authors = new JSONArray();
        localBook.getAuthors()
                 .stream()
                 .map(author -> author.getFormattedName(true))
                 .forEach(authors::put);
        return authors;
    }

    private void encodeSeries(@NonNull final Book localBook,
                              @NonNull final JSONObject calibreBook) {
        final Optional<Series> optSeries = localBook.getPrimarySeries();
        final String seriesTitle = optSeries.map(Series::getTitle).orElse("");
        // Calibre can only accept Floats; send '1' on any error, as that is the Calibre default
        float number = 1;
        if (optSeries.isPresent()) {
            try {
                number = Float.parseFloat(optSeries.get().getNumber());
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }

        calibreBook.put(CalibreBookJsonKey.SERIES, seriesTitle);
        calibreBook.put(CalibreBookJsonKey.SERIES_INDEX, number);
    }

    @NonNull
    private JSONArray encodeTags(@NonNull final Book localBook) {
        return new JSONArray(localBook.getTags().stream().map(Tag::getName)
                                      .collect(Collectors.toList()));
    }

    @NonNull
    private JSONArray encodeLanguages(@NonNull final Book localBook) {
        final JSONArray languages = new JSONArray();
        final String language = localBook.getLanguage();
        if (!language.isEmpty()) {
            languages.put(language);
        }
        return languages;
    }

    @Nullable
    private JSONObject encodeIdentifiers(@Nullable final JSONObject calibreBookIdentifiers,
                                         @NonNull final Book localBook) {
        // The server expects a FULL set of identifiers. Any not present, will be deleted.
        // https://github.com/kovidgoyal/calibre/blob/master/src/calibre/db/write.py#L480
        // So, we send a combination of changes + the identifiers we don't know back to the server.

        // Collect all known local Identifiers
        final JSONObject localIdentifiers = new JSONObject();
        localBook.getIdentifiers().forEach(iv -> {
            // Map our key to the calibre key, or if not found, just use the key itself
            final String calKey = CalibreIdentifiers.IDENTIFIER_MAPPING_WRITER
                    .getOrDefault(iv.getKey(), iv.getKey());
            //noinspection DataFlowIssue
            localIdentifiers.put(calKey, iv.getSid());
        });

        // add the ISBN which Calibre treats as just another identifier
        final String isbn = localBook.getRawProductCode();
        if (!isbn.isEmpty()) {
            localIdentifiers.put(CalibreIdentifiers.IDENTIFIER_ISBN, isbn);
        }

        // add the remotes, overwriting with locals as needed.
        if (!localIdentifiers.isEmpty()) {
            if (calibreBookIdentifiers == null) {
                // no remotes, just send all locals
                return localIdentifiers;
            } else {
                // overwrite remotes with locals
                final Iterator<String> it = localIdentifiers.keys();
                while (it.hasNext()) {
                    final String key = it.next();
                    calibreBookIdentifiers.put(key, localIdentifiers.get(key));
                }
            }
        }
        // send the combined set
        return calibreBookIdentifiers;
    }

    /**
     * Encode the covers. For now we only send the first cover.
     *
     * @param context   Current context
     * @param localBook to encode
     * @param changes   to add the encoded data to
     *
     * @return {@code true} if a cover was added
     *
     * @throws IOException on generic/other IO failures
     */
    public boolean encodeCovers(@NonNull final Context context,
                                @NonNull final Book localBook,
                                @NonNull final JSONObject changes)
            throws IOException {
        final Optional<File> coverFile = localBook.getImage(context, 0);
        if (coverFile.isPresent()) {
            final File file = coverFile.get();
            final byte[] bFile = new byte[(int) file.length()];
            try (FileInputStream is = new FileInputStream(file)) {
                //noinspection ResultOfMethodCallIgnored
                is.read(bFile);
            }
            changes.put(CalibreBookJsonKey.COVER, Base64.encodeToString(bFile, 0));
            return true;
        }

        changes.put(CalibreBookJsonKey.COVER, "");
        return false;
    }
}
