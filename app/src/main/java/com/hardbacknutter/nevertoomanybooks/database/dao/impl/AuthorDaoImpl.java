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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMergeHelper;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class AuthorDaoImpl
        extends BaseDaoImpl
        implements AuthorDao {

    /** Log tag. */
    private static final String TAG = "AuthorDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    private static final String[] Z_ARRAY_STRING = new String[0];

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public AuthorDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    /**
     * Single column, for sorting on the formatted name of the Author.
     * <p>
     * Dev note: Note how the 'otherwise' will always concatenate the names without white space.
     *
     * @param givenNameFirst {@code true}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "GivenNamesFamilyName"
     *                       {@code false}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "FamilyNameGivenNames"
     *
     * @return column expression
     */
    @NonNull
    public static String getSortingDomainExpression(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return Sql.SORT_AUTHOR_GIVEN_FIRST;
        } else {
            return Sql.SORT_AUTHOR_FAMILY_FIRST;
        }
    }

    /**
     * Single column, with the formatted name of the Author.
     *
     * @param givenNameFirst {@code true}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "GivenNames FamilyName"
     *                       {@code false}
     *                       If no given name -> "FamilyName"
     *                       otherwise -> "FamilyName, GivenNames"
     *
     * @return column expression
     */
    @NonNull
    public static String getDisplayDomainExpression(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return Sql.DISPLAY_AUTHOR_GIVEN_FIRST;
        } else {
            return Sql.DISPLAY_AUTHOR_FAMILY_FIRST;
        }
    }

    @NonNull
    @Override
    public Optional<Author> findById(@IntRange(from = 1) final long id) {
        try (Cursor cursor = db.rawQuery(Sql.SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(new Author(id, new CursorRow(cursor)));
            } else {
                return Optional.empty();
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <strong>IMPORTANT:</strong> the query can return more than one row if the
     * given-name of the author is empty. e.g. "Asimov" and "Asimov"+"Isaac"
     * We only return the <strong>first entity found</strong>.
     *
     * @param context Current context
     * @param author  to find the id of
     * @param locale  to use
     *
     * @return the Author
     */
    @Override
    @NonNull
    public Optional<Author> findByName(@NonNull final Context context,
                                       @NonNull final Author author,
                                       @NonNull final Locale locale) {

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{
                SqlEncode.orderByColumn(author.getFamilyName(), locale),
                SqlEncode.orderByColumn(author.getGivenNames(), locale)})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new Author(rowData.getLong(DBKey.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    @NonNull
    public List<String> getNames(@NonNull final String key) {
        switch (key) {
            case DBKey.AUTHOR_FAMILY_NAME:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_FAMILY_NAMES);

            case DBKey.AUTHOR_GIVEN_NAMES:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_GIVEN_NAMES);

            case DBKey.AUTHOR_FORMATTED:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES_FORMATTED_FAMILY_FIRST);

            case DBKey.AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsStringArrayList(Sql.SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST);

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    @NonNull
    public List<Author> getByBookId(@IntRange(from = 1) final long bookId) {
        final List<Author> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Author(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<Long> getBookIds(final long authorId) {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(Sql.FIND_BOOK_IDS_BY_AUTHOR_ID,
                                         new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public List<AuthorWork> getAuthorWorks(@NonNull final Author author,
                                           final long bookshelfId,
                                           final boolean withTocEntries,
                                           final boolean withBooks,
                                           @WorksOrderBy @Nullable final String orderBy) {
        // sanity check
        if (!withTocEntries && !withBooks) {
            throw new IllegalArgumentException("Must specify what to fetch");
        }

        final String orderByColumns;
        if (orderBy == null || DBKey.TITLE_OB.equals(orderBy)) {
            orderByColumns = DBKey.TITLE_OB + _COLLATION;
        } else if (DBKey.FIRST_PUBLICATION__DATE.equals(orderBy)) {
            orderByColumns = DBKey.FIRST_PUBLICATION__DATE + ',' + DBKey.TITLE_OB + _COLLATION;
        } else {
            throw new IllegalArgumentException("Invalid orderBy");
        }

        final boolean byShelf = bookshelfId != Bookshelf.ALL_BOOKS;

        // rawQuery wants String[] as bind parameters
        final String authorIdStr = String.valueOf(author.getId());
        final String bookshelfIdStr = String.valueOf(bookshelfId);

        String sql = "";
        final List<String> paramList = new ArrayList<>();

        // MUST be toc first, books second; otherwise the GROUP BY is done on the whole
        // UNION instead of on the toc only; and SqLite rejects () around the sub selects.
        if (withTocEntries) {
            sql += Sql.FIND_TOC_ENTRIES_BY_AUTHOR_ID
                   + (byShelf ? " JOIN " + TBL_BOOK_BOOKSHELF.ref()
                                + " ON (" + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_BOOK)
                                + '=' + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + ")"
                              : "")
                   + _WHERE_ + TBL_TOC_ENTRIES.dot(DBKey.FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?" : "")
                   + " GROUP BY " + TBL_TOC_ENTRIES.dot(DBKey.PK_ID);
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        if (withBooks && withTocEntries) {
            sql += " UNION ";
        }

        if (withBooks) {
            sql += Sql.FIND_BOOK_TITLES_BY_AUTHOR_ID
                   + (byShelf ? TBL_BOOKS.join(TBL_BOOK_BOOKSHELF) : "")
                   + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF) + "=?" : "");
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        sql += _ORDER_BY_ + orderByColumns;

        final List<AuthorWork> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, paramList.toArray(Z_ARRAY_STRING))) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final AuthorWork.Type type = AuthorWork.Type.getType(
                        rowData.getString(DBKey.AUTHOR_WORK_TYPE).charAt(0));

                switch (type) {
                    case TocEntry:
                        list.add(new TocEntry(rowData.getLong(DBKey.PK_ID), author, rowData));
                        break;

                    case BookLight:
                        list.add(new BookLight(rowData.getLong(DBKey.PK_ID), author, rowData));
                        break;

                    case Book:
                    default:
                        throw new IllegalArgumentException(String.valueOf(type));
                }
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return db.rawQuery(Sql.SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countBooks(@NonNull final Author author) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_BOOKS)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean setComplete(@NonNull final Author author,
                               final boolean complete) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.SET_COMPLETE)) {
            stmt.bindBoolean(1, complete);

            stmt.bindLong(2, author.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            author.setComplete(complete);
            return true;
        }
        return false;
    }

    /**
     * Remove duplicates.
     * Consolidates author/- and author/type.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean pruneList(@NonNull final Context context,
                             @NonNull final Collection<Author> list,
                             @NonNull final Function<Author, Locale> localeSupplier) {
        // Reminder: only abort if empty. We rely on 'fixId' being called for ALL list values.
        if (list.isEmpty()) {
            return false;
        }

        final EntityMergeHelper<Author> mergeHelper = new AuthorMergeHelper();
        return mergeHelper.merge(context, list, localeSupplier,
                                 // Don't lookup the locale a 2nd time.
                                 (current, locale) -> fixId(context, current, locale));
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final Author author,
                      @NonNull final Locale locale) {
        final long found = findByName(context, author, locale)
                .map(Author::getId).orElse(0L);
        author.setId(found);

        final Author realAuthor = author.getRealAuthor();
        if (realAuthor != null) {
            fixId(context, realAuthor, locale);
        }
    }

    @Override
    public void refresh(@NonNull final Context context,
                        @NonNull final Author author,
                        @NonNull final Locale locale) {

        // If needed, check if we already have it in the database.
        if (author.getId() == 0) {
            fixId(context, author, locale);
        }

        // If we do already have it, update the object
        if (author.getId() > 0) {
            final Optional<Author> dbAuthor = findById(author.getId());
            // Sanity check
            if (dbAuthor.isPresent()) {
                // copy any updated fields
                author.copyFrom(dbAuthor.get(), false);
            } else {
                // we shouldn't get here... but if we do, set it to 'new'
                author.setId(0);
            }
        }
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @IntRange(from = 1) final long bookId,
                               final boolean doUpdates,
                               @NonNull final Collection<Author> list,
                               @NonNull final Function<Author, Locale> localeSupplier)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        pruneList(context, list, localeSupplier);

        // Just delete all current links; we'll re-insert them for easier positioning
        try (SynchronizedStatement stmt1 = db.compileStatement(Sql.DELETE_BOOK_LINKS_BY_BOOK_ID)) {
            stmt1.bindLong(1, bookId);
            stmt1.executeUpdateDelete();
        }

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        int position = 0;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_BOOK_LINK)) {
            for (final Author author : list) {
                final Locale locale = localeSupplier.apply(author);
                fixId(context, author, locale);

                // create if needed - do NOT do updates unless explicitly allowed
                if (author.getId() == 0) {
                    insert(context, author, locale);
                } else if (doUpdates) {
                    // https://stackoverflow.com/questions/6677517/update-if-different-changed
                    // ONLY update if there are actual changes.
                    // Otherwise the trigger "after_update_on" + TBL_AUTHORS
                    // would set DATE_LAST_UPDATED__UTC for ALL books by that author
                    // while not needed.
                    final Optional<Author> found = findById(author.getId());
                    // Check for being identical!
                    if (found.isPresent() && !found.get().isIdentical(author)) {
                        update(context, author, locale);
                    }
                }

                position++;

                stmt.bindLong(1, bookId);
                stmt.bindLong(2, author.getId());
                stmt.bindLong(3, position);
                stmt.bindLong(4, author.getType());
                if (stmt.executeInsert() == -1) {
                    throw new DaoInsertException("insert Book-Author");
                }
            }
        }
    }

    @Override
    @IntRange(from = 1)
    public long insert(@NonNull final Context context,
                       @NonNull final Author author,
                       @NonNull final Locale locale)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                stmt.bindString(1, author.getFamilyName());
                stmt.bindString(2, SqlEncode.orderByColumn(author.getFamilyName(), locale));
                stmt.bindString(3, author.getGivenNames());
                stmt.bindString(4, SqlEncode.orderByColumn(author.getGivenNames(), locale));
                stmt.bindBoolean(5, author.isComplete());
                iId = stmt.executeInsert();
            }

            if (iId != -1) {
                author.setId(iId);
                insertOrUpdateRealAuthor(context, author, locale);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return iId;
            }
        } catch (@NonNull final DaoWriteException e) {
            author.setId(0);
            throw e;
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
        // The insert failed with -1
        author.setId(0);
        throw new DaoInsertException(ERROR_INSERT_FROM + author);
    }

    @Override
    public void update(@NonNull final Context context,
                       @NonNull final Author author,
                       @NonNull final Locale locale)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, author.getFamilyName());
                stmt.bindString(2, SqlEncode.orderByColumn(author.getFamilyName(), locale));
                stmt.bindString(3, author.getGivenNames());
                stmt.bindString(4, SqlEncode.orderByColumn(author.getGivenNames(), locale));
                stmt.bindBoolean(5, author.isComplete());

                stmt.bindLong(6, author.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }

            if (rowsAffected > 0) {
                insertOrUpdateRealAuthor(context, author, locale);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATE_FROM + author);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * Handle the real-author storage.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param author  the 'original' author
     * @param locale  Locale to use if the item has none set
     *
     * @throws DaoWriteException on failure
     */
    private void insertOrUpdateRealAuthor(@NonNull final Context context,
                                          @NonNull final Author author,
                                          @NonNull final Locale locale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // always delete any previous link
        deletePseudonymLink(author.getId());

        final Author realAuthor = author.getRealAuthor();
        if (realAuthor == null) {
            // all done
            return;
        }

        fixId(context, realAuthor, locale);
        if (realAuthor.getId() == 0) {
            insert(context, realAuthor, locale);
        } else {
            update(context, realAuthor, locale);
        }
        insertPseudonymLink(author.getId(), realAuthor.getId());
    }

    // ENHANCE: allow delete of author if all books have another author
    public boolean isDeletable(@NonNull final Author author) {

        final String sql = SELECT_DISTINCT_ + 1
                           + _FROM_ + TBL_BOOK_AUTHOR.getName()
                           + _WHERE_ + DBKey.FK_BOOK
                           + _IN_ + '(' + Sql.FIND_BOOK_IDS_BY_AUTHOR_ID + ')'
                           + _GROUP_BY_ + DBKey.FK_BOOK
                           + " HAVING COUNT(" + DBKey.FK_AUTHOR + ")=1";

        final long rows;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, author.getId());
            rows = stmt.simpleQueryForLongOrZero();
        }

        // no rows? then DELETE OK
        return rows == 0;
    }

    @Override
    public boolean delete(@NonNull final Context context,
                          @NonNull final Author author) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_ID)) {
                stmt.bindLong(1, author.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                author.setId(0);
                fixPositions(context);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return true;
            }
            return false;
        } catch (@NonNull final DaoWriteException e) {
            return false;
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    private void insertPseudonymLink(final long authorId,
                                     final long realAuthorId)
            throws DaoInsertException {

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT_PSEUDONYM_LINKS)) {
            stmt.bindLong(1, authorId);
            stmt.bindLong(2, realAuthorId);
            if (stmt.executeInsert() == -1) {
                throw new DaoInsertException("Failed to insert PseudonymLink author=" + authorId
                                             + ", real=" + realAuthorId);
            }
        }
    }

    private void deletePseudonymLink(final long pseudonymId) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_PSEUDONYM_LINKS)) {
            stmt.bindLong(1, pseudonymId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public void moveBooks(@NonNull final Context context,
                          @NonNull final Author source,
                          @NonNull final Author target)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // Updating all TOCs is easy: just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(DBKey.FK_AUTHOR, target.getId());
            db.update(TBL_TOC_ENTRIES.getName(), cv, DBKey.FK_AUTHOR + "=?",
                      new String[]{String.valueOf(source.getId())});

            // Relink books with the target Author,
            // respecting the position of the Author in the list for each book.
            for (final long bookId : getBookIds(source.getId())) {
                final Book book = Book.from(bookId);

                final List<Author> fromBook = book.getAuthors();
                final List<Author> destList = new ArrayList<>();

                for (final Author item : fromBook) {
                    if (source.getId() == item.getId()) {
                        // We MUST preserve the author type as originally set.
                        target.setType(item.getType());
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
                insertOrUpdate(context, bookId, false, destList, author -> bookLocale);
            }

            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    @WorkerThread
    public void purge() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.PURGE)) {
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public int fixPositions(@NonNull final Context context)
            throws DaoWriteException {

        final List<Long> bookIds = getColumnAsLongArrayList(Sql.REPOSITION);
        if (!bookIds.isEmpty()) {
            Synchronizer.SyncLock txLock = null;
            try {
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final Book book = Book.from(bookId);
                    final List<Author> list = getByBookId(bookId);
                    final Locale bookLocale = book.getLocaleOrUserLocale(context);
                    // We KNOW there are no updates needed.
                    insertOrUpdate(context, bookId, false, list, author -> bookLocale);
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
        return bookIds.size();
    }

    private static final class Sql {

        /** Insert an {@link Author}. */
        static final String INSERT =
                INSERT_INTO_ + TBL_AUTHORS.getName()
                + '(' + DBKey.AUTHOR_FAMILY_NAME + ',' + DBKey.AUTHOR_FAMILY_NAME_OB
                + ',' + DBKey.AUTHOR_GIVEN_NAMES + ',' + DBKey.AUTHOR_GIVEN_NAMES_OB
                + ',' + DBKey.AUTHOR_IS_COMPLETE
                + ") VALUES (?,?,?,?,?)";

        /** Update an {@link Author}. */
        static final String UPDATE =
                UPDATE_ + TBL_AUTHORS.getName()
                + _SET_ + DBKey.AUTHOR_FAMILY_NAME + "=?," + DBKey.AUTHOR_FAMILY_NAME_OB + "=?"
                + ',' + DBKey.AUTHOR_GIVEN_NAMES + "=?," + DBKey.AUTHOR_GIVEN_NAMES_OB + "=?"
                + ',' + DBKey.AUTHOR_IS_COMPLETE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        static final String SET_COMPLETE =
                UPDATE_ + TBL_AUTHORS.getName()
                + _SET_ + DBKey.AUTHOR_IS_COMPLETE + "=?"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Delete an {@link Author}. */
        static final String DELETE_BY_ID =
                DELETE_FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** Purge all {@link Author}s which are no longer in use. */
        static final String PURGE =
                DELETE_FROM_ + TBL_AUTHORS.getName()

                + _WHERE_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR
                + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'

                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.FK_AUTHOR
                + _FROM_ + TBL_TOC_ENTRIES.getName() + ')'

                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.AUTHOR_PSEUDONYM
                + _FROM_ + TBL_PSEUDONYM_AUTHOR.getName() + ')'
                + _AND_ + DBKey.PK_ID + _NOT_IN_
                + '(' + SELECT_DISTINCT_ + DBKey.AUTHOR_REAL_AUTHOR
                + _FROM_ + TBL_PSEUDONYM_AUTHOR.getName() + ')';

        /** Insert the link between a {@link Book} and an {@link Author}. */
        static final String INSERT_BOOK_LINK =
                INSERT_INTO_ + TBL_BOOK_AUTHOR.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.FK_AUTHOR
                + ',' + DBKey.BOOK_AUTHOR_POSITION
                + ',' + DBKey.AUTHOR_TYPE__BITMASK
                + ") VALUES(?,?,?,?)";

        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_BOOK_LINKS_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + DBKey.FK_BOOK + "=?";

        /** Insert the link between a pseudonym name and an {@link Author}. */
        static final String INSERT_PSEUDONYM_LINKS =
                INSERT_INTO_ + TBL_PSEUDONYM_AUTHOR.getName()
                + '(' + DBKey.AUTHOR_PSEUDONYM
                + ',' + DBKey.AUTHOR_REAL_AUTHOR
                + ") VALUES (?,?)";

        /**
         * Delete the link between a pseudonym name and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String DELETE_PSEUDONYM_LINKS =
                DELETE_FROM_ + TBL_PSEUDONYM_AUTHOR.getName()
                + _WHERE_ + DBKey.AUTHOR_PSEUDONYM + "=?";

        /** Get a count of the {@link Author}s. */

        static final String COUNT_ALL =
                SELECT_COUNT_FROM_ + TBL_AUTHORS.getName();

        /** Count the number of {@link Book}'s by an {@link Author}. */
        static final String COUNT_BOOKS =
                SELECT_ + "COUNT(" + DBKey.FK_BOOK + ")"
                + _FROM_ + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + DBKey.FK_AUTHOR + "=?";

        /** A list of all {@link Author}s, unordered. */
        static final String SELECT_ALL =
                SELECT_ + TBL_AUTHORS.dot("*")
                + ',' + TBL_PSEUDONYM_AUTHOR.dotAs(DBKey.AUTHOR_REAL_AUTHOR)
                + _FROM_ + TBL_AUTHORS.ref() + TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR);

        /** Get an {@link Author} by its id. */
        static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKey.PK_ID + "=?";

        /**
         * Find an {@link Author} by family and given name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String FIND_BY_NAME =
                SELECT_ALL
                + _WHERE_ + DBKey.AUTHOR_FAMILY_NAME_OB + "=?" + _COLLATION
                + _AND_ + DBKey.AUTHOR_GIVEN_NAMES_OB + "=?" + _COLLATION;

        /**
         * All {@link Author}s for a {@link Book}.
         * Ordered by position.
         */
        static final String FIND_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_AUTHORS.dotAs(DBKey.PK_ID,
                                                     DBKey.AUTHOR_FAMILY_NAME,
                                                     DBKey.AUTHOR_GIVEN_NAMES,
                                                     DBKey.AUTHOR_IS_COMPLETE)
                + ',' + TBL_BOOK_AUTHOR.dotAs(DBKey.BOOK_AUTHOR_POSITION,
                                              DBKey.AUTHOR_TYPE__BITMASK)

                + ',' + TBL_PSEUDONYM_AUTHOR.dotAs(DBKey.AUTHOR_REAL_AUTHOR)

                + _FROM_ + TBL_BOOK_AUTHOR.startJoin(TBL_AUTHORS)
                + TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(DBKey.BOOK_AUTHOR_POSITION);

        /** All {@link Book}s (id only!) for a given {@link Author}. */
        static final String FIND_BOOK_IDS_BY_AUTHOR_ID =
                SELECT_ + TBL_BOOK_AUTHOR.dotAs(DBKey.FK_BOOK)
                + _FROM_ + TBL_BOOK_AUTHOR.ref()
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR) + "=?";


        /** Column definition for sorting by given-names first. */
        static final String SORT_AUTHOR_GIVEN_FIRST =
                CASE_WHEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES_OB) + "=''"
                + _THEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME_OB)
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES_OB)
                + "||" + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME_OB)
                + _END;

        /** Column definition for sorting by family-name first. */
        static final String SORT_AUTHOR_FAMILY_FIRST =
                CASE_WHEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES_OB) + "=''"
                + _THEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME_OB)
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME_OB)
                + "||" + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES_OB)
                + _END;

        /** Column definition for displaying by given-names first. */
        static final String DISPLAY_AUTHOR_GIVEN_FIRST =
                CASE_WHEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES) + "=''"
                + _THEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME)
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES)
                + "||' '||" + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME)
                + _END;

        /** Get a list of {@link Author} "given family" names for use in a dropdown selection. */
        static final String SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST =
                SELECT_ + DISPLAY_AUTHOR_GIVEN_FIRST
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + DBKey.AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + DBKey.AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** Column definition for displaying by family-name first. */
        static final String DISPLAY_AUTHOR_FAMILY_FIRST =
                CASE_WHEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES) + "=''"
                + _THEN_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME)
                + _ELSE_ + TBL_AUTHORS.dot(DBKey.AUTHOR_FAMILY_NAME)
                + "||', '||" + TBL_AUTHORS.dot(DBKey.AUTHOR_GIVEN_NAMES)
                + _END;

        /** Get a list of {@link Author} "family, given" names for use in a dropdown selection. */
        static final String SELECT_ALL_NAMES_FORMATTED_FAMILY_FIRST =
                SELECT_ + DISPLAY_AUTHOR_FAMILY_FIRST
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + DBKey.AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + DBKey.AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** Get a list of {@link Author} family names for use in a dropdown selection. */
        static final String SELECT_ALL_FAMILY_NAMES =
                SELECT_DISTINCT_ + DBKey.AUTHOR_FAMILY_NAME
                + _FROM_ + TBL_AUTHORS.getName()
                + _ORDER_BY_ + DBKey.AUTHOR_FAMILY_NAME_OB + _COLLATION;

        /** Get a list of {@link Author} given names for use in a dropdown selection. */
        static final String SELECT_ALL_GIVEN_NAMES =
                SELECT_DISTINCT_ + DBKey.AUTHOR_GIVEN_NAMES
                + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + DBKey.AUTHOR_GIVEN_NAMES_OB + "<> ''"
                + _ORDER_BY_ + DBKey.AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /**
         * All Book titles and their first pub. date, for an Author,
         * returned as an {@link AuthorWork}.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * The pub. date is cut down to the year (4 year digits) only.
         * We need TITLE_OB as it will be used to ORDER BY
         */
        static final String FIND_BOOK_TITLES_BY_AUTHOR_ID =
                SELECT_
                + "'" + AuthorWork.Type.BookLight.asChar() + "'" + _AS_ + DBKey.AUTHOR_WORK_TYPE
                + ',' + TBL_BOOKS.dotAs(DBKey.PK_ID, DBKey.TITLE, DBKey.TITLE_OB)
                + ",SUBSTR(" + TBL_BOOKS.dot(DBKey.FIRST_PUBLICATION__DATE) + ",0,5)"
                + _AS_ + DBKey.FIRST_PUBLICATION__DATE
                + ',' + TBL_BOOKS.dotAs(DBKey.LANGUAGE)
                + ",1" + _AS_ + DBKey.BOOK_COUNT
                + _FROM_ + TBL_BOOKS.startJoin(TBL_BOOK_AUTHOR);

        /**
         * All {@link TocEntry}'s for an Author,
         * returned as an {@link AuthorWork}.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * The pub. date is cut down to the year (4 year digits) only.
         * We need TITLE_OB as it will be used to ORDER BY
         */
        static final String FIND_TOC_ENTRIES_BY_AUTHOR_ID =
                SELECT_
                + "'" + AuthorWork.Type.TocEntry.asChar() + "'" + _AS_ + DBKey.AUTHOR_WORK_TYPE
                + ',' + TBL_TOC_ENTRIES.dotAs(DBKey.PK_ID, DBKey.TITLE, DBKey.TITLE_OB)
                // Year only
                + ",SUBSTR(" + TBL_TOC_ENTRIES.dot(DBKey.FIRST_PUBLICATION__DATE) + ",0,5)"
                + _AS_ + DBKey.FIRST_PUBLICATION__DATE
                // The Toc table does not have a language field, just return an empty string
                + ",''" + _AS_ + DBKey.LANGUAGE
                // count the number of books this TOC entry is present in.
                + ", COUNT(" + TBL_TOC_ENTRIES.dot(DBKey.PK_ID) + ")" + _AS_ + DBKey.BOOK_COUNT
                // join with the books, so we can group by toc id, and get the number of books.
                + _FROM_ + TBL_TOC_ENTRIES.startJoin(TBL_BOOK_TOC_ENTRIES);

        static final String REPOSITION =
                SELECT_ + DBKey.FK_BOOK
                + _FROM_
                + '(' + SELECT_ + DBKey.FK_BOOK
                + ",MIN(" + DBKey.BOOK_AUTHOR_POSITION + ')' + _AS_ + "mp"
                + _FROM_ + TBL_BOOK_AUTHOR.getName()
                + _GROUP_BY_ + DBKey.FK_BOOK
                + ')'
                + _WHERE_ + "mp>1";
    }
}
