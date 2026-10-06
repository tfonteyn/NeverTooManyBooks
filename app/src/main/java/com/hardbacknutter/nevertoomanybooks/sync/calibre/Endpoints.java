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

import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.CalibreBookJsonKey;

/**
 * Calibre endpoint definitions for the Calibre native "ajax" module
 * and the "nevertoomanybooks" plugin.
 */
final class Endpoints {

    /**
     * The standard request to get information about the libraries available
     * on the server.
     * <p>
     * We're also calling this for connection validation.
     * <p>
     * Param 1: serverUri
     *
     * @see #NTMB_GET_LIBRARY_INFO
     */
    static final String GET_LIBRARY_INFO = "%1$s/ajax/library-info";

    /**
     * The custom plugin request to get information about the libraries available
     * on the server.
     * <p>
     * Param 1: serverUri
     *
     * @see CalibreContentServer#isPluginInstalled()
     * @see #GET_LIBRARY_INFO
     */
    static final String NTMB_GET_LIBRARY_INFO =
            "%1$s/nevertoomanybooks/library-info";

    /**
     * Request the list of virtual libraries.
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: (ids) csv list of book ids
     *
     * @see CalibreContentServer#isPluginInstalled()
     */
    static final String NTMB_VIRTUAL_LIBRARIES_FOR_BOOKS =
            "%1$s/nevertoomanybooks/virtual-libraries-for-books/%2$s?ids=%3$s";

    /**
     * Similar to {@link #GET_BOOKS_BY_ID} but
     * instead of full book objects the result contains only the fields
     * {@link CalibreBookJsonKey#LAST_MODIFIED},
     * {@link CalibreBookJsonKey#IDENTIFIERS}.
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: (ids) a csv list of numeric book ids
     */
    static final String NTMB_PREP_FOR_PUSHING =
            "%1$s/nevertoomanybooks/prep-for-pushing/%2$s?ids=%3$s";
    /**
     * Run a search.
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: (num) the maximum number of entries to return
     * Param 4: (offset) the offset for the next set to return
     * Param 5: (query) the query to execute
     */
    static final String SEARCH = "%1$s/ajax/search/%2$s"
                                 + "?num=%3$d&offset=%4$d"
                                 + "&query=%5$s";

    /**
     * Fetch all book ids for the given library.
     * The ids are fetched in 'pages' of {number of books} starting at {offset}.
     * <ul>
     * <li>{@code "616c6c626f6f6b73" == "allbooks"}</li>
     * <li>{@code "30" == '0'} ignored, used as placeholder</li>
     * </ul>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: number of books
     * Param 4: offset to start fetching from
     *
     * @see CalibreContentServer#getBookIds(String, int, int)
     */
    static final String GET_BOOK_IDS =
            "%1$s/ajax/books_in/616c6c626f6f6b73/30/%2$s?num=%3$d&offset=%4$d";

    /**
     * Fetch a single book by its numeric id.
     * <p>
     * Param 1: serverUri
     * Param 2: book id (as a string)
     * Param 3: libraryStringId
     */
    static final String GET_BOOK_BY_ID = "%1$s/ajax/book/%2$s/%3$s?category_urls=false";

    /**
     * Fetch a single book by its UUID.
     * <p>
     * Param 1: serverUri
     * Param 2: book UUID
     * Param 3: libraryStringId
     */
    static final String GET_BOOK_BY_UUID = GET_BOOK_BY_ID + "&id_is_uuid=true";

    /**
     * Fetch a set of books by their numeric ids.
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: (ids) a csv list of numeric book ids
     */
    static final String GET_BOOKS_BY_ID = "%1$s/ajax/books/%2$s?ids=%3$s&category_urls=false";

    /**
     * Fetch a set of books by their UUIDs.
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: (ids) a csv list of book UUIDs
     */
    static final String GET_BOOKS_BY_UUID = GET_BOOKS_BY_ID + "&id_is_uuid=true";

    /**
     * Request a file download.
     * <p>
     * Param 1: serverUri
     * Param 2: file format
     * Param 3: calibre book id
     * Param 4: libraryStringId
     */
    static final String FETCH_FILE = "%1$s/get/%2$s/%3$d/%4$s";

    /**
     * Push changes for a book to the server.
     * Covers can be included as base64.
     * <p>
     * Param 1: serverUri
     * Param 2: calibre book id
     * Param 3: libraryStringId
     */
    static final String PUSH_CHANGES = "%1$s/cdb/set-fields/%2$d/%3$s";

    /**
     * This endpoint in the Calibre code seems not to be used.
     * Not tested as yet. Added here to remind ourselves of its existence.
     * Currently we're using {@link #PUSH_CHANGES} with base64 encoded covers.
     * <p>
     * Param 1: serverUri
     * Param 2: calibre book id
     * Param 3: libraryStringId
     */
    static final String UPLOAD_COVER = "%1$s/cdb/set-cover/%2$d/%3$s";

    private Endpoints() {
    }
}
