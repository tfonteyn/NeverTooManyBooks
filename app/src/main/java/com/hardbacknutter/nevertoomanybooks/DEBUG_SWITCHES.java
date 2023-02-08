/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.activity.result.contract.ActivityResultContract;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;

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

    /** Make the {@link Booklist} use a standard table instead of Temporary ones. */
    public static final boolean BOOK_LIST_USES_STANDARD_TABLE = false;

    /** {@link Booklist}. */
    public static final boolean BOB_THE_BUILDER = false;
    /** enable timers for rough performance measurements. */
    public static final boolean BOB_THE_BUILDER_TIMERS = false;

    /** {@link BooksOnBookshelfViewModel}. */
    public static final boolean BOB_INIT_BOOK_LIST = false;
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.booklist.BooklistNodeDao}
     * Expand/Collapsing nodes.
     */
    public static final boolean BOB_NODE_STATE = false;
    /**
     * {@link BooklistAdapter}
     * {@link BooksOnBookshelf}
     * Display the position and node id. Adds a View from code.
     */
    public static final boolean BOB_NODE_POSITIONS = false;


    /** track results from {@link ActivityResultContract#parseResult}. */
    public static final boolean ON_ACTIVITY_RESULT = false;

    /** all things related to cover image handling. */
    public static final boolean COVERS = false;

    /** all things XML related. */
    public static final boolean XML = false;
    /** all things network related. */
    public static final boolean NETWORK = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.network.JsoupLoader}. */
    public static final boolean JSOUP = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator}. */
    public static final boolean SEARCH_COORDINATOR = false;
    /** enable timers for rough performance measurements. */
    public static final boolean SEARCH_COORDINATOR_TIMERS = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.searchengines.isfdb}. */
    public static final boolean ISFDB = false;

    public static final boolean IMPORT_CSV_BOOKS = false;
    public static final boolean IMPORT_CSV_BOOKS_EXT = false;

    public static final boolean IMPORT_CALIBRE_BOOKS = false;
    public static final boolean IMPORT_STRIP_INFO_BOOKS = false;

    /** Dump SQL. */
    public static final boolean DB_EXEC_SQL = false;

    /** Enable strict mode reporting on network,disc,... */
    public static final boolean STRICT_MODE_THREADING = false;

    private DEBUG_SWITCHES() {
    }
}
