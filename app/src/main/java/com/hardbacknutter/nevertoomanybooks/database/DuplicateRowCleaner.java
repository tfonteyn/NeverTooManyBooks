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

package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

class DuplicateRowCleaner {

    private static final String TAG = "DuplicateRowCleaner";

    private static final String CREATE_TEMP_TABLE_ = "CREATE TEMP TABLE ";
    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String DROP_TABLE_ = "DROP TABLE ";
    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String NOT_EXISTS_ = "NOT EXISTS(";
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

    private static final String KEEP_ID = "keep_id";
    private static final String REMOVE_ID = "remove_id";

    private static final String TMP_KEEP = "k";
    private static final String TMP_REMOVE = "r";

    // A second alias for a table
    private static final String A_2 = "la2";
    private static final String TMP_KEEP_BASE_NAME = "tmp_keep";
    private static final String TMP_REMOVE_BASE_NAME = "tmp_remove";

    /** Database Access. */
    @NonNull
    private final SynchronizedDb db;
    private final Logger logger;

    /**
     * Constructor.
     */
    DuplicateRowCleaner() {
        this.db = ServiceLocator.getInstance().getDb();
        logger = LoggerFactory.getLogger();
    }

    void removeDuplicateAuthors() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpKeep = TMP_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpRemove = TMP_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.AUTHOR.FAMILY_NAME,
                    DBKey.AUTHOR.GIVEN_NAMES
            };

            createKeepTable(tmpKeep, TBL_AUTHORS, equalityColumns);
            createRemovalTable(tmpKeep, tmpRemove, TBL_AUTHORS, equalityColumns);

            insertReplacementRows(tmpRemove, TBL_BOOK_AUTHOR, DBKey.FK_AUTHOR);

            insertReplacementRows(tmpRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);

            deleteRemovedIds(tmpRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);
            deleteRemovedIds(tmpRemove, TBL_BOOK_AUTHOR, DBKey.FK_AUTHOR);
            deleteRemovedIds(tmpRemove, TBL_AUTHORS, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tmpKeep);
            db.execSQL(DROP_TABLE_ + tmpRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    void removeDuplicateSeries() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpKeep = TMP_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpRemove = TMP_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.SERIES.TITLE
            };

            createKeepTable(tmpKeep, TBL_SERIES, equalityColumns);
            createRemovalTable(tmpKeep, tmpRemove, TBL_SERIES, equalityColumns);

            insertReplacementRows(tmpRemove, TBL_BOOK_SERIES, DBKey.FK_SERIES);

            deleteRemovedIds(tmpRemove, TBL_BOOK_SERIES, DBKey.FK_SERIES);
            deleteRemovedIds(tmpRemove, TBL_SERIES, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tmpKeep);
            db.execSQL(DROP_TABLE_ + tmpRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    void removeDuplicatePublishers() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpKeep = TMP_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpRemove = TMP_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.PUBLISHER.NAME
            };

            createKeepTable(tmpKeep, TBL_PUBLISHERS, equalityColumns);
            createRemovalTable(tmpKeep, tmpRemove, TBL_PUBLISHERS, equalityColumns);

            insertReplacementRows(tmpRemove, TBL_BOOK_PUBLISHER, DBKey.FK_PUBLISHER);

            deleteRemovedIds(tmpRemove, TBL_BOOK_PUBLISHER, DBKey.FK_PUBLISHER);
            deleteRemovedIds(tmpRemove, TBL_PUBLISHERS, DBKey.PK_ID);

            db.execSQL(DROP_TABLE_ + tmpKeep);
            db.execSQL(DROP_TABLE_ + tmpRemove);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    void removeDuplicateTocEntries() {
        final int instanceId = ID_COUNTER.incrementAndGet();

        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpKeep = TMP_KEEP_BASE_NAME + instanceId;
        @SuppressWarnings("StringConcatenationMissingWhitespace")
        final String tmpRemove = TMP_REMOVE_BASE_NAME + instanceId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String[] equalityColumns = {
                    DBKey.FK_AUTHOR,
                    DBKey.TITLE
            };

            createKeepTable(tmpKeep, TBL_TOC_ENTRIES, equalityColumns);
            createRemovalTable(tmpKeep, tmpRemove, TBL_TOC_ENTRIES, equalityColumns);

            insertReplacementRows(tmpRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);

            deleteRemovedIds(tmpRemove, TBL_TOC_ENTRIES, DBKey.FK_AUTHOR);

            db.execSQL(DROP_TABLE_ + tmpKeep);
            db.execSQL(DROP_TABLE_ + tmpRemove);

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
     * Reposition the author, series, etc.. lists for each book.
     *
     * @param context Current context
     *
     * @throws DaoWriteException on failure
     */
    void resortPositionalLinks(@NonNull final Context context)
            throws DaoWriteException {

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        int modified;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            modified = serviceLocator.getAuthorDao().fixPositions(context);
            modified += serviceLocator.getSeriesDao().fixPositions(context);
            modified += serviceLocator.getPublisherDao().fixPositions(context);
            modified += serviceLocator.getTocEntryDao().fixPositions(context);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        if (modified > 0) {
            logger.w(TAG, "reposition modified=" + modified);
        }
    }

    /**
     * Keep the smallest ID.
     *
     * @param tmpKeep         table with the ids to keep
     * @param table           to operate on
     * @param equalityColumns to select/group
     */
    private void createKeepTable(@NonNull final String tmpKeep,
                                 @NonNull final TableDefinition table,
                                 @NonNull final String... equalityColumns) {

        final String columns = String.join(",", equalityColumns);

        db.execSQL(
                CREATE_TEMP_TABLE_ + tmpKeep + _AS_
                + SELECT_ + "MIN(" + DBKey.PK_ID + ')' + _AS_ + KEEP_ID
                + ',' + columns
                + _FROM_ + table.getName()
                + _GROUP_BY_ + columns
        );
    }

    /**
     * Determine which IDs to remove and what they map to.
     *
     * @param tmpKeep         table with the ids to keep
     * @param tmpRemove       table with the ids to remove
     * @param table           to operate on
     * @param equalityColumns for the join
     */
    private void createRemovalTable(@NonNull final String tmpKeep,
                                    @NonNull final String tmpRemove,
                                    @NonNull final TableDefinition table,
                                    @NonNull final String... equalityColumns) {

        final String columns = Arrays
                .stream(equalityColumns)
                .map(c -> table.dot(c) + '=' + TMP_KEEP + '.' + c)
                .collect(Collectors.joining(_AND_));

        db.execSQL(
                CREATE_TEMP_TABLE_ + tmpRemove + _AS_
                + SELECT_ + TMP_KEEP + '.' + KEEP_ID
                + ',' + table.dot(DBKey.PK_ID) + _AS_ + REMOVE_ID
                + _FROM_ + table.ref()
                + _JOIN_ + tmpKeep + ' ' + TMP_KEEP + _ON_ + columns
                + _WHERE_ + table.dot(DBKey.PK_ID) + "<>" + TMP_KEEP + '.' + KEEP_ID
        );
    }

    private void insertReplacementRows(@NonNull final String tmpRemove,
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

        try (SynchronizedStatement stmt = db.compileStatement(
                // ignore/skip duplicates here
                "INSERT OR IGNORE INTO " + table.getName() + '(' + keyColumn + ',' + insOthers + ") "
                + SELECT_ + TMP_REMOVE + '.' + KEEP_ID + ',' + selOthers

                + _FROM_ + table.ref()
                + _JOIN_ + tmpRemove + ' ' + TMP_REMOVE
                + _ON_ + table.dot(keyColumn) + '=' + TMP_REMOVE + '.' + REMOVE_ID
                + _WHERE_ + NOT_EXISTS_
                + SELECT_ + '1'
                + _FROM_ + table.getName() + ' ' + A_2
                + _WHERE_ + A_2 + '.' + keyColumn + '=' + TMP_REMOVE + '.' + KEEP_ID
                + _AND_ + whereOthers
                + ')')) {
            final int rowsAffected = stmt.executeUpdateDelete();
            logger.w(TAG, "insertReplacementRows", table.getName() + ':' + rowsAffected);
        }
    }

    private void deleteRemovedIds(@NonNull final String tmpRemove,
                                  @NonNull final TableDefinition table,
                                  @NonNull final String keyColumn) {
        try (SynchronizedStatement stmt = db.compileStatement(
                DELETE_FROM_ + table.getName()
                + _WHERE_ + keyColumn
                + _IN_ + '(' + SELECT_ + REMOVE_ID + _FROM_ + tmpRemove + ')')) {
            final int rowsAffected = stmt.executeUpdateDelete();
            logger.w(TAG, "deleteRemovedIds", table.getName() + ':' + rowsAffected);
        }
    }
}
