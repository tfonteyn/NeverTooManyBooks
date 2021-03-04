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

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.DaoLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithTitle;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class MaintenanceDaoImpl
        extends BaseDaoImpl
        implements MaintenanceDao {

    /** Log tag. */
    private static final String TAG = "MaintenanceDaoImpl";

    /** All Series for a rebuild of the {@link DBKeys#KEY_SERIES_TITLE_OB} column. */
    private static final String SELECT_SERIES_FOR_ORDER_BY_REBUILD =
            // The index of KEY_PK_ID, KEY_SERIES_TITLE, KEY_SERIES_TITLE_OB is hardcoded
            // Don't change!
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_SERIES_TITLE
            + ',' + DBKeys.KEY_SERIES_TITLE_OB
            + _FROM_ + TBL_SERIES.getName();

    /** All Publishers for a rebuild of the {@link DBKeys#KEY_PUBLISHER_NAME_OB} column. */
    private static final String SELECT_PUBLISHERS_FOR_ORDER_BY_REBUILD =
            // The index of KEY_PK_ID, KEY_PUBLISHER_NAME, KEY_PUBLISHER_NAME_OB is hardcoded
            // Don't change!
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_PUBLISHER_NAME
            + ',' + DBKeys.KEY_PUBLISHER_NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName();

    /** All Book titles for a rebuild of the {@link DBKeys#KEY_TITLE_OB} column. */
    private static final String BOOK_TITLES =
            // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_TITLE
            + ',' + DBKeys.KEY_TITLE_OB
            + ',' + DBKeys.KEY_LANGUAGE
            + _FROM_ + TBL_BOOKS.getName();

    /** All TocEntry titles for a rebuild of the {@link DBKeys#KEY_TITLE_OB} column. */
    private static final String TOC_ENTRY_TITLES =
            // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_TITLE
            + ',' + DBKeys.KEY_TITLE_OB
            + _FROM_ + TBL_TOC_ENTRIES.getName();


    /**
     * Constructor.
     *
     * @param context Current context
     */
    public MaintenanceDaoImpl(@NonNull final Context context) {
        super(context, TAG);
    }

    @Override
    public void purge() {

        // Note: purging TocEntry's is automatic due to foreign key cascading.
        // i.e. a TocEntry is linked directly with authors;
        // and linked with books via a link table.

        try {
            final DaoLocator daoLocator = DaoLocator.getInstance();
            daoLocator.getSeriesDao().purge();
            daoLocator.getAuthorDao().purge();
            daoLocator.getPublisherDao().purge();

            mDb.analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad.
            Logger.error(TAG, e);
        }
    }

    @Override
    public void rebuildOrderByTitleColumns(@NonNull final Context context,
                                           final boolean reorder) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final AppLocale localeHelper = AppLocale.getInstance();

            // Books
            String language;
            Locale bookLocale;
            try (Cursor cursor = mDb.rawQuery(BOOK_TITLES, null)) {
                final int langIdx = cursor.getColumnIndex(DBKeys.KEY_LANGUAGE);
                while (cursor.moveToNext()) {
                    language = cursor.getString(langIdx);
                    bookLocale = localeHelper.getLocale(context, language);
                    if (bookLocale == null) {
                        bookLocale = localeHelper.getUserLocale(context);
                    }
                    rebuildOrderByTitleColumns(context, bookLocale, reorder, cursor,
                                               TBL_BOOKS, DBKeys.KEY_TITLE_OB);
                }
            }

            // Series and TOC Entries use the user Locale.
            final Locale userLocale = localeHelper.getUserLocale(context);

            // We should use the locale from the 1st book in the series...
            // but that is a huge overhead.
            try (Cursor cursor = mDb.rawQuery(SELECT_SERIES_FOR_ORDER_BY_REBUILD, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                               TBL_SERIES, DBKeys.KEY_SERIES_TITLE_OB);
                }
            }

            try (Cursor cursor = mDb.rawQuery(SELECT_PUBLISHERS_FOR_ORDER_BY_REBUILD, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                               TBL_PUBLISHERS, DBKeys.KEY_PUBLISHER_NAME_OB);
                }
            }

            // We should use primary book or Author Locale... but that is a huge overhead.
            try (Cursor cursor = mDb.rawQuery(TOC_ENTRY_TITLES, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                               TBL_TOC_ENTRIES, DBKeys.KEY_TITLE_OB);
                }
            }

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Process a <strong>single row</strong> from the cursor.
     *
     * @param context    Current context
     * @param locale     to use for this row
     * @param reorder    flag whether to reorder or not
     * @param cursor     positioned on the row to handle
     * @param table      to update
     * @param domainName to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean rebuildOrderByTitleColumns(@NonNull final Context context,
                                               @NonNull final Locale locale,
                                               final boolean reorder,
                                               @NonNull final Cursor cursor,
                                               @NonNull final TableDefinition table,
                                               @NonNull final String domainName) {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final long id = cursor.getLong(0);
        final String title = cursor.getString(1);
        final String currentObTitle = cursor.getString(2);

        final String rebuildObTitle;
        if (reorder) {
            rebuildObTitle = ItemWithTitle.reorder(context, title, locale);
        } else {
            rebuildObTitle = currentObTitle;
        }

        // only update the database if actually needed.
        if (!currentObTitle.equals(rebuildObTitle)) {
            final ContentValues cv = new ContentValues();
            cv.put(domainName, BaseDaoImpl.encodeOrderByColumn(rebuildObTitle, locale));
            return 0 < mDb.update(table.getName(), cv, DBKeys.KEY_PK_ID + "=?",
                                  new String[]{String.valueOf(id)});
        }

        return true;
    }
}
