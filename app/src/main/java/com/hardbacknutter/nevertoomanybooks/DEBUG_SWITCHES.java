/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Activity;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
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
    /** {@link BooksOnBookshelfModel} Expand/Collapsing nodes. */
    public static final boolean BOOK_LIST_NODE_STATE = false;
    /** {@link BooklistCursor}. */
    public static final boolean BOB_PSEUDO_CURSOR = false;
    /** Dump SQL when executing  {@link SynchronizedStatement}. */
    public static final boolean DB_STMT_EXECUTE = false;

    /** enable timers for performance measurements. */
    public static final boolean TIMERS = false;
    /** track the flow & values on startActivityForResult & onActivityResult. */
    public static final boolean ON_ACTIVITY_RESULT = false;
    /** reading/writing a backup file. */
    public static final boolean BACKUP = false;
    /** {@link com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel}. */
    public static final boolean STARTUP_TASKS = false;
    /** Where listeners are held in a WeakReference, log dead references. */
    public static final boolean TRACE_WEAK_REFERENCES = false;

    /** {@link CoverBrowserDialogFragment}. */
    public static final boolean COVER_BROWSER = false;
    /** {@link com.hardbacknutter.nevertoomanybooks.utils.ImageUtils}. */
    public static final boolean IMAGE_UTILS = false;
    /** all things XML related. */
    public static final boolean XML = false;
    /** all things network related. */
    public static final boolean NETWORK = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.searches.JsoupBase}. */
    public static final boolean JSOUP = false;
    /** {@link com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator}. */
    public static final boolean SEARCH_COORDINATOR = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.goodreads}. */
    public static final boolean GOODREADS = false;
    /** {@link com.hardbacknutter.nevertoomanybooks.searches.isfdb}. */
    public static final boolean ISFDB = false;

    /** {@link com.hardbacknutter.nevertoomanybooks.database.dbsync}. */
    public static final boolean DB_SYNC = false;
    public static final boolean DB_SYNC_LOCKING = false;
    /** Dump SQL for {@link SynchronizedDb#execSQL(String)}. */
    public static final boolean DB_SYNC_EXEC_SQL = false;
    /** {@link BooksOnBookshelfModel#buildBookList}. */
    public static final boolean BOB_INIT_BOOK_LIST = false;

    /**
     * {@link com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter}.
     * Normal logging.
     */
    public static final boolean IMPORT_CSV_BOOKS = false;
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter}.
     * Extensive logging.
     */
    public static final boolean IMPORT_CSV_BOOKS_EXT = false;

    /** dump the style each time it is accessed. Medium length in the log. */
    public static final boolean DUMP_STYLE = false;
    public static final boolean LOCALE = false;

    /** Log the full flow of {@link Activity#recreate()}. */
    public static final boolean RECREATE_ACTIVITY = false;

    /** Enable strict mode reporting on network,disc,... usage. */
    public static final boolean STRICT_MODE = false;

    private DEBUG_SWITCHES() {
    }
}
