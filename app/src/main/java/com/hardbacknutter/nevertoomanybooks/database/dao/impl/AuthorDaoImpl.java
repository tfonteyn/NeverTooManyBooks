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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class AuthorDaoImpl
        extends BaseDaoImpl
        implements AuthorDao {

    /** Log tag. */
    private static final String TAG = "AuthorDaoImpl";

    /** All Books (id only!) for a given Author. */
    private static final String SELECT_BOOK_IDS_BY_AUTHOR_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKeys.KEY_PK_ID)
            + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKeys.KEY_FK_AUTHOR) + "=?";

    /** All Books (id only!) for a given Author and Bookshelf. */
    private static final String SELECT_BOOK_IDS_BY_AUTHOR_ID_AND_BOOKSHELF_ID =
            SELECT_ + TBL_BOOKS.dotAs(DBKeys.KEY_PK_ID)
            + _FROM_ + TBL_BOOK_AUTHOR.ref()
            + TBL_BOOK_AUTHOR.join(TBL_BOOKS)
            + TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
            + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKeys.KEY_FK_AUTHOR) + "=?"
            + _AND_ + TBL_BOOK_BOOKSHELF.dot(DBKeys.KEY_FK_BOOKSHELF) + "=?";

    /** name only. */
    private static final String SELECT_ALL_FAMILY_NAMES =
            SELECT_DISTINCT_
            + DBKeys.KEY_AUTHOR_FAMILY_NAME
            + ',' + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB
            + _FROM_ + TBL_AUTHORS.getName()
            + _ORDER_BY_ + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION;

    /** name only. */
    private static final String SELECT_ALL_GIVEN_NAMES =
            SELECT_DISTINCT_
            + DBKeys.KEY_AUTHOR_GIVEN_NAMES
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB + "<> ''"
            + _ORDER_BY_ + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /** {@link Author}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_AUTHORS.getName();

    /** Get an {@link Author} by the Author id. */
    private static final String SELECT_BY_ID = SELECT_ALL + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Get the id of a {@link Author} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Can return more than one row if the KEY_AUTHOR_GIVEN_NAMES_OB is empty.
     */
    private static final String FIND_ID =
            SELECT_ + DBKeys.KEY_PK_ID + _FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB + "=?" + _COLLATION
            + _AND_ + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB + "=?" + _COLLATION;

    private static final String COUNT_ALL = "SELECT COUNT(*) FROM " + TBL_AUTHORS.getName();

    /** Count the number of {@link Book}'s by an {@link Author}. */
    private static final String COUNT_BOOKS =
            "SELECT COUNT(" + DBKeys.KEY_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR.getName()
            + _WHERE_ + DBKeys.KEY_FK_AUTHOR + "=?";

    /** Count the number of {@link TocEntry}'s by an {@link Author}. */
    private static final String COUNT_TOC_ENTRIES =
            "SELECT COUNT(" + DBKeys.KEY_PK_ID + ") FROM " + TBL_TOC_ENTRIES.getName()
            + _WHERE_ + DBKeys.KEY_FK_AUTHOR + "=?";

    private static final String INSERT =
            INSERT_INTO_ + TBL_AUTHORS.getName()
            + '(' + DBKeys.KEY_AUTHOR_FAMILY_NAME + ',' + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB
            + ',' + DBKeys.KEY_AUTHOR_IS_COMPLETE
            + ") VALUES (?,?,?,?,?)";

    /** Delete an {@link Author}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_AUTHORS.getName() + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
     */
    private static final String PURGE =
            DELETE_FROM_ + TBL_AUTHORS.getName()
            + _WHERE_ + DBKeys.KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + DBKeys.KEY_FK_AUTHOR + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'
            + " AND " + DBKeys.KEY_PK_ID + _NOT_IN_
            + "(SELECT DISTINCT " + DBKeys.KEY_FK_AUTHOR + _FROM_ + TBL_TOC_ENTRIES.getName() + ')';

    private static final String CASE_WHEN_ = "CASE WHEN ";
    private static final String _THEN_ = " THEN ";
    private static final String _ELSE_ = " ELSE ";
    private static final String _END = " END";

    private static final String SORT_AUTHOR_FAMILY_FIRST =
            CASE_WHEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
            + _THEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME_OB)

            + _ELSE_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME_OB)
            + "||" + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB)
            + _END;

    private static final String SORT_AUTHOR_GIVEN_FIRST =
            CASE_WHEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
            + _THEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME_OB)

            + _ELSE_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB)
            + "||" + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME_OB)
            + _END;

    private static final String DISPLAY_AUTHOR_FAMILY_FIRST =
            CASE_WHEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES) + "=''"
            + _THEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME)
            + _ELSE_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME)
            + "||', '||" + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES)
            + _END;

    private static final String DISPLAY_AUTHOR_GIVEN_FIRST =
            CASE_WHEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES) + "=''"
            + _THEN_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME)
            + _ELSE_ + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_GIVEN_NAMES)
            + "||' '||" + TBL_AUTHORS.dot(DBKeys.KEY_AUTHOR_FAMILY_NAME)
            + _END;

    /** name only. */
    private static final String SELECT_ALL_NAMES_FORMATTED =
            SELECT_
            + getDisplayAuthor(false) + _AS_ + DBKeys.KEY_AUTHOR_FORMATTED
            + ',' + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.ref()
            + _ORDER_BY_ + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /** name only. */
    private static final String SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST =
            SELECT_
            + getDisplayAuthor(true) + _AS_ + DBKeys.KEY_AUTHOR_FORMATTED_GIVEN_FIRST
            + ',' + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB
            + _FROM_ + TBL_AUTHORS.ref()
            + _ORDER_BY_ + DBKeys.KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
            + ',' + DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public AuthorDaoImpl(@NonNull final Context context) {
        super(context, TAG);
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
            return SORT_AUTHOR_GIVEN_FIRST;
        } else {
            return SORT_AUTHOR_FAMILY_FIRST;
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
    public static String getDisplayAuthor(final boolean givenNameFirst) {
        if (givenNameFirst) {
            return DISPLAY_AUTHOR_GIVEN_FIRST;
        } else {
            return DISPLAY_AUTHOR_FAMILY_FIRST;
        }
    }

    @Override
    @Nullable
    public Author getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Author(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    @Override
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

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, BaseDaoImpl
                    .encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(2, BaseDaoImpl
                    .encodeOrderByColumn(author.getGivenNames(), authorLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public ArrayList<String> getNames(@NonNull final String key) {
        switch (key) {
            case DBKeys.KEY_AUTHOR_FAMILY_NAME:
                return getColumnAsList(SELECT_ALL_FAMILY_NAMES, key);

            case DBKeys.KEY_AUTHOR_GIVEN_NAMES:
                return getColumnAsList(SELECT_ALL_GIVEN_NAMES, key);

            case DBKeys.KEY_AUTHOR_FORMATTED:
                return getColumnAsList(SELECT_ALL_NAMES_FORMATTED, key);

            case DBKeys.KEY_AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsList(SELECT_ALL_NAMES_FORMATTED_GIVEN_FIRST, key);

            default:
                throw new IllegalArgumentException(key);
        }
    }

    @Override
    @NonNull
    public ArrayList<Long> getBookIds(final long authorId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_IDS_BY_AUTHOR_ID,
                                          new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public ArrayList<Long> getBookIds(final long authorId,
                                      final long bookshelfId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(
                SELECT_BOOK_IDS_BY_AUTHOR_ID_AND_BOOKSHELF_ID,
                new String[]{String.valueOf(authorId), String.valueOf(bookshelfId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return mDb.rawQuery(SELECT_ALL, null);
    }

    @Override
    public long count() {
        try (SynchronizedStatement stmt = mDb.compileStatement(COUNT_ALL)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countBooks(@NonNull final Context context,
                           @NonNull final Author author,
                           @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && fixId(context, author, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mDb.compileStatement(COUNT_BOOKS)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public long countTocEntries(@NonNull final Context context,
                                @NonNull final Author author,
                                @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && fixId(context, author, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mDb.compileStatement(
                COUNT_TOC_ENTRIES)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public boolean setComplete(final long authorId,
                               final boolean isComplete) {
        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_AUTHOR_IS_COMPLETE, isComplete);

        return 0 < mDb.update(TBL_AUTHORS.getName(), cv, DBKeys.KEY_PK_ID + "=?",
                              new String[]{String.valueOf(authorId)});
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final Author author,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {
        final long id = find(context, author, lookupLocale, bookLocale);
        author.setId(id);
        return id;
    }

    @Override
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

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Author author) {

        final Locale authorLocale =
                author.getLocale(context, AppLocale.getInstance().getUserLocale(context));

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
            stmt.bindString(1, author.getFamilyName());
            stmt.bindString(2, BaseDaoImpl
                    .encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(3, author.getGivenNames());
            stmt.bindString(4, BaseDaoImpl
                    .encodeOrderByColumn(author.getGivenNames(), authorLocale));
            stmt.bindBoolean(5, author.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                author.setId(iId);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Author author) {

        final Locale authorLocale =
                author.getLocale(context, AppLocale.getInstance().getUserLocale(context));

        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_AUTHOR_FAMILY_NAME, author.getFamilyName());
        cv.put(DBKeys.KEY_AUTHOR_FAMILY_NAME_OB,
               BaseDaoImpl.encodeOrderByColumn(author.getFamilyName(), authorLocale));
        cv.put(DBKeys.KEY_AUTHOR_GIVEN_NAMES, author.getGivenNames());
        cv.put(DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB,
               BaseDaoImpl.encodeOrderByColumn(author.getGivenNames(), authorLocale));
        cv.put(DBKeys.KEY_AUTHOR_IS_COMPLETE, author.isComplete());

        return 0 < mDb.update(TBL_AUTHORS.getName(), cv, DBKeys.KEY_PK_ID + "=?",
                              new String[]{String.valueOf(author.getId())});
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Author author) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
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

    @Override
    public void merge(@NonNull final Context context,
                      @NonNull final Author source,
                      final long destId)
            throws DaoWriteException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // TOC is easy: just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(DBKeys.KEY_FK_AUTHOR, destId);
            mDb.update(TBL_TOC_ENTRIES.getName(), cv, DBKeys.KEY_FK_AUTHOR + "=?",
                       new String[]{String.valueOf(source.getId())});

            // the books must be done one by one, as we need to prevent duplicate authors
            // e.g. suppose we have a book with author
            // a1@pos1
            // a2@pos2
            // and we want to replace a1 with a2, we cannot simply do a mass update.
            final Author destination = getById(destId);

            try (BookDao bookDao = new BookDao(context, TAG)) {
                for (final long bookId : getBookIds(source.getId())) {
                    final Book book = Book.from(bookId, bookDao);

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
                    bookDao.insertBookAuthors(context, bookId, destList, true,
                                              book.getLocale(context));
                }
            }
            // delete the obsolete source.
            delete(context, source);

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    @Override
    public void purge() {
        try (SynchronizedStatement stmt = mDb.compileStatement(PURGE)) {
            stmt.executeUpdateDelete();
        }
    }
}
