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

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class MaintenanceDaoImpl
        extends BaseDaoImpl
        implements MaintenanceDao {

    /** Log tag. */
    private static final String TAG = "MaintenanceDaoImpl";

    /** All Book titles for a rebuild of the {@link DBKey#TITLE_OB} column. */
    private static final String BOOK_TITLES =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.TITLE
            + ',' + DBKey.TITLE_OB
            + ',' + DBKey.LANGUAGE
            + _FROM_ + TBL_BOOKS.getName();
    /** All Series for a rebuild of the {@link DBKey.SERIES#TITLE_OB} column. */
    private static final String SERIES_TITLES =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.SERIES.TITLE
            + ',' + DBKey.SERIES.TITLE_OB
            + _FROM_ + TBL_SERIES.getName();
    /** All Publishers for a rebuild of the {@link DBKey.PUBLISHER#NAME_OB} column. */
    private static final String PUBLISHERS_NAMES =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.PUBLISHER.NAME
            + ',' + DBKey.PUBLISHER.NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName();

    /** All TocEntry titles for a rebuild of the {@link DBKey#TITLE_OB} column. */
    private static final String TOC_ENTRY_TITLES =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.TITLE
            + ',' + DBKey.TITLE_OB
            + _FROM_ + TBL_TOC_ENTRIES.getName();

    private static final String BOOK_REBUILD =
            UPDATE_ + TBL_BOOKS.getName() + _SET_ + DBKey.TITLE_OB + "=?"
            + _WHERE_ + DBKey.PK_ID + "=?";
    private static final String SERIES_REBUILD =
            UPDATE_ + TBL_SERIES.getName() + _SET_ + DBKey.SERIES.TITLE_OB + "=?"
            + _WHERE_ + DBKey.PK_ID + "=?";
    private static final String PUBLISHERS_REBUILD =
            UPDATE_ + TBL_PUBLISHERS.getName() + _SET_ + DBKey.PUBLISHER.NAME_OB + "=?"
            + _WHERE_ + DBKey.PK_ID + "=?";
    private static final String TOC_REBUILD =
            UPDATE_ + TBL_TOC_ENTRIES.getName() + _SET_ + DBKey.TITLE_OB + "=?"
            + _WHERE_ + DBKey.PK_ID + "=?";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public MaintenanceDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @WorkerThread
    public void purge() {
        final Logger logger = LoggerFactory.getLogger();
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        //noinspection CheckStyle
        try {
            int i;
            i = serviceLocator.getSeriesDao().purge();
            if (i > 0) {
                logger.w(TAG, "Purged Series: " + i);
            }
            i = serviceLocator.getAuthorDao().purge();
            if (i > 0) {
                logger.w(TAG, "Purged Author: " + i);
            }
            i = serviceLocator.getPublisherDao().purge();
            if (i > 0) {
                logger.w(TAG, "Purged Publishers: " + i);
            }
            i = serviceLocator.getTocEntryDao().purge();
            if (i > 0) {
                logger.w(TAG, "Purged TocEntries: " + i);
            }

            db.analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad but NOT fatal.
            logger.e(TAG, e);
        }
    }

    @Override
    @WorkerThread
    public void rebuildOrderByTitleColumns(@NonNull final Context context) {
        final Logger logger = LoggerFactory.getLogger();
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final List<Locale> locales = LocaleListUtils.asList(context);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            final AppLocale appLocale = serviceLocator.getAppLocale();
            final ReorderHelper reorderHelper = serviceLocator.getReorderHelper();

            try (Cursor cursor = db.rawQuery(BOOK_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(BOOK_REBUILD)) {
                int i = 0;
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final Locale bookLocale = appLocale
                            .getLocale(context, cursor.getString(3))
                            .orElse(userLocale);
                    final String rTitle = reorderHelper
                            .reorderForSorting(context, title, bookLocale, locales);
                    final String rObTitle = SqlEncode.orderByColumn(rTitle, bookLocale);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rObTitle)) {
                        stmt.bindString(1, rObTitle);
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                        i++;
                    }
                }
                if (i > 0) {
                    logger.w(TAG, "Books rebuild: " + i);
                }
            }

            // We should use the locale from the 1st book in the series...
            // but that is a huge overhead so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(SERIES_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(SERIES_REBUILD)) {
                int i = 0;
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rTitle = reorderHelper
                            .reorderForSorting(context, title, userLocale, locales);
                    final String rObTitle = SqlEncode.orderByColumn(rTitle, userLocale);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rObTitle)) {
                        stmt.bindString(1, rObTitle);
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                        i++;
                    }
                }
                if (i > 0) {
                    logger.w(TAG, "Series rebuild: " + i);
                }
            }

            // A publisher is not linked to a Locale, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(PUBLISHERS_NAMES, null);
                 SynchronizedStatement stmt = db.compileStatement(PUBLISHERS_REBUILD)) {
                int i = 0;
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rTitle = reorderHelper
                            .reorderForSorting(context, title, userLocale, locales);
                    final String rObTitle = SqlEncode.orderByColumn(rTitle, userLocale);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rObTitle)) {
                        stmt.bindString(1, rObTitle);
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                        i++;
                    }
                }
                if (i > 0) {
                    logger.w(TAG, "Publishers rebuild: " + i);
                }
            }

            // We should use primary book or Author Locale...
            // but that is a huge overhead, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(TOC_ENTRY_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(TOC_REBUILD)) {
                int i = 0;
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rTitle = reorderHelper
                            .reorderForSorting(context, title, userLocale, locales);
                    final String rObTitle = SqlEncode.orderByColumn(rTitle, userLocale);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rObTitle)) {
                        stmt.bindString(1, rObTitle);
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                        i++;
                    }
                }
                if (i > 0) {
                    logger.w(TAG, "TocEntry rebuild: " + i);
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
    }
}
