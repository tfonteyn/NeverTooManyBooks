package com.hardbacknutter.nevertomanybooks;

import android.app.Activity;

import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertomanybooks.searches.isfdb.IsfdbBook;

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
public final class DEBUG_SWITCHES {

    // special case, uncomment this to enable OAUTH debug messages
//    static {
//            System.setProperty("debug","true");
//    }

    public static final boolean USER_MESSAGE_STACK_TRACE = false;

    /* ****************************************************************************************** */
    /** enable timers for performance measurements. */
    public static final boolean TIMERS = false;
    /** Log the full flow of {@link Activity#recreate()}. */
    public static final boolean RECREATE_ACTIVITY = false;

    /* ****************************************************************************************** */
    /** track the flow & values on startActivityForResult & onActivityResult. */
    public static final boolean ON_ACTIVITY_RESULT = false;
    /** reading/writing a backup file. */
    public static final boolean BACKUP = false;
    /** {@link com.hardbacknutter.nevertomanybooks.viewmodels.StartupViewModel}. */
    public static final boolean STARTUP_TASKS = false;

    /* ****************************************************************************************** */
    /** {@link com.hardbacknutter.nevertomanybooks.tasks.managedtasks.ManagedTask}. */
    public static final boolean MANAGED_TASKS = true;
    /** Where listeners are held in a WeakReference, log dead references. */
    public static final boolean TRACE_WEAK_REFERENCES = false;
    /** {@link com.hardbacknutter.nevertomanybooks.booklist.BooklistBuilder} and related. */
    public static final boolean BOOKLIST_BUILDER = false;

    /* ****************************************************************************************** */
    /** {@link com.hardbacknutter.nevertomanybooks.viewmodels
     * .BooksOnBookshelfModel#initBookList}. */
    public static final boolean BOB_INIT_BOOK_LIST = false;
    /** {@link CoverBrowserFragment}. */
    public static final boolean COVER_BROWSER = false;
    /** {@link com.hardbacknutter.nevertomanybooks.utils.ImageUtils}. */
    public static final boolean IMAGE_UTILS = false;
    /** {@link com.hardbacknutter.nevertomanybooks.utils.StorageUtils}. */
    public static final boolean STORAGE_UTILS = false;
    /** everything related to Dates/Timezone. */
    public static final boolean DATETIME = false;
    /** all things XML related. */
    public static final boolean XML = false;
    /**
     * {@link com.hardbacknutter.nevertomanybooks.utils.NetworkUtils}.
     * {@link com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection}.
     */
    public static final boolean NETWORK = false;
    /** {@link com.hardbacknutter.nevertomanybooks.searches.SearchCoordinator} and related. */
    public static final boolean SEARCH_INTERNET = false;
    /**
     * GoodReads API.
     * {@link com.hardbacknutter.nevertomanybooks.goodreads}.
     */
    public static final boolean GOODREADS = false;

    /* ****************************************************************************************** */
    /** {@link com.hardbacknutter.nevertomanybooks.searches.librarything.LibraryThingManager}. */
    public static final boolean LIBRARY_THING_MANAGER = false;
    /** {@link IsfdbBook}. */
    public static final boolean ISFDB_SEARCH = false;
    /** {@link com.hardbacknutter.nevertomanybooks.searches.openlibrary.OpenLibraryManager}. */
    public static final boolean OPEN_LIBRARY_SEARCH = false;
    /** {@link DAO}. Check for leaking instances. */
    public static final boolean DAO_INSTANCE_COUNT = false;
    /** {@link BooksOnBookshelf}. Inserting into the link tables between a Book and X. */
    public static final boolean DAO_INSERT_BOOK_LINKS = false;


    /* ****************************************************************************************** */
    /** Global replace author/series/... */
    public static final boolean DAO_GLOBAL_REPLACE = false;
    /** Insert & update TOC entries. */
    public static final boolean DAO_TOC = false;
    /** {@link com.hardbacknutter.nevertomanybooks.database.cursors.TrackedCursor}. */
    public static final boolean TRACKED_CURSOR = false;
    /**
     * Dump SQL CREATE / DROP strings to the log.
     * {@link com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition}
     * {@link com.hardbacknutter.nevertomanybooks.database.definitions.IndexDefinition}
     */
    public static final boolean SQL_DDL = false;

    /* ****************************************************************************************** */
    /** {@link com.hardbacknutter.nevertomanybooks.database.dbsync}. */
    public static final boolean DB_SYNC = false;
    public static final boolean DB_SYNC_LOCKING = false;
    /**
     * Dump execSQL strings to the log.
     * {@link com.hardbacknutter.nevertomanybooks.database.dbsync.SynchronizedDb#execSQL(String)}
     */
    public static final boolean DB_SYNC_EXEC_SQL = false;
    /**
     * Dump the SQL and the result.
     * {@link SynchronizedStatement#simpleQueryForLong()}
     * {@link SynchronizedStatement#simpleQueryForLongOrZero()}
     * {@link SynchronizedStatement#count()}
     * {@link SynchronizedStatement#simpleQueryForString()}
     */
    public static final boolean DB_SYNC_SIMPLE_QUERY_FOR = false;
    /**
     * Dump the SQL for all 'execute...' calls.
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
    public static final boolean DB_SYNC_EXECUTE_UPDATE_DELETE = false;
    /**
     * Dump the raw Bundle at insert time of a book - LARGE! , not recommended during imports.
     * {@link DAO#insertBook}.
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_INSERT = false;
    /**
     * Dump the raw Bundle at update time of a book. LARGE!
     * {@link DAO#updateBook}
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_UPDATE = false;

    /* ****************************************************************************************** */
    /**
     * dump savedInstanceState/outState/extras/arguments for functions that
     * have those parameters.
     */
    public static final boolean DUMP_INSTANCE_STATE = false;
    /** dump the style each time it is accessed. Medium length in the log. */
    public static final boolean DUMP_STYLE = false;
    /**
     * Dump (just) the URL used in
     * {@link com.hardbacknutter.nevertomanybooks.tasks.TerminatorConnection}.
     */
    public static final boolean DUMP_HTTP_URL = false;
    /**
     * Dump entire HTTP response to System.out.
     * WARNING: can abort the function it's in.
     */
    public static final boolean DUMP_HTTP_RESPONSE = false;
    /** Enable strict mode reporting on network,disc,... usage. */
    static final boolean STRICT_MODE = false;
    /** {@link BooksOnBookshelf}#fixPositionWhenDrawn. */
    static final boolean BOB_FIX_POSITION = false;

    private DEBUG_SWITCHES() {
    }
}
