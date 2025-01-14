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
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.TagDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMergeHelper;

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
    public List<Tag> getList() {
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
    public Collection<Tag> getByBookId(@IntRange(from = 1) final long bookId) {
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

    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Tag> list,
                             @NonNull final Function<Tag, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final TagMergeHelper mergeHelper = new TagMergeHelper();
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
                tag.setName(dbTag.get().getName());
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

                // create if needed - do NOT do updates unless explicitly allowed
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

    private static final class Sql {

        /** Insert a {@link Tag}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_TAGS.getName()
                + '(' + DBKey.TAG
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
