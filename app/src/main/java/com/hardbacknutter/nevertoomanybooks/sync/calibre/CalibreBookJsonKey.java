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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

/**
 * These are the field names the Calibre Content Server uses/expects in its AJAX API.
 * See the constructor javadoc for an example of the json blob.
 */
final class CalibreBookJsonKey {

    static final String USER_METADATA = "user_metadata";

    static final String ID = "application_id";
    static final String UUID = "uuid";
    static final String TITLE = "title";
    static final String DESCRIPTION = "comments";
    static final String LAST_MODIFIED = "last_modified";
    static final String RATING = "rating";

    static final String SERIES = "series";
    static final String SERIES_INDEX = "series_index";

    static final String PUBLISHER = "publisher";

    static final String TAGS_ARRAY = "tags";

    static final String LANGUAGES_ARRAY = "languages";
    static final String AUTHOR_ARRAY = "authors";

    static final String IDENTIFIERS = "identifiers";
    /** The URL when reading; Base64 encoded image when writing. */
    static final String COVER = "cover";

    static final String DATE_PUBLISHED = "pubdate";
    static final String EBOOK_FORMAT = "main_format";

    /**
     * <pre>
     * {
     *   "link_maps": {},
     *   "thumbnail": "/get/thumb/1/calibre-test",
     *   "pubdate": "2009-08-14T23:00:00+00:00",
     *   "identifiers": {
     *     "isbn": "9781101061275"
     *   },
     *   "user_categories": {},
     *   "application_id": 1,
     *   "authors": [
     *     "Alastair Reynolds"
     *   ],
     *   "uuid": "8c6a22d4-d6bd-430e-9ae0-fa13d442e823",
     *   "author_sort_map": {
     *     "Alastair Reynolds": "Reynolds, Alastair"
     *   },
     *   "publisher": "Gollancz",
     *   "tags": [
     *     "Action & Adventure",
     *     "Fiction",
     *     "Hard Science Fiction",
     *     "Science Fiction",
     *     "Space Opera"
     *   ],
     *   "last_modified": "2025-01-08T22:53:56+00:00",
     *   "user_metadata": {},
     *   "rating": 5,
     *   "author_sort": "Reynolds, Alastair",
     *   "languages": [
     *     "eng"
     *   ],
     *   "comments": "<div>\n<p><strong>A spectacular, large-scale space opera - the ultimate galaxy-spanning adventure</strong> </p>\n<p>Six million years ago, at the very dawn of the starfaring era, Abigail Gentian fractured herself into a thousand male and female clones: the shatterlings. Sent out into the galaxy, these shatterlings have stood aloof as they document the rise and fall of countless human empires. They meet every two hundred thousand years, to exchange news and memories of their travels with their siblings. </p>\n<p>Campion and Purslane are not only late for their thirty-second reunion, but they have brought along an amnesiac golden robot for a guest. But the wayward shatterlings get more than the scolding they expect: they face the discovery that someone has a very serious grudge against the Gentian line, and there is a very real possibility of traitors in their midst. The surviving shatterlings have to dodge exotic weapons while they regroup to try to solve the mystery of who is persecuting them, and why - before their ancient line is wiped out of existence, forever.</p></div>",
     *   "timestamp": "2025-01-08T22:53:56+00:00",
     *   "series_index": 1,
     *   "title": "House of Suns",
     *   "series": "House of Suns",
     *   "title_sort": "House of Suns",
     *   "cover": "/get/cover/1/calibre-test",
     *   "format_metadata": {
     *     "epub": {
     *       "path": "C:\\tmp\\calibre-test\\Alastair Reynolds\\House of Suns (1)\\House of Suns - Alastair Reynolds.epub",
     *       "size": 535543,
     *       "mtime": "2025-01-08T22:53:56.786683+00:00"
     *     }
     *   },
     *   "formats": [
     *     "epub"
     *   ],
     *   "main_format": {
     *     "epub": "/get/epub/1/calibre-test"
     *   },
     *   "other_formats": {},
     *   "category_urls": {
     *     "authors": {
     *       "Alastair Reynolds": "/ajax/books_in/617574686f7273/31/calibre-test"
     *     },
     *     "publisher": {
     *       "Gollancz": "/ajax/books_in/7075626c6973686572/31/calibre-test"
     *     },
     *     "tags": {
     *       "Action & Adventure": "/ajax/books_in/74616773/31/calibre-test",
     *       "Fiction": "/ajax/books_in/74616773/32/calibre-test",
     *       "Hard Science Fiction": "/ajax/books_in/74616773/33/calibre-test",
     *       "Science Fiction": "/ajax/books_in/74616773/34/calibre-test",
     *       "Space Opera": "/ajax/books_in/74616773/35/calibre-test"
     *     },
     *     "languages": {},
     *     "series": {
     *       "House of Suns": "/ajax/books_in/736572696573/31/calibre-test"
     *     }
     *   }
     * }
     * </pre>
     */
    private CalibreBookJsonKey() {
    }
}
