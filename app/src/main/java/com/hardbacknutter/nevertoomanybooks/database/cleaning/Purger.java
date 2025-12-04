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

package com.hardbacknutter.nevertoomanybooks.database.cleaning;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.Purgeable;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

public class Purger {

    private static final String TAG = "Purger";

    private final Logger logger;

    private final SynchronizedDb db;

    private final List<Purgeable> daos = new ArrayList<>();

    public Purger() {
        logger = LoggerFactory.getLogger();

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        db = serviceLocator.getDb();

        daos.add(serviceLocator.getAuthorDao());
        daos.add(serviceLocator.getPublisherDao());
        daos.add(serviceLocator.getSeriesDao());
        daos.add(serviceLocator.getTocEntryDao());
    }

    /**
     * Purge anything that is no longer in use.
     * <p>
     * Purging is no longer done at every occasion where it *might* be needed.
     * It was noticed (in the logs) that it was done far to often. It is now called only:
     * <ul>
     *  <li>Before a (Zip) backup.</li>
     *  <li>After an import of data (all sources).</li>
     * </ul>
     * So orphaned data will stay around a little longer which in fact may be beneficial
     * while entering/correcting a book collection.
     * <p>
     * <strong>All RuntimeException are ignored,
     * but the transaction is rolled back on any error</strong>
     */
    @WorkerThread
    public void purge() {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            for (final Purgeable dao : daos) {
                final int i = dao.purge();
                if (i > 0) {
                    logger.w(TAG, dao.getClass().getName(), "Purged: " + i);
                }
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }

            db.analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad but NOT fatal.
            logger.e(TAG, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }
}
