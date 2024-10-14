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

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
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

    /** All Series for a rebuild of the {@link DBKey#SERIES_TITLE_OB} column. */
    private static final String SELECT_SERIES_FOR_ORDER_BY_REBUILD =
            // The index of PK_ID, SERIES_TITLE, SERIES_TITLE_OB is hardcoded
            // Don't change!
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.SERIES_TITLE
            + ',' + DBKey.SERIES_TITLE_OB
            + _FROM_ + TBL_SERIES.getName();

    /** All Publishers for a rebuild of the {@link DBKey#PUBLISHER_NAME_OB} column. */
    private static final String SELECT_PUBLISHERS_FOR_ORDER_BY_REBUILD =
            // The index of PK_ID, PUBLISHER_NAME, PUBLISHER_NAME_OB is hardcoded
            // Don't change!
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.PUBLISHER_NAME
            + ',' + DBKey.PUBLISHER_NAME_OB
            + _FROM_ + TBL_PUBLISHERS.getName();

    /** All Book titles for a rebuild of the {@link DBKey#TITLE_OB} column. */
    private static final String BOOK_TITLES =
            // The index of PK_ID, TITLE, TITLE_OB is hardcoded - don't change!
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.TITLE
            + ',' + DBKey.TITLE_OB
            + ',' + DBKey.LANGUAGE
            + _FROM_ + TBL_BOOKS.getName();

    /** All TocEntry titles for a rebuild of the {@link DBKey#TITLE_OB} column. */
    private static final String TOC_ENTRY_TITLES =
            // The index of PK_ID, TITLE, TITLE_OB is hardcoded - don't change!
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.TITLE
            + ',' + DBKey.TITLE_OB
            + _FROM_ + TBL_TOC_ENTRIES.getName();

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
     * @param tocEntryDaoSupplier  deferred supplier for the {@link TocEntryDao}
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

        // Books
        String language;
        Locale bookLocale;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            try (Cursor cursor = db.rawQuery(BOOK_TITLES, null)) {
                final int langIdx = cursor.getColumnIndex(DBKey.LANGUAGE);
                while (cursor.moveToNext()) {
                    language = cursor.getString(langIdx);
                    bookLocale = appLocaleSupplier.get().getLocale(context, language)
                                                  .orElse(userLocale);
                    rebuildOrderByTitleColumns(context, bookLocale, locales,
                                               cursor, TBL_BOOKS, DBKey.TITLE_OB);
                }
            }

            // We should use the locale from the 1st book in the series...
            // but that is a huge overhead so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(SELECT_SERIES_FOR_ORDER_BY_REBUILD, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, locales,
                                               cursor, TBL_SERIES, DBKey.SERIES_TITLE_OB);
                }
            }

            // A publisher is not linked to a Locale, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(SELECT_PUBLISHERS_FOR_ORDER_BY_REBUILD, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, locales,
                                               cursor, TBL_PUBLISHERS, DBKey.PUBLISHER_NAME_OB);
                }
            }

            // We should use primary book or Author Locale...
            // but that is a huge overhead, so we use the user-locale directly.
            try (Cursor cursor = db.rawQuery(TOC_ENTRY_TITLES, null)) {
                while (cursor.moveToNext()) {
                    rebuildOrderByTitleColumns(context, userLocale, locales,
                                               cursor, TBL_TOC_ENTRIES, DBKey.TITLE_OB);
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

    /**
     * Process a <strong>single row</strong> from the cursor.
     *
     * @param context    Current context
     * @param locale     the locale of the title
     * @param locales    all user locales to use for parsing
     * @param cursor     positioned on the row to handle
     * @param table      to update
     * @param domainName to update
     */
    @WorkerThread
    private void rebuildOrderByTitleColumns(@NonNull final Context context,
                                            @NonNull final Locale locale,
                                            @NonNull final List<Locale> locales,
                                            @NonNull final Cursor cursor,
                                            @NonNull final TableDefinition table,
                                            @NonNull final String domainName) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                //noinspection CheckStyle
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final long id = cursor.getLong(0);
        final String title = cursor.getString(1);
        final String currentObTitle = cursor.getString(2);

        final String rebuildObTitle = reorderHelperSupplier
                .get().reorderForSorting(context, title, locale, locales);

        // only update the database if actually needed.
        if (!currentObTitle.equals(rebuildObTitle)) {
            final ContentValues cv = new ContentValues();
            cv.put(domainName, SqlEncode.orderByColumn(rebuildObTitle, locale));
            db.update(table.getName(), cv, DBKey.PK_ID + "=?",
                      new String[]{String.valueOf(id)});
        }
    }
}
