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

package com.hardbacknutter.nevertoomanybooks.database.cleaning;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.debug.DbDebugUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.Positional;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Manual check for duplicates.
 * {@code
 * SELECT family_name ,given_names FROM authors GROUP BY family_name ,given_names HAVING COUNT(_id) >1
 * SELECT series_name FROM series GROUP BY series_name HAVING COUNT(_id) >1
 * SELECT publisher_name FROM publishers GROUP BY publisher_name HAVING COUNT(_id) >1
 * SELECT author, title FROM anthology GROUP BY author, title HAVING COUNT(_id) >1
 * }
 */
class DuplicateRowCleaner {

    private static final String TAG = "DuplicateRowCleaner";

    private static final String CREATE_TEMP_TABLE_ = "CREATE TEMP TABLE ";
    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String DROP_TABLE_ = "DROP TABLE ";
    private static final String INSERT_OR_IGNORE_INTO_ = "INSERT OR IGNORE INTO ";
    private static final String NOT_EXISTS_ = "NOT EXISTS ";
    private static final String SELECT_ = "SELECT ";
    private static final String _AND_ = " AND ";
    private static final String _AS_ = " AS ";
    private static final String _FROM_ = " FROM ";
    private static final String _GROUP_BY_ = " GROUP BY ";
    private static final String _IN_ = " IN ";
    private static final String _JOIN_ = " JOIN ";
    private static final String _ON_ = " ON ";
    private static final String _WHERE_ = " WHERE ";

    /**
     * Counter for generating ID's. Only increments.
     * Used to create unique names for the temporary tables.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** The table base name, an instance counter will be concatenated for debug purposes. */
    private static final String TBL_KEEP_BASE_NAME = "tmp_keep";
    private static final String TBL_REMOVE_BASE_NAME = "tmp_remove";

    /** Table alias. */
    private static final String TBL_KEEP = "k";
    private static final String TBL_REMOVE = "r";

    /** A second alias for a table. */
    private static final String A_2 = "la2";

    /** Column name. */
    private static final String KEEP_ID = "keep_id";
    private static final String REMOVE_ID = "remove_id";
    private static final int DUMP_TABLE_ROW_LIMIT = 50;

    @NonNull
    private final SynchronizedDb db;
    private final Logger logger;
    @NonNull
    private final Set<CleanOptions> options;

    /**
     * Constructor.
     *
     * @param options to use
     */
    DuplicateRowCleaner(@NonNull final Set<CleanOptions> options) {
        this.options = options;
        db = ServiceLocator.getInstance().getDb();
        logger = LoggerFactory.getLogger();
    }

    /**
     * Run the actions as set in the options.
     * Participates in, or creates a Transaction.
     *
     * @param context Current context
     *
     * @throws DaoWriteException on failure
     */
    void dedup(@NonNull final Context context)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            if (options.contains(CleanOptions.RemoveDuplicateAuthors)) {
                removeDuplicateAuthors();

                final Positional authorDao = ServiceLocator.getInstance().getAuthorDao();
                final int rows = authorDao.fixPositions(context);
                logger.w(TAG, "Repositioned Authors: " + rows);
            }
            if (options.contains(CleanOptions.RemoveDuplicatePublishers)) {
                removeDuplicatePublishers();

                final Positional publisherDao = ServiceLocator.getInstance().getPublisherDao();
                final int rows = publisherDao.fixPositions(context);
                logger.w(TAG, "Repositioned Publishers: " + rows);
            }
            if (options.contains(CleanOptions.RemoveDuplicateSeries)) {
                removeDuplicateSeries();

                final Positional seriesDao = ServiceLocator.getInstance().getSeriesDao();
                final int rows = seriesDao.fixPositions(context);
                logger.w(TAG, "Repositioned Series: " + rows);
            }
            // removeDuplicateTocEntries is dependent on RemoveDuplicateAuthors
            // having run first.
            if (options.contains(CleanOptions.RemoveDuplicateAuthors)
                && options.contains(CleanOptions.RemoveDuplicateTocEntries)) {
                removeDuplicateTocEntries();

                final Positional tocEntryDao = ServiceLocator.getInstance().getTocEntryDao();
                final int rows = tocEntryDao.fixPositions(context);
                logger.w(TAG, "Repositioned TocEntry: " + rows);
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    private void removeDuplicateAuthors() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblKeep = TBL_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblRemove = TBL_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.AUTHOR.FAMILY_NAME,
                    DBKey.AUTHOR.GIVEN_NAMES
            };

