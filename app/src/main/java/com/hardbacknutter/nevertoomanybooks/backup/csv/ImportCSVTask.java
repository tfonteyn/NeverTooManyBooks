/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.ProgressListenerBase;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class ImportCSVTask
        extends TaskBase<Integer> {

    @NonNull
    private final File mFile;
    @NonNull
    private final Importer mImporter;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param settings     the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public ImportCSVTask(@NonNull final Context context,
                         @NonNull final File file,
                         @NonNull final ImportOptions settings,
                         @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_CSV_IMPORT, taskListener);
        mFile = file;
        mImporter = new CsvImporter(context, settings);
    }

    @Override
    @WorkerThread
    @Nullable
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("ImportCSVTask");

        Context userContext = App.getFakeUserContext();
        Locale userLocale = LocaleUtils.getPreferredLocale();

        try (FileInputStream is = new FileInputStream(mFile)) {
            mImporter.doBooks(userContext, userLocale,
                              is, new LocalCoverFinder(mFile.getParent()),
                              new ProgressListenerBase() {

                                  @Override
                                  public void onProgress(final int absPosition,
                                                         @Nullable final Object message) {
                                      Object[] values = {message};
                                      publishProgress(new TaskProgressMessage(mTaskId, getMax(),
                                                                              absPosition, values));
                                  }

                                  @Override
                                  public boolean isCancelled() {
                                      return ImportCSVTask.this.isCancelled();
                                  }
                              }
                             );

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;

        } catch (@NonNull final ImportException e) {
            Logger.error(this, e);
            mException = e;

        } finally {
            try {
                mImporter.close();
            } catch (@NonNull final IOException ignore) {
            }
        }
        return null;
    }
}
