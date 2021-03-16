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
package com.hardbacknutter.nevertoomanybooks.database.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Data cleaning. Done on each startup.
 */
public class DBCleanerTask
        extends LTask<Boolean>
        implements StartupViewModel.StartupTask {

    /** Log tag. */
    private static final String TAG = "DBCleanerTask";

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public DBCleanerTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_CLEANER, TAG, taskListener);
    }

    @Override
    @UiThread
    public void start() {
        execute();
    }

    @WorkerThread
    @Override
    protected Boolean doWork(@NonNull final Context context) {
        publishProgress(1, context.getString(R.string.progress_msg_optimizing));

        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);
        try (DBCleaner cleaner = new DBCleaner();
             BookDao bookDao = new BookDao(TAG)) {

            // do a mass update of any languages not yet converted to ISO 639-2 codes
            cleaner.languages(context, userLocale);

            // make sure there are no 'T' separators in datetime fields
            cleaner.datetimeFormat();

            // validate booleans to have 0/1 content (could do just ALL_TABLES)
            cleaner.booleanColumns(DBDefinitions.TBL_BOOKS,
                                   DBDefinitions.TBL_AUTHORS,
                                   DBDefinitions.TBL_SERIES);

            // clean/correct style UUID's on Bookshelves for deleted styles.
            cleaner.bookshelves(context);

            //TEST: we only check & log for now, but don't update yet...
            // we need to test with bad data
            cleaner.bookBookshelf(true);


            // re-sort positional links - theoretically this should never be needed... flw.
            int modified = bookDao.repositionAuthor(context);
            modified += bookDao.repositionSeries(context);
            modified += bookDao.repositionPublishers(context);
            modified += bookDao.repositionTocEntries(context);
            if (modified > 0) {
                Logger.warn(context, TAG, "bookDao.reposition modified=" + modified);
            }
            return true;

        } finally {
            // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
            StartupViewModel.scheduleMaintenance(false);
        }
    }
}
