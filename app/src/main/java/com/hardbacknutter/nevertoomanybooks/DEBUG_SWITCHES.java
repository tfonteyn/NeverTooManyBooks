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
package com.hardbacknutter.nevertoomanybooks;

import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;

/**
 * Global location where you can switch individual DEBUG options of/off
 * <p>
 * When set to true, the global BuildConfig.DEBUG should still suppress them
 * Use something like this:
 * <p>
 * if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
 * Logger.info("some debug info);
 * }
 * <p>
 * RELEASE: set all to false ! in case you forgot to '&&' with BuildConfig.DEBUG anywhere...
 * <p>
 * The compiler should remove all code between dead if() blocks
 */
@SuppressWarnings("WeakerAccess")
public final class DEBUG_SWITCHES {

    // special case, uncomment this to enable OAUTH debug messages
//    static {
//            System.setProperty("debug","true");
//    }

    /**
     * Make the {@link com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder}
     * use standard tables instead of Temporary ones.
     */
    public static final boolean BOOK_LIST_USES_STANDARD_TABLES = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder}. */
    public static final boolean BOB_THE_BUILDER = false;
    /** enable timers for rough performance measurements. */
    public static final boolean BOB_THE_BUILDER_TIMERS = false;

    /** {@link BooksOnBookshelfModel}. */
    public static final boolean BOB_INIT_BOOK_LIST = false;
    /** {@link BooksOnBookshelfModel} Expand/Collapsing nodes. */
    public static final boolean BOB_NODE_STATE = false;
    /** {@link BooksOnBookshelfModel} Display the position and node id. Adds a View from code. */
    public static final boolean BOB_NODE_ID = false;

    /** track the flow & values on startActivityForResult & onActivityResult. */
    public static final boolean ON_ACTIVITY_RESULT = false;

    /** {@link CoverBrowserDialogFragment}. */
    public static final boolean COVER_BROWSER = false;

    /** all things XML related. */
    public static final boolean XML = false;
    /** all things network related. */
    public static final boolean NETWORK = false;

    /** {@link JsoupLoader}. */
    public static final boolean JSOUP = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator}. */
    public static final boolean SEARCH_COORDINATOR = false;
    /** enable timers for rough performance measurements. */
    public static final boolean SEARCH_COORDINATOR_TIMERS = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.goodreads}. */
    public static final boolean GOODREADS_HTTP_XML = false;
    public static final boolean GOODREADS_SEND = false;
    public static final boolean GOODREADS_IMPORT = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.searches.isfdb}. */
    public static final boolean ISFDB = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter}. */
    public static final boolean IMPORT_CSV_BOOKS = false;
    /** {@link com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter}. Extensive. */
    public static final boolean IMPORT_CSV_BOOKS_EXT = false;

    public static final boolean IMPORT_CALIBRE_BOOKS = false;

    /** Dump SQL. */
    public static final boolean DB_EXEC_SQL = false;

    /** Enable strict mode reporting on network,disc,... usage. */
    public static final boolean STRICT_MODE = false;

    private DEBUG_SWITCHES() {
    }
}
