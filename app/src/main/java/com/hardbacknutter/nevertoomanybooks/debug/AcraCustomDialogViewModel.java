/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.debug;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;

import org.acra.dialog.CrashReportDialogHelper;
import org.json.JSONException;

@SuppressWarnings("WeakerAccess")
public class AcraCustomDialogViewModel
        extends ViewModel {

    private final ReportWriterTask writerTask = new ReportWriterTask();

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onWriteDataFailure() {
        return writerTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onWriteDataCancelled() {
        return writerTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<Boolean>> onWriteDataFinished() {
        return writerTask.onFinished();
    }

    void start(@NonNull final Uri uri,
               @NonNull final CrashReportDialogHelper crashReportHelper) {
        writerTask.start(uri, crashReportHelper);
    }

    private static class ReportWriterTask
            extends MTask<Boolean> {

        private static final String TAG = "ReportWriterTask";
        private Uri uri;
        private CrashReportDialogHelper crashReportHelper;

        ReportWriterTask() {
            super(R.id.TASK_ID_EXPORT, TAG);
        }

        @UiThread
        void start(@NonNull final Uri uri,
                   @NonNull final CrashReportDialogHelper crashReportHelper) {
            this.uri = uri;
            this.crashReportHelper = crashReportHelper;
            execute();
        }

        @NonNull
        @Override
        protected Boolean doWork()
                throws CancellationException, IOException, FileNotFoundException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

            // Get a temporary file to write to.
            final File tmpFile = new File(context.getCacheDir(), TAG + ".tmp");

            try (OutputStream os = new FileOutputStream(tmpFile);
                 Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE)) {

                bw.write(crashReportHelper.getReportData().toJSON());

            } catch (@NonNull final IOException | JSONException e) {
                LoggerFactory.getLogger().e(TAG, e);
                FileUtils.delete(tmpFile);
                return false;
            }

            // The output file is now properly closed, export it to the user Uri
            try (InputStream is = new FileInputStream(tmpFile);
                 OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    FileUtils.copy(is, os);
                }
            } finally {
                FileUtils.delete(tmpFile);
            }

            return true;
        }
    }
}
