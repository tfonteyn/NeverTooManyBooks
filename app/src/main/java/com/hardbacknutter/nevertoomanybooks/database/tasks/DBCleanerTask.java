/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Data cleaning. Done on each startup.
 */
public class DBCleanerTask
        extends TaskBase<Boolean> {

    private static final String TAG = "DBCleanerTask";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task finished listener.
     * @param db           Database Access
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public DBCleanerTask(final int taskId,
                         @NonNull final DAO db,
                         @NonNull final TaskListener<Boolean> taskListener) {
        super(taskId, taskListener);
        mDb = db;
    }

    @WorkerThread
    @Override
    protected Boolean doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        publishProgress(new ProgressMessage(getTaskId(), context.getString(
                R.string.progress_msg_optimizing)));
        try {
            final DBCleaner cleaner = new DBCleaner(mDb);

            // do a mass update of any languages not yet converted to ISO 639-2 codes
            cleaner.languages(context);

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
        }
    }
}
