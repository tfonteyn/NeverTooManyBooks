package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.Prefs;

/**
 * Global location where you can switch individual DEBUG options of/off
 *
 * When set to true, the global BuildConfig.DEBUG should still suppress them
 * Use something like this:
 *
 * if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
 * Logger.info("some debug info);
 * }
 *
 * RELEASE: set all to false ! in case you forgot to '&&' with BuildConfig.DEBUG anywhere...
 *
 * The compiler should remove all code between dead if() blocks
 */
public final class DEBUG_SWITCHES {

    public static final boolean TMP_ANTHOLOGY = true;
    /** dumps the raw Bundle at insert time of a book - LARGE! , not recommended during imports. */
    public static final boolean DUMP_BOOK_BUNDLE_AT_INSERT = false;
    /** dumps the raw Bundle at update time of a book - LARGE! */
    public static final boolean DUMP_BOOK_BUNDLE_AT_UPDATE = false;

    public static final boolean BOOKLIST_BUILDER = false;

    /** specific to debugging the broken {@link BooklistBuilder#rebuild()} */
    public static final boolean BOOKLIST_BUILDER_REBUILD = false;

    public static final boolean BOOKS_ON_BOOKSHELF = false;

    /** enable timers for performance measurements */
    public static final boolean TIMERS = false;

    /** {@link Prefs} */
    public static final boolean PREFS = true;

    public static final boolean DUMP_STYLE = true;

    /** dump the sql string to the log */
    public static final boolean SQL = false;

    /** {@link com.eleybourn.bookcatalogue.database.CatalogueDBAdapter} */
    public static final boolean DB_ADAPTER = false;

    /** {@link com.eleybourn.bookcatalogue.database.DbSync} */
    public static final boolean DB_SYNC = false;
    /** {@link com.eleybourn.bookcatalogue.database.DbSync} */
    public static final boolean DB_SYNC_QUERY_FOR_LONG = false;

    /** {@link com.eleybourn.bookcatalogue.database.cursors.TrackedCursor} */
    public static final boolean TRACKED_CURSOR = false;

    /** {@link com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager} */
    public static final boolean LIBRARY_THING_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.goodreads} */
    public static final boolean GOODREADS = false;

    /** {@link com.eleybourn.bookcatalogue.utils.ImageUtils} */
    public static final boolean IMAGE_UTILS = false;

    /** {@link SimpleTaskQueueProgressDialogFragment} */
    public static final boolean SQPFragment = false;

    /** {@link com.eleybourn.bookcatalogue.utils.StorageUtils} */
    public static final boolean STORAGE_UTILS = false;

    /** {@link TaskManager} */
    public static final boolean TASK_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.SearchManager} and related */
    public static final boolean SEARCH_INTERNET = true;

    /** {@link com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook} */
    public static final boolean ISFDB_SEARCH = true;

    /** all things related to {@link com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask} */
    public static final boolean MANAGED_TASKS = false;

    /** all things related to {@link com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTask} */
    public static final boolean SIMPLE_TASKS = false;

    /** reading/writing a backup file */
    public static final boolean BACKUP = true;

    /** all things that can happen during startup only */
    public static final boolean STARTUP = false;

    /** everything related to Dates/Timezone... */
    public static final boolean DATETIME = true;

    /** track the flow & values on startActivityForResult & onActivityResult */
    public static final boolean ON_ACTIVITY_RESULT = false;

    /** track the flow of onLoadFieldsFromBook/onSaveFieldsToBook */
    public static final boolean FIELD_BOOK_TRANSFERS = false;

    /** dump savedInstanceState/outState/extras/arguments for functions that have those parameters */
    public static final boolean INSTANCE_STATE = false;

    /**
     * Dump entire HTTP response to System.out
     * WARNING: can abort the function it's in.
     */
    public static final boolean DUMP_HTTP_RESPONSE = false;
    /**
     * The Temporary database tables wil be created as Standard if set to true.
     */
    public static final boolean TEMP_TABLES_ARE_STANDARD = true;

    private DEBUG_SWITCHES() {
    }
}
