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
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.MaintenanceDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.TocEntryDao;
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

    @NonNull
    private final Supplier<AuthorDao> authorDaoSupplier;
    @NonNull
    private final Supplier<SeriesDao> seriesDaoSupplier;
    @NonNull
    private final Supplier<PublisherDao> publisherDaoSupplier;
    @NonNull
    private final Supplier<TocEntryDao> tocEntryDaoSupplier;
    @NonNull
    private final Supplier<AppLocale> appLocaleSupplier;
    @NonNull
    private final Supplier<ReorderHelper> reorderHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                    Underlying database
     * @param authorDaoSupplier     deferred supplier for the {@link AuthorDao}
     * @param seriesDaoSupplier     deferred supplier for the {@link SeriesDao}
     * @param publisherDaoSupplier  deferred supplier for the {@link PublisherDao}
     * @param tocEntryDaoSupplier   deferred supplier for the {@link TocEntryDao}
     * @param appLocaleSupplier     deferred supplier for the {@link AppLocale}
     * @param reorderHelperSupplier deferred supplier for the {@link ReorderHelper}
     */
    public MaintenanceDaoImpl(@NonNull final SynchronizedDb db,
                              @NonNull final Supplier<AuthorDao> authorDaoSupplier,
                              @NonNull final Supplier<SeriesDao> seriesDaoSupplier,
                              @NonNull final Supplier<PublisherDao> publisherDaoSupplier,
                              @NonNull final Supplier<TocEntryDao> tocEntryDaoSupplier,
                              @NonNull final Supplier<AppLocale> appLocaleSupplier,
                              @NonNull final Supplier<ReorderHelper> reorderHelperSupplier) {
        super(db, TAG);
        this.authorDaoSupplier = authorDaoSupplier;
        this.seriesDaoSupplier = seriesDaoSupplier;
        this.publisherDaoSupplier = publisherDaoSupplier;
        this.tocEntryDaoSupplier = tocEntryDaoSupplier;
        this.appLocaleSupplier = appLocaleSupplier;
        this.reorderHelperSupplier = reorderHelperSupplier;
    }

    @Override
    @WorkerThread
    public void purge() {
        final Logger logger = LoggerFactory.getLogger();
        //noinspection CheckStyle
        try {
            int i;
            i = seriesDaoSupplier.get().purge();
            logger.w(TAG, "Purged Series: " + i);
            i = authorDaoSupplier.get().purge();
            logger.w(TAG, "Purged Author: " + i);
            i = publisherDaoSupplier.get().purge();
            logger.w(TAG, "Purged Publishers: " + i);
            i = tocEntryDaoSupplier.get().purge();
            logger.w(TAG, "Purged TocEntries: " + i);

            db.analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad but NOT fatal.
            logger.e(TAG, e);
        }
    }

    @Override
    @WorkerThread
    public void rebuildOrderByTitleColumns(@NonNull final Context context) {
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final List<Locale> locales = LocaleListUtils.asList(context);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            try (Cursor cursor = db.rawQuery(BOOK_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(BOOK_REBUILD)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);
                    final Locale bookLocale = appLocaleSupplier
                            .get()
                            .getLocale(context, cursor.getString(3))
                            .orElse(userLocale);
                    final String rebuildObTitle = reorderHelperSupplier
                            .get().reorderForSorting(context, title, bookLocale, locales);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rebuildObTitle)) {
                        stmt.bindString(1, SqlEncode.orderByColumn(rebuildObTitle, bookLocale));
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                    }
                }
            }

            // We should use the locale from the 1st book in the series...
            // but that is a huge overhead so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(SERIES_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(SERIES_REBUILD)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rebuildObTitle = reorderHelperSupplier
                            .get().reorderForSorting(context, title, userLocale, locales);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rebuildObTitle)) {
                        stmt.bindString(1, SqlEncode.orderByColumn(rebuildObTitle, userLocale));
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                    }
                }
            }

            // A publisher is not linked to a Locale, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(PUBLISHERS_NAMES, null);
                 SynchronizedStatement stmt = db.compileStatement(PUBLISHERS_REBUILD)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rebuildObTitle = reorderHelperSupplier
                            .get().reorderForSorting(context, title, userLocale, locales);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rebuildObTitle)) {
                        stmt.bindString(1, SqlEncode.orderByColumn(rebuildObTitle, userLocale));
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
                    }
                }
            }

            // We should use primary book or Author Locale...
            // but that is a huge overhead, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(TOC_ENTRY_TITLES, null);
                 SynchronizedStatement stmt = db.compileStatement(TOC_REBUILD)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final String title = cursor.getString(1);
                    final String currentObTitle = cursor.getString(2);

                    final String rebuildObTitle = reorderHelperSupplier
                            .get().reorderForSorting(context, title, userLocale, locales);

                    // only update the database if actually needed.
                    if (!currentObTitle.equals(rebuildObTitle)) {
                        stmt.bindString(1, SqlEncode.orderByColumn(rebuildObTitle, userLocale));
                        stmt.bindLong(2, id);
                        stmt.executeUpdateDelete();
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
    }
}
