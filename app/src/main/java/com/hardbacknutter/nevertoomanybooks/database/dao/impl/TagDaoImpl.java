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

package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;

public class TagDaoImpl
        extends BaseDaoImpl
        implements TagDao {

    private static final String TAG = "TagDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public TagDaoImpl(final SynchronizedDb db) {
        super(db, TAG);
    }

    @NonNull
    @Override
    public Optional<Tag> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Tag(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    @NonNull
    @Override
    public Optional<Tag> findByName(@NonNull final Tag tag) {

        final String name = tag.getName();

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Tag(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @NonNull
    @Override
    public List<Tag> getAll() {
        final List<Tag> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.GET_ALL, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Tag(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @NonNull
    @Override
    public List<Tag> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Tag> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Tag(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @NonNull
    @Override
    public List<Long> getBookIds(final long tagId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BOOK_IDS_BY_TAG_ID,
                                         new String[]{String.valueOf(tagId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Tag> list,
                             @NonNull final Function<Tag, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final EntityMergeHelper<Tag> mergeHelper = new EntityMergeHelper<>();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(current));
    }

    @Override
    public void fixId(@NonNull final Tag tag) {
        final long found = findByName(tag)
                .map(Tag::getId).orElse(0L);
        tag.setId(found);
    }

    @Override
    public void refresh(@NonNull final Tag tag) {

        // If needed, check if we already have it in the database.
        if (tag.getId() == 0) {
            fixId(tag);
        }

        // If we do already have it, update the object
        if (tag.getId() > 0) {
            final Optional<Tag> dbTag = findById(tag.getId());
            // Sanity check
            if (dbTag.isPresent()) {
                // copy any updated fields
                tag.copyFrom(dbTag.get());
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                tag.setId(0);
            }
        }
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               @NonNull final Collection<Tag> list,
                               @NonNull final Function<Tag, Locale> localeSupplier)
            throws DaoInsertException, DaoUpdateException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        pruneList(context, list, localeSupplier);

        // Just delete all current links
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Tag tag : list) {
                fixId(tag);

                // create if needed
                if (tag.getId() == 0) {
                    insert(tag);
                } else {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise the trigger "after_update_on" + TBL_TAG
                    // would set DATE_LAST_UPDATED__UTC for ALL books with that tag
                    // while not needed.
                    final Optional<Tag> found = findById(tag.getId());
                    // Check for the name being equals.
                    if (found.isPresent() && !found.get().getName().equals(tag.getName())) {
                        update(tag);
                    }
                }

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, tag.getId());
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert Book-Tag");
                }
            }
        }
    }

    @IntRange(from = 1)
    @Override
    public long insert(@NonNull final Tag tag)
            throws DaoInsertException {

        final long iId;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindString(1, tag.getName());
            iId = stmt.executeInsert();
        }

        if (iId != -1) {
            tag.setId(iId);
            return iId;
        }

        // The insert failed with -1
        throw new DaoInsertException(ERROR_INSERT_FROM + tag);
    }

    @Override
    public void update(@NonNull final Tag tag)
            throws DaoUpdateException {

        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
            stmt.bindString(1, tag.getName());

            stmt.bindLong(2, tag.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            return;
        }

        throw new DaoUpdateException(ERROR_UPDATE_FROM + tag);
    }

    @Override
    public boolean delete(@NonNull final Tag tag) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
            stmt.bindLong(1, tag.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }
        if (rowsAffected > 0) {
            tag.setId(0);
            return true;
        }
        return false;
    }

    @Override
    public int moveBooks(@NonNull final Context context,
                         @NonNull final Tag source,
                         @NonNull final Tag target)
            throws DaoInsertException, DaoUpdateException {

        int booksMoved;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Relink books with the target Tag,
            // respecting the position of the Tag in the list for each book.
            final List<Long> bookIds = getBookIds(source.getId());
            booksMoved = bookIds.size();

            for (final long bookId : bookIds) {
                final Book book = Book.from(bookId);

                final List<Tag> fromBook = book.getTags();
                final List<Tag> destList = new ArrayList<>();

                for (final Tag item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(target);
                        // We could 'break' here as there should be no duplicates,
                        // but paranoia...
                    } else {
                        // just keep/copy
                        destList.add(item);
                    }
                }

                // delete old links and store all new links
                // We KNOW there are no updates needed.
                final Locale bookLocale = book.getLocaleOrUserLocale(context);
                insertOrUpdate(context, bookId, destList, tag -> bookLocale);
            }

            // delete the obsolete source.
            delete(source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        return booksMoved;
    }

    @Override
    @WorkerThread
    public int importRecords(@NonNull final List<Tag> list) {
        int count = 0;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BULK)) {
                for (final Tag tag : list) {
                    stmt.bindString(1, tag.getName());
                    final long iId = stmt.executeInsert();
                    // simply ignore failure, see SQL statement.
                    if (iId != -1) {
                        count++;
                    }
                }
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
        return count;
    }

    private static final class Sql {

        /** Insert a {@link Tag}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_TAGS.getName()
                + '(' + DBKey.TAG
                + ") VALUES (?)";

        static final String INSERT_BULK =
                INSERT_OR_IGNORE_INTO_ + TBL_TAGS.getName()
                + "(" + DBKey.TAG
                + ") VALUES (?)";

        /** Update a {@link Tag}. */
        static final String UPDATE =
                UPDATE_ + TBL_TAGS.getName()
                + _SET_ + DBKey.TAG + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete a {@link Tag}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_TAGS.getName() + _WHERE_ + DBKey.PK_ID + "=?";

        static final String GET_ALL =
                SELECT_ + DBKey.PK_ID + ',' + DBKey.TAG
                + _FROM_ + TBL_TAGS.getName()
                + _ORDER_BY_ + DBKey.TAG;

        static final String FIND_BY_ID =
                SELECT_ + DBKey.PK_ID + ',' + DBKey.TAG
                + _FROM_ + TBL_TAGS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        static final String FIND_BY_NAME =
                SELECT_ + DBKey.PK_ID + ',' + DBKey.TAG
                + _FROM_ + TBL_TAGS.getName()
                + _WHERE_ + DBKey.TAG + "=?";

        static final String FIND_BY_BOOK_ID =
                SELECT_ + TBL_TAGS.dotAs(DBKey.PK_ID)
                + ',' + TBL_TAGS.dotAs(DBKey.TAG)
                + _FROM_ + TBL_BOOK_TAG.startJoin(TBL_TAGS)
                + _WHERE_ + TBL_BOOK_TAG.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + DBKey.TAG;

        /** Insert the link between a {@link Book} and a {@link Identifier}. */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_TAG.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_TAG
                + ") VALUES(?,?)";

        /** All {@link Book}s (id only!) for a given {@link Tag}. */
        static final String FIND_BOOK_IDS_BY_TAG_ID =
                SELECT_ + TBL_BOOK_TAG.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_TAG.ref()
                + _WHERE_ + TBL_BOOK_TAG.dot(DBKey.FK_TAG) + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Identifier}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_TAG.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";
    }
}
