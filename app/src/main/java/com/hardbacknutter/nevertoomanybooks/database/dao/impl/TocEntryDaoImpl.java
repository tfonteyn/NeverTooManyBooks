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

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.DaoLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Note the insert + update method reside in {@link BookDao} as they are only
 * ever needed there + as they are always called in a loop, benefit from the caching of statements.
 */
public class TocEntryDaoImpl
        extends BaseDaoImpl
        implements TocEntryDao {

    /** Log tag. */
    private static final String TAG = "TocEntryDaoImpl";

    /** All Books (id only) for a given TocEntry. */
    private static final String SELECT_BOOK_IDS_BY_TOC_ENTRY_ID =
            SELECT_ + DBKeys.KEY_FK_BOOK + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
            + _WHERE_ + DBKeys.KEY_FK_TOC_ENTRY + "=?";

    /** All Books (id+title (as a pair) only) for a given TocEntry. */
    private static final String SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID =
            SELECT_ + TBL_BOOKS.dot(DBKeys.KEY_PK_ID) + ',' + TBL_BOOKS.dot(DBKeys.KEY_TITLE)
            + _FROM_ + TBL_BOOK_TOC_ENTRIES.ref() + TBL_BOOK_TOC_ENTRIES.join(TBL_BOOKS)
            + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(DBKeys.KEY_FK_TOC_ENTRY) + "=?"
            + _ORDER_BY_ + TBL_BOOKS.dot(DBKeys.KEY_TITLE_OB);

    /** {@link TocEntry}, all columns. */
    private static final String SELECT_ALL = "SELECT * FROM " + TBL_TOC_ENTRIES.getName();

    /**
     * Get the id of a {@link TocEntry} by Title.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     * Search KEY_TITLE_OB on both "The Title" and "Title, The"
     */
    private static final String FIND_ID =
            SELECT_ + DBKeys.KEY_PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
            + _WHERE_ + DBKeys.KEY_FK_AUTHOR + "=?"
            + " AND (" + DBKeys.KEY_TITLE_OB + "=? " + _COLLATION
            + _OR_ + DBKeys.KEY_TITLE_OB + "=?" + _COLLATION + ')';

    /** Delete a {@link TocEntry}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public TocEntryDaoImpl(@NonNull final Context context) {
        super(context, TAG);
    }

    @Override
    public long fixId(@NonNull final Context context,
                      @NonNull final TocEntry tocEntry,
                      final boolean lookupLocale,
                      @NonNull final Locale bookLocale) {

        DaoLocator.getInstance().getAuthorDao()
                  .fixId(context, tocEntry.getPrimaryAuthor(), lookupLocale, bookLocale);

        final long id = find(context, tocEntry, lookupLocale, bookLocale);
        tocEntry.setId(id);
        return id;
    }

    @Override
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
            stmt.bindString(2, BaseDaoImpl.encodeOrderByColumn(tocEntry.getTitle(), tocLocale));
            stmt.bindString(3, BaseDaoImpl.encodeOrderByColumn(obTitle, tocLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public Cursor fetchAll() {
        return mDb.rawQuery(SELECT_ALL, null);
    }

    @Override
    @NonNull
    public List<Pair<Long, String>> getBookTitles(@IntRange(from = 1) final long id) {
        final List<Pair<Long, String>> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_BOOK_TITLES_BY_TOC_ENTRY_ID,
                                          new String[]{String.valueOf(id)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Pair<>(rowData.getLong(DBKeys.KEY_PK_ID),
                                    rowData.getString(DBKeys.KEY_TITLE)));
            }
        }
        return list;
    }

    @Override
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

    @Override
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
