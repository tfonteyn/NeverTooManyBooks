/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/** @noinspection CheckStyle */
final class Triggers {
    private static final String DROP_TRIGGER_IF_EXISTS_ = "DROP TRIGGER IF EXISTS";
    private static final String CREATE_TRIGGER_ = "CREATE TRIGGER ";

    private static final String AFTER_DELETE_ON_ = "AFTER DELETE ON ";
    private static final String AFTER_UPDATE_ON_ = "AFTER UPDATE ON ";
    private static final String AFTER_INSERT_ON_ = "AFTER INSERT ON ";
    private static final String AFTER_UPDATE_OF_ = "AFTER UPDATE OF ";


    private Triggers() {
    }

    /**
     * Create all database triggers.
     *
     * <p>
     * set Book dirty when:
     * - Author: delete, update.
     * - Series: delete, update.
     * - Bookshelf: delete.
     * - Loan: delete, update, insert.
     *
     * <p>
     * Update FTS when:
     * - Book: delete (update,insert is to complicated to use a trigger)
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
     * @param db Underlying database
     */
    static void create(@NonNull final SQLiteDatabase db) {

        String name;
        String body;

        /*
         * When an entry in a book-x link table is deleted.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        afterDeleteOn(db, TBL_BOOK_BOOKSHELF);
        // It's currently not possible to delete an Author directly.
        //  createTriggerAfterDeleteOn(db, TBL_BOOK_AUTHOR);
        afterDeleteOn(db, TBL_BOOK_SERIES);
        afterDeleteOn(db, TBL_BOOK_PUBLISHER);
        afterDeleteOn(db, TBL_BOOK_LOANEE);

        /*
         * Updating an {@link Author}.
         *
         * This is for both actual Books, and for any TocEntry's they have done in anthologies.
         * The latter because a Book might not have the full list of Authors set.
         * (i.e. each toc has the right author, but the book says "many authors")
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         *
         * dev note: "after_update_on" is missing a "_" at the end!
         */
        name = "after_update_on" + TBL_AUTHORS.getName();
        body = AFTER_UPDATE_ON_ + TBL_AUTHORS.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"

               + " WHERE " + DBKey.PK_ID + " IN "
               // actual books by this Author
               + "(SELECT " + DBKey.FK_BOOK + " FROM " + TBL_BOOK_AUTHOR.getName()
               + " WHERE " + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ')'

               + " OR " + DBKey.PK_ID + " IN "
               // books with entries in anthologies by this Author
               + "(SELECT " + DBKey.FK_BOOK
               + " FROM " + TBL_BOOK_TOC_ENTRIES.startJoin(TBL_TOC_ENTRIES)
               + " WHERE " + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ");"

               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);


        /*
         * Update a {@link Series}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         *
         * dev note: "after_update_on" is missing a "_" at the end!
         */
        name = "after_update_on" + TBL_SERIES.getName();
        body = AFTER_UPDATE_ON_ + TBL_SERIES.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + " IN "
               + "(SELECT " + DBKey.FK_BOOK + " FROM " + TBL_BOOK_SERIES.getName()
               + " WHERE " + DBKey.FK_SERIES + "=OLD." + DBKey.PK_ID + ");"
               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);


        /*
         * Update a {@link Publisher}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         *
         * dev note: "after_update_on" is missing a "_" at the end!
         */
        name = "after_update_on" + TBL_PUBLISHERS.getName();
        body = AFTER_UPDATE_ON_ + TBL_PUBLISHERS.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + " IN "
               + "(SELECT " + DBKey.FK_BOOK + " FROM " + TBL_BOOK_PUBLISHER.getName()
               + " WHERE " + DBKey.FK_PUBLISHER + "=OLD." + DBKey.PK_ID + ");"
               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);


        /*
         * After a Book is lend-out.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE.getName();
        body = AFTER_INSERT_ON_ + TBL_BOOK_LOANEE.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ';'
               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);

        /*
         * After a lend-out Book is returned.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         *
         * dev note: "after_update_on" HAS a "_" at the end!
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE.getName();
        body = AFTER_UPDATE_ON_ + TBL_BOOK_LOANEE.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ';'
               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);


        /*
         * Deleting a {@link Book}.
         *
         * <ul>
         * <li>Delete the book from FTS.</li>
         * <li>Add the uuid to {@link DBDefinitions#TBL_DELETED_BOOKS} unless already present.</li>
         * </ul>
         */
        name = "after_delete_on_" + TBL_BOOKS.getName();
        body = AFTER_DELETE_ON_ + TBL_BOOKS.getName()
               + " FOR EACH ROW"
               + " BEGIN"
               + " DELETE FROM " + TBL_FTS_BOOKS.getName()
               + "  WHERE " + DBKey.FTS_BOOK_ID + "=OLD." + DBKey.PK_ID + ';'
               // we must use IGNORE for when we do a sync. i.e.
               // the TBL_DELETED_BOOKS contains a UUID which we imported from another device,
               // and we're syncing the delete on the local device.
               + " INSERT OR IGNORE INTO " + TBL_DELETED_BOOKS.getName()
               + " (" + DBKey.BOOK_UUID + ") VALUES(OLD." + DBKey.BOOK_UUID + ");"
               + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);

        /*
         * If the ISBN of a {@link Book} is changed, reset external ID's and sync dates.
         */
        name = "after_update_of_" + DBKey.BOOK_ISBN + "_on_" + TBL_BOOKS.getName();
        body = AFTER_UPDATE_OF_ + DBKey.BOOK_ISBN + " ON " + TBL_BOOKS.getName()
               + " FOR EACH ROW"
               + " WHEN NEW." + DBKey.BOOK_ISBN + " <> OLD." + DBKey.BOOK_ISBN
               + " BEGIN"
               + "  UPDATE " + TBL_BOOKS.getName() + " SET ";

        body += SearchEngineConfig
                .getExternalIdDomains()
                .stream()
                .map(domain -> domain.getName() + "=null")
                .collect(Collectors.joining(","));

        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        body += " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.PK_ID + ";"
                + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }


    /**
     * Create an "AFTER DELETE ON" on a TBL_BOOK_* table.
     * <p>
     * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
     *
     * @param db        Underlying database
     * @param linkTable the TBL_BOOK_* link table on which to set the trigger
     */
    private static void afterDeleteOn(@NonNull final SQLiteDatabase db,
                                      @NonNull final TableDefinition linkTable) {

        final String name = "after_delete_on_" + linkTable.getName();
        final String body = AFTER_DELETE_ON_ + linkTable.getName()
                            + " FOR EACH ROW"
                            + " BEGIN"
                            + "  UPDATE " + TBL_BOOKS.getName()
                            + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
                            + "  WHERE " + DBKey.PK_ID + "=OLD." + DBKey.FK_BOOK + ';'
                            + " END";

        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + " " + name);
        db.execSQL(CREATE_TRIGGER_ + name + ' ' + body);
    }
}
