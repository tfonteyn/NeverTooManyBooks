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
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

public class MaintenanceDaoImpl
        extends BaseDaoImpl
        implements MaintenanceDao {

    /** Log tag. */
    private static final String TAG = "MaintenanceDaoImpl";


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
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final Locale userLocale = userLocales.get(0);
        final List<Locale> locales = LocaleListUtils.asList(userLocales);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final ReorderHelper reorderHelper = new ReorderHelper(locales);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            int i;

            i = serviceLocator.getAuthorDao().rebuildOrderByColumns(userLocale);
            if (i > 0) {
                LoggerFactory.getLogger().w(TAG, "Authors rebuild: " + i);
            }
            i = serviceLocator.getBookDao().rebuildOrderByColumns(context, userLocale,
                                                                  reorderHelper);
            if (i > 0) {
                LoggerFactory.getLogger().w(TAG, "Books rebuild: " + i);
            }
            i = serviceLocator.getSeriesDao().rebuildOrderByColumns(context, userLocale,
                                                                    reorderHelper);
            if (i > 0) {
                LoggerFactory.getLogger().w(TAG, "Series rebuild: " + i);
            }
            i = serviceLocator.getPublisherDao().rebuildOrderByColumns(context, userLocale,
                                                                       reorderHelper);
            if (i > 0) {
                LoggerFactory.getLogger().w(TAG, "Publishers rebuild: " + i);
            }
            i = serviceLocator.getTocEntryDao().rebuildOrderByColumns(context, userLocale,
                                                                      reorderHelper);
            if (i > 0) {
                LoggerFactory.getLogger().w(TAG, "TocEntry rebuild: " + i);
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
