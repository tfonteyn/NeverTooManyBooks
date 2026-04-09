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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

/**
 * These are the field names the Calibre Content Server uses/expects in its AJAX API.
 * See the constructor Javadoc for an example of the JSON blob.
 */
final class CalibreBookJsonKey {

    static final String AUTHOR_ARRAY = "authors";
    static final String COMMENTS = "comments";
    /** The URL when reading; Base64 encoded image when writing. */
    static final String COVER = "cover";
    static final String EBOOK_FORMAT = "main_format";
    static final String ID = "application_id";
    /** Not an array, but an object with key:value pairs. */
    static final String IDENTIFIERS = "identifiers";
    static final String LANGUAGES_ARRAY = "languages";
    static final String LAST_MODIFIED = "last_modified";
    /** {@code int}. */
    static final String PAGES = "pages";
    static final String PUBLICATION_DATE = "pubdate";
    /** A single publisher name. */
    static final String PUBLISHER = "publisher";
    static final String RATING = "rating";
    static final String SERIES = "series";
    static final String SERIES_INDEX = "series_index";
    static final String TAGS_ARRAY = "tags";
    static final String TITLE = "title";
    static final String USER_METADATA = "user_metadata";
    static final String UUID = "uuid";

    /**
     * <pre>
     * {
     *   "title": "On the Steel Breeze",
     *   "publisher": "Gollancz",
     *   "pages": 0,
     *   "pubdate": "2022-01-15T00:00:00+00:00",
     *   "author_sort_map": {
     *     "Alastair Reynolds": "Reynolds, Alastair"
     *   },
     *   "comments": "blah blah",
     *   "user_metadata": {
     *     "#read_progress": {
     *       "table": "custom_column_1",
     *       "column": "value",
     *       "datatype": "composite",
     *       "is_multiple": null,
     *       "kind": "field",
     *       "name": "Read progress",
     *       "search_terms": [
     *         "#read_progress"
     *       ],
     *       "label": "read_progress",
     *       "colnum": 1,
     *       "display": {
     *         "composite_template": "{id:reading_progress()}",
     *         "composite_sort": "number",
     *         "make_category": false,
     *         "contains_html": false,
     *         "composite_show_in_comments": false,
     *         "composite_store_template_value_in_opf": false,
     *         "use_decorations": false,
     *         "description": "To customize this, read the help for the reading_progress() template function in the calibre User Manual",
     *         "web_search_template": ""
     *       },
     *       "is_custom": true,
     *       "is_category": false,
     *       "link_column": "value",
     *       "category_sort": "value",
     *       "is_csp": false,
     *       "is_editable": true,
     *       "rec_index": 23,
     *       "#value#": "4%",
     *       "is_multiple2": {}
     *     }
     *   },
     *   "user_categories": {},
     *   "thumbnail": "/get/thumb/2/calibre-test",
     *   "last_modified": "2026-04-09T14:25:26.129803+00:00",
     *   "link_maps": {},
     *   "uuid": "590901b3-7ace-41ab-9217-3497d4787d07",
     *   "authors": [
     *     "Alastair Reynolds"
     *   ],
     *   "languages": [
     *     "eng"
     *   ],
     *   "series": "Poseidon's Children",
     *   "rating": 2.0,
     *   "title_sort": "On the Steel Breeze",
     *   "author_sort": "Reynolds, Alastair",
     *   "application_id": 2,
     *   "series_index": 2.0,
     *   "tags": [
     *     "Action & Adventure",
     *     "Fiction",
     *     "Hard Science Fiction",
     *     "Science Fiction",
     *     "Space Opera"
     *   ],
     *   "timestamp": "2025-01-08T22:54:04+00:00",
     *   "cover": "/get/cover/2/calibre-test",
     *   "identifiers": {
     *     "isbn": "9780575090453",
     *     "google": "AEFJAgAAQBAJ",
     *     "goodreads": "15999018"
     *   },
     *   "format_metadata": {
     *     "epub": {
     *       "path": "C:\\tmp\\calibre-test\\Alastair Reynolds\\On the Steel Breeze (2)\\On the Steel Breeze - Alastair Reynolds.epub",
     *       "size": 1685564,
     *       "mtime": "2025-01-08T22:54:04.607814+00:00"
     *     }
     *   },
     *   "formats": [
     *     "epub"
     *   ],
     *   "main_format": {
     *     "epub": "/get/epub/2/calibre-test"
     *   },
     *   "other_formats": {},
     *   "category_urls": {
     *     "publisher": {
     *       "Gollancz": "/ajax/books_in/7075626c6973686572/31/calibre-test"
     *     },
     *     "authors": {
     *       "Alastair Reynolds": "/ajax/books_in/617574686f7273/31/calibre-test"
     *     },
     *     "languages": {},
     *     "series": {
     *       "Poseidon's Children": "/ajax/books_in/736572696573/32/calibre-test"
     *     },
     *     "tags": {
     *       "Action & Adventure": "/ajax/books_in/74616773/31/calibre-test",
     *       "Fiction": "/ajax/books_in/74616773/32/calibre-test",
     *       "Hard Science Fiction": "/ajax/books_in/74616773/33/calibre-test",
     *       "Science Fiction": "/ajax/books_in/74616773/34/calibre-test",
     *       "Space Opera": "/ajax/books_in/74616773/35/calibre-test"
     *     }
     *   }
     * }
     * </pre>
     */
    private CalibreBookJsonKey() {
    }
}
