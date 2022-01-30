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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveWriterTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

@SuppressWarnings("WeakerAccess")
public class ExportViewModel
        extends ViewModel {

    private static final List<ArchiveEncoding> ENCODINGS =
            Arrays.asList(ArchiveEncoding.Zip,
                          ArchiveEncoding.Csv,
                          ArchiveEncoding.Json,
                          ArchiveEncoding.Xml,
                          ArchiveEncoding.SqLiteDb);

    private static final int[] ENCODING_RES_IDS = {
            R.string.lbl_archive_type_backup_zip,
            R.string.lbl_archive_type_csv,
            R.string.lbl_archive_type_json,
            R.string.lbl_archive_type_xml,
            R.string.lbl_archive_type_db};

    /** Export configuration. */
    @NonNull
    private final ExportHelper mExportHelper = new ExportHelper();
    private final ArchiveWriterTask mWriterTask = new ArchiveWriterTask();
    private boolean mQuickOptionsAlreadyShown;

    @NonNull
    ExportHelper getExportHelper() {
        return mExportHelper;
    }

    boolean isQuickOptionsAlreadyShown() {
        return mQuickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown(
            @SuppressWarnings("SameParameterValue") final boolean quickOptionsAlreadyShown) {
        mQuickOptionsAlreadyShown = quickOptionsAlreadyShown;
    }


    /**
     * Get the {@link ArchiveEncoding} for the given position in the dropdown menu.
     *
     * @param position to get
     *
     * @return encoding
     */
    @NonNull
    ArchiveEncoding getEncoding(final int position) {
        return ENCODINGS.get(position);
    }

    /**
     * Get the list of options (and initial position) for the drop down menu
     * for the archive format.
     *
     * @param context Current context
     *
     * @return initial position + list
     */
    @NonNull
    Pair<Integer, ArrayList<String>> getFormatOptions(@NonNull final Context context) {
        final ArchiveEncoding currentEncoding = mExportHelper.getEncoding();
        int initialPos = 0;
        final ArrayList<String> list = new ArrayList<>();
        for (int i = 0; i < ENCODINGS.size(); i++) {
            final ArchiveEncoding encoding = ENCODINGS.get(i);
            if (encoding == currentEncoding) {
                initialPos = i;
            }
            list.add(context.getString(ENCODING_RES_IDS[i]));
        }

        return new Pair<>(initialPos, list);
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mWriterTask.onProgressUpdate();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ExportResults>>> onExportCancelled() {
        return mWriterTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onExportFailure() {
        return mWriterTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ExportResults>>> onExportFinished() {
        return mWriterTask.onFinished();
    }

    boolean isExportRunning() {
        return mWriterTask.isRunning();
    }

    void startExport(@NonNull final Uri uri) {
        mExportHelper.setUri(uri);
        mWriterTask.start(mExportHelper);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mWriterTask.getTaskId()) {
            mWriterTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
