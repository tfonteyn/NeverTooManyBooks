/*
 * @Copyright 2020 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Data cleaning. Done on each startup.
 */
public class DBCleanerTask
        extends LTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "DBCleanerTask";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /**
     * Constructor.
     *
     * @param db           Database Access
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public DBCleanerTask(@NonNull final DAO db,
                         @NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_CLEANER, taskListener);
        mDb = db;
    }

    @WorkerThread
    @Override
    protected Boolean doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG);
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        publishProgress(new ProgressMessage(getTaskId(), context.getString(
                R.string.progress_msg_optimizing)));
        try {
            final DBCleaner cleaner = new DBCleaner(mDb);

            // do a mass update of any languages not yet converted to ISO 639-2 codes
            cleaner.languages(context, userLocale);

            // make sure there are no 'T' separators in datetime fields
            cleaner.dates();

            // validate booleans to have 0/1 content (could do just ALL_TABLES)
            cleaner.booleanColumns(DBDefinitions.TBL_BOOKS,
                                   DBDefinitions.TBL_AUTHORS,
                                   DBDefinitions.TBL_SERIES);

            // clean/correct style UUID's on Bookshelves for deleted styles.
            cleaner.bookshelves(context);

            // re-sort positional links
            cleaner.bookAuthor(context);
            cleaner.bookSeries(context);
            cleaner.bookPublisher(context);

            //TEST: we only check & log for now, but don't update yet...
            // we need to test with bad data
            cleaner.maybeUpdate(context, true);
            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return false;

        } finally {
            // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
            StartupViewModel.scheduleMaintenance(context, false);
        }
    }
}
