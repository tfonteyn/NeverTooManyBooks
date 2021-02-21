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

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Note the insert + update method reside in {@link BookDao} as they are only
 * ever needed there + as they are always called in a loop, benefit from the caching of statements.
 */
public final class TocEntryDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "TocEntryDao";

    /** All Books (id only) for a given TocEntry. */
    private static final String SELECT_BOOK_IDS_BY_TOC_ENTRY_ID =
            SELECT_ + KEY_FK_BOOK + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
            + _WHERE_ + KEY_FK_TOC_ENTRY + "=?";

    /** All Books (id+title (as a pair) only) for a given TocEntry. */
    private static final String SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID =
            SELECT_ + TBL_BOOKS.dot(KEY_PK_ID) + ',' + TBL_BOOKS.dot(KEY_TITLE)
            + _FROM_ + TBL_BOOK_TOC_ENTRIES.ref() + TBL_BOOK_TOC_ENTRIES.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_TOC_ENTRY) + "=?"
            + _ORDER_BY_ + TBL_BOOKS.dot(KEY_TITLE_OB);

    /** {@link TocEntry}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_TOC_ENTRIES.getName();

    /**
     * Get the id of a {@link TocEntry} by Title.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Search KEY_TITLE_OB on both "The Title" and "Title, The"
     */
    private static final String FIND_ID =
            SELECT_ + KEY_PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
            + _WHERE_ + KEY_FK_AUTHOR + "=?"
            + " AND (" + KEY_TITLE_OB + "=? " + _COLLATION
            + _OR_ + KEY_TITLE_OB + "=?" + _COLLATION + ')';

    /** Delete a {@link TocEntry}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + KEY_PK_ID + "=?";

    /** Singleton. */
    private static TocEntryDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private TocEntryDao(@NonNull final Context context,
                        @NonNull final String logTag) {
        super(context, logTag);
    }

    public static TocEntryDao getInstance() {
        if (sInstance == null) {
            sInstance = new TocEntryDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param context      Current context
     * @param tocEntry     to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    public long fixId(@NonNull final Context context,
                      @NonNull final TocEntry tocEntry,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {

        AuthorDao.getInstance().fixId(context, tocEntry.getPrimaryAuthor(),
                                      lookupLocale, bookLocale);

        final long id = find(context, tocEntry, lookupLocale, bookLocale);
        tocEntry.setId(id);
        return id;
    }

    /**
     * Return the TocEntry id. The incoming object is not modified.
     * Note that the publication year is NOT used for comparing, under the assumption that
     * two search-sources can give different dates by mistake.
     *
     * @param context      Current context
     * @param tocEntry     tocEntry to search for
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long find(@NonNull final Context context,
                     @NonNull final TocEntry tocEntry,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale) {

        final Locale tocLocale;
        if (lookupLocale) {
            tocLocale = tocEntry.getLocale(context, bookLocale);
        } else {
            tocLocale = bookLocale;
        }

        final String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindLong(1, tocEntry.getPrimaryAuthor().getId());
            stmt.bindString(2, encodeOrderByColumn(tocEntry.getTitle(), tocLocale));
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get all TOC entries; mainly for the purpose of backups.
     *
     * @return Cursor over all TOC entries
     */
    @NonNull
    public Cursor fetchAll() {
        return mDb.rawQuery(SELECT_ALL, null);
    }

    /**
     * Return a list of paired book-id and book-title 's for the given TOC id.
     *
     * @param id TOC id
     *
     * @return list of id/titles of books.
     */
    @NonNull
    public List<Pair<Long, String>> getBookTitles(@IntRange(from = 1) final long id) {
        final List<Pair<Long, String>> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID,
                                          new String[]{String.valueOf(id)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Pair<>(rowData.getLong(KEY_PK_ID),
                                    rowData.getString(KEY_TITLE)));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's (most often just the one) in which this TocEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIds(final long tocId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_IDS_BY_TOC_ENTRY_ID,
                                          new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Delete the passed {@link TocEntry}.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final TocEntry tocEntry) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
            stmt.bindLong(1, tocEntry.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            tocEntry.setId(0);
            try (BookDao bookDao = new BookDao(context, TAG)) {
                bookDao.repositionTocEntries(context);
            }
        }
        return rowsAffected == 1;
    }
}
