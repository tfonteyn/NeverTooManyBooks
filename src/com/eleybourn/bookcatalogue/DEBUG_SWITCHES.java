package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.searches.SearchCoordinator;
import com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

/**
 * Global location where you can switch individual DEBUG options of/off
 * <p>
 * When set to true, the global BuildConfig.DEBUG should still suppress them
 * Use something like this:
 * <p>
 * if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
 * Logger.info("some debug info);
 * }
 * <p>
 * RELEASE: set all to false ! in case you forgot to '&&' with BuildConfig.DEBUG anywhere...
 * <p>
 * The compiler should remove all code between dead if() blocks
 */
public final class DEBUG_SWITCHES {

    public static final boolean TMP_ANTHOLOGY = false;


    public static final boolean BOOKLIST_BUILDER = true;

    /** specific to debugging the broken {@link BooklistBuilder#rebuild()}. */
    public static final boolean BOOKLIST_BUILDER_REBUILD = true;

    public static final boolean BOOKS_ON_BOOKSHELF = true;

    /** enable timers for performance measurements. */
    public static final boolean TIMERS = false;

    /** dump the style each time it is accessed. */
    public static final boolean DUMP_STYLE = false;

    /** {@link LibraryThingManager}. */
    public static final boolean LIBRARY_THING_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.goodreads}. */
    public static final boolean GOODREADS = false;

    /** {@link com.eleybourn.bookcatalogue.utils.ImageUtils}. */
    public static final boolean IMAGE_UTILS = false;

    /** {@link com.eleybourn.bookcatalogue.utils.StorageUtils}. */
    public static final boolean STORAGE_UTILS = false;

    /** {@link TaskManager}. */
    public static final boolean TASK_MANAGER = false;

    /** {@link SearchCoordinator} and related. */
    public static final boolean SEARCH_INTERNET = false;

    /** {@link ISFDBBook}. */
    public static final boolean ISFDB_SEARCH = false;

    /** all things related to {@link ManagedTask}. */
    public static final boolean MANAGED_TASKS = false;

    /** reading/writing a backup file. */
    public static final boolean BACKUP = false;

    /** all things that can happen during startup only. */
    public static final boolean STARTUP = false;

    /** everything related to Dates/Timezone. */
    public static final boolean DATETIME = false;

    /** track the flow & values on startActivityForResult & onActivityResult. */
    public static final boolean ON_ACTIVITY_RESULT = false;

    /** track the flow of onLoadFieldsFromBook/onSaveFieldsToBook. */
    public static final boolean FIELD_BOOK_TRANSFERS = false;

    /**
     * dump savedInstanceState/outState/extras/arguments for functions that
     * have those parameters.
     */
    public static final boolean INSTANCE_STATE = false;

    /**
     * Dump entire HTTP response to System.out.
     * WARNING: can abort the function it's in.
     */
    public static final boolean DUMP_HTTP_RESPONSE = false;

    /**
     * all things XML related.
     */
    public static final boolean XML = false;


    /* ****************************************************************************************** */

    /**
     * The Temporary database tables wil be created as Standard if set.
     */
    public static final boolean TEMP_TABLES_ARE_STANDARD = false;

    /** {@link TrackedCursor}. */
    public static final boolean TRACKED_CURSOR = false;

    /** {@link DBA}. */
    public static final boolean DB_ADAPTER = false;

    /** {@link com.eleybourn.bookcatalogue.database.dbsync}. */
    public static final boolean DB_SYNC = false;

    /**
     * Dump the SQL and the result.
     * {@link SynchronizedStatement#simpleQueryForLong()}
     * {@link SynchronizedStatement#simpleQueryForLongOrZero()}
     */
    public static final boolean DB_SYNC_SIMPLE_QUERY_FOR = false;

    /** dump *all* SQL strings to the log. */
    public static final boolean SQL = false;

    /**
     * Dump SQL CREATE TABLE strings to the log.
     * {@link com.eleybourn.bookcatalogue.database.definitions.TableDefinition}
     */
    public static final boolean SQL_CREATE_TABLE = false;

    /**
     * Dump SQL CREATE INDEX strings to the log.
     * {@link com.eleybourn.bookcatalogue.database.definitions.IndexDefinition}
     */
    public static final boolean SQL_CREATE_INDEX = false;

    /**
     * Dump the SQL.
     * {@link SynchronizedStatement#execute()}
     */
    public static final boolean DB_SYNC_EXECUTE = false;

    /**
     * Dump the SQL.
     * {@link SynchronizedStatement#executeInsert()}
     */
    public static final boolean DB_SYNC_EXECUTE_INSERT = false;

    /**
     * Dump the SQL and the rowsAffected.
     * {@link SynchronizedStatement#executeUpdateDelete()}
     */
    public static final boolean DB_SYNC_ROWS_AFFECTED = false;

    /* ****************************************************************************************** */

    /**
     * Dump the raw Bundle at insert time of a book - LARGE! , not recommended during imports.
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_INSERT = false;

    /**
     * Dump the raw Bundle at update time of a book - LARGE!
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_UPDATE = false;

    private DEBUG_SWITCHES() {
    }
}
