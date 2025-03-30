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

/**
 * {@link com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine}
 * <p>
 * When the user has configured their credentials for the site,
 * the engine will perform a login and have access to collection data for the individual books.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.CollectionFormParser}:
 * posts a request to the site to get the "side-panel" with the full collection data as a FORM.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.CollectionParser}:
 * reads the individual fields.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData}:
 * puts collection data is into the Book.
 * <p>
 * ============================================================
 * <p>
 * Synchronization access uses the standard reader/writer approach.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoReader}
 * Fetches and parses the <strong>user collection list page</strong> from the site.
 * This list is available from the site top row menu bar.
 * Actual fetching and processing is done by:
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.UserCollection}
 * See that class for more docs on sync-reading.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoWriter}
 * Posting to the site is done by
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.CollectionFormUploader}
 * See that class for more docs on sync-writing.
 * <p>
 * ============================================================
 * <p>
 * Helpers:
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData}
 * holds the data for one book.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.sync.stripinfo.BookshelfMapper}
 * takes care of mapping the 'wishlist' and similar site flags to local Bookshelves.
 */
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;