            createKeepTable(tblKeep, TBL_AUTHORS, equalityColumns);
            createRemovalTable(tblKeep, tblRemove, TBL_AUTHORS, equalityColumns);

            insertReplacementRows(tblRemove, TBL_BOOK_AUTHOR, DBKey.FK_AUTHOR);
            deleteRemovedIds(tblRemove, TBL_BOOK_AUTHOR, DBKey.FK_AUTHOR);

            // ONLY handles author id's; Duplicate TocEntries are handled in a 2nd step.
            insertReplacementRows(tblRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);
            deleteRemovedIds(tblRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);

            deleteRemovedIds(tblRemove, TBL_AUTHORS, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tblKeep);
            db.execSQL(DROP_TABLE_ + tblRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Find duplicate entries in the {@link DBDefinitions#TBL_SERIES} table.
     * Dedup the {@link DBDefinitions#TBL_BOOK_SERIES}
     * and remove them from {@link DBDefinitions#TBL_SERIES}.
     */
    private void removeDuplicateSeries() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblKeep = TBL_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblRemove = TBL_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            // Ignore DBKey.SERIES_IS_COMPLETE; "keep" row wins.
            final String[] equalityColumns = {
                    DBKey.SERIES.TITLE
            };

            createKeepTable(tblKeep, TBL_SERIES, equalityColumns);
            createRemovalTable(tblKeep, tblRemove, TBL_SERIES, equalityColumns);

            insertReplacementRows(tblRemove, TBL_BOOK_SERIES, DBKey.FK_SERIES);
            deleteRemovedIds(tblRemove, TBL_BOOK_SERIES, DBKey.FK_SERIES);

            deleteRemovedIds(tblRemove, TBL_SERIES, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tblKeep);
            db.execSQL(DROP_TABLE_ + tblRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Find duplicate entries in the {@link DBDefinitions#TBL_PUBLISHERS} table.
     * Dedup the {@link DBDefinitions#TBL_BOOK_PUBLISHER}
     * and remove them from {@link DBDefinitions#TBL_PUBLISHERS}.
     */
    private void removeDuplicatePublishers() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblKeep = TBL_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblRemove = TBL_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.PUBLISHER.NAME
            };

            createKeepTable(tblKeep, TBL_PUBLISHERS, equalityColumns);
            createRemovalTable(tblKeep, tblRemove, TBL_PUBLISHERS, equalityColumns);

            insertReplacementRows(tblRemove, TBL_BOOK_PUBLISHER, DBKey.FK_PUBLISHER);
            deleteRemovedIds(tblRemove, TBL_BOOK_PUBLISHER, DBKey.FK_PUBLISHER);

            deleteRemovedIds(tblRemove, TBL_PUBLISHERS, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tblKeep);
            db.execSQL(DROP_TABLE_ + tblRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Any author duplication in the {@link DBDefinitions#TBL_TOC_ENTRIES}
     * table is cleaned up in {@link #removeDuplicateAuthors()}
     * which <strong>MUST</strong> be called before this method is called.
     * <p>
     * Find duplicate entries in the {@link DBDefinitions#TBL_TOC_ENTRIES} table.
     * Dedup the {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}
     * and remove them from {@link DBDefinitions#TBL_TOC_ENTRIES}.
     */
    private void removeDuplicateTocEntries() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblKeep = TBL_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tblRemove = TBL_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Ignore DBKey.FIRST_PUBLICATION_DATE; "keep" row wins.
            final String[] equalityColumns = {
                    DBKey.FK_AUTHOR,
                    DBKey.TITLE
            };

            createKeepTable(tblKeep, TBL_TOC_ENTRIES, equalityColumns);
            createRemovalTable(tblKeep, tblRemove, TBL_TOC_ENTRIES, equalityColumns);

            insertReplacementRows(tblRemove, TBL_BOOK_TOC_ENTRIES, DBKey.FK_TOC_ENTRY);
            deleteRemovedIds(tblRemove, TBL_BOOK_TOC_ENTRIES, DBKey.FK_TOC_ENTRY);

            deleteRemovedIds(tblRemove, TBL_TOC_ENTRIES, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tblKeep);
            db.execSQL(DROP_TABLE_ + tblRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Keep the smallest ID.
     *
     * @param tblKeep         table with the ids to keep
     * @param table           to operate on
     * @param equalityColumns to select/group
     */
    private void createKeepTable(@NonNull final String tblKeep,
                                 @NonNull final TableDefinition table,
                                 @NonNull final String... equalityColumns) {

        final String columns = String.join(",", equalityColumns);

        db.execSQL(
                CREATE_TEMP_TABLE_ + tblKeep + _AS_
                + SELECT_ + "MIN(" + DBKey.PK_ID + ')' + _AS_ + KEEP_ID
                + ',' + columns
                + _FROM_ + table.getName()
                + _GROUP_BY_ + columns
        );
    }

    /**
     * Determine which IDs to remove and what they map to.
     *
     * @param tblKeep         table with the ids to keep
     * @param tblRemove       table with the ids to remove
     * @param table           to operate on
     * @param equalityColumns for the join
     */
    private void createRemovalTable(@NonNull final String tblKeep,
                                    @NonNull final String tblRemove,
                                    @NonNull final TableDefinition table,
                                    @NonNull final String... equalityColumns) {

        final String columns = Arrays
                .stream(equalityColumns)
                .map(c -> table.dot(c) + '=' + TBL_KEEP + '.' + c)
                .collect(Collectors.joining(_AND_));

        final String keepIdColumn = TBL_KEEP + '.' + KEEP_ID;
        final String removeIdColumn = table.dot(DBKey.PK_ID) + _AS_ + REMOVE_ID;

        db.execSQL(
                CREATE_TEMP_TABLE_ + tblRemove + _AS_
                + SELECT_ + keepIdColumn + ',' + removeIdColumn
                + _FROM_ + table.as() + _JOIN_ + tblKeep + ' ' + TBL_KEEP + _ON_ + columns
                + _WHERE_ + table.dot(DBKey.PK_ID) + "<>" + keepIdColumn
        );

        if (BuildConfig.DEBUG /* always */) {
            DbDebugUtils.dumpTable(db.getSQLiteDatabase(), tblRemove, DUMP_TABLE_ROW_LIMIT,
                                   tblRemove + '.' + KEEP_ID,
                                   TAG, "createRemovalTable (limit=" + DUMP_TABLE_ROW_LIMIT
                                        + ") from: " + table.getName());
        }
    }

    private void insertReplacementRows(@NonNull final String tblRemove,
                                       @NonNull final TableDefinition table,
                                       @NonNull final String keyColumn) {

        // Collect all columns we need to copy,
        // except the key column which will be replaced.
        // Ignore the DBKey.PK_ID ("_id") if we have one (TBL_TOC_ENTRIES uses one)
        final List<String> columns = table.getDomains()
                                          .stream()
                                          .map(Domain::getName)
                                          .filter(n -> !keyColumn.equals(n))
                                          .filter(n -> !DBKey.PK_ID.equals(n))
                                          .collect(Collectors.toList());

        final String insOthers = String.join(",", columns);
        final String selOthers = columns.stream()
                                        .map(table::dot)
                                        .collect(Collectors.joining(","));
        final String whereOthers = columns.stream()
                                          .map(c -> A_2 + '.' + c + '=' + table.dot(c))
                                          .collect(Collectors.joining(_AND_));

        final String keepIdColumn = TBL_REMOVE + '.' + KEEP_ID;
        final String removeIdColumn = TBL_REMOVE + '.' + REMOVE_ID;

        try (SynchronizedStatement stmt = db.compileStatement(
                // ignore/skip duplicates
                INSERT_OR_IGNORE_INTO_ + table.getName()
                + '(' + keyColumn + ',' + insOthers + ") "
                + SELECT_ + keepIdColumn + ',' + selOthers
                + _FROM_ + table.as()
                + _JOIN_ + tblRemove + ' ' + TBL_REMOVE
                + _ON_ + table.dot(keyColumn) + '=' + removeIdColumn
                + _WHERE_ + NOT_EXISTS_
                + '('
                + SELECT_ + '1' + _FROM_ + table.getName() + ' ' + A_2
                + _WHERE_ + A_2 + '.' + keyColumn + '=' + keepIdColumn
                + _AND_ + whereOthers + ')')) {
            final int rowsAffected = stmt.executeUpdateDelete(null);
            logger.w(TAG, "insertReplacementRows", table.getName() + ':' + rowsAffected);
        }
    }

    private void deleteRemovedIds(@NonNull final String tblRemove,
                                  @NonNull final TableDefinition table,
                                  @NonNull final String keyColumn) {
        try (SynchronizedStatement stmt = db.compileStatement(
                DELETE_FROM_ + table.getName()
                + _WHERE_ + keyColumn
                + _IN_ + '(' + SELECT_ + REMOVE_ID + _FROM_ + tblRemove + ')')) {
            final int rowsAffected = stmt.executeUpdateDelete(null);
            logger.w(TAG, "deleteRemovedIds", table.getName() + ':' + rowsAffected);
        }
    }
}
