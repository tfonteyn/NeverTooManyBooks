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

package com.hardbacknutter.nevertoomanybooks.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

final class Triggers {

    private static final String AFTER_DELETE_ON_ = "AFTER DELETE ON ";
    private static final String AFTER_UPDATE_ON_ = "AFTER UPDATE ON ";
    private static final String AFTER_INSERT_ON_ = "AFTER INSERT ON ";

    private static final String AFTER_UPDATE_OF_ = "AFTER UPDATE OF ";

    private static final String DROP_TRIGGER_IF_EXISTS_ = "DROP TRIGGER IF EXISTS ";
    private static final String CREATE_TRIGGER_ = "CREATE TRIGGER ";

    private static final String _FOR_EACH_ROW = " FOR EACH ROW";
    private static final String _BEGIN_ = " BEGIN ";
    private static final String _END = " END";
    private static final String DELETE_FROM_ = "DELETE FROM ";

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _IN_ = " IN ";

    private static final String UPDATE_BOOKS_SET =
            "UPDATE " + TBL_BOOKS.getName()
            + " SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp";

    private Triggers() {
    }

    /**
     * Create all database triggers.
     * <p>
     * Set Book dirty when:
     * <ul>
     *     <li>Bookshelf: delete (backups use the bookshelf id on books, hence,
     *                    updated bookshelf names are not an issue)</li>
     *     <li>Author: update (linked authors cannot be deleted).</li>
     *     <li>Series: delete, update.</li>
     *     <li>Publisher: delete, update.</li>
     *     <li>Tag: delete, update.</li>
     *     <li>TocEntry: delete, update.</li>
     *     <li>Loan: delete, update, insert.</li>
     * </ul>
     *
     * @param db Underlying database
     */
    static void create(@NonNull final SQLiteDatabase db) {

        books(db);
        bookshelf(db);
        authors(db);
        series(db);
        publisher(db);
        tocEntry(db);
        tags(db);
        identifiers(db);
        loanee(db);
    }

    private static void books(@NonNull final SQLiteDatabase db) {
        String body;
        String name;

        /*
         * When a Book is deleted.
         * <ul>
         *     <li>FTS: delete (update,insert is too complicated to use a trigger)</li>
         *     <li>Add the uuid to {@link DBDefinitions#TBL_DELETED_BOOKS}
         *         unless already present.</li>
         * </ul>
         */
        name = "after_delete_on_" + TBL_BOOKS.getName();
        body = AFTER_DELETE_ON_ + TBL_BOOKS.getName()
               + _FOR_EACH_ROW
               + _BEGIN_
               + DELETE_FROM_ + TBL_FTS_BOOKS.getName()
               + _WHERE_ + DBKey.FTS.PK_BOOK_ID + "=OLD." + DBKey.PK_ID + ';'

               // we must use IGNORE for when we do a sync. i.e.
               // the TBL_DELETED_BOOKS contains a UUID which we imported from another device,
               // and we're syncing the delete operation on the local device.
               + " INSERT OR IGNORE INTO " + TBL_DELETED_BOOKS.getName()
               + " (" + DBKey.BOOK_UUID + ") VALUES(OLD." + DBKey.BOOK_UUID + ");"
               + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);

        /*
         * When the ISBN of a {@link Book} is changed, remove all other Identifiers for that book.
         */
        name = "after_update_of_" + DBKey.ISBN + "_on_" + TBL_BOOKS.getName();
        body = AFTER_UPDATE_OF_ + DBKey.ISBN + " ON " + TBL_BOOKS.getName()
               + _FOR_EACH_ROW
               // only if the field itself was changed
               + " WHEN OLD." + DBKey.ISBN + " IS NOT NEW." + DBKey.ISBN

