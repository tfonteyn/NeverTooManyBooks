/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public final class AuthorDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "AuthorDao";

    /** All Books (id only!) for a given Author. */
    private static final String SELECT_BOOK_IDS_BY_AUTHOR_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?";

    /** All Books (id only!) for a given Author and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_AUTHOR_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
            + _FROM_ + TBL_BOOK_AUTHOR.ref()
            + TBL_BOOK_AUTHOR.join(TBL_BOOKS)
            + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_FAMILY_NAMES =
            SELECT_DISTINCT_ + KEY_AUTHOR_FAMILY_NAME + ',' + KEY_AUTHOR_FAMILY_NAME_OB
            + _FROM_ + TBL_AUTHORS.getName()
            + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION;

    /** name only. */
    private static final String SELECT_ALL_GIVEN_NAMES =
            SELECT_DISTINCT_ + KEY_AUTHOR_GIVEN_NAMES + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + KEY_AUTHOR_GIVEN_NAMES_OB + "<> ''"
            + _ORDER_BY_ + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /** {@link Author}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_AUTHORS.getName();

    /** Get an {@link Author} by the Author id. */
    private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Author} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Can return more than one row if the KEY_AUTHOR_GIVEN_NAMES_OB is empty.
     */
    private static final String FIND_ID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + KEY_AUTHOR_FAMILY_NAME_OB + "=?" + _COLLATION
            + _AND_ + KEY_AUTHOR_GIVEN_NAMES_OB + "=?" + _COLLATION;

    private static final String COUNT_ALL = "SELECT COUNT(*) FROM " + TBL_AUTHORS.getName();

    /** Count the number of {@link Book}'s by an {@link Author}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR.getName()
            + _WHERE_ + KEY_FK_AUTHOR + "=?";

    /** Count the number of {@link TocEntry}'s by an {@link Author}. */
    private static final String COUNT_TOC_ENTRIES =
            "SELECT COUNT(" + KEY_PK_ID + ") FROM " + TBL_TOC_ENTRIES.getName()
            + _WHERE_ + KEY_FK_AUTHOR + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_AUTHORS.getName()
            + '(' + KEY_AUTHOR_FAMILY_NAME + ',' + KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + KEY_AUTHOR_GIVEN_NAMES + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
            + ',' + KEY_AUTHOR_IS_COMPLETE
            + ") VALUES (?,?,?,?,?)";

    /** Delete an {@link Author}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_AUTHORS.getName() + _WHERE_ + KEY_PK_ID + "=?";

    /**
     * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
     */
    private static final String PURGE =
            DELETE_FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'
            + " AND " + KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_TOC_ENTRIES.getName() + ')';

    private static final String CASE_WHEN_ = "CASE WHEN ";
    private static final String _THEN_ = " THEN ";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";

    /** name only. */
    private static final String SELECT_ALL_NAMES_FORMATTED =
            SELECT_
            + getDisplayAuthor(TBL_AUTHORS.getAlias(), false)
            + _AS_ + KEY_AUTHOR_FORMATTED
            + ',' + KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.ref()
            + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
            + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /** name only. */
    private static final String SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST =
            SELECT_
            + getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
            + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST
            + ',' + KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.ref()
            + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
            + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /** Singleton. */
    private static AuthorDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private AuthorDao(@NonNull final Context context,
                      @NonNull final String logTag) {
        super(context, logTag);
    }

    public static AuthorDao getInstance() {
        if (sInstance == null) {
            sInstance = new AuthorDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Single column, with the formatted name of the Author.
     * Note how the 'otherwise' will always concatenate the names without white space.
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
    public static String getSortAuthor(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return CASE_WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                   + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                   + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                   + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                   + _END;
        } else {
            return CASE_WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                   + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                   + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                   + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                   + _END;
        }
    }

    /**
     * Single column, with the formatted name of the Author.
     *
     * @param tableAlias     to prefix
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
    public static String getDisplayAuthor(@NonNull final String tableAlias,
                                          final boolean givenNameFirst) {
        if (givenNameFirst) {
            return CASE_WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                   + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME

                   + _ELSE_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                   + "||' '||" + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                   + _END;
        } else {
            return CASE_WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                   + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME

                   + _ELSE_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                   + "||', '||" + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                   + _END;
        }
    }

    /**
     * Get the {@link Author} based on the given id.
     *
     * @param id of Author to find
     *
     * @return the {@link Author}, or {@code null} if not found
     */
    @Nullable
    public Author getById(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Author(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Author} by using the appropriate fields of the passed {@link Author}.
     * The incoming object is not modified.
     *
     * <strong>IMPORTANT:</strong> the query can return more than one row if the
     * given-name of the author is empty. e.g. "Asimov" and "Asimov"+"Isaac"
     * We only return the id of the  <strong>first row found</strong>.
     *
     * @param context      Current context
     * @param author       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long find(@NonNull final Context context,
                     @NonNull final Author author,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale) {

        final Locale authorLocale;
        if (lookupLocale) {
            authorLocale = author.getLocale(context, bookLocale);
        } else {
            authorLocale = bookLocale;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(2, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get a unique list of author names in the specified format.
     *
     * @param key type of name wanted, one of
     *            {@link DBDefinitions#KEY_AUTHOR_FAMILY_NAME},
     *            {@link DBDefinitions#KEY_AUTHOR_GIVEN_NAMES},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    public ArrayList<String> getNames(@NonNull final String key) {
        switch (key) {
            case KEY_AUTHOR_FAMILY_NAME:
                return getColumnAsList(SELECT_ALL_FAMILY_NAMES, key);

            case KEY_AUTHOR_GIVEN_NAMES:
                return getColumnAsList(SELECT_ALL_GIVEN_NAMES, key);

            case KEY_AUTHOR_FORMATTED:
                return getColumnAsList(SELECT_ALL_NAMES_FORMATTED, key);

            case KEY_AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsList(SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST, key);

            default:
                throw new IllegalArgumentException(key);
        }
    }

    /**
     * Get a list of book ID's for the given Author.
     *
     * @param authorId id of the author
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long authorId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SELECT_BOOK_IDS_BY_AUTHOR_ID,
                                                new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Author and Bookshelf.
     *
     * @param authorId    id of the Author
     * @param bookshelfId id of the Bookshelf
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long authorId,
                                      final long bookshelfId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(
                SELECT_BOOK_IDS_BY_AUTHOR_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(authorId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get all Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAll() {
        return mSyncedDb.rawQuery(SELECT_ALL, null);
    }

    public long count() {
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the books for the given Author.
     *
     * @param context    Current context
     * @param author     to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countBooks(@NonNull final Context context,
                           @NonNull final Author author,
                           @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && fixId(context, author, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the TocEntry's for the given Author.
     *
     * @param context    Current context
     * @param author     to count the TocEntries of
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    public long countTocEntries(@NonNull final Context context,
                                @NonNull final Author author,
                                @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && fixId(context, author, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                COUNT_TOC_ENTRIES)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Update the 'complete' status of an Author.
     *
     * @param authorId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setComplete(final long authorId,
                               final boolean isComplete) {
        final ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_IS_COMPLETE, isComplete);

        return 0 < mSyncedDb.update(TBL_AUTHORS.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(authorId)});
    }

    /**
     * Try to find the Author. If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param author       to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Context context,
                      @NonNull final Author author,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, author, lookupLocale, bookLocale);
        author.setId(id);
        return id;
    }

    /**
     * Refresh the passed Author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Author.
     * <p>
     * Will NOT insert a new Author if not found.
     *
     * @param context    Current context
     * @param author     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refresh(@NonNull final Context context,
                        @NonNull final Author author,
                        @NonNull final Locale bookLocale) {

        if (author.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            fixId(context, author, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Author dbAuthor = getById(author.getId());
            if (dbAuthor != null) {
                // copy any updated fields
                author.copyFrom(dbAuthor, false);
            } else {
                // not found?, set as 'new'
                author.setId(0);
            }
        }
    }

    /**
     * Creates a new {@link Author}.
     *
     * @param context Current context
     * @param author  object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted Author, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Author author) {

        final Locale authorLocale =
                author.getLocale(context, AppLocale.getInstance().getUserLocale(context));

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(INSERT)) {
            stmt.bindString(1, author.getFamilyName());
            stmt.bindString(2, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(3, author.getGivenNames());
            stmt.bindString(4, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            stmt.bindBoolean(5, author.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                author.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update an Author.
     *
     * @param context Current context
     * @param author  to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Author author) {

        final Locale authorLocale =
                author.getLocale(context, AppLocale.getInstance().getUserLocale(context));

        final ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_FAMILY_NAME, author.getFamilyName());
        cv.put(KEY_AUTHOR_FAMILY_NAME_OB,
               encodeOrderByColumn(author.getFamilyName(), authorLocale));
        cv.put(KEY_AUTHOR_GIVEN_NAMES, author.getGivenNames());
        cv.put(KEY_AUTHOR_GIVEN_NAMES_OB,
               encodeOrderByColumn(author.getGivenNames(), authorLocale));
        cv.put(KEY_AUTHOR_IS_COMPLETE, author.isComplete());

        return 0 < mSyncedDb.update(TBL_AUTHORS.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(author.getId())});
    }

    /**
     * Delete the passed {@link Author}.
     *
     * @param context Current context
     * @param author  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Author author) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, author.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            author.setId(0);
            try (BookDao bookDao = new BookDao(context, TAG)) {
                bookDao.repositionAuthor(context);
            }
        }
        return rowsAffected == 1;
    }

    /**
     * Moves all books from the 'source' {@link Author}, to the 'destId' {@link Author}.
     * The (now unused) 'source' {@link Author} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Author source,
                      final long destId)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // TOC is easy: just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(KEY_FK_AUTHOR, destId);
            mSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv, KEY_FK_AUTHOR + "=?",
                             new String[]{String.valueOf(source.getId())});

            // the books must be done one by one, as we need to prevent duplicate authors
            // e.g. suppose we have a book with author
            // a1@pos1
            // a2@pos2
            // and we want to replace a1 with a2, we cannot simply do a mass update.
            final Author destination = getById(destId);

            try (BookDao db = new BookDao(context, TAG)) {
                for (final long bookId : getBookIds(source.getId())) {
                    final Book book = Book.from(bookId, db);

                    final Collection<Author> fromBook =
                            book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
                    final Collection<Author> destList = new ArrayList<>();

                    for (final Author item : fromBook) {
                        if (source.getId() == item.getId()) {
                            // replace this one.
                            destList.add(destination);
                            // We could 'break' here as there should be no duplicates,
                            // but paranoia...
                        } else {
                            // just keep/copy
                            destList.add(item);
                        }
                    }
                    // delete old links and store all new links
                    db.insertBookAuthors(context, bookId, destList, true, book.getLocale(context));
                }
            }
            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    public void purge() {
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad.
            Logger.error(TAG, e);
        }
    }
}
