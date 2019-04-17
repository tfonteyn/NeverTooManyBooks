package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.File;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.IndexDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_DOCID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_TITLE_LC;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_UUID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_TOC_ENTRIES;


/**
 * Our version of {@link SQLiteOpenHelper} handling {@link #onCreate} and {@link #onUpgrade}.
 * Uses the application context.
 * Singleton.
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /**
     * RELEASE: Update database version. (last official version was 82).
     */
    public static final int DATABASE_VERSION = 100;

    /**
     * the one and only (aside of the covers one...).
     */
    private static final String DATABASE_NAME = "book_catalogue";

    /**
     * Indexes which have not been added to the TBL definitions yet.
     */
    private static final String[] DATABASE_CREATE_INDICES = {
            // for now, there is no API to add an index with a collation suffix.
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_GIVEN_NAMES + DBA.COLLATION + ')',
            // for now, there is no API to add an index with a collation suffix.
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_FAMILY_NAME + DBA.COLLATION + ')',

            // for now, there is no API to add an index with a collation suffix.
            // TOMF: is this needed after adding the _lc column?
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS
                    + " (" + DOM_TITLE + DBA.COLLATION + ')',
            };


    /** Readers/Writer lock for this database. */
    private static Synchronizer sSynchronizer;

    /** Singleton. */
    private static DBHelper sInstance;

    /**
     * Constructor.
     *
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    private DBHelper(@SuppressWarnings("SameParameterValue")
                     @NonNull final SQLiteDatabase.CursorFactory factory,
                     @SuppressWarnings("SameParameterValue")
                     @NonNull final Synchronizer synchronizer) {
        // *always* use the app context!
        super(App.getAppContext(), DATABASE_NAME, factory, DATABASE_VERSION);
        sSynchronizer = synchronizer;
    }

    /**
     * Get the singleton instance.
     *
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    public static DBHelper getInstance(@SuppressWarnings("SameParameterValue")
                                       @NonNull final SQLiteDatabase.CursorFactory factory,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final Synchronizer synchronizer) {
        if (sInstance == null) {
            sInstance = new DBHelper(factory, synchronizer);
        }
        return sInstance;
    }

    /**
     * @return the physical path of the database file.
     */
    public static String getDatabasePath() {
        return App.getAppContext().getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column reordering. This method will copy ALL columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to NULL if no default
     * is defined.
     *
     * @param sdb         the database
     * @param source      from table
     * @param destination to table
     * @param toRemove    (optional) List of fields to be removed from the source table
     *                    (skipped in copy)
     */
    static void copyTableSafely(@NonNull final SynchronizedDb sdb,
                                @SuppressWarnings("SameParameterValue") @NonNull final String source,
                                @NonNull final String destination,
                                @NonNull final String... toRemove) {
        // Get the source info
        TableInfo sourceTable = new TableInfo(sdb, source);
        // Build the column list
        StringBuilder cols = new StringBuilder();
        boolean first = true;
        for (ColumnInfo ci : sourceTable.getColumns()) {
            boolean isNeeded = true;
            for (String s : toRemove) {
                if (s.equalsIgnoreCase(ci.name)) {
                    isNeeded = false;
                    break;
                }
            }
            if (isNeeded) {
                if (first) {
                    first = false;
                } else {
                    cols.append(',');
                }
                cols.append(ci.name);
            }
        }
        String colList = cols.toString();
        String sql = "INSERT INTO " + destination + '(' + colList + ") SELECT " + colList + " FROM " + source;
        try (SynchronizedStatement stmt = sdb.compileStatement(sql)) {
            stmt.executeInsert();
        }
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              the database
     * @param tableToRecreate the table
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    private static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                               @SuppressWarnings("SameParameterValue")
                                               @NonNull final TableDefinition tableToRecreate,
                                               @NonNull final String... toRemove) {
        final String tableName = tableToRecreate.getName();
        final String tempName = "recreate_tmp";
        db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tempName);
        tableToRecreate.createAll(db);
        // This handles re-ordered fields etc.
        copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * Run at installation (and v200 upgrade) time to add the builtin style id's to the database.
     * This allows foreign keys to work.
     * <p>
     * Set's both id and uuid columns to the builtin style id.
     *
     * @param syncedDb the database
     */
    private static void prepareStylesTable(@NonNull final SynchronizedDb syncedDb) {
        String sqlInsertStyles =
                "INSERT INTO " + TBL_BOOKLIST_STYLES + '('
                        + DOM_PK_ID
                        + ',' + DOM_UUID
                        + ") VALUES(?,?)";
        try (SynchronizedStatement stmt = syncedDb.compileStatement(sqlInsertStyles)) {
            for (long id = BooklistStyles.BUILTIN_MAX_ID; id < 0; id++) {
                stmt.bindLong(1, id);
                stmt.bindString(2, String.valueOf(id));
                // oops... after inserting '-1' our debug logging will claim that insert failed.
                if (BuildConfig.DEBUG /* always */) {
                    if (id == -1) {
                        Logger.debug(BooklistStyles.class, "prepareStylesTable",
                                     "Ignore the debug message about inserting -1 here...");
                    }
                }
                stmt.executeInsert();
            }
        }
    }

    /**
     * This function is called when the database is first created.
     *
     * @param db The database to be created
     */
    @SuppressWarnings("unused")
    @Override
    @CallSuper
    public void onCreate(@NonNull final SQLiteDatabase db) {
        // 'Upgrade' from not being installed. Run this first to avoid racing issues.
        UpgradeMessageManager.setUpgradeAcknowledged();

        if (BuildConfig.DEBUG /* always */) {
            Logger.debugEnter(this, "onCreate", "database: " + db.getPath());
        }

        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        TableDefinition.createTables(syncedDb,
                                     // app tables
                                     TBL_BOOKLIST_STYLES,
                                     // basic user data tables
                                     TBL_BOOKSHELF,
                                     TBL_AUTHORS,
                                     TBL_SERIES,
                                     TBL_BOOKS,
                                     TBL_TOC_ENTRIES,
                                     // link tables
                                     TBL_BOOK_TOC_ENTRIES,
                                     TBL_BOOK_AUTHOR,
                                     TBL_BOOK_BOOKSHELF,
                                     TBL_BOOK_SERIES,
                                     TBL_BOOK_LOANEE,
                                     // permanent booklist management tables
                                     TBL_BOOK_LIST_NODE_SETTINGS);

        // create the indexes not covered in the calls above.
        createIndices(syncedDb, false);

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        syncedDb.execSQL("INSERT INTO " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ')'
                                 + " VALUES ('"
                                 + App.getAppContext().getString(R.string.bookshelf_my_books)
                                 + "')");

        // insert the builtin style id's so foreign key rules are possible.
        prepareStylesTable(syncedDb);

        //reminder: FTS columns don't need a type nor constraints
        TBL_BOOKS_FTS.create(syncedDb, false);

        createTriggers(syncedDb);
    }

    /**
     * Create all database triggers.
     *
     * <p>
     * set Book dirty when:
     * - Author: delete, update.
     * - Bookshelf: delete, update.
     * - Series: delete, update.
     * - Loan: delete, update, insert.
     *
     * <p>
     * Update FTS when:
     * - Book: delete (note: update,insert is to complicated to use a trigger)
     *
     * <p>
     * Others:
     * - When a books ISBN is updated, reset external data.
     *
     * <p>
     * not needed + why now:
     * - insert a new Series,Author,TocEntry is only done when a Book is inserted/updated.
     * - insert a new Bookshelf has no effect until a Book is added to the shelf (update bookshelf)
     * <p>
     * - insert/delete/update TocEntry only done when a book is inserted/updated.
     * ENHANCE: once we allow editing of TocEntry's through the 'author detail' screen
     * this will need to be added.
     *
     * @param syncedDb the database
     */
    void createTriggers(@NonNull final SynchronizedDb syncedDb) {

        String name;
        String body;

        /**
         * Deleting a {@link Bookshelf).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_BOOKSHELF;
        body = " AFTER DELETE ON " + TBL_BOOK_BOOKSHELF + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Updating a {@link Bookshelf}.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_BOOKSHELF;
        body = " AFTER UPDATE ON " + TBL_BOOKSHELF + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + " IN \n"
                + "(SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_BOOKSHELF
                + " WHERE " + DOM_FK_BOOKSHELF_ID + "=Old." + DOM_PK_ID + ");\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

//        /**
//         * Deleting an {@link Author). Currently not possible to delete an Author directly.
//         *
//         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
//         */
//        name = "after_delete_on_" + TBL_BOOK_AUTHOR;
//        body = " AFTER DELETE ON " + TBL_BOOK_AUTHOR + " FOR EACH ROW\n"
//                + " BEGIN\n"
//                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
//                + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK_ID + ";\n"
//                + " END";
//
//        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
//        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Updating an {@link Author).
         *
         * This is for both actual Books, and for any TocEntry's they have done in anthologies.
         * The latter because a Book might not have the full list of Authors set.
         * (i.e. each toc has the right author, but the book says "many authors")
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_AUTHORS;
        body = " AFTER UPDATE ON " + TBL_AUTHORS + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"

                + " WHERE " + DOM_PK_ID + " IN \n"
                // actual books by this Author
                + "(SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_AUTHOR
                + " WHERE " + DOM_FK_AUTHOR_ID + "=Old." + DOM_PK_ID + ")\n"

                + " OR " + DOM_PK_ID + " IN \n"
                // books with entries in anthologies by this Author
                + "(SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_TOC_ENTRIES.ref()
                + TBL_BOOK_TOC_ENTRIES.join(TBL_TOC_ENTRIES)
                + " WHERE " + DOM_FK_AUTHOR_ID + "=Old." + DOM_PK_ID + ");\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Deleting a {@link Series).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_SERIES;
        body = " AFTER DELETE ON " + TBL_BOOK_SERIES + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Update a {@link Series}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_SERIES;
        body = " AFTER UPDATE ON " + TBL_SERIES + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + " IN \n"
                + "(SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_SERIES
                + " WHERE " + DOM_FK_SERIES_ID + "=Old." + DOM_PK_ID + ");\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Deleting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_LOANEE;
        body = " AFTER DELETE ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Updating a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE;
        body = " AFTER UPDATE ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=New." + DOM_FK_BOOK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /**
         * Inserting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE;
        body = " AFTER INSERT ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_PK_ID + "=New." + DOM_FK_BOOK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /**
         * Deleting a {@link Book).
         *
         * Delete the book from FTS.
         */
        name = "after_delete_on_" + TBL_BOOKS;
        body = " AFTER DELETE ON " + TBL_BOOKS + " FOR EACH ROW\n"
                + " BEGIN\n"
                + "  DELETE FROM " + TBL_BOOKS_FTS
                + " WHERE " + DOM_PK_DOCID + '=' + "Old." + DOM_PK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /**
         * If the ISBN of a {@link Book) is changed, reset external id's and sync dates.
         */
        name = "after_update_of_" + DOM_BOOK_ISBN + "_on_" + TBL_BOOKS;
        body = " AFTER UPDATE OF " + DOM_BOOK_ISBN + " ON " + TBL_BOOKS + " FOR EACH ROW\n"
                + " WHEN New." + DOM_BOOK_ISBN + " <> Old." + DOM_BOOK_ISBN + '\n'
                + " BEGIN\n"
                + "    UPDATE " + TBL_BOOKS + " SET "
                + /* */ DOM_BOOK_GOODREADS_BOOK_ID + "=0"
                + ',' + DOM_BOOK_ISFDB_ID + "=0"
                + ',' + DOM_BOOK_LIBRARY_THING_ID + "=0"
                + ',' + DOM_BOOK_OPEN_LIBRARY_ID + "=0"

                + ',' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=''"
                + /* */ " WHERE " + DOM_PK_ID + "=New." + DOM_PK_ID + ";\n"
                + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);
    }

    /**
     * This method should only be called at the *END* of onCreate/onUpdate.
     * <p>
     * (re)Creates the indexes as defined on the tables,
     * followed by the {@link #DATABASE_CREATE_INDICES} set of indexes.
     *
     * @param syncedDb the database
     * @param recreate if <tt>true</tt> will delete all indexes first, and recreate them.
     */
    private void createIndices(@NonNull final SynchronizedDb syncedDb,
                               final boolean recreate) {

        if (recreate) {
            // clean slate
            IndexDefinition.dropAllIndexes(syncedDb);
            // recreate the ones that are defined with the TableDefinition's
            for (TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
                table.createIndices(syncedDb);
            }
        }

        // create/recreate the indexes with collation / which we have not added to
        // the TableDefinition's yet
        for (String create_index : DATABASE_CREATE_INDICES) {
            try {
                syncedDb.execSQL(create_index);
            } catch (SQLException e) {
                // bad sql is a developer issue... die!
                Logger.error(this, e);
                throw e;
            } catch (RuntimeException e) {
                Logger.error(this, e, "Index creation failed: " + create_index);
            }
        }
        syncedDb.analyze();
    }

    /**
     * This function is called each time the database is upgraded.
     * It will run all upgrade scripts between the oldVersion and the newVersion.
     * <p>
     * Minimal application version 4.0.0 (database version 71). Older versions not supported.
     * <p>
     * REMINDER: do not use [column].ref() or [table].create/createAll.
     * The 'current' definition might not match the upgraded definition!
     *
     * @param db         The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     *
     * @see #DATABASE_VERSION
     */
    @SuppressWarnings("unused")
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        if (BuildConfig.DEBUG /* always */) {
            Logger.debugEnter(this, "onUpgrade",
                              "Old database version: " + oldVersion,
                              "Upgrading database: " + db.getPath());
        }
        if (oldVersion < 71) {
            throw new UpgradeException(R.string.error_database_upgrade_failed);
        }

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.updateProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.exportFile(db.getPath(), "DbUpgrade-" + oldVersion + '-' + newVersion);
        }

        int curVersion = oldVersion;
        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        // older version upgrades archived/execute in UpgradeDatabase
        if (curVersion < 82) {
            curVersion = UpgradeDatabase.doUpgrade(db, syncedDb, curVersion);
        }

        // db82 == app179 == 5.2.2 == last official version.
        if (curVersion < newVersion && curVersion == 82) {
            // db100 == app200 == 6.0.0;
            //noinspection UnusedAssignment
            curVersion = 100;

            /* now using the 'real' cache directory */
            StorageUtils.deleteFile(new File(StorageUtils.getSharedStorage()
                                                     + File.separator + "tmp_images"));

            // migrate old properties.
            Prefs.migratePreV200preferences(App.PREF_LEGACY_BOOK_CATALOGUE);

            // this trigger was replaced.
            syncedDb.execSQL("DROP TRIGGER IF EXISTS books_tg_reset_goodreads");

            //TEST a proper upgrade from 82 to 100 with non-clean data
            // Due to a number of code remarks, and some observation... do a clean of some columns.

            // these two are due to a remark in the CSV exporter that (at one time?)
            // the author name and title could be bad
            final String UNKNOWN = App.getAppContext().getString(R.string.unknown);
            db.execSQL("UPDATE " + TBL_AUTHORS
                               + " SET " + DOM_AUTHOR_FAMILY_NAME + "='" + UNKNOWN + '\''
                               + " WHERE " + DOM_AUTHOR_FAMILY_NAME + "=''"
                               + " OR " + DOM_AUTHOR_FAMILY_NAME + " IS NULL");

            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_TITLE + "='" + UNKNOWN + '\''
                               + " WHERE " + DOM_TITLE + "='' OR " + DOM_TITLE + " IS NULL");

            // clean columns where we adding a "not null default ''" constraint
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_FORMAT + "=''"
                               + " WHERE " + DOM_BOOK_FORMAT + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GENRE + "=''"
                               + " WHERE " + DOM_BOOK_GENRE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LANGUAGE + "=''"
                               + " WHERE " + DOM_BOOK_LANGUAGE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LOCATION + "=''"
                               + " WHERE " + DOM_BOOK_LOCATION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PUBLISHER + "=''"
                               + " WHERE " + DOM_BOOK_PUBLISHER + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_ISBN + "=''"
                               + " WHERE " + DOM_BOOK_ISBN + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PRICE_LISTED + "=''"
                               + " WHERE " + DOM_BOOK_PRICE_LISTED + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DESCRIPTION + "=''"
                               + " WHERE " + DOM_BOOK_DESCRIPTION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_NOTES + "=''"
                               + " WHERE " + DOM_BOOK_NOTES + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_START + "=''"
                               + " WHERE " + DOM_BOOK_READ_START + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_END + "=''"
                               + " WHERE " + DOM_BOOK_READ_END + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DATE_PUBLISHED + "=''"
                               + " WHERE " + DOM_BOOK_DATE_PUBLISHED + " IS NULL");

            // clean boolean columns where we have seen non-0/1 values.
            // 'true'/'false' were seen in 5.2.2 exports. 't'/'f' just because paranoid.
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=1"
                               + " WHERE lower(" + DOM_BOOK_READ + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=0"
                               + " WHERE lower(" + DOM_BOOK_READ + ") IN ('false', 'f')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=1"
                               + " WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=0"
                               + " WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('false', 'f')");

            // probably not needed, but there were some 'coalesce' usages. Paranoia again...
            db.execSQL("UPDATE " + TBL_BOOKSHELF + " SET " + DOM_BOOKSHELF + "=''"
                               + " WHERE " + DOM_BOOKSHELF + " IS NULL");

            // this better work....
            recreateAndReloadTable(syncedDb, TBL_BOOKS);

            // anthology-titles are now cross-book;
            // e.g. one 'story' can be present in multiple books
            db.execSQL("CREATE TABLE " + TBL_BOOK_TOC_ENTRIES
                               + '(' + DOM_FK_BOOK_ID + " integer REFERENCES "
                               + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE"

                               + ',' + DOM_FK_TOC_ENTRY_ID + " integer REFERENCES "
                               + TBL_TOC_ENTRIES + " ON DELETE CASCADE ON UPDATE CASCADE"

                               + ',' + DOM_BOOK_TOC_ENTRY_POSITION + " integer not null"
                               + ", PRIMARY KEY (" + DOM_FK_BOOK_ID
                               + ',' + DOM_FK_TOC_ENTRY_ID + ')'
                               + ", FOREIGN KEY (" + DOM_FK_BOOK_ID + ')'
                               + " REFERENCES " + TBL_BOOKS + '(' + DOM_PK_ID + ')'
                               + ", FOREIGN KEY (" + DOM_FK_TOC_ENTRY_ID + ')'
                               + " REFERENCES " + TBL_TOC_ENTRIES + '(' + DOM_PK_ID + ')'
                               + ')');

            // move the existing book-anthology links to the new table
            db.execSQL("INSERT INTO " + TBL_BOOK_TOC_ENTRIES
                               + " SELECT " + DOM_FK_BOOK_ID + ',' + DOM_PK_ID + ','
                               + DOM_BOOK_TOC_ENTRY_POSITION + " FROM " + TBL_TOC_ENTRIES);

            // reorganise the original table
            recreateAndReloadTable(syncedDb, TBL_TOC_ENTRIES,
                    /* remove fields: */ DOM_FK_BOOK_ID.name, DOM_BOOK_TOC_ENTRY_POSITION.name);

            // just for consistence, rename the table.
            db.execSQL("ALTER TABLE book_bookshelf_weak RENAME TO " + TBL_BOOK_BOOKSHELF);

            // add the 'order by' lower-case columns
            // Note this is a lazy approach as compared to the DBA code where we take the book's
            // language/locale into account! The overhead here would be huge.
            // If the user has any specific book issue, a simple update of the book will fix it.
            db.execSQL(
                    "ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_TITLE_LC + " text not null default ''");
            db.execSQL(
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_TITLE_LC + "=lower(" + DOM_TITLE + ')');
            db.execSQL(
                    "ALTER TABLE " + TBL_TOC_ENTRIES + " ADD " + DOM_TITLE_LC + " text not null default ''");
            db.execSQL(
                    "UPDATE " + TBL_TOC_ENTRIES + " SET " + DOM_TITLE_LC + "=lower(" + DOM_TITLE + ')');


            // add the UUID field for the move of styles to SharedPreferences
            db.execSQL("ALTER TABLE " + TBL_BOOKLIST_STYLES
                               + " ADD " + DOM_UUID + " text not null default ''");

            // insert the builtin style id's so foreign key rules are possible.
            prepareStylesTable(syncedDb);

            // convert user styles from serialized storage to SharedPreference xml.
            try (Cursor stylesCursor = db.rawQuery("SELECT " + DOM_PK_ID + ",style"
                                                           + " FROM " + TBL_BOOKLIST_STYLES,
                                                   null)) {
                while (stylesCursor.moveToNext()) {
                    long id = stylesCursor.getLong(0);
                    byte[] blob = stylesCursor.getBlob(1);
                    BooklistStyle style;
                    try {
                        // de-serializing will in effect write out the preference file.
                        style = SerializationUtils.deserializeObject(blob);
                        // update db with the newly created prefs file name.
                        db.execSQL("UPDATE " + TBL_BOOKLIST_STYLES
                                           + " SET " + DOM_UUID + "='" + style.getUuid() + '\''
                                           + " WHERE " + DOM_PK_ID + '=' + id);

                    } catch (SerializationUtils.DeserializationException e) {
                        Logger.error(this, e, "BooklistStyle id=" + id);
                    }
                }
            }
            // drop the serialized field.
            recreateAndReloadTable(syncedDb, TBL_BOOKLIST_STYLES,
                    /* remove field */ "style");

            // add the foreign key rule pointing to the styles table.
            recreateAndReloadTable(syncedDb, TBL_BOOKSHELF);

            /* all books with a list price are assumed to be USD based on the only search up to v82
             * being Amazon US (via proxy... so this is my best guess).
             *
             * FIXME: if a user has manually edited the list price, can we scan for currencies ?
             *
             * set all rows which have a price, to being in USD. Does not change the price field.
             */
            db.execSQL("UPDATE " + TBL_BOOKS
                               + " SET " + DOM_BOOK_PRICE_LISTED_CURRENCY + "='USD'"
                               + " WHERE NOT " + DOM_BOOK_PRICE_LISTED + "=''");

            /* move cover files to a sub-folder.
            Only files with matching rows in 'books' are moved. */
            UpgradeDatabase.v200_moveCoversToDedicatedDirectory(syncedDb);
        }

        // Rebuild all indices
        createIndices(syncedDb, true);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }

    public static class UpgradeException
            extends RuntimeException {

        private static final long serialVersionUID = -6910121313418068318L;

        @StringRes
        public final int messageId;

        UpgradeException(final int messageId) {
            this.messageId = messageId;
        }
    }
}