               + _BEGIN_
               + DELETE_FROM_ + TBL_BOOK_IDENTIFIER.getName()
               + _WHERE_ + DBKey.FK_BOOK + "=NEW." + DBKey.PK_ID + ";"
               + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    private static void bookshelf(@NonNull final SQLiteDatabase db) {
        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_BOOKSHELF);
    }

    private static void authors(@NonNull final SQLiteDatabase db) {

        // linked authors cannot be deleted

        /*
         * After updating an {@link Author},
         * update the books last-update-date.
         *
         * This is for both actual Books, and for any TocEntry's they have done in anthologies.
         * The latter because a Book might not have the full list of Authors set.
         * (i.e. each toc has the right author, but the book itself says "many authors".
         *
         * dev note: the name "after_update_on" is missing a "_" at the end!
         */
        final String name = "after_update_on" + TBL_AUTHORS.getName();
        final String body =
                AFTER_UPDATE_ON_ + TBL_AUTHORS.getName()
                + _FOR_EACH_ROW
                + _BEGIN_

                + UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + _IN_
                // actual books by this Author
                + '(' + SELECT_ + DBKey.FK_BOOK
                + _FROM_ + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ");"

                + UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + _IN_
                // other books (anthologies) with TocEntries by this Author
                + '(' + SELECT_ + DBKey.FK_BOOK
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.startJoin(TBL_TOC_ENTRIES)
                + _WHERE_ + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ");"

                + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    private static void series(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_SERIES);

        // dev note: the name "after_update_on" is missing a "_" at the end!
        createLinkTableUpdateTrigger(db, "after_update_on",
                                     TBL_SERIES, TBL_BOOK_SERIES, DBKey.FK_SERIES);
    }

    private static void publisher(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_PUBLISHER);

        // dev note: the name "after_update_on" is missing a "_" at the end!
        createLinkTableUpdateTrigger(db, "after_update_on",
                                     TBL_PUBLISHERS, TBL_BOOK_PUBLISHER, DBKey.FK_PUBLISHER);
    }

    private static void tags(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_TAG);

        // dev note: the name "after_update_on" is missing a "_" at the end!
        createLinkTableUpdateTrigger(db, "after_update_on",
                                     TBL_TAGS, TBL_BOOK_TAG, DBKey.FK_TAG);
    }

    private static void tocEntry(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_TOC_ENTRIES);

        createLinkTableUpdateTrigger(db, "after_update_on_",
                                     TBL_TOC_ENTRIES, TBL_BOOK_TOC_ENTRIES, DBKey.FK_TOC_ENTRY);

    }

    private static void identifiers(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_IDENTIFIER);

        final String name = "after_update_of_" + TBL_IDENTIFIERS.getName() + "_name";
        final String body =
                AFTER_UPDATE_OF_ + DBKey.IDENTIFIERS.NAME + " ON " + TBL_IDENTIFIERS.getName()
                + _FOR_EACH_ROW
                // only if the field itself was changed
                + " WHEN " + "OLD." + DBKey.IDENTIFIERS.NAME
                + " IS NOT NEW." + DBKey.IDENTIFIERS.NAME

                + _BEGIN_
                + createLinkUpdateSql(TBL_BOOK_IDENTIFIER, DBKey.FK_IDENTIFIER)
                + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    private static void loanee(@NonNull final SQLiteDatabase db) {

        afterDeleteOnUpdateBookLastUpdated(db, TBL_BOOK_LOANEE);

        String name;
        String body;

        /*
         * After a Book is lend-out,
         * update the books last-update-date.
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE.getName();
        body = AFTER_INSERT_ON_ + TBL_BOOK_LOANEE.getName()
               + _FOR_EACH_ROW
               + _BEGIN_
               + UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ';'
               + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);

        /*
         * After a lend-out Book is returned and lent out to a new person in the same 'go',
         * update the books last-update-date.
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE.getName();
        body = AFTER_UPDATE_ON_ + TBL_BOOK_LOANEE.getName()
               + _FOR_EACH_ROW
               + _BEGIN_
               + UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ';'
               + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    /**
     * Create an "AFTER DELETE ON" on a {@code TBL_BOOK_*} table
     * to Update the books last-update-date.
     *
     * @param db        Underlying database
     * @param linkTable the TBL_BOOK_* link table on which to set the trigger
     */
    private static void afterDeleteOnUpdateBookLastUpdated(
            @NonNull final SQLiteDatabase db,
            @NonNull final TableDefinition linkTable) {

        final String name = "after_delete_on_" + linkTable.getName();
        final String body =
                AFTER_DELETE_ON_ + linkTable.getName()
                + _FOR_EACH_ROW
                + _BEGIN_
                + UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + "=OLD." + DBKey.FK_BOOK + ';'
                + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    private static void createLinkTableUpdateTrigger(@NonNull final SQLiteDatabase db,
                                                     @NonNull final String namePrefix,
                                                     @NonNull final TableDefinition table,
                                                     @NonNull final TableDefinition linkTable,
                                                     @NonNull final String linkColumn) {

        final String name = namePrefix + table.getName();
        final String body = AFTER_UPDATE_ON_ + table.getName()
                            + _FOR_EACH_ROW
                            + _BEGIN_
                            + createLinkUpdateSql(linkTable, linkColumn)
                            + _END;

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }

    @NonNull
    private static String createLinkUpdateSql(@NonNull final TableDefinition linkTable,
                                              @NonNull final String linkColumn) {
        return UPDATE_BOOKS_SET + _WHERE_ + DBKey.PK_ID + _IN_
               + '(' + SELECT_ + DBKey.FK_BOOK + _FROM_ + linkTable.getName()
               + _WHERE_ + linkColumn + "=OLD." + DBKey.PK_ID + ");";
    }

    public static void dropAllTriggers(@NonNull final SQLiteDatabase db) {
        db.beginTransaction();
        try {
            final List<String> triggerNames = new ArrayList<>();

            // User-defined triggers
            try (Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='trigger'"
                    + " AND name NOT LIKE 'sqlite_%'", null)) {
                while (cursor.moveToNext()) {
                    triggerNames.add(cursor.getString(0));
                }
            }

            for (final String triggerName : triggerNames) {
                db.execSQL(DROP_TRIGGER_IF_EXISTS_ + triggerName);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
