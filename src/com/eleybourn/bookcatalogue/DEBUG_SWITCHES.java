package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;

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

    /* ****************************************************************************************** */

    /** Enable strict mode reporting on network,disc,... usage. */
    public static final boolean STRICT_MODE = false;

    /** enable timers for performance measurements. */
    public static final boolean TIMERS = false;

    /* ****************************************************************************************** */


    /** to be removed soon. */
    public static final boolean TMP_ANTHOLOGY = true;




    /** Global replace author/series/... */
    public static final boolean DBA_GLOBAL_REPLACE = false;

    /** Log the full flow of {@link Activity#recreate()}. */
    public static final boolean RECREATE_ACTIVITY = false;

    /** {@link com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask}. */
    public static final boolean MANAGED_TASKS = false;

    /** reading/writing a backup file. */
    public static final boolean BACKUP = false;

    /** everything related to Dates/Timezone. */
    public static final boolean DATETIME = false;

    /** track the flow & values on startActivityForResult & onActivityResult. */
    public static final boolean ON_ACTIVITY_RESULT = false;

    /** all things XML related. */
    public static final boolean XML = false;



    /* ****************************************************************************************** */

    /** {@link com.eleybourn.bookcatalogue.booklist.BooklistBuilder} and related. */
    public static final boolean BOOKLIST_BUILDER = false;

    /** {@link BooksOnBookshelf}. */
    public static final boolean BOOKS_ON_BOOKSHELF = false;

    /** {@link CoverBrowser}. */
    public static final boolean COVER_BROWSER = false;

    /** {@link com.eleybourn.bookcatalogue.utils.ImageUtils}. */
    public static final boolean IMAGE_UTILS = false;

    /** {@link com.eleybourn.bookcatalogue.utils.StorageUtils}. */
    public static final boolean STORAGE_UTILS = false;

    /**
     * {@link com.eleybourn.bookcatalogue.utils.NetworkUtils}.
     * {@link com.eleybourn.bookcatalogue.tasks.TerminatorConnection}.
     */
    public static final boolean NETWORK = false;


    /* ****************************************************************************************** */

    /** {@link com.eleybourn.bookcatalogue.searches.SearchCoordinator} and related. */
    public static final boolean SEARCH_INTERNET = false;

    /**
     * GoodReads search, but also the sync API.
     * {@link com.eleybourn.bookcatalogue.searches.goodreads}.
     */
    public static final boolean GOODREADS = false;

    /** {@link com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager}. */
    public static final boolean LIBRARY_THING_MANAGER = false;

    /** {@link com.eleybourn.bookcatalogue.searches.isfdb.ISFDBBook}. */
    public static final boolean ISFDB_SEARCH = false;

    /** {@link com.eleybourn.bookcatalogue.searches.openlibrary.OpenLibraryManager}. */
    public static final boolean OPEN_LIBRARY_SEARCH = false;


    /* ****************************************************************************************** */

    /** {@link com.eleybourn.bookcatalogue.database.cursors.TrackedCursor}. */
    public static final boolean TRACKED_CURSOR = false;

    /** {@link DBA}. */
    public static final boolean DB_ADAPTER = false;

    /** {@link com.eleybourn.bookcatalogue.database.dbsync}. */
    public static final boolean DB_SYNC = false;
    public static final boolean DB_SYNC_LOCKING = false;


    /* ****************************************************************************************** */

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
     * Dump execSQL strings to the log.
     * {@link com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb#execSQL(String)}
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

    /* ****************************************************************************************** */

    /**
     * Dump the raw Bundle at insert time of a book - LARGE! , not recommended during imports.
     * {@link DBA#insertBook}.
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_INSERT = false;

    /**
     * Dump the raw Bundle at update time of a book - LARGE!
     * {@link DBA#updateBook}
     */
    public static final boolean DUMP_BOOK_BUNDLE_AT_UPDATE = false;

    /**
     * dump savedInstanceState/outState/extras/arguments for functions that
     * have those parameters.
     */
    public static final boolean DUMP_INSTANCE_STATE = false;

    /** dump the style each time it is accessed. Medium length in the log. */
    public static final boolean DUMP_STYLE = false;

    /**
     * Dump entire HTTP response to System.out.
     * WARNING: can abort the function it's in.
     */
    public static final boolean DUMP_HTTP_RESPONSE = false;

    private DEBUG_SWITCHES() {
    }

    /**
     * complex sql + fetch
     * t=806.311.600|count=983|.
     * simple + NO fetch
     * t=799.380.500|count=983
     * simple + extra fetch
     * t=1.254.915.700|count=983
     *
     */
    static void debugPerformanceFetchExtras(@NonNull final Context context) {
        DBA mDb = new DBA(context);


        /* Bitmask indicating which extras to get. */
        int mExtras = BooklistStyle.EXTRAS_AUTHOR
                & BooklistStyle.EXTRAS_FORMAT
                & BooklistStyle.EXTRAS_LOCATION
                & BooklistStyle.EXTRAS_PUBLISHER;
//                & BooklistStyle.EXTRAS_BOOKSHELVES;

        /* Locale to use for formatting. */
        Locale mLocale;
        /* Resulting location data. */
        String mLocation;
        /* Resulting publisher data. */
        String mPublisher;
        /* Resulting Format data. */
        String mFormat;
        /* Resulting author data. */
        String mAuthor;
        /* Resulting shelves data. */
        String mShelves;
        int count =0;
        long t = System.nanoTime();
        for (long mBookId = 1; mBookId < 1000; mBookId++) {
            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId, mExtras)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    continue;
                }
                count++;
                ColumnMapper mapper = new ColumnMapper(cursor, null,
                                                       DOM_AUTHOR_FORMATTED,
                                                       DOM_BOOK_LOCATION,
                                                       DOM_BOOK_FORMAT,
                                                       DOM_BOOK_PUBLISHER,
                                                       DOM_BOOK_DATE_PUBLISHED,
                                                       DOM_BOOKSHELF);

                if ((mExtras & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    mAuthor = mapper.getString(DOM_AUTHOR_FORMATTED);
                }

                if ((mExtras & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    mLocation = mapper.getString(DOM_BOOK_LOCATION);
                }

                if ((mExtras & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    mFormat = mapper.getString(DOM_BOOK_FORMAT);
                }

//                if (((mExtras & BooklistStyle.EXTRAS_BOOKSHELVES) != 0)) {
                    //TEST performance:
                    mShelves = Bookshelf.toDisplayString(mDb.getBookshelvesByBookId(mBookId));
//                    mShelves = mapper.getString(DOM_BOOKSHELF);
//                }

                if ((mExtras & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                    mPublisher = mapper.getString(DOM_BOOK_PUBLISHER);
//                    String tmpPubDate = mapper.getString(DOM_BOOK_DATE_PUBLISHED);
                }
            } catch (NumberFormatException e) {
//                Logger.error(this, e);
            }
        }
        Logger.debug(BooksMultiTypeListHandler.class, "", "t=" + (System.nanoTime() - t), "count=" + count);
    }
}
