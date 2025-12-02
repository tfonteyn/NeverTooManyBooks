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
package com.hardbacknutter.nevertoomanybooks.database.tasks;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Rebuild all OrderBy columns. Can take several seconds.
 */
public class RebuildTitleOrderByColumnTask
        extends LTask<Boolean>
        implements StartupViewModel.StartupTask {

    /** Log tag. */
    private static final String TAG = "RebuildOrderByTitle";

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RebuildTitleOrderByColumnTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_REBUILD_TITLE_OB, TAG, taskListener);
    }

    @Override
    @UiThread
    public void start() {
        execute();
    }

    @Override
    @WorkerThread
    @NonNull
    protected Boolean doWork() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // incorrect progress message, but it's half-true.
        publishProgress(1, context.getString(R.string.progress_msg_rebuilding_search_index));

        try {
            rebuildOrderByTitleColumns(context);
            return true;

        } finally {
            // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
            StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_TITLE_OB, false);
        }
    }

    /**
     * Repopulate all OrderBy columns.
     * Cleans up whitespace and non-ascii characters.
     * Optional reordering or restoring
     *
     * @param context Current context
     */
    @WorkerThread
    private void rebuildOrderByTitleColumns(@NonNull final Context context) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final Locale userLocale = userLocales.get(0);
        final List<Locale> locales = LocaleListUtils.asList(userLocales);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final ReorderHelper reorderHelper = new ReorderHelper(locales);

        final SynchronizedDb db = serviceLocator.getDb();

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
